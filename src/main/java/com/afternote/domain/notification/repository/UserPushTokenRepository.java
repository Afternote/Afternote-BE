package com.afternote.domain.notification.repository;

import com.afternote.domain.notification.model.UserPushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, Long> {

    Optional<UserPushToken> findByToken(String token);

    List<UserPushToken> findAllByUser_Id(Long userId);

    long countByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);

    void deleteByUser_IdAndToken(Long userId, String token);
}
