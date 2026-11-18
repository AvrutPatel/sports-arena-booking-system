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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.sportsarena.booking_service.entity.Notification;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Transactional(propagation = Propagation.NEVER)
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FacilityClosureRepository closureRepository;

    @Autowired
    private BookingRecoveryWorker recoveryWorker;

    @Autowired
    private BookingPriceService priceService;

    @Autowired
    private ReservationTransactionService reservations;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private BookingHoldCleanup holdCleanup;
    @Autowired private VenueBookingAccessClient access;

    // Get user bookings
    public List<Booking> getAuthenticatedUserBookings() {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return bookingRepository.findByUserEmailOrderByBookingDateDesc(currentEmail);
    }
    public void cleanStalePendingBookings() {
        holdCleanup.runOnce();
    }

    public List<SlotResponseDto> getAvailableSlots(Long courtId, LocalDate date) {
        var court = access.court(courtId);
        reservationService.prepareVenue(court.venueId());
        boolean closed = reservations.isClosed(court.venueId(), courtId, date);
        reservationService.expireCourtHolds(courtId);
        List<SlotResponseDto> timeline = new ArrayList<>();
        LocalTime openTime = LocalTime.of(6, 0);
        LocalTime closeTime = LocalTime.of(22, 0);

        List<Booking> activeBookings = bookingRepository.findByCourtIdAndBookingDateAndStatusIn(
                courtId, date, ReservationTransactionService.ACTIVE_STATUSES);

        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        while (openTime.isBefore(closeTime)) {
            LocalTime slotEnd = openTime.plusHours(1);
            LocalTime finalOpenTime = openTime;

            boolean isBooked = activeBookings.stream().anyMatch(b ->
                    (finalOpenTime.isBefore(b.getEndTime()) && slotEnd.isAfter(b.getStartTime()))
            );

            boolean isPast = date.isEqual(today) && finalOpenTime.isBefore(currentTime);
            boolean isAvailable = !closed && !isBooked && !isPast;

            timeline.add(new SlotResponseDto(openTime, slotEnd, isAvailable));
            openTime = slotEnd;
        }
        return timeline;
    }

    public Booking createPendingBooking(BookingRequestDto dto, String authenticatedEmail) {
        // Pricing performs HTTP before entering the reservation transaction.
        BigDecimal price = priceService.calculate(dto);
        return reservationService.reserve(dto, authenticatedEmail, price);
    }

    public void confirmBooking(UUID bookingId) {
        reservations.confirm(bookingId);
    }

    public void failBooking(UUID bookingId) {
        reservations.paymentFailed(bookingId);
    }

    // Orchestrator for booking process
    public record BookingOutcome(UUID bookingId, String status, String message) {}

    public BookingOutcome checkAndCreateBooking(BookingRequestDto dto) {
        return checkAndCreateBooking(dto, UUID.randomUUID());
    }

    public BookingOutcome checkAndCreateBooking(BookingRequestDto dto, UUID requestKey) {
        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Booking pendingBooking = reservationService.lookup(dto, authenticatedEmail, requestKey);
        if (pendingBooking == null) {
            BigDecimal price = priceService.calculate(dto);
            pendingBooking = reservationService.reserve(dto, authenticatedEmail, price, requestKey);
        }
        reservations.startPaymentOnce(pendingBooking.getId());

        recoveryWorker.processOne(pendingBooking.getId());
        Booking current = reservations.snapshot(pendingBooking.getId());
        String message = switch (current.getStatus()) {
            case "CONFIRMED" -> "Reservation confirmed. ₹" + current.getTotalAmount() + " deducted from your wallet.";
            case "FAILED" -> "Reservation was not completed. No wallet debit was applied.";
            case "REFUND_PENDING", "PAYMENT_RELEASED" -> "Reservation window closed. Any successful payment will be refunded automatically.";
            case "CANCELLED" -> "Reservation was cancelled and your refund completed.";
            case "EXPIRED" -> "Reservation hold expired before payment started.";
            default -> "Payment is processing automatically. Check your booking history; do not submit another payment.";
        };
        return new BookingOutcome(current.getId(), current.getStatus(), message);
    }

    
    public Booking getBookingByIdForUpdate(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Error: Booking record not found."));
        requireCustomer(booking);
        return booking;
    }

    // Cancel booking
    public String cancelBooking(UUID bookingId) {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Booking booking = reservations.requestCancellation(bookingId, currentEmail, false);
        if ("CANCELLED".equals(booking.getStatus())) return "Reservation already cancelled.";
        recoveryWorker.processOne(bookingId);
        return "CANCELLED".equals(reservations.snapshot(bookingId).getStatus())
                ? "Reservation cancelled. Your refund has been credited to your wallet."
                : "Reservation cancelled. Your refund is processing automatically.";
    }

    // Generate refund preview
    public Map<String, Object> getRefundPreview(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        requireCustomer(booking);

        double refundPercentage = calculateRefundPercentage(booking.getBookingDate());
        BigDecimal refundAmount = booking.getTotalAmount().multiply(BigDecimal.valueOf(refundPercentage))
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> response = new HashMap<>();
        response.put("refundPercentage", refundPercentage * 100);
        response.put("refundAmount", refundAmount);
        response.put("totalAmount", booking.getTotalAmount());

        return response;
    }

    private double calculateRefundPercentage(LocalDate bookingDate) {
        LocalDate today = LocalDate.now();
        long daysUntilBooking = ChronoUnit.DAYS.between(today, bookingDate);

        if (daysUntilBooking > 7) {
            return 1.0;  // 100% if > 1 week
        } else if (daysUntilBooking > 1) {
            return 0.75; // 75% if between 1 day and 1 week
        } else {
            return 0.50; // 50% if <= 1 day
        }
    }


    public void saveUpdatedBooking(Booking booking) {
        requireCustomer(bookingRepository.findById(booking.getId()).orElseThrow());
        // This endpoint updates the review flag only; never merge a stale booking status.
        reservations.markReviewed(booking.getId(), booking.isReviewed());
    }


    public List<FacilityClosure> getClosuresForVenue(Long venueId) {
        access.requireVenueOwner(venueId);
        return closureRepository.findByVenueIdOrderByStartDateDesc(venueId);
    }

    public List<Booking> getBookingsByVenue(Long venueId) {
        access.requireVenueOwner(venueId);
        return bookingRepository.findByVenueIdOrderByBookingDateDesc(venueId);
    }

    private void requireCustomer(Booking booking) {
        String caller = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!booking.getUserEmail().equalsIgnoreCase(caller)) throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "This booking belongs to another customer.");
    }
}
