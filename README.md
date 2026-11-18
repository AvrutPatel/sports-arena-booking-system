# 🏟️ Sports Arena Booking Platform

See [setup and GitHub publishing steps](docs/github-setup.md) for environment variables, verification commands, local startup, and pushing this repository. Safe application configs are included; real credentials must be supplied locally.

A full-stack, distributed microservices platform designed for managing and booking sports academies. Built using the BMAD (Build More, Architect Dreams) methodology, this system provides a seamless experience for both players and academy owners, featuring dynamic slot computation, cross-service digital wallets, and automated refund policies.

## 🚀 Key Features

**For Players**
* **Digital Wallet System:** Add funds and execute instant, one-click bookings.
* **Smart Dashboards:** Manage personalized profiles, select avatars, and curate favorite venues.
* **Automated Refunds:** Receive instant wallet refunds based on dynamic cancellation policies.
* **Verified Reviews:** Rate and review specific courts after a booking is completed.

**For Academy Owners**
* **Venue Management:** Register academies, add courts, and dynamically adjust hourly pricing.
* **Facility Roadblocks:** Execute targeted bulk-cancellations (facility closures) for maintenance, automatically refunding affected players.
* **Analytics Dashboard:** Monitor real-time booking statuses and estimated revenue streams across multiple academies.

---

## 🏗️ Microservices Architecture

The backend is structurally decoupled into specialized, independent services communicating via REST (`RestTemplate`). 

### 1. Auth Service (`user-profile-engine`)
* Manages JWT-based authentication and authorization (`PLAYER`, `ACADEMY_OWNER`).
* Houses the `UserProfile` entity (Avatars, Interested Sports, Favorite Venues).
* Acts as the central ledger for the Player's Digital Wallet, exposing secure endpoints for deductions and refunds.

### 2. Venue Service (`facility-engine`)
* Manages `Venue` and `Court` entities.
* Handles public-facing catalogs and filtering.
* Processes and calculates aggregate `Review` ratings for specific courts and overall academies.

### 3. Booking Service (`transaction-engine`)
* Handles the lifecycle of a `Booking`.
* Executes the dynamic slot algorithm and concurrency checks.
* Creates a temporary reservation, then coordinates wallet deduction outside database transactions before confirming the booking.
* Manages `FacilityClosure` logic for maintenance roadblocks.
* Verifies owner administration with venue-service, coordinates closures with reservations, persists restart-safe bulk cancellation jobs, and deduplicates booking requests. See [booking administration rollout](docs/booking-administration.md).

---

## 🧠 Core Algorithms & Logic Engines

### 1. Dynamic Slot Computation Algorithm
Instead of hardcoding time slots in the database, the system calculates available slots on the fly.
* **Mechanism:** The engine generates 1-hour intervals between facility operating hours (e.g., 06:00 to 22:00).
* **Evaluation:** It checks confirmed bookings and active holds (`PENDING`, `PAYMENT_PROCESSING`, `PAYMENT_UNKNOWN`) for the requested date. Overlapping or past slots appear unavailable.

### 2. Concurrency Control (Double-Booking Prevention)
* **Mechanism:** Uses permanent venue and court coordination rows, locked in that order. An ordinary JPQL update acquires its transactional write lock. The same short `READ_COMMITTED` transaction checks closures and overlaps and creates the hold before releasing the court lock.
* **Evaluation:** Competing writes for the same court serialize, including its first booking. Conflicting requests return `409 Conflict`. Expiry and payment transitions use the same coordination. See [reservation design, rollout, and tests](docs/booking-reservations.md).

### 3. Cross-Service Wallet Deduction Protocol
* **Mechanism:** A persisted reservation and idempotent wallet operation, with separate short database transactions.
* **Evaluation:** Claiming a hold persists a recovery task. Service-authenticated workers use stable operation IDs, retry uncertain payments/refunds, release expired payment inventory, and refund late successful charges. See [automatic recovery and rollout](docs/automatic-recovery.md).

### 4. Dynamic Refund & Cancellation Policy Engine
Calculates penalty fees mathematically based on proximity to the booking date.
* **> 7 Days Notice:** 100% Wallet Refund.
* **1 to 7 Days Notice:** 75% Wallet Refund.
* **< 24 Hours Notice:** 50% Wallet Refund.
* **Owner Bulk Cancel:** Bypasses the policy engine and forces a 100% refund to all affected users.

### 5. Facility Roadblock Protocol (The Maintenance Pattern)
* **Mechanism:** Creates immutable records in the `FacilityClosure` table (`venueId`, `courtId`, `startDate`, `endDate`).
* **Evaluation:** When a user queries a date, the API first checks for intersecting closures. If a roadblock exists, it halts the slot computation algorithm entirely and returns a "Facility Closed" response with the owner's specific reason.

---

## 🗄️ Entity Relationships & Data Flow

To maintain strict domain isolation, database tables do not use hard foreign keys across microservices. Instead, they rely on soft-linking via IDs.

* **`UserProfile` (Auth DB):** Linked to authentication via `email`. Stores `walletBalance`. Soft-links to venues via an `@ElementCollection` of `favoriteVenueIds`.
* **`Venue` & `Court` (Venue DB):** `Court` has a `@ManyToOne` relationship with `Venue`. `Venue` tracks ownership via `ownerId` (soft-linked to the owner's email).
* **`Review` (Venue DB):** Belongs to a `Venue`, optionally linked to a specific `Court`. Tracks the author via `userEmail`.
* **`Booking` (Booking DB):** Soft-linked to `venueId`, `courtId`, and `userEmail`. Acts as the single source of truth for transaction states (`CONFIRMED`, `CANCELLED`).
* **`FacilityClosure` (Booking DB):** Soft-linked to `venueId` and optionally `courtId` to dictate system-wide or court-specific roadblocks.

---

## 💻 Tech Stack

**Frontend**
* React.js
* Tailwind CSS
* React Router DOM
* Axios (Interceptors for JWT management)

**Backend**
* Java Spring Boot
* Spring Security (JWT Authentication)
* Spring Data JPA / Hibernate
* REST APIs (`RestTemplate` for microservice orchestration)

**Database**
* PostgreSQL (current runtime driver/configuration); H2 and HSQLDB for automated tests.
