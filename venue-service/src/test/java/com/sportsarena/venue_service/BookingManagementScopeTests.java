package com.sportsarena.venue_service;

import com.sportsarena.venue_service.entity.*;
import com.sportsarena.venue_service.repository.*;
import com.sportsarena.venue_service.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingManagementScopeTests {
    @Mock VenueRepository venueRepository;
    @Mock CourtRepository courtRepository;
    @InjectMocks VenueService venues;
    @InjectMocks CourtService courts;
    @BeforeEach void authenticate() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner-a@example.com",null)); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void venueOwnerCanProveOwnership() {
        Venue venue=venue("owner-a@example.com");when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        assertEquals(1L,venues.bookingManagementScope(1L).venueId());
    }
    @Test void anotherOwnerCannotProveOwnershipOfVenue() {
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue("owner-b@example.com")));
        assertEquals(403,assertThrows(ResponseStatusException.class,()->venues.bookingManagementScope(1L)).getStatusCode().value());
    }
    @Test void courtOwnershipComesFromItsActualVenue() {
        Court court=new Court();court.setId(42L);court.setVenue(venue("owner-a@example.com"));
        when(courtRepository.findById(42L)).thenReturn(Optional.of(court));
        assertEquals(1L,courts.bookingManagementScope(42L).venueId());
    }
    @Test void unownedCourtIsRejectedEvenIfCallerIsAnOwner() {
        Court court=new Court();court.setId(42L);court.setVenue(venue("owner-b@example.com"));
        when(courtRepository.findById(42L)).thenReturn(Optional.of(court));
        assertEquals(403,assertThrows(ResponseStatusException.class,()->courts.bookingManagementScope(42L)).getStatusCode().value());
    }
    private Venue venue(String owner) { Venue venue=new Venue();venue.setId(1L);venue.setOwnerId(owner);return venue; }
}
