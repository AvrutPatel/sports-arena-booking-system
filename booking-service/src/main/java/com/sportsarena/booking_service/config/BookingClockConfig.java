package com.sportsarena.booking_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

@Configuration
@EnableScheduling
public class BookingClockConfig {
    @Bean
    public ThreadPoolTaskScheduler bookingTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("booking-recovery-");
        return scheduler;
    }
    @Bean
    public Clock bookingClock() {
        return Clock.systemDefaultZone();
    }
}
