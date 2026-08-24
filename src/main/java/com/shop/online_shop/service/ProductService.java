package com.shop.online_shop.service;

import com.shop.online_shop.dto.request.ProductRequest;
import com.shop.online_shop.entity.Category;
import com.shop.online_shop.entity.Product;
import com.shop.online_shop.entity.ProductImage;
import com.shop.online_shop.entity.RoleCode;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.ProductImageRepository;
import com.shop.online_shop.repository.ProductRepository;
import com.shop.online_shop.repository.UserRepository;
import com.shop.online_shop.security.AccessGuard;
import com.shop.online_shop.security.Scope;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.spec.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String MANAGE_ALL = "PRODUCT_MANAGE_ALL";
    private static final String READ_OWN = "PRODUCT_READ_OWN";

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final CartService cartService;
    private final AccessGuard accessGuard;

    /** فیلترهای مشترک همه دامنه‌ها */
    public record ProductFilter(String keyword, Long categoryId,
                                BigDecimal minPrice, BigDecimal maxPrice,
                                Boolean inStockOnly, Long sellerId) {
    }

    // ==================== فهرست ====================

    /**
     * یک نقطه ورود برای هر سه دامنه دید.
     * دامنه تعیین می‌کند چه چیزی دیده شود و چه مجوزی لازم است.
     */
    @Transactional(readOnly = true)
    public Page<Product> list(Scope scope, ProductFilter filter,
                              UserPrincipal me, Pageable pageable) {

        Set<Long> categoryIds = filter.categoryId() != null
                ? categoryService.collectDescendantIds(filter.categoryId())
                : null;

        Specification<Product> spec = Specification
                .where(ProductSpecifications.nameContains(filter.keyword()))
                .and(ProductSpecifications.inCategories(categoryIds))
                .and(ProductSpecifications.priceAtLeast(filter.minPrice()))
                .and(ProductSpecifications.priceAtMost(filter.maxPrice()))
                .and(ProductSpecifications.inStockOnly(filter.inStockOnly()));

        spec = switch (scope) {

            // فهرست عمومی — فقط محصولات فعال، بدون نیاز به توکن
            case PUBLIC -> spec.and(ProductSpecifications.isActive());

            // محصولات خود کاربر — شامل غیرفعال‌ها
            case MINE -> {
                accessGuard.requireAuthority(me, READ_OWN);
                yield spec.and(ProductSpecifications.bySeller(me.getId()));
            }

            // همه محصولات همه فروشندگان — شامل غیرفعال‌ها
            case ALL -> {
                accessGuard.requireAuthority(me, MANAGE_ALL);
                yield spec.and(ProductSpecifications.bySeller(filter.sellerId()));
            }
        };

        return productRepository.findAll(spec, pageable);
    }

    /**
     * جزئیات محصول. محصول غیرفعال تنها برای مالک و مدیر قابل مشاهده است.
     */
    @Transactional(readOnly = true)
    public Product getById(Long id, UserPrincipal me) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("محصول یافت نشد"));

        if (product.isActive()) {
            return product;
        }

        boolean isOwner = me != null && product.getSeller().getId().equals(me.getId());
        boolean canManageAll = accessGuard.has(me, MANAGE_ALL);

        if (!isOwner && !canManageAll) {
            throw ApiException.notFound("محصول یافت نشد");
        }
        return product;
    }

    // ==================== نوشتن ====================

    @Transactional
    public Product create(ProductRequest req, UserPrincipal me) {
        if (productRepository.existsBySku(req.sku().trim())) {
            throw ApiException.conflict("این کد کالا قبلاً ثبت شده است");
        }

        Category category = requireLeafCategory(req.categoryId());
        User seller = resolveSeller(req.sellerId(), me);

        Product saved = productRepository.save(Product.builder()
                .sku(req.sku().trim())
                .name(req.name().trim())
                .description(req.description())
                .price(req.price())
                .stock(req.stock())
                .category(category)
                .seller(seller)
                .active(true)
                .build());

        auditLogService.record(me.getId(), "PRODUCT_CREATED",
                "id: " + saved.getId() + " | seller: " + seller.getId());

        return saved;
    }

    /** مالک محصول هنگام ویرایش تغییر نمی‌کند */
    @Transactional
    public Product update(Long productId, ProductRequest req, UserPrincipal me) {
        Product product = findOrThrow(productId);
        assertCanManage(product, me);

        String newSku = req.sku().trim();
        if (!product.getSku().equals(newSku) && productRepository.existsBySku(newSku)) {
            throw ApiException.conflict("این کد کالا قبلاً ثبت شده است");
        }

        Category category = requireLeafCategory(req.categoryId());

        product.setSku(newSku);
        product.setName(req.name().trim());
        product.setDescription(req.description());
        product.setPrice(req.price());
        product.setStock(req.stock());
        product.setCategory(category);

        auditLogService.record(me.getId(), "PRODUCT_UPDATED",
                "id: " + productId + " | seller: " + product.getSeller().getId());

        return productRepository.save(product);
    }

    @Transactional
    public void deactivate(Long productId, UserPrincipal me) {
        Product product = findOrThrow(productId);
        assertCanManage(product, me);

        product.setActive(false);
        product.setDeactivatedBySuspension(false);
        productRepository.save(product);

        cartService.purgeProduct(productId);

        auditLogService.record(me.getId(), "PRODUCT_DEACTIVATED", "id: " + productId);
    }

    @Transactional
    public Product activate(Long productId, UserPrincipal me) {
        Product product = findOrThrow(productId);
        assertCanManage(product, me);

        product.setActive(true);
        product.setDeactivatedBySuspension(false);

        auditLogService.record(me.getId(), "PRODUCT_ACTIVATED", "id: " + productId);
        return productRepository.save(product);
    }

    // ==================== تصاویر ====================

    @Transactional
    public Product addImage(Long productId, MultipartFile file,
                            boolean makePrimary, UserPrincipal me) {
        Product product = findOrThrow(productId);
        assertCanManage(product, me);

        long current = imageRepository.countByProductId(productId);
        if (current >= Product.MAX_IMAGES) {
            throw ApiException.badRequest(
                    "حداکثر " + Product.MAX_IMAGES + " تصویر برای هر محصول مجاز است");
        }

        String fileName = fileStorageService.store(file);

        boolean primary = makePrimary || current == 0;
        if (primary) {
            imageRepository.clearPrimaryFlags(productId);
            product.getImages().forEach(i -> i.setPrimary(false));
        }

        product.getImages().add(ProductImage.builder()
                .product(product)
                .fileName(fileName)
                .url(fileStorageService.publicUrl(fileName))
                .primary(primary)
                .build());

        return productRepository.save(product);
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId, UserPrincipal me) {
        Product product = findOrThrow(productId);
        assertCanManage(product, me);

        ProductImage image = product.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("تصویر یافت نشد"));

        boolean wasPrimary = image.isPrimary();

        product.getImages().remove(image);
        fileStorageService.delete(image.getFileName());

        if (wasPrimary) {
            product.getImages().stream()
                    .findFirst()
                    .ifPresent(next -> next.setPrimary(true));
        }

        productRepository.save(product);
    }

    @Transactional
    public Product setPrimaryImage(Long productId, Long imageId, UserPrincipal me) {
        Product product = findOrThrow(productId);
        assertCanManage(product, me);

        boolean exists = product.getImages().stream()
                .anyMatch(i -> i.getId().equals(imageId));

        if (!exists) {
            throw ApiException.notFound("تصویر یافت نشد");
        }

        product.getImages().forEach(i -> i.setPrimary(i.getId().equals(imageId)));
        return productRepository.save(product);
    }

    // ==================== کمکی ====================

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("محصول یافت نشد"));
    }

    private Category requireLeafCategory(Long categoryId) {
        Category category = categoryService.getById(categoryId);

        if (!category.isLeaf()) {
            throw ApiException.badRequest(
                    "محصول باید در پایین‌ترین سطح دسته‌بندی ثبت شود");
        }
        return category;
    }

    /**
     * مالک محصول را تعیین می‌کند.
     * دارنده مجوز مدیریت سراسری می‌تواند محصول را به نام فروشنده دیگری ثبت کند؛
     * برای بقیه، مالک همیشه خود فرستنده است.
     */
    private User resolveSeller(Long requestedSellerId, UserPrincipal me) {
        if (requestedSellerId == null || !accessGuard.has(me, MANAGE_ALL)) {
            return userRepository.findById(me.getId())
                    .orElseThrow(() -> ApiException.notFound("کاربر یافت نشد"));
        }

        User seller = userRepository.findById(requestedSellerId)
                .orElseThrow(() -> ApiException.badRequest(
                        "کاربر با شناسه " + requestedSellerId + " یافت نشد"));

        // نقش سفارشی فروشنده‌محور هم پذیرفته می‌شود
        if (!seller.needsSellerApproval()) {
            throw ApiException.badRequest("کاربر انتخاب‌شده فروشنده نیست");
        }
        return seller;
    }

    private void assertCanManage(Product product, UserPrincipal me) {
        accessGuard.assertOwnerOrPrivileged(
                product.getSeller().getId(), me, MANAGE_ALL,
                "این محصول متعلق به شما نیست");
    }
}