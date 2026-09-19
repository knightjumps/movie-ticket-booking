package com.moviebooking.notification;

import com.moviebooking.domain.Booking;
import com.moviebooking.domain.Customer;

public final class ConsoleNotificationService implements NotificationService {
    public void bookingConfirmed(Booking b) {
        System.out.println("EMAIL to " + b.customer().email() + ": booking " + b.id() + " confirmed; tickets=" + b.tickets().stream().map(t -> t.seatLabel()).toList());
    }

    public void bookingCancelled(Booking b) {
        System.out.println("EMAIL to " + b.customer().email() + ": booking " + b.id() + " cancelled and refunded.");
    }

    public void newMovie(Customer c, String title) {
        System.out.println("EMAIL to " + c.email() + ": New release: " + title);
    }
}
