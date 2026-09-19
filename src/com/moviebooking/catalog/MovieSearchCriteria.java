package com.moviebooking.catalog;

import java.time.LocalDate;

public record MovieSearchCriteria(String title, String language, String genre, LocalDate releaseDate) {
}
