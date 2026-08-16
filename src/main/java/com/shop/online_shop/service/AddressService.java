package com.shop.online_shop.service;

import com.shop.online_shop.dto.request.AddressRequest;
import com.shop.online_shop.entity.Address;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.AddressRepository;
import com.shop.online_shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Address> getMyAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    /**
     * همیشه با userId جستجو می‌شود؛ اگر آدرس متعلق به کاربر دیگری باشد
     * اصلاً پیدا نمی‌شود و ۴۰۴ می‌گیرد — نه ۴۰۳ که وجودش را لو بدهد.
     */
    @Transactional(readOnly = true)
    public Address getMyAddress(Long addressId, Long userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> ApiException.notFound("آدرس یافت نشد"));
    }

    @Transactional
    public Address create(AddressRequest req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("کاربر یافت نشد"));

        boolean isFirst = addressRepository.countByUserId(userId) == 0;
        boolean makeDefault = req.setAsDefault() || isFirst;

        if (makeDefault) {
            addressRepository.clearDefaultFlags(userId);
        }

        return addressRepository.save(Address.builder()
                .user(user)
                .title(req.title().trim())
                .province(req.province().trim())
                .city(req.city().trim())
                .fullAddress(req.fullAddress().trim())
                .postalCode(req.postalCode())
                .isDefault(makeDefault)
                .build());
    }

    @Transactional
    public Address update(Long addressId, AddressRequest req, Long userId) {
        Address address = getMyAddress(addressId, userId);

        address.setTitle(req.title().trim());
        address.setProvince(req.province().trim());
        address.setCity(req.city().trim());
        address.setFullAddress(req.fullAddress().trim());
        address.setPostalCode(req.postalCode());

        if (req.setAsDefault() && !address.isDefault()) {
            addressRepository.clearDefaultFlags(userId);
            address.setDefault(true);
        }

        return addressRepository.save(address);
    }

    @Transactional
    public void delete(Long addressId, Long userId) {
        Address address = getMyAddress(addressId, userId);
        boolean wasDefault = address.isDefault();

        addressRepository.delete(address);

        // اگر آدرس پیش‌فرض حذف شد، اولین آدرس باقی‌مانده جایگزین می‌شود
        if (wasDefault) {
            addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefault(true);
                        addressRepository.save(next);
                    });
        }
    }

    @Transactional
    public Address setDefault(Long addressId, Long userId) {
        Address address = getMyAddress(addressId, userId);

        addressRepository.clearDefaultFlags(userId);
        address.setDefault(true);

        return addressRepository.save(address);
    }

    // ==================== مدیر ====================

    @Transactional(readOnly = true)
    public Page<Address> getAllAddresses(Long userId, Pageable pageable) {
        return userId != null
                ? addressRepository.findByUserId(userId, pageable)
                : addressRepository.findAll(pageable);
    }
}