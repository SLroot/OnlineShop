package com.shop.online_shop.service;

import com.shop.online_shop.dto.response.CartResponse;
import com.shop.online_shop.entity.Cart;
import com.shop.online_shop.entity.CartItem;
import com.shop.online_shop.entity.Product;
import com.shop.online_shop.entity.ProductImage;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.CartItemRepository;
import com.shop.online_shop.repository.CartRepository;
import com.shop.online_shop.repository.ProductRepository;
import com.shop.online_shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private static final String EMPTY_MESSAGE = "سبد خرید شما خالی است";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // ==================== ساخت اولیه ====================

    /** هنگام ثبت‌نام مشتری صدا زده می‌شود */
    @Transactional
    public Cart createFor(User user) {
        return cartRepository.save(Cart.builder().user(user).build());
    }

    // ==================== خواندن ====================

    /**
     * سبد کاربر را برمی‌گرداند و همزمان اصلاح می‌کند:
     * اقلام غیرفعال یا ناموجود حذف و اقلام مازاد بر موجودی کاهش می‌یابند.
     */
    @Transactional
    public CartResponse getMyCart(Long userId) {
        Cart cart = requireCart(userId);
        List<String> notices = new ArrayList<>();

        reconcile(cart, notices);

        return toResponse(cart, notices);
    }

    /** نمای مدیر — سبد اصلاح نمی‌شود */
    @Transactional(readOnly = true)
    public CartResponse getUserCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("سبد خرید یافت نشد"));

        return toResponse(cart, List.of());
    }

    // ==================== نوشتن ====================

    @Transactional
    public CartResponse addItem(Long userId, Long productId, int quantity) {
        Cart cart = requireCart(userId);
        Product product = requirePurchasableProduct(productId);

        assertNotOwnProduct(product, userId);

        CartItem existing = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), productId)
                .orElse(null);

        int requested = (existing != null ? existing.getQuantity() : 0) + quantity;
        List<String> notices = new ArrayList<>();

        int finalQuantity = capToStock(product, requested, notices);

        if (existing != null) {
            existing.setQuantity(finalQuantity);
            cartItemRepository.save(existing);
        } else {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(finalQuantity)
                    .build());
        }

        cartRepository.save(cart);
        return toResponse(requireCart(userId), notices);
    }

    @Transactional
    public CartResponse updateQuantity(Long userId, Long itemId, int quantity) {
        Cart cart = requireCart(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("این قلم در سبد شما نیست"));

        Product product = item.getProduct();

        if (!product.isActive()) {
            cart.getItems().remove(item);
            cartRepository.save(cart);
            throw ApiException.badRequest("این محصول دیگر در دسترس نیست و از سبد حذف شد");
        }

        List<String> notices = new ArrayList<>();
        item.setQuantity(capToStock(product, quantity, notices));

        cartRepository.save(cart);
        return toResponse(requireCart(userId), notices);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = requireCart(userId);

        boolean removed = cart.getItems().removeIf(i -> i.getId().equals(itemId));

        if (!removed) {
            throw ApiException.notFound("این قلم در سبد شما نیست");
        }

        cartRepository.save(cart);
        return toResponse(requireCart(userId), List.of());
    }

    @Transactional
    public CartResponse clear(Long userId) {
        Cart cart = requireCart(userId);
        cart.getItems().clear();
        cartRepository.save(cart);

        return toResponse(cart, List.of());
    }

    // ==================== پاکسازی هنگام تغییر محصول ====================

    /** محصول غیرفعال شد — از سبد همه کاربران حذف شود */
    @Transactional
    public void purgeProduct(Long productId) {
        int removed = cartItemRepository.deleteByProductId(productId);
        if (removed > 0) {
            log.info("Removed product {} from {} carts", productId, removed);
        }
    }

    /** فروشنده تعلیق شد — همه محصولاتش از سبدها حذف شود */
    @Transactional
    public void purgeSellerProducts(Long sellerId) {
        int removed = cartItemRepository.deleteBySellerId(sellerId);
        if (removed > 0) {
            log.info("Removed products of seller {} from {} cart items", sellerId, removed);
        }
    }

    // ==================== کمکی ====================

    private Cart requireCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    // کاربران قدیمی که هنگام ثبت‌نام سبد نگرفته‌اند
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> ApiException.notFound("کاربر یافت نشد"));
                    return createFor(user);
                });
    }

    private Product requirePurchasableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("محصول یافت نشد"));

        if (!product.isActive()) {
            throw ApiException.badRequest("این محصول در دسترس نیست");
        }
        if (!product.isInStock()) {
            throw ApiException.badRequest("این محصول ناموجود است");
        }
        return product;
    }

    private void assertNotOwnProduct(Product product, Long userId) {
        if (product.getSeller().getId().equals(userId)) {
            throw ApiException.badRequest("نمی‌توانید محصول خودتان را خریداری کنید");
        }
    }

    /** تعداد را به موجودی موجود محدود می‌کند و در صورت کاهش، پیام ثبت می‌کند */
    private int capToStock(Product product, int requested, List<String> notices) {
        int stock = product.getStock();

        if (requested <= stock) {
            return requested;
        }

        notices.add("تعداد «" + product.getName() + "» به " + stock
                + " عدد کاهش یافت — موجودی بیشتری وجود ندارد");
        return stock;
    }

    /**
     * سبد را با وضعیت فعلی محصولات هماهنگ می‌کند:
     * اقلام غیرفعال یا ناموجود حذف و اقلام مازاد بر موجودی کاهش می‌یابند.
     */
    private void reconcile(Cart cart, List<String> notices) {
        boolean changed = false;
        List<CartItem> toRemove = new ArrayList<>();

        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();

            if (!product.isActive() || !product.isInStock()) {
                toRemove.add(item);
                notices.add("«" + product.getName() + "» دیگر در دسترس نیست و از سبد حذف شد");
                continue;
            }

            if (item.getQuantity() > product.getStock()) {
                item.setQuantity(product.getStock());
                notices.add("تعداد «" + product.getName() + "» به " + product.getStock()
                        + " عدد کاهش یافت — موجودی بیشتری وجود ندارد");
                changed = true;
            }
        }

        if (!toRemove.isEmpty()) {
            cart.getItems().removeAll(toRemove);
            changed = true;
        }

        if (changed) {
            cartRepository.save(cart);
        }
    }

    private CartResponse toResponse(Cart cart, List<String> notices) {
        List<CartResponse.CartItemResponse> items = cart.getItems().stream()
                .sorted(Comparator.comparing(CartItem::getAddedAt))
                .map(this::toItemResponse)
                .toList();

        BigDecimal subtotal = items.stream()
                .map(CartResponse.CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalQuantity = items.stream()
                .mapToInt(CartResponse.CartItemResponse::quantity)
                .sum();

        boolean empty = items.isEmpty();

        return new CartResponse(
                cart.getId(),
                items,
                items.size(),
                totalQuantity,
                subtotal,
                empty,
                empty ? EMPTY_MESSAGE : null,
                notices
        );
    }

    private CartResponse.CartItemResponse toItemResponse(CartItem item) {
        Product product = item.getProduct();

        // قیمت هر بار از محصول خوانده می‌شود، نه ذخیره‌شده در سبد
        BigDecimal unitPrice = product.getPrice();
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

        String imageUrl = product.getImages().stream()
                .filter(ProductImage::isPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElseGet(() -> product.getImages().stream()
                        .findFirst()
                        .map(ProductImage::getUrl)
                        .orElse(null));

        return new CartResponse.CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSku(),
                imageUrl,
                unitPrice,
                item.getQuantity(),
                lineTotal,
                product.getStock(),
                item.getQuantity().equals(product.getStock())
        );
    }
}