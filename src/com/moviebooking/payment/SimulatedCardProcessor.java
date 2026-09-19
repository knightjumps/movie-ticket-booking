package com.moviebooking.payment;

import java.math.BigDecimal;

public final class SimulatedCardProcessor implements PaymentProcessor {
    public PaymentMethod method() {
        return PaymentMethod.CREDIT_CARD;
    }

    public PaymentResult charge(String bookingId, BigDecimal amount) {
        return new PaymentResult(true, "card-" + bookingId);
    }

    public void refund(String reference, BigDecimal amount) {
        System.out.println("Card refund submitted: " + reference + ", amount=" + amount);
    }
}
