package com.moviebooking.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class Booking {
    private final String id;
    private final Customer customer;
    private final Show show;
    private final List<String> seatIds;
    private final BigDecimal totalAmount;
    private final Instant createdAt;
    private BookingStatus status = BookingStatus.PAYMENT_PENDING;
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    private List<Ticket> tickets = List.of();

    public Booking(String id, Customer customer, Show show, List<String> seatIds, BigDecimal totalAmount, Instant createdAt) {
        this.id = id;
        this.customer = customer;
        this.show = show;
        this.seatIds = List.copyOf(seatIds);
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public Customer customer() {
        return customer;
    }

    public Show show() {
        return show;
    }

    public List<String> seatIds() {
        return seatIds;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public BookingStatus status() {
        return status;
    }

    public PaymentStatus paymentStatus() {
        return paymentStatus;
    }

    public List<Ticket> tickets() {
        return tickets;
    }

    public void confirm(List<Ticket> issuedTickets) {
        status = BookingStatus.CONFIRMED;
        paymentStatus = PaymentStatus.SUCCEEDED;
        tickets = List.copyOf(issuedTickets);
    }

    public void decline() {
        status = BookingStatus.PAYMENT_DECLINED;
        paymentStatus = PaymentStatus.DECLINED;
    }

    public void cancel() {
        status = BookingStatus.CANCELLED;
        paymentStatus = PaymentStatus.REFUNDED;
    }

    public void expire() {
        status = BookingStatus.EXPIRED;
    }
}
