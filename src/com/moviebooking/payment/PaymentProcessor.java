package com.moviebooking.payment;

import java.math.BigDecimal;

public interface PaymentProcessor {
    PaymentMethod method();

    PaymentResult charge(String bookingId, BigDecimal amount);

    void refund(String paymentReference, BigDecimal amount);
}
