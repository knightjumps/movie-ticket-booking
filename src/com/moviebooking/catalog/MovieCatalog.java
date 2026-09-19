package com.moviebooking.catalog;

import com.moviebooking.domain.Movie;
import com.moviebooking.domain.Show;

import java.util.*;

public final class MovieCatalog {
    private final Map<String, Movie> movies = new HashMap<>();
    private final Map<String, Show> shows = new HashMap<>();

    public void addMovie(Movie movie) {
        movies.put(movie.id(), movie);
    }

    public void addShow(Show show) {
        shows.put(show.id(), show);
    }

    public void removeMovie(String movieId) {
        movies.remove(movieId);
        shows.values().removeIf(show -> show.movie().id().equals(movieId));
    }

    public void removeShow(String showId) {
        shows.remove(showId);
    }

    public Optional<Show> findShow(String showId) {
        return Optional.ofNullable(shows.get(showId));
    }

    public List<Movie> search(MovieSearchCriteria c) {
        return movies.values().stream().filter(m -> matches(c.title(), m.title())).filter(m -> matches(c.language(), m.language()))
                .filter(m -> matches(c.genre(), m.genre())).filter(m -> c.releaseDate() == null || c.releaseDate().equals(m.releaseDate())).toList();
    }

    public List<Show> showsForMovie(String movieId) {
        return shows.values().stream().filter(s -> s.movie().id().equals(movieId)).sorted(Comparator.comparing(Show::startsAt)).toList();
    }

    private boolean matches(String filter, String value) {
        return filter == null || filter.isBlank() || value.toLowerCase().contains(filter.toLowerCase());
    }
}
