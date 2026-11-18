package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BookingRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BookingPriceServiceTests {
    private MockRestServiceServer server;
    private BookingPriceService prices;

    @BeforeEach
    void setup() {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.createServer(rest);
        prices = new BookingPriceService(rest, "http://venue/api/courts/venue/{venueId}");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer customer-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void cleanup() {
        server.verify();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ignoresClientPriceAndChargesForFullDuration() {
        server.expect(requestTo("http://venue/api/courts/venue/1"))
                .andExpect(header("Authorization", "Bearer customer-token"))
                .andRespond(withSuccess("[{\"id\":42,\"venueId\":1,\"hourlyRate\":120.00,\"name\":\"Court\"}]", MediaType.APPLICATION_JSON));
        assertEquals(new BigDecimal("180.00"), prices.calculate(request("10:00", "11:30")));
    }

    @Test
    void roundsFractionalHourPriceToTwoDecimals() {
        server.expect(requestTo("http://venue/api/courts/venue/1"))
                .andRespond(withSuccess("[{\"id\":42,\"venueId\":1,\"hourlyRate\":100.00}]", MediaType.APPLICATION_JSON));
        assertEquals(new BigDecimal("1.67"), prices.calculate(request("10:00", "10:01")));
    }

    @Test
    void missingOrMismatchedCourtCannotBeBooked() {
        server.expect(requestTo("http://venue/api/courts/venue/1"))
                .andRespond(withSuccess("[{\"id\":42,\"venueId\":2,\"hourlyRate\":120.00}]", MediaType.APPLICATION_JSON));
        assertThrows(IllegalArgumentException.class, () -> prices.calculate(request("10:00", "11:00")));
    }

    @Test
    void invalidDurationDoesNotCallVenueService() {
        assertThrows(IllegalArgumentException.class, () -> prices.calculate(request("11:00", "10:00")));
        assertThrows(IllegalArgumentException.class, () -> prices.calculate(request("10:00", "10:00")));
        assertThrows(IllegalArgumentException.class, () -> prices.calculate(request("10:00:01", "11:00")));
    }

    private BookingRequestDto request(String start, String end) {
        BookingRequestDto dto = new BookingRequestDto();
        dto.setCourtId(42L);
        dto.setVenueId(1L);
        dto.setStartTime(LocalTime.parse(start));
        dto.setEndTime(LocalTime.parse(end));
        dto.setTotalAmount(new BigDecimal("0.01"));
        return dto;
    }
}
