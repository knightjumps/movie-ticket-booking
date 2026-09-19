package com.moviebooking.domain;

import java.time.LocalDate;
import java.util.Objects;

public final class Movie {
    private final String id;
    private final String title;
    private final String language;
    private final String genre;
    private final LocalDate releaseDate;
    private final int durationMinutes;

    public Movie(String id, String title, String language, String genre, LocalDate releaseDate, int durationMinutes) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.language = Objects.requireNonNull(language);
        this.genre = Objects.requireNonNull(genre);
        this.releaseDate = Objects.requireNonNull(releaseDate);
        this.durationMinutes = durationMinutes;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String language() {
        return language;
    }

    public String genre() {
        return genre;
    }

    public LocalDate releaseDate() {
        return releaseDate;
    }

    public int durationMinutes() {
        return durationMinutes;
    }
}
