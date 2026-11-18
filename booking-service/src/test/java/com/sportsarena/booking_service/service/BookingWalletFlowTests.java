package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BookingRequestDto;
import com.sportsarena.booking_service.entity.Booking;
import com.sportsarena.booking_service.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingWalletFlowTests {
    @Mock BookingRepository bookingRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock FacilityClosureRepository closureRepository;
    @Mock BookingRecoveryWorker recoveryWorker;
    @Mock BookingPriceService priceService;
    @Mock ReservationService reservationService;
    @Mock ReservationTransactionService reservations;
    @Mock BookingHoldCleanup holdCleanup;
    @Mock VenueBookingAccessClient access;
    @InjectMocks BookingService service;
    @BeforeEach void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("player@example.com", null));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void pendingBookingUsesServerPrice() {
        BookingRequestDto request = new BookingRequestDto();
        Booking expected = booking("PENDING");
        when(priceService.calculate(request)).thenReturn(new BigDecimal("250.00"));
        when(reservationService.reserve(request, "player@example.com", new BigDecimal("250.00"))).thenReturn(expected);
        assertSame(expected, service.createPendingBooking(request, "player@example.com"));
    }

    @Test void paymentClaimCommitsBeforeWorkerRunsAndPendingResponseDoesNotAskCustomerToRetry() {
        BookingRequestDto request = new BookingRequestDto();
        Booking pending = booking("PENDING");
        when(priceService.calculate(request)).thenReturn(new BigDecimal("250.00"));
        when(reservationService.reserve(eq(request), anyString(), any(), any())).thenReturn(pending);
        when(reservations.snapshot(pending.getId())).thenReturn(bookingWithId(pending.getId(), "PAYMENT_UNKNOWN"));
        BookingService.BookingOutcome result = service.checkAndCreateBooking(request);
        assertEquals("PAYMENT_UNKNOWN", result.status());
        assertTrue(result.message().contains("processing automatically"));
        InOrder order = inOrder(reservations, recoveryWorker);
        order.verify(reservations).startPaymentOnce(pending.getId());
        order.verify(recoveryWorker).processOne(pending.getId());
    }

    @Test void expiredHoldReturnsItsSavedResultWithoutCreatingAnotherBooking() {
        BookingRequestDto request = new BookingRequestDto();
        Booking pending = booking("PENDING");
        when(reservationService.lookup(eq(request), anyString(), any())).thenReturn(pending);
        when(reservations.snapshot(pending.getId())).thenReturn(bookingWithId(pending.getId(),"EXPIRED"));
        assertEquals("EXPIRED",service.checkAndCreateBooking(request).status());
        verifyNoInteractions(priceService);
    }

    @Test void cancellationIntentIsPersistedBeforeRefundAttempt() {
        Booking pending = booking("REFUND_PENDING");
        when(reservations.requestCancellation(pending.getId(), "player@example.com", false)).thenReturn(pending);
        when(reservations.snapshot(pending.getId())).thenReturn(pending);
        assertTrue(service.cancelBooking(pending.getId()).contains("processing automatically"));
        InOrder order = inOrder(reservations, recoveryWorker);
        order.verify(reservations).requestCancellation(pending.getId(), "player@example.com", false);
        order.verify(recoveryWorker).processOne(pending.getId());
    }

    @Test void completedCancellationDoesNotRepeatRefund() {
        Booking cancelled = booking("CANCELLED");
        when(reservations.requestCancellation(cancelled.getId(), "player@example.com", false)).thenReturn(cancelled);
        assertEquals("Reservation already cancelled.", service.cancelBooking(cancelled.getId()));
        verifyNoInteractions(recoveryWorker);
    }

    private Booking booking(String status) { return bookingWithId(UUID.randomUUID(), status); }
    private Booking bookingWithId(UUID id, String status) {
        Booking booking = new Booking(); booking.setId(id); booking.setStatus(status);
        booking.setTotalAmount(new BigDecimal("250.00")); return booking;
    }
}
