package com.sportsarena.booking_service.service;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class VenueBookingAccessClientTests {
    MockRestServiceServer server; VenueBookingAccessClient access;
    @BeforeEach void setup() {
        RestTemplate rest = new RestTemplate(); server=MockRestServiceServer.createServer(rest);access=new VenueBookingAccessClient(rest,"http://venue");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner@example.com",null,List.of(new SimpleGrantedAuthority("ROLE_ACADEMY_OWNER"))));
        MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer owner-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void cleanup() { server.verify();SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes(); }
    @Test void ownerProofIsFetchedUsingCallerJwt() {
        server.expect(requestTo("http://venue/api/venues/1/booking-management")).andExpect(header("Authorization","Bearer owner-token"))
                .andRespond(withSuccess("{\"venueId\":1,\"courtId\":null}",MediaType.APPLICATION_JSON));
        assertEquals(1L,access.requireVenueOwner(1L).venueId());
    }
    @Test void ownerRejectionIsNotTreatedAsPermission() {
        server.expect(requestTo("http://venue/api/courts/42/booking-management")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertEquals(403,assertThrows(ResponseStatusException.class,()->access.requireCourtOwner(42L)).getStatusCode().value());
    }
    @Test void unavailableAuthorityFailsClosed() {
        server.expect(requestTo("http://venue/api/venues/1/booking-management")).andRespond(withServerError());
        assertEquals(503,assertThrows(ResponseStatusException.class,()->access.requireVenueOwner(1L)).getStatusCode().value());
    }
    @Test void playerCannotRequestManagementProof() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("player@example.com",null,List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))));
        assertEquals(403,assertThrows(ResponseStatusException.class,()->access.requireVenueOwner(1L)).getStatusCode().value());
    }
}
