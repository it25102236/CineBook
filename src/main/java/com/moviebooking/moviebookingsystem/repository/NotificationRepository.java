package com.moviebooking.moviebookingsystem.repository;

import com.moviebooking.moviebookingsystem.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientRoleOrderByCreatedAtDesc(String recipientRole);

    List<Notification> findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(String recipientEmail);

    default void markAsRead(Long id) {
        Notification notification = findById(id).orElse(null);
        if (notification != null && !notification.isRead()) {
            notification.setRead(true);
            save(notification);
        }
    }

    default void markAllAsRead(String recipientRole) {
        findByRecipientRoleOrderByCreatedAtDesc(recipientRole)
                .forEach(notification -> markAsRead(notification.getId()));
    }

    default void markAllAsReadForEmail(String recipientEmail) {
        findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(recipientEmail)
                .forEach(notification -> markAsRead(notification.getId()));
    }
}