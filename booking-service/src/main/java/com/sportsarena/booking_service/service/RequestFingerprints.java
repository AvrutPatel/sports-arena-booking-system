package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BookingRequestDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestFingerprints {
    private RequestFingerprints() {}
    public static String booking(BookingRequestDto dto) {
        // totalAmount is deliberately excluded: it is untrusted and ignored by server pricing.
        return hash(dto.getVenueId() + "|" + dto.getCourtId() + "|" + dto.getBookingDate()
                + "|" + dto.getStartTime() + "|" + dto.getEndTime());
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
