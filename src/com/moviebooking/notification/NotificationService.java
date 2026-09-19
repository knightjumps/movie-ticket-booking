package com.moviebooking.notification;

import com.moviebooking.domain.Booking;
import com.moviebooking.domain.Customer;

public interface NotificationService {
    void bookingConfirmed(Booking booking);

    void bookingCancelled(Booking booking);

    void newMovie(Customer customer, String movieTitle);
}
