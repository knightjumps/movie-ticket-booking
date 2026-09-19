package com.moviebooking.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Mutable inventory record for one physical seat during exactly one show.
 */
public final class ShowSeat {
    private final Seat seat;
    private final BigDecimal price;
    private SeatStatus status = SeatStatus.AVAILABLE;
    private String bookingId;
    private Instant holdExpiresAt;

    public ShowSeat(Seat seat, BigDecimal price) {
        this.seat = Objects.requireNonNull(seat);
        this.price = Objects.requireNonNull(price);
    }

    public Seat seat() {
        return seat;
    }

    public BigDecimal price() {
        return price;
    }

    public SeatStatus status() {
        return status;
    }

    public boolean isAvailableAt(Instant now) {
        expireIfNeeded(now);
        return status == SeatStatus.AVAILABLE;
    }

    void hold(String requestedBookingId, Instant expiry, Instant now) {
        expireIfNeeded(now);
        if (status != SeatStatus.AVAILABLE)
            throw new IllegalStateException("Seat " + seat.label() + " is no longer available");
        status = SeatStatus.HELD;
        bookingId = requestedBookingId;
        holdExpiresAt = expiry;
    }

    void confirm(String requestedBookingId, Instant now) {
        expireIfNeeded(now);
        if (status != SeatStatus.HELD || !requestedBookingId.equals(bookingId))
            throw new IllegalStateException("Seat hold is invalid or expired");
        status = SeatStatus.BOOKED;
        holdExpiresAt = null;
    }

    void release(String requestedBookingId) {
        if (requestedBookingId.equals(bookingId) && status != SeatStatus.BOOKED) clear();
    }

    void cancelConfirmed(String requestedBookingId) {
        if (requestedBookingId.equals(bookingId) && status == SeatStatus.BOOKED) clear();
    }

    private void expireIfNeeded(Instant now) {
        if (status == SeatStatus.HELD && !holdExpiresAt.isAfter(now)) clear();
    }

    private void clear() {
        status = SeatStatus.AVAILABLE;
        bookingId = null;
        holdExpiresAt = null;
    }
}
