package com.moviebooking.domain;

import java.util.Objects;

/**
 * Physical, reusable chair in a hall. Its availability belongs to ShowSeat, not here.
 */
public final class Seat {
    private final String id;
    private final String label;
    private final SeatType type;

    public Seat(String id, String label, SeatType type) {
        this.id = Objects.requireNonNull(id);
        this.label = Objects.requireNonNull(label);
        this.type = Objects.requireNonNull(type);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public SeatType type() {
        return type;
    }
}
