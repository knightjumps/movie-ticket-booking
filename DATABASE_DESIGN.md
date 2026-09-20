# Database Design for a Movie Ticket Booking System

This document explains how to present the database design of a movie ticket booking system in an LLD interview. The design prioritizes the hardest correctness requirement: two customers must never buy the same seat for the same show.

## 1. Choosing the database

PostgreSQL is a suitable primary database because booking inventory, payments, and multi-seat reservations require:

- ACID transactions.
- Row-level locking or atomic conditional updates.
- Unique and foreign-key constraints.
- Strong consistency for limited inventory.
- Reliable audit and payment records.

A search engine such as Elasticsearch or OpenSearch may later serve fuzzy movie discovery, but PostgreSQL should remain the source of truth for bookings and seat inventory.

## 2. High-level relationships

```text
City
 └── Cinema
      └── Hall
           └── Seat

Movie
 └── MovieShow
      └── ShowSeat

UserAccount
 └── Booking
      ├── BookingSeat / Ticket
      └── PaymentAttempt
           └── Refund
```

The most important modeling distinction is between `seat` and `show_seat`:

- `seat` represents a permanent physical chair, such as A10 in Hall 3.
- `show_seat` represents the availability and price of A10 for one particular screening.

A physical seat must not contain a global `BOOKED` flag. Seat A10 can be booked for the 6 PM show and available for the 9 PM show.

## 3. Venue tables

### `city`

```sql
CREATE TABLE city (
    id            BIGINT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    state         VARCHAR(100),
    country_code  CHAR(2) NOT NULL,
    timezone      VARCHAR(50) NOT NULL
);
```

The timezone is useful because showtimes belong to the cinema's local timezone. Two shows advertised as 7 PM in different countries represent different instants.

### `cinema`

```sql
CREATE TABLE cinema (
    id          BIGINT PRIMARY KEY,
    city_id     BIGINT NOT NULL REFERENCES city(id),
    name        VARCHAR(200) NOT NULL,
    address     TEXT NOT NULL,
    status      VARCHAR(30) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_cinema_city ON cinema(city_id);
```

Example status values are `ACTIVE` and `INACTIVE`. The city index supports queries such as “find cinemas in Bangalore.”

### `hall`

```sql
CREATE TABLE hall (
    id          BIGINT PRIMARY KEY,
    cinema_id   BIGINT NOT NULL REFERENCES cinema(id),
    name        VARCHAR(100) NOT NULL,
    capacity    INT NOT NULL CHECK (capacity >= 0),
    status      VARCHAR(30) NOT NULL,
    UNIQUE (cinema_id, name)
);
```

The unique constraint prevents one cinema from accidentally having two halls with the same name.

### `seat`

```sql
CREATE TABLE seat (
    id           BIGINT PRIMARY KEY,
    hall_id      BIGINT NOT NULL REFERENCES hall(id),
    row_label    VARCHAR(10) NOT NULL,
    seat_number  VARCHAR(10) NOT NULL,
    seat_type    VARCHAR(30) NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    UNIQUE (hall_id, row_label, seat_number)
);

CREATE INDEX idx_seat_hall ON seat(hall_id);
```

Possible `seat_type` values are `SILVER`, `GOLD`, and `PLATINUM`. The physical seat status may be `ACTIVE` or `OUT_OF_SERVICE`.

The physical seat table deliberately does not contain `AVAILABLE`, `HELD`, or `BOOKED`. Those states are specific to a show.

## 4. Movie and show tables

### `movie`

```sql
CREATE TABLE movie (
    id                BIGINT PRIMARY KEY,
    title             VARCHAR(300) NOT NULL,
    language          VARCHAR(50) NOT NULL,
    genre             VARCHAR(100) NOT NULL,
    release_date      DATE,
    duration_minutes  INT NOT NULL CHECK (duration_minutes > 0),
    description       TEXT,
    status            VARCHAR(30) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_movie_title ON movie(title);
CREATE INDEX idx_movie_language ON movie(language);
CREATE INDEX idx_movie_genre ON movie(genre);
CREATE INDEX idx_movie_release_date ON movie(release_date);
```

Possible states are `UPCOMING`, `RELEASED`, and `ARCHIVED`.

For a simple design, `language` and `genre` can be columns. If a movie supports several genres or languages, normalize them into `genre`, `movie_genre`, `language`, and `movie_language` tables. Fuzzy, typo-tolerant title search can eventually move to a specialized search engine.

### `movie_show`

`show` is a reserved word in some databases, so `movie_show` is a safer table name.

