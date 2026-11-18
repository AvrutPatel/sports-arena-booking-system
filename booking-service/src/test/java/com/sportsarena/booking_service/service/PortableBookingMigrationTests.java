package com.sportsarena.booking_service.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PortableBookingMigrationTests {
    @ParameterizedTest
    @ValueSource(strings = {"jdbc:h2:mem:hold_migration", "jdbc:hsqldb:mem:hold_migration"})
    void migrationPreservesBookingsAndQuarantinesLegacyPendingPayments(String baseUrl) throws Exception {
        // Keep one connection alive while testing the unmodified deployment script.
        DriverManagerDataSource database = new DriverManagerDataSource(baseUrl, "sa", "");
        try (var ignored = database.getConnection()) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE bookings (id UUID PRIMARY KEY, court_id BIGINT, venue_id BIGINT DEFAULT 1, "
                    + "booking_date DATE, start_time TIME, end_time TIME, status VARCHAR(255), total_amount NUMERIC(19,2) DEFAULT 100.00)");
            String one = "00000000-0000-0000-0000-000000000001", two = "00000000-0000-0000-0000-000000000002",
                    three = "00000000-0000-0000-0000-000000000003";
            jdbc.update("INSERT INTO bookings (id, court_id, status) VALUES (?, 42, 'CONFIRMED')", java.util.UUID.fromString(one));
            jdbc.update("INSERT INTO bookings (id, court_id, status) VALUES (?, 43, 'PENDING')", java.util.UUID.fromString(two));
            jdbc.update("INSERT INTO bookings (id, court_id, status) VALUES (?, 42, 'CANCELLED')", java.util.UUID.fromString(three));
            jdbc.execute("CREATE TABLE facility_closures (id BIGINT PRIMARY KEY, venue_id BIGINT, court_id BIGINT, start_date DATE, end_date DATE)");
            jdbc.update("INSERT INTO facility_closures (id,venue_id) VALUES (7,2)");
            new ResourceDatabasePopulator(new FileSystemResource(
                    Path.of("..", "docs", "migrations", "booking-holds.sql"))).execute(database);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM court_booking_locks", Integer.class));
            assertEquals(0L, jdbc.queryForObject("SELECT revision FROM court_booking_locks WHERE court_id = 42", Long.class));
            assertEquals("PAYMENT_UNKNOWN", jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, java.util.UUID.fromString(two)));
            assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, java.util.UUID.fromString(one)));
            assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, java.util.UUID.fromString(three)));
            assertEquals(0L, jdbc.queryForObject("SELECT version FROM bookings WHERE id = ?", Long.class, java.util.UUID.fromString(one)));
            assertNull(jdbc.queryForObject("SELECT hold_expires_at FROM bookings WHERE id = ?", Long.class, java.util.UUID.fromString(two)));
            new ResourceDatabasePopulator(new FileSystemResource(Path.of("..", "docs", "migrations", "booking-recovery.sql"))).execute(database);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM booking_recovery_tasks", Integer.class));
            assertEquals(0L, jdbc.queryForObject("SELECT payment_deadline FROM booking_recovery_tasks", Long.class));
            new ResourceDatabasePopulator(new FileSystemResource(Path.of("..", "docs", "migrations", "auth-payment-attempts.sql"))).execute(database);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_payment_attempts", Integer.class));
            new ResourceDatabasePopulator(new FileSystemResource(Path.of("..", "docs", "migrations", "booking-administration.sql"))).execute(database);
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM venue_booking_locks",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking_requests",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM bulk_cancellation_jobs",Integer.class));
        }
    }
}
