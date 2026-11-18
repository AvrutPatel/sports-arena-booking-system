package com.sportsarena.booking_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.context.request.*;

@Service
public class VenueBookingAccessClient {
    public record Scope(Long venueId, Long courtId) {}
    public record CourtScope(Long id, Long venueId) {}
    private final RestTemplate rest;
    private final String base;
    public VenueBookingAccessClient(RestTemplate rest,
            @Value("${venue.service.base-url:http://VENUE-SERVICE}") String base) {
        this.rest = rest; this.base = base;
    }
    public Scope requireVenueOwner(Long id) {
        requireOwnerRole();
        Scope scope = get("/api/venues/" + id + "/booking-management", Scope.class);
        if (scope == null || !id.equals(scope.venueId()) || scope.courtId() != null) throw unavailable();
        return scope;
    }
    public Scope requireCourtOwner(Long id) {
        requireOwnerRole();
        Scope scope = get("/api/courts/" + id + "/booking-management", Scope.class);
        if (scope == null || !id.equals(scope.courtId()) || scope.venueId() == null) throw unavailable();
        return scope;
    }
    public CourtScope court(Long id) {
        CourtScope scope = get("/api/courts/" + id, CourtScope.class);
        if (scope == null || !id.equals(scope.id()) || scope.venueId() == null) throw unavailable();
        return scope;
    }
    private void requireOwnerRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a ->
                a.getAuthority().equals("ACADEMY_OWNER") || a.getAuthority().equals("ROLE_ACADEMY_OWNER"))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Academy owner access required.");
        }
    }
    private <T> T get(String path, Class<T> type) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION));
        try { return rest.exchange(base + path, HttpMethod.GET, new HttpEntity<>(headers), type).getBody(); }
        catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 403 || ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 401)
                throw new org.springframework.web.server.ResponseStatusException(ex.getStatusCode(), "Venue access denied or resource not found.");
            throw unavailable();
        } catch (RestClientException ex) { throw unavailable(); }
    }
    private org.springframework.web.server.ResponseStatusException unavailable() {
        return new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Venue authorization is unavailable. No changes were accepted.");
    }
}
