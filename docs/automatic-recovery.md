# Automatic payment and refund recovery

Recovery is driven by persisted `booking_recovery_tasks`, not by a browser retry
or an in-memory queue. Payment claiming and creation of its recovery task commit
together. Cancellation intent, its frozen refund amount, its stable refund ID,
and inventory release commit together before contacting the wallet.

## Payment behavior

1. Create the ordinary PENDING reservation, then claim it as PAYMENT_PROCESSING
   and persist a PAYMENT task with the booking UUID as its operation ID.
2. Attempt the task immediately. If it cannot complete, the scheduled worker
   retries automatically. A lost response reuses the same ID.
3. Before the payment deadline, a verified APPLIED decision confirms the booking.
   DECLINED/VOIDED releases it as FAILED.
4. At the deadline, release inventory as PAYMENT_RELEASED while retaining the
   durable task. Reservation creation, availability cleanup, periodic hold cleanup,
   and the recovery worker all cooperate with that release policy.
5. Reconcile after release using `allowDebit=false`: an already-applied debit
   queues a full refund; an unprocessed debit is terminally VOIDED. A late
   successful result can never revive released inventory.

The wallet persists APPLIED/DECLINED/VOIDED in `wallet_payment_attempts` under the
same wallet-row lock as money operations. A VOIDED decision prevents a delayed
request with the same reference from charging later. APPLIED decisions, debit
ledger records, and balance changes are committed atomically. Insufficient funds
is a terminal DECLINED decision; topping up later cannot accidentally cause the
same old attempt to charge. A new legitimate booking requires a new operation ID.

The internal settlement endpoint is intentionally more than a status lookup:
it atomically resolves or closes an attempt. A future external payment adapter
must provide equivalent idempotency and terminal cancellation/late-charge
compensation guarantees. A plain "not found" response alone does not prove a
delayed payment can never execute.

## Refund behavior

Accepting cancellation sets REFUND_PENDING, immediately frees inventory, and
persists the refund obligation. Its amount is calculated once. Retries never
recalculate it using a later date or changed policy. Customer cancellation after
the booking start is rejected; academy cancellations retain the existing full-
refund policy.

The worker requests an idempotent refund and, after success, atomically marks the
booking CANCELLED, the task COMPLETED, and creates the customer notification.
If that database transaction fails after the refund succeeded, the task is still
pending/running and retries the same refund ID before completing the state update.
The wallet returns an already-applied refund without crediting again.

Original debit verification and the aggregate refundable-amount limit still apply.
Refunding and charging are operations on different services, not one distributed
database transaction. Recovery provides eventual completion after dependencies
become available, not immediate completion during an outage.

## Worker coordination

- Default polling interval: 5 seconds (`booking.recovery.interval-ms`).
- Workers claim up to 20 due tasks per scan, in separate short transactions.
- Default lease: 60 seconds (`booking.recovery.lease-seconds`, minimum effective 30).
- A random lease token fences stale workers. An expired lease is reclaimable after
  process death/restart; a stale result cannot overwrite a newer worker's task.
- Failures retry with exponential backoff, capped below five minutes. Payment
  retries are brought forward to the payment deadline when necessary.
- Tasks are not dropped after a maximum retry count. The attempt count and sanitized
  error code remain queryable; the worker logs pending failures without credentials.
- Court, booking, and task locks are released before HTTP. Completion takes the
  same lock order in a fresh transaction. No locks span a remote request.
- A two-thread scheduler allows hold cleanup to continue while recovery is waiting
  on network timeouts. Cleanup and recovery are bounded batch processes, so exact
  release latency depends on backlog, database availability, and scheduling.

## Service authentication and API behavior

Both services still require the same server-only WALLET_SERVICE_KEY (at least 32
bytes). Recovery calls do not store or reuse customer JWTs. Auth-service restricts
these endpoints to its service-key authentication:

- POST `/internal/wallet/settle-debit`: operationId, email, amount, allowDebit.
- POST `/internal/wallet/refund`: operationId, debitReference, amount.

`auth.service.wallet.internal.url` defaults to
`http://AUTH-SERVICE/internal/wallet`. Keep this route internal and use HTTPS or
an encrypted service network. The existing customer-facing debit/refund routes
remain protected for compatibility; delayed legacy debits also respect terminal
void decisions.

Booking creation returns 200 when confirmed, 409 for a definitive failed payment,
or 202 while recovery/refund is pending. X-Booking-Id identifies the saved booking.
Cancellation acknowledges accepted intent; its message distinguishes completed
refunds from processing refunds. The profile page polls pending booking/refund
states and wallet balance. Closing the page does not stop recovery.

## Rollout

1. Back up databases and stop all booking instances to prevent mixed old writers.
2. Apply earlier money/hold migrations if not already applied.
3. Apply `docs/migrations/auth-payment-attempts.sql` to auth's database.
4. Apply `docs/migrations/booking-recovery.sql` to booking's database. It seeds
   recovery for historical PAYMENT_PROCESSING/PAYMENT_UNKNOWN bookings. Missing
   historical deadlines are treated as already expired, not permission to charge.
5. Deploy auth-service first, configure the shared service key/internal URL, then
   deploy booking-service and the updated frontend. Verify target-engine settings.

No scripts have been applied to live databases. For another database, map UUID
columns to the same physical ID representation used by its existing entities.
The recovery logic uses ordinary JPQL updates, JPA locks, and relational tables.

Historical cancellation intent that was never persisted cannot be reconstructed
automatically. Historical missing debit ledger entries require trusted financial
backfill; the worker never fabricates a debit to authorize a refund. Permanent
credential/configuration/data errors remain visible as pending tasks and logs
until operators repair the cause; customers do not need to resubmit cancellation.

Maintenance closure coordination, owner authorization, durable bulk jobs, and
booking-request idempotency are covered by the subsequent
[booking administration change](booking-administration.md).

## Verification

Booking tests on H2 and HSQLDB cover lost responses, process-death leases, stale
workers, expired inventory, late successful payments, frozen refund amounts, and
database failures after financial success:

```powershell
cd booking-service
mvn "-Dtest=ReservationH2Tests,ReservationHsqlTests,BookingWalletFlowTests,BookingPriceServiceTests,WalletClientTests,PortableBookingMigrationTests" test
```

Auth tests cover internal authentication, terminal decisions, concurrent debit/
void, idempotent refunds, and rollback of debit/ledger when decision persistence fails:

```powershell
cd auth-service
mvn "-Dtest=AuthenticationBoundaryTests,WalletServiceTests,WalletAttemptDatabaseTests" test
```

The H2 wallet tests use pre-created profiles and stub only the existing PostgreSQL
profile-initialization upsert. Wallet row locking, decisions, balances, ledger,
and transactions execute against the real test database. PostgreSQL-specific
profile-creation tests remain in the Docker-dependent WalletPostgresTests suite.
