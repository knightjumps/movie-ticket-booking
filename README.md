# Movie Ticket Booking System — Low-Level Design

A dependency-free Java implementation of a movie ticket booking system. It is designed as an LLD exercise: the code favors clear object responsibilities, extensibility, and correct seat allocation over framework-specific infrastructure.

## Supported flows

- Admin adds or updates movies and shows.
- Customers search movies by title, language, genre, and release date.
- Customers view currently available seats for a show.
- A customer holds one or more seats in one atomic operation.
- A held booking is paid by credit card or cash.
- A confirmed booking issues one ticket per seat.
- A customer cancels a confirmed booking; payment is refunded and seats return to availability.
- Booking confirmation and cancellation notifications are sent.
- A competing reservation for an already-held seat is rejected.

## Design overview

```text
Movie -> Show -> ShowSeat -> Seat
                   |
                   +-> Booking -> Ticket(s)
                   |
                   +-> Payment

BookingService -> PaymentProcessor
               -> NotificationService
MovieCatalog   -> Movie / Show lookup
AdminService   -> movie/show lifecycle
```

### Important domain decision: `Seat` vs `ShowSeat`

`Seat` represents a permanent physical chair in a hall, for example `A1` in Screen 1. It does **not** store booking state.

`ShowSeat` represents that physical chair for a particular `Show` and owns its price, hold information, and state:

```text
AVAILABLE -> HELD -> BOOKED
    ^          |
    +----------+ payment failure or hold expiry
```

This distinction allows the same physical seat to be booked for a 6 PM show while remaining available for a 9 PM show.

## Concurrency approach

`Show.holdSeats(...)` is synchronized, so availability validation and the transition from `AVAILABLE` to `HELD` occur as one atomic operation for a single show. A second request for the same seat therefore fails instead of creating a double booking.

The lock is scoped to one show; customers can still reserve seats for different shows concurrently. A production implementation would generally use database transactions, conditional updates/versioning, and a uniqueness constraint on `(show_id, seat_id)` as the final correctness guard.

## Package layout

| Package | Responsibility |
|---|---|
| `com.moviebooking.app` | Executable demonstration driver. |
| `com.moviebooking.domain` | Core entities, statuses, inventory state, booking, and tickets. |
| `com.moviebooking.catalog` | Movie/show storage and filter-based search. |
| `com.moviebooking.service` | Booking use cases and admin lifecycle operations. |
| `com.moviebooking.payment` | Payment abstraction and simulated cash/card processors. |
| `com.moviebooking.pricing` | Seat-pricing abstraction and fixed tier pricing. |
| `com.moviebooking.notification` | Notification abstraction and console implementation. |

## Extensibility points

- Add a payment method by implementing `PaymentProcessor`.
- Add dynamic, coupon, or surge pricing by implementing `PricingService`.
- Add email, SMS, push, or Kafka-backed notifications by implementing `NotificationService`.
- Replace the in-memory catalog/booking maps with repositories backed by a database.

## Run the demo

Requires Java 17 or newer (the project was verified with Java 25).

```bash
javac -d /private/tmp/movie-ticket-booking-classes $(find src -name '*.java' | sort)
java -cp /private/tmp/movie-ticket-booking-classes com.moviebooking.app.Driver
```

The demo prints the search result, available seats, a successful multi-seat booking, a rejected duplicate-seat request, confirmation, cancellation/refund, and a walk-in cash booking.

## Deliberate simplifications

- Payment processors are simulations; no card data is persisted.
- Notification delivery is printed to the console.
- Shows are managed in memory.
- An actual service would authenticate customers/admins, enforce authorization, use idempotency keys for payment retries, and schedule expiry cleanup for abandoned holds.
