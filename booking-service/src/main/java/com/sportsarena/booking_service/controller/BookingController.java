package com.sportsarena.booking_service.controller;

import com.sportsarena.booking_service.dto.BookingRequestDto;
import com.sportsarena.booking_service.dto.SlotResponseDto;
import com.sportsarena.booking_service.entity.Booking;
import com.sportsarena.booking_service.entity.FacilityClosure;
import com.sportsarena.booking_service.repository.FacilityClosureRepository;
import com.sportsarena.booking_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private FacilityClosureRepository closureRepository;

    // Public slot checker mapping
    @GetMapping("/slots")
    public ResponseEntity<List<SlotResponseDto>> checkSlots(
            @RequestParam Long courtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(bookingService.getAvailableSlots(courtId, date));
    }

    // Secure booking registration endpoint
    @PostMapping
    public ResponseEntity<String> processReservation(@Valid @RequestBody BookingRequestDto request) {
        String confirmation = bookingService.checkAndCreateBooking(request);
        return ResponseEntity.ok(confirmation);
    }

    // Fetch history for the logged-in user
    @GetMapping("/my-bookings")
    public ResponseEntity<List<com.sportsarena.booking_service.entity.Booking>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getAuthenticatedUserBookings());
    }

    // Soft-delete/Cancel a specific reservation
    @PatchMapping("/{bookingId}/cancel")
    public ResponseEntity<String> cancelReservation(@PathVariable java.util.UUID bookingId) {
        String response = bookingService.cancelBooking(bookingId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{bookingId}/mark-reviewed")
    public ResponseEntity<String> markAsReviewed(@PathVariable java.util.UUID bookingId) {
        com.sportsarena.booking_service.entity.Booking booking = bookingService.getBookingByIdForUpdate(bookingId);
        booking.setReviewed(true);
        bookingService.saveUpdatedBooking(booking);
        return ResponseEntity.ok("Booking marked as reviewed");
    }

    @PostMapping("/admin/bulk-cancel")
    public ResponseEntity<String> bulkCancel(@RequestBody com.sportsarena.booking_service.dto.BulkCancelRequestDto request) {

        // Security check to ensure only owners can trigger this mass-delete
        String role = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().toString();
        if (!role.contains("ACADEMY_OWNER")) {
            return ResponseEntity.status(403).body("Unauthorized: Only owners can perform bulk cancellations.");
        }

        int canceledCount = bookingService.bulkCancelBookings(request);
        return ResponseEntity.ok("Successfully canceled " + canceledCount + " bookings and notified the affected players.");
    }

    // GET: /api/bookings/closures/check?venueId=7&courtId=3&date=2026-05-25
    @GetMapping("/closures/check")
    public ResponseEntity<java.util.Map<String, Object>> checkClosure(
            @RequestParam Long venueId,
            @RequestParam Long courtId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {

        List<com.sportsarena.booking_service.entity.FacilityClosure> closures =
                closureRepository.findActiveClosuresForDate(courtId, venueId, date);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        if (!closures.isEmpty()) {
            response.put("isClosed", true);
            response.put("reason", closures.get(0).getReason());
        } else {
            response.put("isClosed", false);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/closures/venue/{venueId}")
    public ResponseEntity<List<FacilityClosure>> getVenueClosures(@PathVariable Long venueId) {
        // Security check ensures only owners can view this
        String role = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().toString();
        if (!role.contains("ACADEMY_OWNER")) return ResponseEntity.status(403).build();

        return ResponseEntity.ok(bookingService.getClosuresForVenue(venueId));
    }

    @DeleteMapping("/admin/closures/{closureId}")
    public ResponseEntity<String> removeClosure(@PathVariable Long closureId) {
        String role = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().toString();
        if (!role.contains("ACADEMY_OWNER")) return ResponseEntity.status(403).build();

        bookingService.removeClosure(closureId);
        return ResponseEntity.ok("Facility successfully reactivated.");
    }

    // Add this to BookingController.java
    @GetMapping("/admin/venue/{venueId}")
    public ResponseEntity<List<Booking>> getBookingsForVenue(@PathVariable Long venueId) {

        // Security check
        String role = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().toString();
        if (!role.contains("ACADEMY_OWNER")) {
            return org.springframework.http.ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(bookingService.getBookingsByVenue(venueId));
    }

    @GetMapping("/{bookingId}/refund-preview")
    public ResponseEntity<java.util.Map<String, Object>> getRefundPreview(@PathVariable java.util.UUID bookingId) {
        return ResponseEntity.ok(bookingService.getRefundPreview(bookingId));
    }
}