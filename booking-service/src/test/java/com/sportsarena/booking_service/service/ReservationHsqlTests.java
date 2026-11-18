package com.sportsarena.booking_service.service;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:hsqldb:mem:reservation_hsql;hsqldb.tx=mvcc",
        "spring.datasource.driver-class-name=org.hsqldb.jdbc.JDBCDriver",
        "spring.jpa.database-platform=org.hibernate.dialect.HSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.HSQLDialect",
        "spring.datasource.username=sa", "spring.datasource.password="
})
class ReservationHsqlTests extends AbstractReservationConcurrencyTests {}
