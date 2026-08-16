package com.shop.online_shop.service;

import com.shop.online_shop.entity.SellerProfile;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.ProductRepository;
import com.shop.online_shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final CartService cartService;

    @Transactional(readOnly = true)
    public Page<User> list(UserStatus status, Pageable pageable) {
        return status != null
                ? userRepository.findByRoleNameAndStatus("SELLER", status, pageable)
                : userRepository.findByRoleName("SELLER", pageable);
    }

    @Transactional(readOnly = true)
    public User getById(Long sellerId) {
        User user = userRepository.findById(sellerId)
                .orElseThrow(() -> ApiException.notFound("فروشنده یافت نشد"));

        if (!"SELLER".equals(user.getRole().getName())) {
            throw ApiException.notFound("فروشنده یافت نشد");
        }
        return user;
    }

    @Transactional
    public User approve(Long sellerId, Long reviewerId) {
        User seller = getById(sellerId);

        if (seller.getStatus() == UserStatus.ACTIVE) {
            throw ApiException.badRequest("این فروشنده قبلاً تأیید شده است");
        }

        seller.setStatus(UserStatus.ACTIVE);
        markReviewed(seller, reviewerId);
        seller.getSellerProfile().setRejectionReason(null);

        auditLogService.record(reviewerId, "SELLER_APPROVED", "seller: " + sellerId);
        return userRepository.save(seller);
    }

    @Transactional
    public User reject(Long sellerId, String reason, Long reviewerId) {
        User seller = getById(sellerId);

        if (seller.getStatus() == UserStatus.ACTIVE) {
            throw ApiException.badRequest(
                    "این فروشنده فعال است — برای توقف فعالیت از تعلیق استفاده کنید");
        }

        seller.setStatus(UserStatus.REJECTED);
        markReviewed(seller, reviewerId);
        seller.getSellerProfile().setRejectionReason(reason);

        refreshTokenService.revokeAllForUser(sellerId);
        auditLogService.record(reviewerId, "SELLER_REJECTED",
                "seller: " + sellerId + " | " + reason);

        return userRepository.save(seller);
    }

    /**
     * تعلیق فروشنده — محصولات فعالش غیرفعال و علامت‌گذاری می‌شوند
     * تا موقع رفع تعلیق فقط همان‌ها برگردند.
     */
    @Transactional
    public User suspend(Long sellerId, String reason, Long reviewerId) {
        User seller = getById(sellerId);

        if (seller.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.badRequest("فقط فروشنده فعال قابل تعلیق است");
        }

        seller.setStatus(UserStatus.SUSPENDED);
        markReviewed(seller, reviewerId);
        seller.getSellerProfile().setSuspensionReason(reason);

        int affected = productRepository.suspendSellerProducts(sellerId);
        cartService.purgeSellerProducts(sellerId);
        refreshTokenService.revokeAllForUser(sellerId);

        auditLogService.record(reviewerId, "SELLER_SUSPENDED",
                "seller: " + sellerId + " | products: " + affected + " | " + reason);

        return userRepository.save(seller);
    }

    @Transactional
    public User unsuspend(Long sellerId, Long reviewerId) {
        User seller = getById(sellerId);

        if (seller.getStatus() != UserStatus.SUSPENDED) {
            throw ApiException.badRequest("این فروشنده تعلیق نشده است");
        }

        seller.setStatus(UserStatus.ACTIVE);
        markReviewed(seller, reviewerId);
        seller.getSellerProfile().setSuspensionReason(null);

        int restored = productRepository.restoreSellerProducts(sellerId);

        auditLogService.record(reviewerId, "SELLER_UNSUSPENDED",
                "seller: " + sellerId + " | products restored: " + restored);

        return userRepository.save(seller);
    }

    private void markReviewed(User seller, Long reviewerId) {
        SellerProfile profile = seller.getSellerProfile();
        if (profile == null) {
            throw ApiException.badRequest("پروفایل فروشندگی یافت نشد");
        }
        profile.setReviewedBy(reviewerId);
        profile.setReviewedAt(Instant.now());
    }
}