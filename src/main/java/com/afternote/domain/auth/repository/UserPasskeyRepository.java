package com.afternote.domain.auth.repository;

import com.afternote.domain.auth.model.UserPasskey;
import com.afternote.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPasskeyRepository extends JpaRepository<UserPasskey, Long> {

    List<UserPasskey> findAllByUserOrderByIdDesc(User user);

    @Query("SELECT p FROM UserPasskey p JOIN FETCH p.user WHERE p.credentialId = :credentialId")
    Optional<UserPasskey> findByCredentialId(@Param("credentialId") byte[] credentialId);

    boolean existsByCredentialId(byte[] credentialId);

    long countByUser(User user);

    void deleteAllByUser(User user);
}
