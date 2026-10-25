package com.afternote.domain.notification.repository;

import com.afternote.domain.notification.model.UserPushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, Long> {

    Optional<UserPushToken> findByToken(String token);

    List<UserPushToken> findAllByUser_Id(Long userId);

    long countByUser_Id(Long userId);

    // Update only the original owner: a late FCM response must not alter another account.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserPushToken t set t.pushEnabled = false, t.invalidatedAt = :at, "
            + "t.invalidationReason = 'UNREGISTERED' where t.id = :id and t.user.id = :userId")
    int markInvalid(@Param("id") Long id, @Param("userId") Long userId, @Param("at") LocalDateTime at);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserPushToken t set t.invalidatedAt = null, t.invalidationReason = null "
            + "where t.id = :id and t.user.id = :userId and t.pushEnabled = true")
    int clearInvalidation(@Param("id") Long id, @Param("userId") Long userId);


    void deleteByUser_Id(Long userId);

    void deleteByUser_IdAndToken(Long userId, String token);
}
