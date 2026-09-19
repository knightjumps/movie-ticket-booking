package com.moviebooking.domain;

import java.util.List;
import java.util.Objects;

public final class Hall {
    private final String id;
    private final String name;
    private final List<Seat> seats;

    public Hall(String id, String name, List<Seat> seats) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.seats = List.copyOf(seats);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<Seat> seats() {
        return seats;
    }
}
