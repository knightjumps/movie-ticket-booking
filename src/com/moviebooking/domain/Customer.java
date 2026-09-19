package com.moviebooking.domain;

import java.util.Objects;

public record Customer(String id, String name, String email) {
    public Customer {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(email);
    }
}
