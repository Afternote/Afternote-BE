package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.Receiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiverRepository extends JpaRepository<Receiver, Long> {
    List<Receiver> findAllByUserId(Long userId);
    List<Receiver> findAllByAcceptedUserIdOrderByIdDesc(Long acceptedUserId);
    Optional<Receiver> findByIdAndAcceptedUserId(Long id, Long acceptedUserId);
    boolean existsByUserIdAndAcceptedUserId(Long userId, Long acceptedUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Receiver receiver set receiver.acceptedUserId = null where receiver.acceptedUserId = :userId")
    int clearAcceptedUserId(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
