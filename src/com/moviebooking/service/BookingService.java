package com.moviebooking.service;

import com.moviebooking.catalog.MovieCatalog;
import com.moviebooking.domain.*;
import com.moviebooking.notification.NotificationService;
import com.moviebooking.payment.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Orchestrates use cases; domain objects retain the seat-state invariants.
 */
public final class BookingService {
    private final MovieCatalog catalog;
    private final Map<PaymentMethod, PaymentProcessor> processors;
    private final NotificationService notifications;
    private final Clock clock;
    private final Duration holdDuration;
    private final Map<String, Booking> bookings = new HashMap<>();
    private final Map<String, String> paymentReferences = new HashMap<>();

    public BookingService(MovieCatalog catalog, Collection<PaymentProcessor> processors, NotificationService notifications, Clock clock, Duration holdDuration) {
        this.catalog = catalog;
        this.notifications = notifications;
        this.clock = clock;
        this.holdDuration = holdDuration;
        this.processors = new EnumMap<>(PaymentMethod.class);
        processors.forEach(p -> this.processors.put(p.method(), p));
    }

    public Booking createPendingBooking(Customer customer, String showId, List<String> seatIds) {
        Show show = catalog.findShow(showId).orElseThrow(() -> new IllegalArgumentException("Show not found"));
        Instant now = clock.instant();
        String id = UUID.randomUUID().toString();
        List<ShowSeat> held = show.holdSeats(seatIds, id, now.plus(holdDuration), now);
        BigDecimal total = held.stream().map(ShowSeat::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        Booking booking = new Booking(id, customer, show, seatIds, total, now);
        bookings.put(id, booking);
        return booking;
    }

    public Booking pay(String bookingId, PaymentMethod method) {
        Booking booking = required(bookingId);
        if (booking.status() != BookingStatus.PAYMENT_PENDING)
            throw new IllegalStateException("Booking cannot be paid in state " + booking.status());
        PaymentResult result = processors.get(method).charge(booking.id(), booking.totalAmount());
        if (!result.successful()) {
            booking.show().releaseHeldSeats(booking.seatIds(), booking.id());
            booking.decline();
            return booking;
        }
        try {
            booking.show().confirmSeats(booking.seatIds(), booking.id(), clock.instant());
            List<Ticket> tickets = booking.seatIds().stream().map(id -> new Ticket(UUID.randomUUID().toString(), booking.id(), booking.show().id(), booking.show().seatLabel(id))).toList();
            booking.confirm(tickets);
            paymentReferences.put(booking.id(), result.reference());
            notifications.bookingConfirmed(booking);
            return booking;
        } catch (RuntimeException e) {
            booking.show().releaseHeldSeats(booking.seatIds(), booking.id());
            booking.decline();
            throw e;
        }
    }

    public void cancel(String bookingId, PaymentMethod method) {
        Booking booking = required(bookingId);
        if (booking.status() != BookingStatus.CONFIRMED)
            throw new IllegalStateException("Only confirmed bookings can be cancelled");
        processors.get(method).refund(paymentReferences.get(booking.id()), booking.totalAmount());
        booking.show().cancelBookedSeats(booking.seatIds(), booking.id());
        booking.cancel();
        notifications.bookingCancelled(booking);
    }

    public Optional<Booking> find(String bookingId) {
        return Optional.ofNullable(bookings.get(bookingId));
    }

    private Booking required(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Booking not found"));
    }
}
