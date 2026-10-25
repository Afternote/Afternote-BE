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

    @Query("select i.inviterUserId from ReceiverInvitation i where i.tokenHash = :hash")
    Optional<Long> findInviterIdByTokenHash(@Param("hash") String hash);

    @Query("select i.inviterUserId from ReceiverInvitation i where i.id = :id")
    Optional<Long> findInviterIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ReceiverInvitation i where i.id = :id")
    Optional<ReceiverInvitation> findByIdForUpdate(@Param("id") Long id);

    @Query("select i.id from ReceiverInvitation i where i.id > :afterId and i.status = "
            + "com.afternote.domain.receiver.model.ReceiverInvitationStatus.PENDING "
            + "and (i.createdAt <= :reminderBefore or i.expiresAt <= :now) order by i.id")
    java.util.List<Long> findLifecycleCandidates(@Param("afterId") Long afterId,
            @Param("reminderBefore") java.time.LocalDateTime reminderBefore,
            @Param("now") java.time.LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    void deleteByInviterUserIdOrAcceptedUserId(Long inviterUserId, Long acceptedUserId);
}
