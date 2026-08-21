package com.afternote.domain.timeletter.repository;

import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimeLetterRepository extends JpaRepository<TimeLetter, Long> {

    @Query("SELECT t.id FROM TimeLetter t WHERE t.user.id = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"blocks"})
    List<TimeLetter> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, TimeLetterStatus status);

    @EntityGraph(attributePaths = {"blocks"})
    Optional<TimeLetter> findByIdAndUserId(Long id, Long userId);

    /**
     * 동일 타임레터 동시 수정 시 blocks orphanRemoval 충돌을 막기 위해 행 단위 배타 락.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"blocks"})
    @Query("SELECT t FROM TimeLetter t WHERE t.id = :id AND t.user.id = :userId")
    Optional<TimeLetter> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    List<TimeLetter> findByIdInAndUserId(List<Long> ids, Long userId);

    void deleteByUserIdAndStatus(Long userId, TimeLetterStatus status);
}
