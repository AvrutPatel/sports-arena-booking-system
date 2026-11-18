# Wallet integrity and deployment

The current payment/refund client and automatic failure recovery supersede the
original synchronous flow below. See [automatic recovery](automatic-recovery.md)
for service-only endpoints, terminal payment decisions, migrations, and retries.

## Scope

External top-up payment verification is assumed to work, as requested. The
existing authenticated top-up route remains available. This change does not
implement a payment provider or verify settlement.

Wallet balance changes, idempotency checks, and ledger entries are committed in
one database transaction. Each operation locks the user's profile row. Profile
edits take the same lock so they cannot overwrite a concurrent balance change.
First-time profile creation uses PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`.
The operation ID is a UUID primary key in the ledger.

Money uses BigDecimal/NUMERIC(19,2) in the auth, booking, and venue services.
Amounts must be positive and fit two fractional and 17 integral digits. NaN and
infinity cannot bind to BigDecimal and return 400. Prices are calculated from the
venue service's active court rate and whole-minute duration, rounded HALF_UP to
two places. Client `totalAmount` is accepted for compatibility but ignored.
All amounts use the existing application's single currency, INR.

## Required rollout steps

1. Back up the three databases and stop auth, booking, and venue services.
2. Review existing balances, booking amounts, and court rates. The migrations
   round historical floating-point values to two decimals and intentionally fail
   on invalid/null values rather than silently repair financial data.
3. Apply each SQL migration once to its corresponding database:
   - `docs/migrations/auth-wallet.sql`
   - `docs/migrations/booking-money.sql`
   - `docs/migrations/venue-money.sql`
4. Set the same **server-only**, randomly generated `WALLET_SERVICE_KEY` of at
   least 32 bytes in auth-service and booking-service. Never put it in Vite
   variables, browser code, logs, or source control. It can alternatively be
   supplied as the Spring property `wallet.service.key`.
5. Deploy/restart the services together and rebuild the frontend. Do not roll
   back to the old wallet implementation while accepting financial writes.

No migrations have been executed against your databases by this change. SQL
files are explicit rollout scripts; this repository does not yet run Flyway.
For a completely fresh database, initialize the existing schema before applying
these scripts, with the application kept out of traffic.

The additional service credential travels in `X-Wallet-Service-Key`, alongside
the existing customer/owner JWT. Missing or short server configuration disables
debit/refund calls. Use HTTPS or an encrypted private service network; rotate
the key through server configuration. A shared key is a pragmatic fit for this
project; workload identity or OAuth client credentials offers stronger isolation
and rotation controls for larger deployments.

Optional configuration: `venue.service.courts.url` defaults to
`http://VENUE-SERVICE/api/courts/venue/{venueId}` and uses the existing load-balanced
RestTemplate. HTTP calls now have five-second connect and ten-second read limits.

## API contract changes

Every wallet write requires an `Idempotency-Key` header containing a UUID. Reuse
the same ID after a timeout. Reuse with a different amount, recipient, operation
type, or original debit returns 409. Replays leave the balance unchanged and
return the current profile, not a historical snapshot.

- `POST /api/auth/profile/wallet/add?amount=100.00`: existing authenticated
  top-up flow; the frontend now retains its operation ID across retries while
  the page is mounted and prevents overlapping submissions. A real verified
  payment flow should use a stable server-issued payment reference so a reload
  cannot turn a payment retry into a new top-up.
- `POST /api/auth/profile/wallet/deduct?amount=100.00`: requires customer JWT
  plus service key. Booking uses its booking UUID as the operation ID.
- `POST /api/auth/profile/wallet/refund?amount=50.00&debitReference=<booking-uuid>`:
  requires a valid JWT plus service key. The recipient is read from the original
  debit. Total refunds cannot exceed that debit. Booking derives a stable UUID
  for cancellation refunds. The old `targetEmail` contract is removed.

Insufficient funds, conflicting references, and excessive refunds return 409;
missing original debit returns 404; invalid request data returns 400. Ordinary
customer and owner JWTs cannot directly call debit/refund without the service key.

## Historical data and remaining issues

- Existing balances are preserved as opening balances. Historical debits are
  not fabricated. Bookings created before this change cannot automatically be
  refunded through the new contract until an operator reconciles and backfills
  their actual debits from trusted historical records.
- Wallet network/server errors are now handled by persisted recovery tasks.
  Overdue payment reservations release inventory and are closed or refunded
  automatically. See the automatic recovery document for the current policy.
- Cancellation freezes its refund amount and operation ID before contacting the
  wallet. Background retries no longer recalculate policy or rely on a customer
  request, and the UI distinguishes pending from completed refunds.
- Booking overlap coordination and hold state transitions are addressed by the
  subsequent [portable reservation change](booking-reservations.md). Academy
  ownership checks on bulk cancellations remain separate work. Payment
  confirmation and booking persistence still span two services without a
  distributed transaction.

## Verification

```powershell
cd auth-service
mvn "-Dtest=AuthenticationBoundaryTests,WalletServiceTests,WalletPostgresTests" test
```

The PostgreSQL concurrency/rollback suite uses an isolated `postgres:17-alpine`
Testcontainers instance. It is explicitly skipped when Docker is unavailable;
run it with Docker enabled before production rollout. Mock-based tests do not
prove database locking behavior.

```powershell
cd booking-service
mvn "-Dtest=BookingPriceServiceTests,WalletClientTests,BookingWalletFlowTests" test
```

Booking tests verify server pricing, stable wallet references, credentials,
unknown payment outcomes, and refund failure propagation.
