package com.sportsarena.booking_service.service;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservation_h2;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.username=sa", "spring.datasource.password="
})
class ReservationH2Tests extends AbstractReservationConcurrencyTests {}