```sql
CREATE TABLE movie_show (
    id          BIGINT PRIMARY KEY,
    movie_id    BIGINT NOT NULL REFERENCES movie(id),
    hall_id     BIGINT NOT NULL REFERENCES hall(id),
    starts_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    status      VARCHAR(30) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE INDEX idx_show_movie_time
    ON movie_show(movie_id, starts_at);

CREATE INDEX idx_show_hall_time
    ON movie_show(hall_id, starts_at);

CREATE INDEX idx_show_status_time
    ON movie_show(status, starts_at);
```

Possible states are:

```text
SCHEDULED
BOOKING_OPEN
BOOKING_CLOSED
STARTED
COMPLETED
CANCELLED
```

The indexes support finding upcoming shows for a movie, looking up a hall's schedule, and retrieving shows currently open for booking.

### Preventing overlapping shows

Two shows cannot occupy the same hall during overlapping time intervals. Two intervals overlap when:

```text
newStart < existingEnd AND existingStart < newEnd
```

The check and insert must be atomic because two administrators could schedule competing shows concurrently. PostgreSQL can enforce this with an exclusion constraint over a timestamp range. A simpler implementation can lock the relevant hall schedule inside a transaction, check for overlap, and insert only if no conflict exists.

## 5. Show inventory table

### `show_seat`

This is the central table in the system.

```sql
CREATE TABLE show_seat (
    show_id          BIGINT NOT NULL REFERENCES movie_show(id),
    seat_id          BIGINT NOT NULL REFERENCES seat(id),
    status           VARCHAR(30) NOT NULL,
    price            NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    hold_booking_id  UUID,
    hold_expires_at  TIMESTAMP WITH TIME ZONE,
    version          BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (show_id, seat_id)
);

CREATE INDEX idx_show_seat_availability
    ON show_seat(show_id, status);
```

Possible states are:

```text
AVAILABLE
HELD
BOOKED
BLOCKED
```

| Column | Purpose |
|---|---|
| `show_id`, `seat_id` | Uniquely identify one physical seat's inventory for one show. |
| `status` | Record whether that inventory is available, held, booked, or blocked. |
| `price` | Store the price for that seat at that particular show. |
| `hold_booking_id` | Identify the booking that owns a temporary hold. |
| `hold_expires_at` | Prevent abandoned checkout sessions from blocking seats forever. |
| `version` | Support optimistic concurrency control. |

The composite primary key guarantees one inventory record per seat per show.

Price belongs here because a Gold seat can have different prices for a weekday morning show and a weekend premiere. The purchased price should also be copied into `booking_seat` as a historical snapshot.

## 6. User and role tables

### `user_account`

```sql
CREATE TABLE user_account (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(320) UNIQUE,
    phone       VARCHAR(30),
    status      VARCHAR(30) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
```

### `user_role`

```sql
CREATE TABLE user_role (
    user_id  UUID NOT NULL REFERENCES user_account(id),
    role     VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);
```

Example roles are `CUSTOMER`, `ADMIN`, and `TICKET_AGENT`. A role table is usually preferable to separate person tables when all actors share the same identity fields.

For walk-in bookings, `booked_by_user_id` identifies the ticket agent. The customer reference may be absent, or optional contact information can be captured according to the requirements.

## 7. Booking and ticket tables

### `booking`

```sql
CREATE TABLE booking (
    id                 UUID PRIMARY KEY,
    customer_id        UUID REFERENCES user_account(id),
    booked_by_user_id  UUID REFERENCES user_account(id),
    show_id            BIGINT NOT NULL REFERENCES movie_show(id),
    status             VARCHAR(30) NOT NULL,
    booking_channel    VARCHAR(30) NOT NULL,
    subtotal_amount    NUMERIC(12, 2) NOT NULL,
    discount_amount    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    tax_amount         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_amount       NUMERIC(12, 2) NOT NULL,
    currency           CHAR(3) NOT NULL,
    expires_at         TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at       TIMESTAMP WITH TIME ZONE,
    cancelled_at       TIMESTAMP WITH TIME ZONE,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_booking_customer_created
    ON booking(customer_id, created_at DESC);

CREATE INDEX idx_booking_show_status
    ON booking(show_id, status);
```

Possible booking states are:

```text
PENDING_PAYMENT
CONFIRMED
PAYMENT_FAILED
EXPIRED
CANCELLED
```

Possible booking channels are `ONLINE` and `BOX_OFFICE`.

For online bookings, `customer_id` and `booked_by_user_id` may identify the same customer. For a walk-in booking, `booked_by_user_id` identifies the ticket agent.

