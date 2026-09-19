package com.moviebooking.service;

import com.moviebooking.catalog.MovieCatalog;
import com.moviebooking.domain.Movie;
import com.moviebooking.domain.Show;

import java.util.Objects;

/**
 * Application boundary for admin-only movie/show lifecycle operations.
 */
public final class AdminService {
    private final MovieCatalog catalog;

    public AdminService(MovieCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public void addOrUpdateMovie(Movie movie) {
        catalog.addMovie(movie);
    }

    public void addOrUpdateShow(Show show) {
        catalog.addShow(show);
    }

    public void deleteMovie(String movieId) {
        catalog.removeMovie(movieId);
    }

    public void cancelShow(String showId) {
        catalog.removeShow(showId);
    }
}
