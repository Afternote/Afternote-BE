package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.ReceiverInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReceiverInvitationRepository extends JpaRepository<ReceiverInvitation, Long> {

    Optional<ReceiverInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from ReceiverInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<ReceiverInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    void deleteByInviterUserIdOrAcceptedUserId(Long inviterUserId, Long acceptedUserId);
}
