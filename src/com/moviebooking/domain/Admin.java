package com.moviebooking.domain;

import java.util.Objects;

/**
 * Explicit actor type keeps catalogue-management permissions separate from customer actions.
 */
public record Admin(String id, String name, String email) {
    public Admin {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(email);
    }
}
