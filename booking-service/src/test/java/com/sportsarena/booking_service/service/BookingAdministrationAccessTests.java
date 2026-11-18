package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BulkCancelRequestDto;
import com.sportsarena.booking_service.entity.*;
import com.sportsarena.booking_service.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingAdministrationAccessTests {
    @Mock VenueBookingAccessClient access;
    @Mock ReservationService reservations;
    @Mock BookingAdministrationTransactions transactions;
    @Mock BulkCancellationJobRepository jobs;
    @Mock FacilityClosureRepository closures;
    @InjectMocks BookingAdministrationService service;
    @BeforeEach void authenticate() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner@example.com",null)); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void unownedVenueCannotCreateClosureOrJob() {
        when(access.requireVenueOwner(1L)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class,()->service.submit(request(1L,null),UUID.randomUUID()));
        verifyNoInteractions(transactions,reservations,jobs,closures);
    }
    @Test void forgedCourtVenueAssociationIsRejectedBeforeWrites() {
        when(access.requireCourtOwner(42L)).thenReturn(new VenueBookingAccessClient.Scope(2L,42L));
        assertThrows(ResponseStatusException.class,()->service.submit(request(1L,42L),UUID.randomUUID()));
        verifyNoInteractions(transactions,reservations,jobs,closures);
    }
    @Test void unownedClosureCannotBeRemoved() {
        FacilityClosure closure = new FacilityClosure(); closure.setVenueId(2L); closure.setCourtId(42L);
        when(closures.findById(5L)).thenReturn(Optional.of(closure));
        when(access.requireCourtOwner(42L)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class,()->service.removeClosure(5L));
        verifyNoInteractions(transactions,reservations);
    }
    @Test void jobProgressRequiresCurrentVenueOwnership() {
        BulkCancellationJob job = new BulkCancellationJob(); job.setId(UUID.randomUUID()); job.setVenueId(2L);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(access.requireVenueOwner(2L)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class,()->service.status(job.getId()));
        verifyNoInteractions(transactions);
    }
    @Test void authorizedSubmissionUsesCanonicalScopeAndAuthenticatedOwner() {
        var scope = new VenueBookingAccessClient.Scope(2L,42L);
        when(access.requireCourtOwner(42L)).thenReturn(scope);
        UUID key = UUID.randomUUID(); BulkCancelRequestDto request = request(null,42L);
        BulkCancellationJob job = new BulkCancellationJob(); job.setId(UUID.randomUUID());job.setVenueId(2L);job.setCourtId(42L);job.setState("ENQUEUING");
        when(transactions.create(request,scope,"owner@example.com",key)).thenReturn(job);
        assertEquals(job.getId(),service.submit(request,key).id());
        verify(reservations).prepareVenue(2L);
    }
    private BulkCancelRequestDto request(Long venue,Long court) {
        BulkCancelRequestDto dto = new BulkCancelRequestDto(); dto.setVenueId(venue);dto.setCourtId(court);dto.setStartDate(LocalDate.now().plusDays(1));return dto;
    }
}
