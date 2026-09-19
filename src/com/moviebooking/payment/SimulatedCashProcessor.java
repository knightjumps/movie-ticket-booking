package com.moviebooking.payment;

import java.math.BigDecimal;

public final class SimulatedCashProcessor implements PaymentProcessor {
    public PaymentMethod method() {
        return PaymentMethod.CASH;
    }

    public PaymentResult charge(String bookingId, BigDecimal amount) {
        return new PaymentResult(true, "cash-" + bookingId);
    }

    public void refund(String reference, BigDecimal amount) {
        System.out.println("Cash refund authorised: " + reference + ", amount=" + amount);
    }
}
