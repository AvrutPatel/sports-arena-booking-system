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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import com.sportsarena.booking_service.entity.Notification;

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

    @Value("${auth.service.wallet.deduct.url}")
    private String walletDeductUrl;

    @Value("${auth.service.wallet.refund.url}")
    private String walletRefundUrl;

    // Get user bookings
    public List<Booking> getAuthenticatedUserBookings() {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return bookingRepository.findByUserEmailOrderByBookingDateDesc(currentEmail);
    }
    @Transactional
    public void cleanStalePendingBookings() {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        List<Booking> staleBookings = bookingRepository.findByStatusAndCreatedAtBefore("PENDING", fiveMinutesAgo);
        for (Booking booking : staleBookings) {
            booking.setStatus("FAILED");
            bookingRepository.save(booking);
        }
    }

    public List<SlotResponseDto> getAvailableSlots(Long courtId, LocalDate date) {
        cleanStalePendingBookings();
        List<SlotResponseDto> timeline = new ArrayList<>();
        LocalTime openTime = LocalTime.of(6, 0);
        LocalTime closeTime = LocalTime.of(22, 0);

        List<Booking> activeBookings = bookingRepository.findByCourtIdAndBookingDateAndStatus(courtId, date, "CONFIRMED");

        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        while (openTime.isBefore(closeTime)) {
            LocalTime slotEnd = openTime.plusHours(1);
            LocalTime finalOpenTime = openTime;

            boolean isBooked = activeBookings.stream().anyMatch(b ->
                    (finalOpenTime.isBefore(b.getEndTime()) && slotEnd.isAfter(b.getStartTime()))
            );

            boolean isPast = date.isEqual(today) && finalOpenTime.isBefore(currentTime);
            boolean isAvailable = !isBooked && !isPast;

            timeline.add(new SlotResponseDto(openTime, slotEnd, isAvailable));
            openTime = slotEnd;
        }
        return timeline;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking createPendingBooking(BookingRequestDto dto, String authenticatedEmail) {
        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        // No past dates
        if (dto.getBookingDate().isBefore(today)) {
            throw new RuntimeException("Validation Failure: Cannot book a date in the past.");
        }

        // No past times today
        if (dto.getBookingDate().isEqual(today) && dto.getStartTime().isBefore(currentTime)) {
            throw new RuntimeException("Validation Failure: This time slot has already passed.");
        }

        // Max 30 days advance
        if (dto.getBookingDate().isAfter(today.plusDays(30))) {
            throw new RuntimeException("Validation Failure: You can only book up to 30 days in advance.");
        }

        // Check for overlaps
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
        booking.setUserEmail(authenticatedEmail);
        booking.setStatus("PENDING");

        return bookingRepository.save(booking);
    }

    @Transactional
    public void confirmBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus("CONFIRMED");
        bookingRepository.save(booking);
    }

    @Transactional
    public void failBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus("FAILED");
        bookingRepository.save(booking);
    }

    // Orchestrator for booking process
    @CircuitBreaker(name = "walletDeduction", fallbackMethod = "fallbackCreateBooking")
    public String checkAndCreateBooking(BookingRequestDto dto) {
        cleanStalePendingBookings();
        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        // Create pending booking
        Booking pendingBooking = createPendingBooking(dto, authenticatedEmail);

        // Prepare auth-service call
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String jwtToken = attributes.getRequest().getHeader("Authorization");
        String authServiceUrl = walletDeductUrl + "?amount=" + dto.getTotalAmount();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", jwtToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // Deduct wallet
            restTemplate.exchange(authServiceUrl, HttpMethod.POST, entity, String.class);
            
            // Confirm booking on success
            confirmBooking(pendingBooking.getId());
            return "Reservation successfully processed! ₹" + dto.getTotalAmount() + " deducted from your wallet.";
            
        } catch (HttpClientErrorException e) {
            // Free slot on failure
            failBooking(pendingBooking.getId());
            throw new RuntimeException("Booking failed: Insufficient wallet balance. Please add funds to your profile.");
        } catch (Exception e) {
            // Free slot on network error
            failBooking(pendingBooking.getId());
            throw new RuntimeException("Booking failed due to an unexpected error during wallet deduction.");
        }
    }

    public String fallbackCreateBooking(BookingRequestDto dto, Throwable t) {
        cleanStalePendingBookings();
        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        
        // Find the pending booking created in this flow and fail it
        List<Booking> userBookings = bookingRepository.findByUserEmailOrderByBookingDateDesc(authenticatedEmail);
        for(Booking b : userBookings) {
            if(b.getStatus().equals("PENDING") && b.getCourtId().equals(dto.getCourtId()) && b.getBookingDate().equals(dto.getBookingDate()) && b.getStartTime().equals(dto.getStartTime())) {
                failBooking(b.getId());
                break;
            }
        }
        
        throw new RuntimeException("Booking failed: Wallet service is currently unavailable. Please try again later.");
    }
    
    public Booking getBookingByIdForUpdate(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Error: Booking record not found."));
    }

    // Cancel booking
    @Transactional
    public String cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Error: Booking record not found."));

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!booking.getUserEmail().equalsIgnoreCase(currentEmail)) {
            throw new RuntimeException("Access Denied: Unauthorized cancellation attempt.");
        }

        double refundPercentage = calculateRefundPercentage(booking.getBookingDate());
        double refundAmount = booking.getTotalAmount() * refundPercentage;

        if (refundAmount > 0) {
            processCrossServiceRefund(booking.getUserEmail(), refundAmount);
        }

        booking.setStatus("CANCELLED");
        bookingRepository.save(booking);

        String msg = String.format(
                "You cancelled your booking on %s. Based on our policy, a %.0f%% refund of ₹%.2f has been credited to your wallet.",
                booking.getBookingDate(), (refundPercentage * 100), refundAmount
        );
        notificationRepository.save(new Notification(booking.getUserEmail(), msg));

        return "Reservation cancelled. ₹" + refundAmount + " refunded to your wallet.";
    }

    // Bulk cancel bookings
    @Transactional
    public int bulkCancelBookings(BulkCancelRequestDto request) {
        List<Booking> affectedBookings;

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

        // Process cancellations and notify
        for (Booking booking : affectedBookings) {
            processCrossServiceRefund(booking.getUserEmail(), booking.getTotalAmount());
            booking.setStatus("CANCELLED");
            bookingRepository.save(booking);

            String baseMessage = "Alert: Your booking for Court " + booking.getCourtId() + " on " + booking.getBookingDate() +
                    " was forcefully canceled by the academy. A 100% refund of ₹" + booking.getTotalAmount() +
                    " has been credited back to your wallet.";

            String finalMessage = (request.getMessage() != null && !request.getMessage().trim().isEmpty())
                    ? baseMessage + " Reason: " + request.getMessage()
                    : baseMessage;

            notificationRepository.save(new Notification(booking.getUserEmail(), finalMessage));
        }

        // Save facility closure
        FacilityClosure closure = new FacilityClosure();
        closure.setVenueId(request.getVenueId());
        closure.setCourtId(request.getCourtId());
        closure.setStartDate(request.getStartDate());
        closure.setEndDate(request.getEndDate());
        closure.setReason(request.getMessage() != null && !request.getMessage().trim().isEmpty() ? request.getMessage() : "Closed by academy administration.");
        closureRepository.save(closure);

        return affectedBookings.size();
    }

    // Generate refund preview
    public Map<String, Object> getRefundPreview(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();

        double refundPercentage = calculateRefundPercentage(booking.getBookingDate());
        double refundAmount = booking.getTotalAmount() * refundPercentage;

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

    private void processCrossServiceRefund(String targetEmail, double amount) {
        // Extract JWT token
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String jwtToken = attributes.getRequest().getHeader("Authorization");

        String url = walletRefundUrl + "?targetEmail=" + targetEmail + "&amount=" + amount;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", jwtToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
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

    public List<Booking> getBookingsByVenue(Long venueId) {
        return bookingRepository.findByVenueIdOrderByBookingDateDesc(venueId);
    }
}