The booking stores subtotal, discount, tax, total, and currency because pricing and tax rules can change. Historical receipts must retain exactly what the customer was charged.

### `booking_seat`

```sql
CREATE TABLE booking_seat (
    booking_id  UUID NOT NULL REFERENCES booking(id),
    show_id     BIGINT NOT NULL,
    seat_id     BIGINT NOT NULL,
    seat_label  VARCHAR(30) NOT NULL,
    seat_type   VARCHAR(30) NOT NULL,
    price       NUMERIC(12, 2) NOT NULL,
    ticket_id   UUID UNIQUE,
    PRIMARY KEY (booking_id, seat_id),
    FOREIGN KEY (show_id, seat_id)
        REFERENCES show_seat(show_id, seat_id)
);
```

This table represents the seats selected by a booking and keeps immutable purchase snapshots such as label, category, and price. If the hall layout or current pricing changes later, an old ticket remains correct.

### Optional separate `ticket`

For a richer ticket lifecycle, use a separate table:

```sql
CREATE TABLE ticket (
    id           UUID PRIMARY KEY,
    booking_id   UUID NOT NULL REFERENCES booking(id),
    show_id      BIGINT NOT NULL,
    seat_id      BIGINT NOT NULL,
    ticket_code  VARCHAR(100) NOT NULL UNIQUE,
    status       VARCHAR(30) NOT NULL,
    issued_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (booking_id, seat_id)
);
```

Possible ticket states are `ACTIVE`, `CANCELLED`, and `USED`. In a compact interview design, `booking_seat` can also serve as the ticket record.

## 8. Payment and refund tables

Payment data should not be stored only in `booking` because one booking can have several payment attempts. For example, the first card may be declined and the second may succeed.

### `payment_attempt`

```sql
CREATE TABLE payment_attempt (
    id                  UUID PRIMARY KEY,
    booking_id          UUID NOT NULL REFERENCES booking(id),
    method              VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    currency            CHAR(3) NOT NULL,
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,
    provider            VARCHAR(50),
    provider_reference  VARCHAR(200),
    failure_code        VARCHAR(100),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (provider, provider_reference)
);

CREATE INDEX idx_payment_booking_created
    ON payment_attempt(booking_id, created_at DESC);
```

Possible payment states are:

```text
INITIATED
PENDING
SUCCEEDED
DECLINED
FAILED
```

The `idempotency_key` ensures a retried client request does not produce a second charge. Do not store CVV or complete raw card details. Store provider tokens/references and safe display metadata such as the last four digits only if needed.

### `refund`

```sql
CREATE TABLE refund (
    id                    UUID PRIMARY KEY,
    payment_attempt_id    UUID NOT NULL REFERENCES payment_attempt(id),
    booking_id            UUID NOT NULL REFERENCES booking(id),
    amount                NUMERIC(12, 2) NOT NULL,
    status                VARCHAR(30) NOT NULL,
    provider_reference    VARCHAR(200),
    reason                VARCHAR(200),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_refund_booking ON refund(booking_id);
```

Possible refund states are `REQUESTED`, `PROCESSING`, `SUCCEEDED`, and `FAILED`.

A booking may be `CANCELLED` while its refund remains `PROCESSING`. Booking, payment, and refund states therefore need independent columns and lifecycles.

## 9. Coupons and discounts

If coupon support is required, it can use the following tables:

```sql
CREATE TABLE coupon (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    discount_type   VARCHAR(30) NOT NULL,
    discount_value  NUMERIC(12, 2) NOT NULL,
    valid_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until     TIMESTAMP WITH TIME ZONE NOT NULL,
    max_uses        INT,
    status          VARCHAR(30) NOT NULL
);

CREATE TABLE booking_coupon (
    booking_id       UUID NOT NULL REFERENCES booking(id),
    coupon_id        BIGINT NOT NULL REFERENCES coupon(id),
    discount_amount  NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (booking_id, coupon_id)
);
```

`booking_coupon.discount_amount` records the discount actually applied. Later changes to the coupon rule must not alter historical bookings.

## 10. Notifications and reliable event publication

### `notification`

```sql
CREATE TABLE notification (
    id             UUID PRIMARY KEY,
    user_id        UUID REFERENCES user_account(id),
    booking_id     UUID REFERENCES booking(id),
    channel        VARCHAR(30) NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    destination    VARCHAR(320) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    attempt_count  INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_notification_delivery
    ON notification(status, created_at);
```

