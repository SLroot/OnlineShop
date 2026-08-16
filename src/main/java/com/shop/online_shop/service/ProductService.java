package com.shop.online_shop.service;

import com.shop.online_shop.dto.request.ProductRequest;
import com.shop.online_shop.entity.Category;
import com.shop.online_shop.entity.Product;
import com.shop.online_shop.entity.ProductImage;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.ProductImageRepository;
import com.shop.online_shop.repository.ProductRepository;
import com.shop.online_shop.repository.UserRepository;
import com.shop.online_shop.security.AccessGuard;
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
    private static final String SELLER_ROLE = "SELLER";

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final CartService cartService;
    private final AccessGuard accessGuard;

    // ==================== خواندن عمومی ====================

    @Transactional(readOnly = true)
    public Page<Product> search(String keyword, Long categoryId,
                                BigDecimal minPrice, BigDecimal maxPrice,
                                Boolean inStockOnly, Pageable pageable) {

        Set<Long> categoryIds = categoryId != null
                ? categoryService.collectDescendantIds(categoryId)
                : null;

        Specification<Product> spec = Specification
                .where(ProductSpecifications.isActive())
                .and(ProductSpecifications.nameContains(keyword))
                .and(ProductSpecifications.inCategories(categoryIds))
                .and(ProductSpecifications.priceAtLeast(minPrice))
                .and(ProductSpecifications.priceAtMost(maxPrice))
                .and(ProductSpecifications.inStockOnly(inStockOnly));

        return productRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Product getPublicById(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ApiException.notFound("محصول یافت نشد"));
    }

    // ==================== لیست مدیریتی ====================

    /** محصولات یک فروشنده، شامل غیرفعال‌ها */
    @Transactional(readOnly = true)
    public Page<Product> getOwnProducts(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerId(sellerId, pageable);
    }

    /** برای مدیر: همه محصولات، با فیلتر اختیاری روی فروشنده */
    @Transactional(readOnly = true)
    public Page<Product> getAllForManagement(Long sellerId, Pageable pageable) {
        return sellerId != null
                ? productRepository.findBySellerId(sellerId, pageable)
                : productRepository.findAll(pageable);
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

    /** غیرفعال‌سازی به جای حذف — محصول از سبد همه کاربران هم حذف می‌شود */
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

        // اولین تصویر خودکار اصلی می‌شود
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

        // اگر تصویر اصلی حذف شد، اولین تصویر باقی‌مانده جایگزین می‌شود
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
     * مدیر می‌تواند sellerId بفرستد تا محصول به نام فروشنده دیگری ثبت شود؛
     * فروشنده عادی همیشه مالک محصول خودش است حتی اگر sellerId بفرستد.
     */
    private User resolveSeller(Long requestedSellerId, UserPrincipal me) {
        if (requestedSellerId == null || !me.hasAuthority(MANAGE_ALL)) {
            return userRepository.findById(me.getId())
                    .orElseThrow(() -> ApiException.notFound("کاربر یافت نشد"));
        }

        User seller = userRepository.findById(requestedSellerId)
                .orElseThrow(() -> ApiException.badRequest(
                        "فروشنده با شناسه " + requestedSellerId + " یافت نشد"));

        if (!SELLER_ROLE.equals(seller.getRole().getName())) {
            throw ApiException.badRequest("کاربر انتخاب‌شده فروشنده نیست");
        }
        return seller;
    }

    /** فروشنده فقط روی محصول خودش، مدیر روی همه */
    private void assertCanManage(Product product, UserPrincipal me) {
        accessGuard.assertOwnerOrPrivileged(
                product.getSeller().getId(), me, MANAGE_ALL,
                "این محصول متعلق به شما نیست");
    }
}