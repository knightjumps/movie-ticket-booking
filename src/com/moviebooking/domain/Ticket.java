package com.moviebooking.domain;

import java.util.UUID;

public record Ticket(String id, String bookingId, String showId, String seatLabel) {
    public static Ticket issue(String bookingId, Show show, ShowSeat seat) {
        return new Ticket(UUID.randomUUID().toString(), bookingId, show.id(), seat.seat().label());
    }
}