Channels may include `EMAIL`, `SMS`, and `PUSH`. Events may include `BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `BOOKING_MODIFIED`, `SHOW_CANCELLED`, and `NEW_MOVIE`.

### `outbox_event`

```sql
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_event(created_at)
    WHERE published_at IS NULL;
```

The booking change and its outbox event are written in the same transaction. A background process publishes the event to Kafka, SQS, or another broker. This avoids committing a booking but losing its notification event.

The outbox is a valuable follow-up topic; it is not necessary to introduce it before explaining the core booking schema.

## 11. Reserving seats safely

### Atomic conditional update

A single available or expired seat can be acquired using an atomic update:

```sql
UPDATE show_seat
SET status = 'HELD',
    hold_booking_id = :bookingId,
    hold_expires_at = :expiry,
    version = version + 1
WHERE show_id = :showId
  AND seat_id = :seatId
  AND (
      status = 'AVAILABLE'
      OR (
          status = 'HELD'
          AND hold_expires_at <= CURRENT_TIMESTAMP
      )
  );
```

Check the affected-row count:

- `1` means the hold succeeded.
- `0` means the seat is already held, booked, blocked, or otherwise unavailable.

### Multi-seat booking transaction

All requested seats must be reserved as one unit:

```text
BEGIN

1. Validate the show is open for booking.
2. Lock all requested show_seat rows in a stable order.
3. Verify every requested seat exists and is available or expired.
4. Create a PENDING_PAYMENT booking.
5. Update every requested show_seat to HELD by that booking.
6. Insert booking_seat records containing price snapshots.

COMMIT
```

If any seat fails validation, execute `ROLLBACK`. This prevents partial reservations where A1 is held even though A2 was unavailable.

With pessimistic row locking:

```sql
SELECT *
FROM show_seat
WHERE show_id = :showId
  AND seat_id IN (:seatIds)
ORDER BY seat_id
FOR UPDATE;
```

Locking rows in a stable order reduces deadlock risk.

## 12. Payment transaction boundaries

Do not hold database locks while communicating with an external payment provider.

```text
Transaction 1:
    AVAILABLE -> HELD
    Create PENDING_PAYMENT booking
    COMMIT

Outside transaction:
    Call payment provider with an idempotency key

Transaction 2:
    Revalidate booking and hold ownership
    HELD -> BOOKED
    Booking -> CONFIRMED
    Create ticket/outbox records
    COMMIT
```

If the payment is definitely declined, release the seats in a short transaction. A payment timeout represents an unknown result, not necessarily failure; query the provider or await its callback before retrying the charge.

If payment succeeds after the hold expired and another customer acquired the seat, do not reclaim the seat. Record the failure and initiate a compensating refund.

## 13. Database-level double-booking protection

Java `synchronized` or an in-process lock only protects one server process. It does not coordinate several application instances.

The database is the final authority through:

1. The composite primary key on `show_seat(show_id, seat_id)`.
2. Atomic conditional state transitions.
3. Row locks or optimistic version checking.
4. An active-allocation uniqueness rule where required.
5. Transactions around all-or-nothing multi-seat operations.

If ticket history is retained after cancellation, a permanent unique constraint over every historical `(show_id, seat_id)` ticket would incorrectly prevent that seat from being sold again. Apply uniqueness to the active allocation or use an appropriate partial constraint.

## 14. Hold expiry

Use both lazy evaluation and background cleanup.

### Lazy expiry

When checking availability or acquiring a hold, treat this as reusable inventory:

```text
status = HELD AND hold_expires_at <= current time
```

This preserves correctness even if the cleanup worker is delayed.

### Background cleanup

```sql
UPDATE show_seat
SET status = 'AVAILABLE',
    hold_booking_id = NULL,
    hold_expires_at = NULL,
    version = version + 1
WHERE status = 'HELD'
  AND hold_expires_at <= CURRENT_TIMESTAMP;
