# 🏟️ Sports Arena Booking Platform

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
* Coordinates cross-service communication (e.g., pinging the Auth Service to deduct funds before committing a booking to the database).
* Manages `FacilityClosure` logic for maintenance roadblocks.

---

## 🧠 Core Algorithms & Logic Engines

### 1. Dynamic Slot Computation Algorithm
Instead of hardcoding time slots in the database, the system calculates available slots on the fly.
* **Mechanism:** The engine generates 1-hour intervals between facility operating hours (e.g., 06:00 to 22:00).
* **Evaluation:** It loops through existing `CONFIRMED` bookings for the requested date. If a generated slot overlaps with an active booking's `startTime` and `endTime`, or if the time has already passed in the real world, the slot is dynamically stripped from the availability timeline.

### 2. Concurrency Control (Double-Booking Prevention)
* **Mechanism:** Implements a strict JPA/SQL aggregation check: `countOverlappingBookings`. 
* **Evaluation:** Before committing a transaction, the database counts any existing records where the requested start/end times intersect. If the count is `> 0`, the transaction is rolled back, preventing race conditions during high-traffic booking windows.

### 3. Cross-Service Wallet Deduction Protocol
* **Mechanism:** Distributed Transaction Simulation.
* **Evaluation:** When a player initiates a booking, the Booking Service intercepts the JWT token and fires a synchronous HTTP request to the Auth Service to deduct the `totalAmount`. If the Auth Service throws a `400 Bad Request` (Insufficient Funds), the Booking Service intercepts the exception and aborts the booking process. 

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
* PostgreSQL / MySQL (Configured via `application.properties`)