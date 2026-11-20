package com.incident.backend.repository;

import com.incident.backend.model.Notification;
import com.incident.backend.model.NotificationDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);
    Page<Notification> findAllByUserIdOrderByCreatedAtDescIdDesc(Integer userId, Pageable pageable);
    long countByUserIdAndIsReadFalse(Integer userId);
    long countByIncidentIdAndNotificationTypeAndUserId(Integer incidentId, String notificationType, Integer userId);
    Optional<Notification> findByIdAndUserId(Integer id, Integer userId);
    Optional<Notification> findByEventKey(String eventKey);
    boolean existsByEventKey(String eventKey);
    List<Notification> findByDeliveryStatusOrderByCreatedAtAsc(
            NotificationDeliveryStatus deliveryStatus, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> findByIdForDelivery(@Param("id") Integer id);

    @Query("""
            select n.id from Notification n
            where n.eventKey is not null and (n.deliveryStatus = :pending
                or (n.deliveryStatus = :failed and n.deliveryAttempts < :maxAttempts))
            order by n.createdAt asc, n.id asc
            """)
    List<Integer> findRetryableIds(@Param("pending") NotificationDeliveryStatus pending,
                                   @Param("failed") NotificationDeliveryStatus failed,
                                   @Param("maxAttempts") Integer maxAttempts, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query(value = """
            INSERT INTO notifications
                (user_id, incident_id, message, notification_type, delivery_status, is_read, event_key, delivery_attempts)
            VALUES
                (:userId, :incidentId, :message, :notificationType, 'PENDING', FALSE, :eventKey, 0)
            ON CONFLICT (event_key) WHERE event_key IS NOT NULL DO NOTHING
            """, nativeQuery = true)
    int insertIfEventKeyAbsent(@Param("userId") Integer userId,
                               @Param("incidentId") Integer incidentId,
                               @Param("message") String message,
                               @Param("notificationType") String notificationType,
                               @Param("eventKey") String eventKey);
}
