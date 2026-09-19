package com.moviebooking.domain;

import java.util.List;
import java.util.Objects;

public final class Cinema {
    private final String id;
    private final String name;
    private final String city;
    private final List<Hall> halls;

    public Cinema(String id, String name, String city, List<Hall> halls) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.city = Objects.requireNonNull(city);
        this.halls = List.copyOf(halls);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String city() {
        return city;
    }

    public List<Hall> halls() {
        return halls;
    }
}