```

The cleanup process should also change the corresponding pending bookings to `EXPIRED`. It improves data cleanliness and normal query performance, while lazy expiry prevents correctness from depending on scheduler timing.

## 15. Normalization and historical snapshots

Normalize stable, reusable entities:

- Movie
- Cinema
- Hall
- Seat
- Show
- User
- Booking
- Payment

Intentionally duplicate historical facts that must remain unchanged:

- Purchased seat label and category
- Purchased seat price
- Booking subtotal, discount, tax, total, and currency
- Provider transaction reference

This balances normalization with auditability. An old receipt must not change because a cinema later renames a row or modifies its prices.

## 16. Deletion and auditing

Avoid hard deletion for bookings, tickets, payment attempts, and refunds. These records are required for customer support, reconciliation, fraud investigation, and auditing.

Prefer lifecycle states:

```text
movie.status       = ARCHIVED
movie_show.status  = CANCELLED
booking.status     = CANCELLED
ticket.status      = CANCELLED
```

Deleting or archiving a movie must not erase previous bookings, tickets, payments, or refunds.

## 17. Compact verbal interview answer

> I would use PostgreSQL because the booking path needs transactions and strong consistency. I would model venues with city, cinema, hall, and physical seat tables. Movies are separate from shows because one movie can have many screenings. The central inventory table is show_seat, keyed by show ID and seat ID, with status, price, hold owner, hold expiry, and version. This prevents us from putting a global booked flag on a physical seat.
>
> A booking stores its customer, show, channel, status, expiry, and price totals. A booking_seat table records each selected seat and its price snapshot. Payment attempts are separate because a booking can have several attempts; refunds are also separate because booking cancellation and refund completion have different lifecycles.
>
> To reserve seats, I lock or conditionally update all requested show-seat rows in one transaction. If any seat is unavailable, the transaction rolls back. I create a temporary hold and pending booking, then commit before calling the payment provider. After successful payment, another short transaction verifies hold ownership and converts the seats to booked. Database constraints and atomic transactions prevent double booking across multiple application instances.
>
> I would index shows by movie, hall, and start time; show seats by show and availability; bookings by customer and creation time; and payments by booking and provider reference. Historical prices and seat details are copied into booking-seat records so old tickets remain accurate when current catalogue data changes.

## 18. Common mistakes

- Storing show-specific availability in the physical `seat` table.
- Keeping selected seat IDs only as a JSON/list column inside `booking`.
- Depending exclusively on Java `synchronized` for correctness.
- Calling the payment provider while holding database locks.
- Combining booking, payment, and refund state into one status column.
- Treating a payment timeout as a definite failure.
- Reserving several seats one at a time without an all-or-nothing transaction.
- Releasing an expired seat without verifying the current hold owner.
- Recalculating historical booking totals from current prices.
- Hard-deleting financial or booking records.
- Using floating-point types for money instead of `NUMERIC`/`BigDecimal` or integer minor units.
- Trusting a price supplied by the client instead of calculating it on the server.

## 19. Table summary

| Table | Required? | Main use case |
|---|---:|---|
| `city` | Core | Groups cinemas geographically and records the local timezone. |
| `cinema` | Core | Stores a theatre location and connects it to a city. |
| `hall` | Core | Represents a screen/auditorium inside a cinema and its capacity. |
| `seat` | Core | Stores the permanent physical seat layout and seat category for a hall. |
| `movie` | Core | Stores reusable movie metadata such as title, language, genre, release date, and duration. |
| `movie_show` | Core | Represents one scheduled screening of a movie in a hall. |
| `show_seat` | Core | Stores per-show seat availability, price, temporary hold owner, expiry, and concurrency version. |
| `user_account` | Core | Stores shared identity and contact information for customers, admins, and ticket agents. |
| `user_role` | Core | Assigns one or more authorization roles to a user account. |
| `booking` | Core | Records the customer transaction, show, status, channel, expiry, and monetary totals. |
| `booking_seat` | Core | Connects a booking to its selected seats and preserves label, category, and purchase-price snapshots. |
| `payment_attempt` | Core | Records each payment attempt, idempotency key, provider reference, amount, and result. |
| `refund` | Core when cancellation/refunds are supported | Tracks refund requests independently from booking cancellation and payment success. |
| `ticket` | Optional separate table | Tracks issued admission tickets and their active, cancelled, or used lifecycle; may be combined with `booking_seat` in a smaller design. |
| `genre` | Optional normalization | Stores reusable genres when movies can have several genres. |
| `movie_genre` | Optional normalization | Implements the many-to-many relationship between movies and genres. |
| `language` | Optional normalization | Stores reusable language definitions when a movie supports several languages. |
| `movie_language` | Optional normalization | Implements the many-to-many relationship between movies and languages. |
| `coupon` | Optional extension | Defines coupon validity, usage limits, and discount policy. |
| `booking_coupon` | Optional extension | Records which coupon was applied and the exact historical discount amount. |
| `notification` | Optional delivery tracking | Tracks email, SMS, or push delivery attempts for booking and movie events. |
| `outbox_event` | Recommended for event-driven integration | Reliably records domain events in the same transaction as booking changes for later publication to a message broker. |
