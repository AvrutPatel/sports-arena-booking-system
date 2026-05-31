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

    // Process and commit a new booking with transactional safety
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

        Booking booking = new Booking();
        booking.setCourtId(dto.getCourtId());
        booking.setVenueId(dto.getVenueId());
        booking.setBookingDate(dto.getBookingDate());
        booking.setStartTime(dto.getStartTime());
        booking.setEndTime(dto.getEndTime());
        booking.setTotalAmount(dto.getTotalAmount());

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        booking.setUserEmail(authenticatedEmail);

        bookingRepository.save(booking);
        return "Reservation successfully processed! Transaction Token: " + booking.getId();
    }

    // Securely cancel a booking
    @Transactional
    public String cancelBooking(java.util.UUID bookingId) {
        // 1. Look up the existing transaction record
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Error: Booking record not found."));

        // 2. Verification: Ensure users can only modify their own transactions
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!booking.getUserEmail().equalsIgnoreCase(currentEmail)) {
            throw new RuntimeException("Access Denied: Unauthorized cancellation attempt.");
        }

        // 3. Update status to free the slot
        booking.setStatus("CANCELLED");
        bookingRepository.save(booking);

        return "Reservation " + bookingId + " has been successfully cancelled.";
    }

    public Booking getBookingByIdForUpdate(java.util.UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Error: Booking record not found."));
    }

    public void saveUpdatedBooking(Booking booking) {
        bookingRepository.save(booking);
    }

    @Transactional
    public int bulkCancelBookings(BulkCancelRequestDto request) {
        List<Booking> affectedBookings;

        // 1. Strategically hunt down the correct bookings
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

        // 2. Process cancellations and notify the players
        for (Booking booking : affectedBookings) {
            // Cancel it
            booking.setStatus("CANCELLED");
            bookingRepository.save(booking);

            // Construct the apology notification
            String baseMessage = "Alert: Your booking for Court " + booking.getCourtId() + " on " + booking.getBookingDate() + " was forcefully canceled by the academy owner.";
            String finalMessage = (request.getMessage() != null && !request.getMessage().trim().isEmpty())
                    ? baseMessage + " Reason: " + request.getMessage()
                    : baseMessage;

            // Save the notification to the database using the player's email
            // (Assumes your Booking entity tracks the user's email as userEmail)
            com.sportsarena.booking_service.entity.Notification notification =
                    new com.sportsarena.booking_service.entity.Notification(booking.getUserEmail(), finalMessage);

            notificationRepository.save(notification);
        }

        FacilityClosure closure = new FacilityClosure();
        closure.setVenueId(request.getVenueId());
        closure.setCourtId(request.getCourtId());
        closure.setStartDate(request.getStartDate());
        closure.setEndDate(request.getEndDate());
        closure.setReason(request.getMessage() != null && !request.getMessage().trim().isEmpty()
                ? request.getMessage()
                : "Closed by academy administration.");

        closureRepository.save(closure);

        return affectedBookings.size();
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