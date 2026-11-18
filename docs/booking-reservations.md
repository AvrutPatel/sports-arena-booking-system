# Portable reservation coordination

Payment/refund failure recovery and deadline behavior have subsequently been
extended: see [automatic recovery](automatic-recovery.md) for the current state
machine, release policy, required migrations, and worker behavior.

The booking path uses standard JPQL updates for court-row write locking, JPA
pessimistic locks for booking refreshes, READ_COMMITTED
transactions, ordinary primary keys, and JPQL overlap queries. It does not use
PostgreSQL exclusion constraints, range types, advisory locks, or native upserts.
The application's existing database driver/configuration is unchanged. The
auth-service's earlier PostgreSQL-specific profile upsert is outside this change.

## Reservation flow

1. Calculate the price through venue-service, without an open reservation transaction.
2. Initialize a permanent `court_booking_locks` row for the court if necessary.
   Competing first inserts are resolved by the primary key. Initialization uses
   an independent transaction that finishes before the reservation starts.
3. Begin a short READ_COMMITTED transaction. Increment that court row's revision
   with an ordinary JPQL UPDATE, acquiring a write lock. Process overdue holds,
   check for overlaps, and insert PENDING before committing that same transaction.
4. Commit the reservation transaction, releasing its locks.
5. In another short transaction, claim the hold as PAYMENT_PROCESSING.
6. Call the wallet outside database transactions, retaining the existing booking
   UUID as the wallet idempotency key.
7. In another short transaction, record the definitive result or PAYMENT_UNKNOWN.

An actual row update avoids depending solely on differences in SELECT FOR UPDATE
handling across engines. The revision is a coordination counter, not a booking
version or a lease. The lock target exists even if there are no booking rows, so simultaneous first
bookings cannot both pass the overlap check. Half-open overlap logic allows
adjacent intervals: `requestedStart < existingEnd AND requestedEnd > existingStart`.
PENDING, PAYMENT_PROCESSING, PAYMENT_UNKNOWN, and CONFIRMED all block overlaps.

Lock rows are never deleted in normal operation. All reservation creation and
status-changing code must use the coordinator; direct SQL or another application
that bypasses this protocol is not protected by an exclusion constraint. Different
dates/times for the same court briefly serialize, while different courts have
independent lock keys. Database implementations may take coarser physical locks.

READ_COMMITTED is explicit so the overlap query observes the preceding winner's
commit after waiting for its court lock, including on databases whose default
is a snapshot-based REPEATABLE_READ. Required capabilities are transactions,
primary-key uniqueness, and transactional write locks; this is not a promise of
support for every database or storage engine.

## Hold state transitions

```text
PENDING --claim before deadline + persist task--> PAYMENT_PROCESSING
   |                                                   |
   | deadline                           success before deadline: CONFIRMED
   v                                    uncertain result: PAYMENT_UNKNOWN
EXPIRED                                                |
                                  deadline: PAYMENT_RELEASED (inventory free)
                                                       |
                                APPLIED: REFUND_PENDING --> CANCELLED
                                VOIDED / DECLINED: FAILED

CONFIRMED --persist cancellation + refund task--> REFUND_PENDING --> CANCELLED
```

An expired PENDING hold cannot start payment or confirm later. Payment is marked
PROCESSING and its recovery task is persisted before any debit is sent. The
subsequent automatic-recovery change releases overdue payment inventory and
refunds late successful charges. A late success cannot revive a released hold.

Every status transition takes the same court lock, refreshes/locks the booking,
and checks its current state. Refresh avoids stale entities retained by
OpenEntityManagerInView. Optimistic versions provide an additional stale-write
guard. Review updates change only the review flag, without merging an old status.

A scheduled worker scans up to 100 overdue holds per run, processing each in its
own short transaction. Multiple service instances may run it safely. Reservation
creation and availability reads also process overdue holds for their court, so
new bookings need not wait for the scheduler. Deadline values are epoch
milliseconds stored as ordinary BIGINT columns.

## Configuration and rollout

- `booking.hold.seconds`: default 300, supported range 1 to 86400.
- `booking.hold.cleanup-interval-ms`: default 60000.
- All application nodes need synchronized clocks. Booking date/time validation
  still follows the application's configured local timezone and 06:00-22:00 hours.

For an existing database: back it up, stop every booking-service instance, audit
existing active overlaps, and apply `docs/migrations/booking-holds.sql` once before
deploying. The script initializes lock rows for historical courts and quarantines
legacy PENDING records as PAYMENT_UNKNOWN. Reconcile historical unknowns against
the wallet ledger. Never mix old writers with new ones during rollout.

For a fresh development database, Hibernate can create these entities/indexes
using the existing schema-generation configuration. Do not also apply an existing-
database migration to a schema already created with the new fields.

No migration has been applied to your actual databases by this change.

## Trade-offs and limits

- The database lock protocol replaces the PostgreSQL-specific exclusion safeguard.
  Every writer must participate. Keep direct database writes restricted.
- No database transaction waits for a customer or a remote wallet request. A hold
  is persisted inventory state, not a database lock held for five minutes.
- PAYMENT_UNKNOWN is now handled by the durable recovery worker. Expired payment
  reservations release inventory; late charges become full-refund tasks. See the
  automatic recovery document for terminal wallet decisions and compensation.
- Booking request idempotency is still separate from wallet-operation idempotency.
- The subsequent [booking administration change](booking-administration.md) adds
  booking-request idempotency, closure coordination, owner authorization, and
  durable bulk jobs. Financial HTTP calls remain outside DB transactions.

## Verification

```powershell
cd booking-service
mvn "-Dtest=ReservationH2Tests,ReservationHsqlTests,PortableBookingMigrationTests,BookingWalletFlowTests,BookingPriceServiceTests,WalletClientTests" test
```

H2 and HSQLDB suites run real database transactions and concurrent threads with
no Docker dependency. They test the first-lock-row race, overlapping and adjacent
intervals, different courts, hold expiration, failed-payment release, idempotent
terminal transitions, and cleanup/confirmation races. Run the same concurrency
scenarios on the deployment database and version before production rollout.

The test suites include real database locking and committed payment-claim
visibility during wallet calls. Recovery-specific failure scenarios have been
added alongside the original reservation concurrency coverage.

References: [JPA locking](https://jakarta.ee/learn/docs/jakartaee-tutorial/current/persist/persistence-locking/persistence-locking.html),
[HSQLDB transactions](https://www.hsqldb.org/doc/guide/sessions-chapt.html).
