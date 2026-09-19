package com.moviebooking.domain;

import java.time.Instant;
import java.util.*;

/**
 * Synchronization is deliberately scoped to one show: unrelated shows can book concurrently.
 */
public final class Show {
    private final String id;
    private final Movie movie;
    private final Cinema cinema;
    private final Hall hall;
    private final Instant startsAt;
    private final Map<String, ShowSeat> seatsById;

    public Show(String id, Movie movie, Cinema cinema, Hall hall, Instant startsAt, List<ShowSeat> showSeats) {
        this.id = Objects.requireNonNull(id);
        this.movie = Objects.requireNonNull(movie);
        this.cinema = Objects.requireNonNull(cinema);
        this.hall = Objects.requireNonNull(hall);
        this.startsAt = Objects.requireNonNull(startsAt);
        this.seatsById = new HashMap<>();
        showSeats.forEach(s -> seatsById.put(s.seat().id(), s));
    }

    public String id() {
        return id;
    }

    public Movie movie() {
        return movie;
    }

    public Cinema cinema() {
        return cinema;
    }

    public Hall hall() {
        return hall;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public synchronized String seatLabel(String seatId) {
        ShowSeat showSeat = seatsById.get(seatId);
        if (showSeat == null) throw new IllegalArgumentException("Unknown seat: " + seatId);
        return showSeat.seat().label();
    }

    public synchronized List<ShowSeat> availableSeats(Instant now) {
        return seatsById.values().stream().filter(s -> s.isAvailableAt(now)).toList();
    }

    public synchronized List<ShowSeat> holdSeats(List<String> seatIds, String bookingId, Instant expiry, Instant now) {
        List<ShowSeat> selected = seatIds.stream().distinct().map(seatsById::get).toList();
        if (selected.size() != seatIds.size() || selected.contains(null))
            throw new IllegalArgumentException("Unknown or duplicate seat requested");
        if (selected.stream().anyMatch(s -> !s.isAvailableAt(now)))
            throw new IllegalStateException("One or more requested seats are unavailable");
        selected.forEach(s -> s.hold(bookingId, expiry, now));
        return List.copyOf(selected);
    }

    public synchronized void confirmSeats(List<String> seatIds, String bookingId, Instant now) {
        forSeats(seatIds).forEach(s -> s.confirm(bookingId, now));
    }

    public synchronized void releaseHeldSeats(List<String> seatIds, String bookingId) {
        forSeats(seatIds).forEach(s -> s.release(bookingId));
    }

    public synchronized void cancelBookedSeats(List<String> seatIds, String bookingId) {
        forSeats(seatIds).forEach(s -> s.cancelConfirmed(bookingId));
    }

    private List<ShowSeat> forSeats(List<String> ids) {
        return ids.stream().map(seatsById::get).toList();
    }
}
