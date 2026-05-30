package com.sportsarena.booking_service.repository;

import com.sportsarena.booking_service.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    long countByUserEmailAndIsReadFalse(String userEmail);
}