package com.shop.online_shop.service;

import com.shop.online_shop.entity.Order;
import com.shop.online_shop.entity.OrderStatus;
import com.shop.online_shop.entity.Payment;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.OrderRepository;
import com.shop.online_shop.repository.PaymentRepository;
import com.shop.online_shop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final AuditLogService auditLogService;
    private final SecureRandom random = new SecureRandom();

    /**
     * شبیه‌ساز درگاه پرداخت.
     * در محیط واقعی اینجا به درگاه بانکی متصل می‌شویم و
     * نتیجه از طریق callback برمی‌گردد.
     */
    @Transactional
    public Payment pay(Long orderId, boolean simulateSuccess, UserPrincipal me) {
        Order order = orderService.getOrder(orderId, me);

        if (!order.getUser().getId().equals(me.getId())) {
            throw ApiException.forbidden("این سفارش متعلق به شما نیست");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.badRequest("این سفارش لغو شده است");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.badRequest("این سفارش قبلاً پرداخت شده است");
        }
        if (order.isPaymentExpired()) {
            throw ApiException.badRequest("مهلت پرداخت این سفارش به پایان رسیده است");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("رکورد پرداخت یافت نشد"));

        if (payment.getStatus() == Payment.PaymentStatus.SUCCESS) {
            throw ApiException.badRequest("این سفارش قبلاً پرداخت شده است");
        }

        if (!simulateSuccess) {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason("پرداخت توسط درگاه رد شد");
            paymentRepository.save(payment);

            auditLogService.record(me.getId(), "PAYMENT_FAILED", "order: " + orderId);

            // سفارش باقی می‌ماند تا کاربر دوباره تلاش کند
            throw ApiException.badRequest(
                    "پرداخت ناموفق بود. تا پایان مهلت می‌توانید دوباره تلاش کنید");
        }

        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        payment.setTransactionRef(generateRef());
        payment.setPaidAt(Instant.now());
        payment.setFailureReason(null);
        paymentRepository.save(payment);

        orderService.markAsPaid(order);

        auditLogService.record(me.getId(), "PAYMENT_SUCCESS",
                "order: " + orderId + " | ref: " + payment.getTransactionRef());

        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getByOrder(Long orderId, UserPrincipal me) {
        orderService.getOrder(orderId, me);   // چک دسترسی

        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("پرداختی برای این سفارش ثبت نشده"));
    }

    /** بازپرداخت هنگام لغو سفارش پرداخت‌شده */
    @Transactional
    public void refundIfPaid(Order order, Long actorId) {
        paymentRepository.findByOrderId(order.getId())
                .filter(Payment::isRefundable)
                .ifPresent(payment -> {
                    payment.setStatus(Payment.PaymentStatus.REFUNDED);
                    payment.setRefundedAt(Instant.now());
                    paymentRepository.save(payment);

                    auditLogService.record(actorId, "PAYMENT_REFUNDED",
                            "order: " + order.getId() + " | amount: " + payment.getAmount());
                });
    }

    @Transactional(readOnly = true)
    public Page<Payment> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }

    private String generateRef() {
        return "TRX" + System.currentTimeMillis()
                + String.format("%04d", random.nextInt(10000));
    }
}