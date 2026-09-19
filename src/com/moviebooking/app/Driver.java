package com.moviebooking.app;

import com.moviebooking.catalog.*;
import com.moviebooking.domain.*;
import com.moviebooking.notification.ConsoleNotificationService;
import com.moviebooking.payment.*;
import com.moviebooking.pricing.*;
import com.moviebooking.service.BookingService;
import com.moviebooking.service.AdminService;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Executable walkthrough of the essential public flows.
 */
public final class Driver {
    public static void main(String[] args) {
        Movie movie = new Movie("m-1", "Arrival", "English", "Sci-Fi", LocalDate.of(2016, 11, 11), 116);
        List<Seat> physicalSeats = List.of(new Seat("s-a1", "A1", SeatType.PLATINUM), new Seat("s-a2", "A2", SeatType.GOLD), new Seat("s-a3", "A3", SeatType.SILVER));
        Hall hall = new Hall("h-1", "Screen 1", physicalSeats);
        Cinema cinema = new Cinema("c-1", "Downtown Cinema", "Seattle", List.of(hall));
        PricingService prices = new FixedSeatPricing(Map.of(SeatType.SILVER, new BigDecimal("10.00"), SeatType.GOLD, new BigDecimal("14.00"), SeatType.PLATINUM, new BigDecimal("18.00")));
        List<ShowSeat> showSeats = physicalSeats.stream().map(s -> new ShowSeat(s, prices.priceFor(s.type()))).toList();
        Show show = new Show("show-1", movie, cinema, hall, Instant.now().plus(Duration.ofHours(3)), showSeats);
        MovieCatalog catalog = new MovieCatalog();
        AdminService adminService = new AdminService(catalog);
        adminService.addOrUpdateMovie(movie);
        adminService.addOrUpdateShow(show);
        BookingService bookings = new BookingService(catalog, List.of(new SimulatedCardProcessor(), new SimulatedCashProcessor()), new ConsoleNotificationService(), Clock.systemUTC(), Duration.ofMinutes(5));
        Customer alice = new Customer("u-1", "Alice", "alice@example.com");
        Customer bob = new Customer("u-2", "Bob", "bob@example.com");
        Admin admin = new Admin("admin-1", "Maya", "maya@cinema.example");

        System.out.println("Admin " + admin.name() + " published movie and show listing.");

        System.out.println("Search result: " + catalog.search(new MovieSearchCriteria("arrival", "English", "Sci-Fi", null)).stream().map(Movie::title).toList());
        System.out.println("Available seats: " + show.availableSeats(Instant.now()).stream().map(s -> s.seat().label()).toList());

        Booking aliceBooking = bookings.createPendingBooking(alice, show.id(), List.of("s-a1", "s-a2"));
        System.out.println("Alice created pending booking " + aliceBooking.id() + ", total=" + aliceBooking.totalAmount());
        try {
            bookings.createPendingBooking(bob, show.id(), List.of("s-a1"));
        } catch (IllegalStateException conflict) {
            System.out.println("Duplicate-seat attempt rejected: " + conflict.getMessage());
        }

        bookings.pay(aliceBooking.id(), PaymentMethod.CREDIT_CARD);
        System.out.println("Alice booking state: " + aliceBooking.status() + ", payment=" + aliceBooking.paymentStatus());
        bookings.cancel(aliceBooking.id(), PaymentMethod.CREDIT_CARD);
        System.out.println("After cancellation, available seats: " + show.availableSeats(Instant.now()).stream().map(s -> s.seat().label()).toList());

        Booking walkIn = bookings.createPendingBooking(bob, show.id(), List.of("s-a3"));
        bookings.pay(walkIn.id(), PaymentMethod.CASH);
        System.out.println("Walk-in cash booking confirmed: " + walkIn.id());
    }
}
