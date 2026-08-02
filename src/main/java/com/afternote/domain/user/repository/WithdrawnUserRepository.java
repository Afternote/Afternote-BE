package com.afternote.domain.user.repository;

import com.afternote.domain.user.model.WithdrawnUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WithdrawnUserRepository extends JpaRepository<WithdrawnUser, Long> {

    Optional<WithdrawnUser> findTopByEmailIgnoreCaseOrderByWithdrawnAtDesc(String email);
}
