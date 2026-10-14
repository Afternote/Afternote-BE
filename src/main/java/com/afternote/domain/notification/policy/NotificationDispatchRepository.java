package com.afternote.domain.notification.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, Long> {
    Optional<NotificationDispatch> findByUserIdAndEventKey(Long userId, String eventKey);
    List<NotificationDispatch> findByUserIdAndStatusAndDueAtLessThanEqual(
            Long userId, NotificationDispatch.Status status, LocalDateTime now);
    boolean existsByUserIdAndSelectedDate(Long userId, LocalDate day);
    @Query("select distinct n.userId from NotificationDispatch n where "
            + "n.status in :statuses and n.dueAt <= :now")
    List<Long> findDueUsers(@Param("now") LocalDateTime now, @Param("statuses") List<NotificationDispatch.Status> statuses, Pageable pageable);
    List<NotificationDispatch> findByUserIdAndStatus(Long userId, NotificationDispatch.Status status);
    List<NotificationDispatch> findByUserIdAndVisibleAtIsNotNullAndStatusNotOrderByIdDesc(Long userId, NotificationDispatch.Status excluded, Pageable pageable);
    long countByUserIdAndVisibleAtIsNotNullAndReadAtIsNullAndStatusNot(Long userId, NotificationDispatch.Status excluded);
    Optional<NotificationDispatch> findByIdAndUserId(Long id, Long userId);
}
