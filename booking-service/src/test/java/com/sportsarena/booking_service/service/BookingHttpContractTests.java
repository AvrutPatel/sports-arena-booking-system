package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.controller.BookingController;
import com.sportsarena.booking_service.repository.FacilityClosureRepository;
import com.sportsarena.booking_service.security.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties={"jwt.secret=booking-http-contract-test-signing-key-at-least-32-bytes","debug=false","logging.level.org.springframework=WARN"})
class BookingHttpContractTests {
    private static final String SECRET="booking-http-contract-test-signing-key-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @MockitoBean BookingService bookings;
    @MockitoBean BookingAdministrationService administration;
    @MockitoBean FacilityClosureRepository closures;
    private final String booking="{\"venueId\":1,\"courtId\":42,\"bookingDate\":\"2030-01-02\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}";
    private final String bulk="{\"venueId\":1,\"startDate\":\"2030-01-02\"}";

    @Test void bookingRequiresIdempotencyKey() throws Exception {
        mvc.perform(post("/api/bookings").header("Authorization",token("PLAYER")).contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bookings);
    }
    @Test void bookingPassesOriginalRequestKeyAndReturnsSavedBookingId() throws Exception {
        UUID key=UUID.randomUUID(), id=UUID.randomUUID();
        when(bookings.checkAndCreateBooking(any(),eq(key))).thenReturn(new BookingService.BookingOutcome(id,"CONFIRMED","Confirmed"));
        mvc.perform(post("/api/bookings").header("Authorization",token("PLAYER")).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isOk()).andExpect(header().string("X-Booking-Id",id.toString()));
    }
    @Test void playerCannotSubmitBulkJob() throws Exception {
        mvc.perform(post("/api/bookings/admin/bulk-cancel").header("Authorization",token("PLAYER"))
                        .header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(bulk))
                .andExpect(status().isForbidden());
        verifyNoInteractions(administration);
    }
    @Test void ownerOwnershipDenialRemainsForbiddenAtHttpBoundary() throws Exception {
        when(administration.submit(any(),any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"Unowned venue"));
        mvc.perform(post("/api/bookings/admin/bulk-cancel").header("Authorization",token("ACADEMY_OWNER"))
                        .header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(bulk))
                .andExpect(status().isForbidden());
    }
    @Test void unownedClosureCannotBeReopened() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"Unowned closure")).when(administration).removeClosure(5L);
        mvc.perform(delete("/api/bookings/admin/closures/5").header("Authorization",token("ACADEMY_OWNER")))
                .andExpect(status().isForbidden());
    }
    @Test void bulkSubmissionRequiresIdempotencyKey() throws Exception {
        mvc.perform(post("/api/bookings/admin/bulk-cancel").header("Authorization",token("ACADEMY_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(bulk))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(administration);
    }
    private String token(String role) {
        return "Bearer "+Jwts.builder().setSubject("caller@example.com").claim("role",role)
                .setExpiration(new Date(System.currentTimeMillis()+60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
