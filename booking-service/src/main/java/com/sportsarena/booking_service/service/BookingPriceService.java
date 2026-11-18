package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BookingRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Arrays;

@Service
public class BookingPriceService {
    private final RestTemplate restTemplate;
    private final String courtsUrl;

    public BookingPriceService(RestTemplate restTemplate,
            @Value("${venue.service.courts.url:http://VENUE-SERVICE/api/courts/venue/{venueId}}") String courtsUrl) {
        this.restTemplate = restTemplate;
        this.courtsUrl = courtsUrl;
    }

    public BigDecimal calculate(BookingRequestDto request) {
        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getStartTime().isBefore(request.getEndTime())
                || request.getStartTime().getSecond() != 0 || request.getEndTime().getSecond() != 0
                || request.getStartTime().getNano() != 0 || request.getEndTime().getNano() != 0) {
            throw new IllegalArgumentException("Booking must have a positive duration in whole minutes within one day.");
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION));
        CourtPrice[] courts = restTemplate.exchange(courtsUrl, HttpMethod.GET, new HttpEntity<>(headers),
                CourtPrice[].class, request.getVenueId()).getBody();
        CourtPrice court = courts == null ? null : Arrays.stream(courts)
                .filter(c -> request.getCourtId().equals(c.id()) && request.getVenueId().equals(c.venueId()))
                .findFirst().orElse(null);
        if (court == null || court.hourlyRate() == null || court.hourlyRate().signum() <= 0) {
            throw new IllegalArgumentException("Court is unavailable or has an invalid rate.");
        }
        long minutes = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
        BigDecimal amount = court.hourlyRate().multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.precision() > 19) {
            throw new IllegalArgumentException("Calculated booking price is outside supported limits.");
        }
        return amount;
    }

    // The venue endpoint returns active courts only. Unknown response fields are ignored by Spring's mapper.
    public record CourtPrice(Long id, Long venueId, BigDecimal hourlyRate) {}
}
