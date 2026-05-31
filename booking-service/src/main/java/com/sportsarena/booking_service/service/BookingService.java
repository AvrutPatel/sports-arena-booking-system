package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BookingRequestDto;
import com.sportsarena.booking_service.dto.BulkCancelRequestDto;
import com.sportsarena.booking_service.dto.SlotResponseDto;
import com.sportsarena.booking_service.entity.Booking;
import com.sportsarena.booking_service.entity.FacilityClosure;
import com.sportsarena.booking_service.repository.BookingRepository;
import com.sportsarena.booking_service.repository.FacilityClosureRepository;
import com.sportsarena.booking_service.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FacilityClosureRepository closureRepository;

    @Autowired
    private RestTemplate restTemplate;

    // Retrieve the authenticated user's booking history
    public List<Booking> getAuthenticatedUserBookings() {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return bookingRepository.findByUserEmailOrderByBookingDateDesc(currentEmail);
    }

    // Dynamic slot computation engine
    public List<SlotResponseDto> getAvailableSlots(Long courtId, LocalDate date) {
        List<SlotResponseDto> timeline = new ArrayList<>();
        LocalTime openTime = LocalTime.of(6, 0);
        LocalTime closeTime = LocalTime.of(22, 0);

        List<Booking> activeBookings = bookingRepository.findByCourtIdAndBookingDateAndStatus(courtId, date, "CONFIRMED");

        // Grab the current real-world date and time
        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        while (openTime.isBefore(closeTime)) {
            LocalTime slotEnd = openTime.plusHours(1);
            LocalTime finalOpenTime = openTime;

            // 1. Check if booked in database
            boolean isBooked = activeBookings.stream().anyMatch(b ->
                    (finalOpenTime.isBefore(b.getEndTime()) && slotEnd.isAfter(b.getStartTime()))
            );

            // 2. Check if the slot time has already passed TODAY
            boolean isPast = date.isEqual(today) && finalOpenTime.isBefore(currentTime);

            // 3. Mark available ONLY if it is neither booked nor in the past
            boolean isAvailable = !isBooked && !isPast;

            timeline.add(new SlotResponseDto(openTime, slotEnd, isAvailable));
            openTime = slotEnd;
        }
        return timeline;
    }

    // Process and commit a new booking with transactional safety and WALLET DEDUCTION
    @Transactional
    public String checkAndCreateBooking(BookingRequestDto dto) {
        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        // VALIDATION 1: No past dates
        if (dto.getBookingDate().isBefore(today)) {
            throw new RuntimeException("Validation Failure: Cannot book a date in the past.");
        }

        // VALIDATION 2: No past times on the current day
        if (dto.getBookingDate().isEqual(today) && dto.getStartTime().isBefore(currentTime)) {
            throw new RuntimeException("Validation Failure: This time slot has already passed.");
        }

        // VALIDATION 3: Maximum 30 days in advance
        if (dto.getBookingDate().isAfter(today.plusDays(30))) {
            throw new RuntimeException("Validation Failure: You can only book up to 30 days in advance.");
        }

        // VALIDATION 4: Concurrency Check (Double Bookings)
        long overlaps = bookingRepository.countOverlappingBookings(
                dto.getCourtId(), dto.getBookingDate(), dto.getStartTime(), dto.getEndTime()
        );

        if (overlaps > 0) {
            throw new RuntimeException("Validation Failure: Selected slot has already been reserved.");
        }

        // --- NEW: WALLET DEDUCTION LOGIC ---
        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        // Grab the JWT token from the incoming request so we can forward it to the auth-service
        org.springframework.web.context.request.ServletRequestAttributes attributes =
                (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String jwtToken = attributes.getRequest().getHeader("Authorization");

        // IMPORTANT: Ensure this URL matches your auth-service port (currently set to 8080)
        String authServiceUrl = "http://localhost:8080/api/auth/profile/wallet/deduct?amount=" + dto.getTotalAmount();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", jwtToken);
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

        try {
            // Attempt to deduct funds. If they have enough, this succeeds and execution continues.
            restTemplate.exchange(authServiceUrl, org.springframework.http.HttpMethod.POST, entity, String.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // If the auth-service throws a 400 Bad Request, block the booking here!
            throw new RuntimeException("Booking failed: Insufficient wallet balance. Please add funds to your profile.");
        }
        // --- END WALLET DEDUCTION ---

        Booking booking = new Booking();
        booking.setCourtId(dto.getCourtId());
        booking.setVenueId(dto.getVenueId());
        booking.setBookingDate(dto.getBookingDate());
        booking.setStartTime(dto.getStartTime());
        booking.setEndTime(dto.getEndTime());
        booking.setTotalAmount(dto.getTotalAmount());
        booking.setUserEmail(authenticatedEmail);
        booking.setStatus("CONFIRMED");

        bookingRepository.save(booking);
        return "Reservation successfully processed! ₹" + dto.getTotalAmount() + " deducted from your wallet. Token: " + booking.getId();
    }

    public Booking getBookingByIdForUpdate(java.util.UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Error: Booking record not found."));
    }

    // Securely cancel a single booking (Case 1) ---
    @Transactional
    public String cancelBooking(java.util.UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Error: Booking record not found."));

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!booking.getUserEmail().equalsIgnoreCase(currentEmail)) {
            throw new RuntimeException("Access Denied: Unauthorized cancellation attempt.");
        }

        // 1. Calculate Refund
        double refundPercentage = calculateRefundPercentage(booking.getBookingDate());
        double refundAmount = booking.getTotalAmount() * refundPercentage;

        // 2. Process Refund Cross-Service
        if (refundAmount > 0) {
            processCrossServiceRefund(booking.getUserEmail(), refundAmount);
        }

        // 3. Update status
        booking.setStatus("CANCELLED");
        bookingRepository.save(booking);

        // 4. Send Notification
        String msg = String.format(
                "You cancelled your booking on %s. Based on our policy, a %.0f%% refund of ₹%.2f has been credited to your wallet.",
                booking.getBookingDate(), (refundPercentage * 100), refundAmount
        );
        notificationRepository.save(new com.sportsarena.booking_service.entity.Notification(booking.getUserEmail(), msg));

        return "Reservation cancelled. ₹" + refundAmount + " refunded to your wallet.";
    }

    // Owner bulk cancellation (Case 2) ---
    @Transactional
    public int bulkCancelBookings(BulkCancelRequestDto request) {
        List<Booking> affectedBookings;

        // Hunt down bookings based on request...
        if (request.getCourtId() != null) {
            if (request.getEndDate() != null) {
                affectedBookings = bookingRepository.findByCourtIdAndBookingDateBetweenAndStatus(
                        request.getCourtId(), request.getStartDate(), request.getEndDate(), "CONFIRMED");
            } else {
                affectedBookings = bookingRepository.findByCourtIdAndBookingDateGreaterThanEqualAndStatus(
                        request.getCourtId(), request.getStartDate(), "CONFIRMED");
            }
        } else if (request.getVenueId() != null) {
            affectedBookings = bookingRepository.findByVenueIdAndBookingDateGreaterThanEqualAndStatus(
                    request.getVenueId(), request.getStartDate(), "CONFIRMED");
        } else {
            throw new IllegalArgumentException("Must provide either a venueId or courtId");
        }

        // Process cancellations, refund 100%, and notify players
        for (Booking booking : affectedBookings) {

            // 1. Give 100% Refund!
            processCrossServiceRefund(booking.getUserEmail(), booking.getTotalAmount());

            // 2. Cancel it
            booking.setStatus("CANCELLED");
            bookingRepository.save(booking);

            // 3. Construct Notification with Refund Proof
            String baseMessage = "Alert: Your booking for Court " + booking.getCourtId() + " on " + booking.getBookingDate() +
                    " was forcefully canceled by the academy. A 100% refund of ₹" + booking.getTotalAmount() +
                    " has been credited back to your wallet.";

            String finalMessage = (request.getMessage() != null && !request.getMessage().trim().isEmpty())
                    ? baseMessage + " Reason: " + request.getMessage()
                    : baseMessage;

            notificationRepository.save(new com.sportsarena.booking_service.entity.Notification(booking.getUserEmail(), finalMessage));
        }

        // Save Facility Closure roadblock...
        FacilityClosure closure = new FacilityClosure();
        closure.setVenueId(request.getVenueId());
        closure.setCourtId(request.getCourtId());
        closure.setStartDate(request.getStartDate());
        closure.setEndDate(request.getEndDate());
        closure.setReason(request.getMessage() != null && !request.getMessage().trim().isEmpty() ? request.getMessage() : "Closed by academy administration.");
        closureRepository.save(closure);

        return affectedBookings.size();
    }

    // Generates the preview for the frontend warning UI
    public java.util.Map<String, Object> getRefundPreview(java.util.UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();

        double refundPercentage = calculateRefundPercentage(booking.getBookingDate());
        double refundAmount = booking.getTotalAmount() * refundPercentage;

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("refundPercentage", refundPercentage * 100);
        response.put("refundAmount", refundAmount);
        response.put("totalAmount", booking.getTotalAmount());

        return response;
    }

    // Mathematical Backend Policy Engine
    private double calculateRefundPercentage(LocalDate bookingDate) {
        LocalDate today = LocalDate.now();
        long daysUntilBooking = java.time.temporal.ChronoUnit.DAYS.between(today, bookingDate);

        if (daysUntilBooking > 7) {
            return 1.0;  // 100% if > 1 week
        } else if (daysUntilBooking > 1) {
            return 0.75; // 75% if between 1 day and 1 week
        } else {
            return 0.50; // 50% if <= 1 day
        }
    }

    // Cross-Service Caller
    private void processCrossServiceRefund(String targetEmail, double amount) {
        // Extract current JWT token to bypass security
        org.springframework.web.context.request.ServletRequestAttributes attributes =
                (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String jwtToken = attributes.getRequest().getHeader("Authorization");

        // Make sure this port matches your Auth Service
        String url = "http://localhost:8080/api/auth/profile/wallet/refund?targetEmail=" + targetEmail + "&amount=" + amount;

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", jwtToken);
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            System.err.println("Failed to process refund for " + targetEmail + ". Ensure Auth Service is running.");
        }
    }


    public void saveUpdatedBooking(Booking booking) {
        bookingRepository.save(booking);
    }


    public List<FacilityClosure> getClosuresForVenue(Long venueId) {
        return closureRepository.findByVenueIdOrderByStartDateDesc(venueId);
    }

    public void removeClosure(Long closureId) {
        closureRepository.deleteById(closureId);
    }

    // Add this to BookingService.java
    public List<com.sportsarena.booking_service.entity.Booking> getBookingsByVenue(Long venueId) {
        return bookingRepository.findByVenueIdOrderByBookingDateDesc(venueId);
    }
}