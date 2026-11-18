# Ownership, closures, bulk jobs, and booking request idempotency

## Ownership authority

Booking-service verifies ownership with venue-service before accepting owner
operations. It forwards the authenticated caller's JWT to role-protected
`GET /api/venues/{id}/booking-management` or
`GET /api/courts/{id}/booking-management`. Venue-service compares that caller to
the stored venue owner and returns only the canonical venue/court scope.

Booking administration never trusts caller-supplied venue/court associations.
A mismatch returns 403. An unavailable authorization dependency fails closed:
no closure or bulk job is accepted. Job status/listing, owner booking history,
closure listing, and reopening all verify current venue ownership. Customer
refund previews and review updates also require ownership of the booking.

Venue/court mutation endpoints require the owner role in addition to the
existing resource ownership checks. There is no ownership-transfer API in this
project; a future transfer feature must coordinate authorization invalidation.

## Closure coordination

`venue_booking_locks` supplies one permanent row per venue. Its ordinary JPQL
revision update acquires a transactional write lock. Creation of a reservation
and creation/removal of a closure use that same venue coordination row.

Reservation creation takes venue then court coordination locks, checks active
closures on the backend, checks overlap, and saves the hold in one short
READ_COMMITTED transaction. The guard covers empty courts and courts added after
a venue-wide closure; it does not rely on enumerating every court in advance.
Closure checks are also applied during payment claim and successful payment
completion. A charge finishing after the closure is accepted queues a full
refund instead of confirming a closed-facility reservation.

The main status-changing path takes venue, court, booking, then recovery-task
locks. A bulk batch takes venue and its job row before cancelling bookings.
Hold-only cleanup may take a court/booking subset; it never acquires a venue
lock after acquiring a court lock. No remote call executes while these locks
are held. Creation of missing coordination rows finishes before taking write
transaction locks, and request lookups have explicit read transaction boundaries
so concurrent requests do not retain pool connections while starting new writes.

Trade-off: short writes for the same venue now serialize, even across different
courts. This is deliberate for portable venue-wide closure correctness. Measure
lock waits on the deployment database before increasing batch sizes.

## Durable bulk cancellation

POST `/api/bookings/admin/bulk-cancel` requires an `Idempotency-Key` UUID and a
valid date range. It returns 202 with a job ID only after the closure and
`bulk_cancellation_jobs` row commit together. The job stores canonical scope,
requester, request key/fingerprint, closure ID, state, created time, and progress.
Reusing a key for changed scope, dates, or reason returns 409.

`BulkCancellationWorker` scans every five seconds by default. Each job batch
processes up to 50 affected bookings in one short transaction:

- CONFIRMED: persist full-refund intent and task; release reservation inventory.
- PENDING: expire without starting a payment.
- PAYMENT_PROCESSING / PAYMENT_UNKNOWN: release the reservation and bring its
  payment reconciliation deadline forward, ensuring any successful charge is
  refunded instead of reviving the reservation.

The cancellation tasks, booking tags/state changes, and queued counter commit
together. A failed batch rolls back all of them and is retried automatically.
Multiple workers serialize through the venue/job locks; process death releases
database locks without losing the durable job. No job lease is needed for a
database-only batch. Financial recovery keeps using its existing leased tasks.

Progress states are ENQUEUING, REFUNDS_PENDING, and COMPLETED. The queued counter
tracks rows processed by batches; payments independently resolved against a
closure can also be tagged to its job and included in pending financial counts.
The worker stops enumeration once all cancellation intents are saved. It then
tracks outstanding tagged payment/refund work until completion.

Reopening returns 409 while the job is ENQUEUING. Once enumeration finishes,
the owner may remove the closure independently of remaining refunds. New
bookings after reopening are not added to an old job. A closure intentionally
continues blocking inventory after refunds complete until the owner removes it.

Owner-only progress endpoints:

- GET `/api/bookings/admin/bulk-jobs/{jobId}`
- GET `/api/bookings/admin/bulk-jobs/venue/{venueId}` (latest 100)

The owner page polls progress and distinguishes queuing cancellations from
waiting for refunds. A list request aggregates pending counts instead of running
a separate count transaction for each job.

Configuration: `booking.bulk.interval-ms=5000`, `booking.bulk.batch-size=50`
(effective batch size clamped to 1-200).

## Booking request idempotency

POST `/api/bookings` now requires an `Idempotency-Key` UUID. `booking_requests`
stores the authenticated user, key, normalized request fingerprint, original
booking ID, and creation time. Its user/key uniqueness is enforced in the DB.
Legacy bookings do not need nullable idempotency fields or a fabricated key.

A replay is looked up before calling venue pricing, so a changed rate or an
unavailable pricing service cannot turn a retry into a new purchase. The
fingerprint covers venue, court, date, start, and end, excluding the ignored
client price. Changed details with the same user/key return 409. The same UUID
may be used independently by a different user.

The reservation and request binding commit atomically. Concurrent first requests
check again under coordination locks; a uniqueness race rolls back the losing
transaction and returns the original booking. Payment claim is idempotent and
creates its task only for PENDING, preventing duplicate claims on a replay.

Replays return the original booking's current status, including cancelled,
expired, or refunded outcomes. They never create another booking after inventory
is released. A new intended purchase needs a new key. The frontend persists
unacknowledged request keys in localStorage across reloads and network errors;
pending payment responses retain their key. Terminal acknowledgments/rejections
end that client intent. Bulk requests also retain keys until acknowledged.

## Rollout and verification

1. Stop old booking writers and back up the booking database.
2. Apply prior money, hold, and recovery migrations if not already applied.
3. Apply `docs/migrations/booking-administration.sql` once. It seeds venue guards
   for historical bookings/closures and creates the request/job tables and indexes.
4. Deploy venue-service first, then booking-service and the frontend together.
   Old clients without request keys will receive 400.

`venue.service.base-url` defaults to `http://VENUE-SERVICE`. Ownership proofs and
court scope lookups use the existing load-balanced client and caller JWT.

No live database migrations were executed. Adapt UUID/DDL representation to the
target database when necessary, and run deployment-engine concurrency tests.
Existing closures are preserved and now enforced by the backend. Cancellation
intent lost before this feature cannot be reconstructed automatically; review
historical closures and bookings before choosing whether to submit new jobs.

Booking tests run the closure/reservation race, late charge refunds, first-key
request races, terminal replays, bulk batch rollback/resume, reopening guards,
HTTP role/header contracts, ownership failures, and migrations on H2 and HSQLDB:

```powershell
cd booking-service
mvn "-Dtest=ReservationH2Tests,ReservationHsqlTests,BookingWalletFlowTests,BookingPriceServiceTests,WalletClientTests,BookingAdministrationAccessTests,VenueBookingAccessClientTests,BookingHttpContractTests,PortableBookingMigrationTests" test
```

```powershell
cd venue-service
mvn -Dtest=BookingManagementScopeTests test
```
