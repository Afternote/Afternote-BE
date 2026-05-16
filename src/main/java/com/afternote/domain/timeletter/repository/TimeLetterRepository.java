package com.afternote.domain.timeletter.repository;

import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeLetterRepository extends JpaRepository<TimeLetter, Long> {

    @EntityGraph(attributePaths = {"blocks"})
    List<TimeLetter> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, TimeLetterStatus status);

    @EntityGraph(attributePaths = {"blocks"})
    Optional<TimeLetter> findByIdAndUserId(Long id, Long userId);

    List<TimeLetter> findByIdInAndUserId(List<Long> ids, Long userId);

    void deleteByUserIdAndStatus(Long userId, TimeLetterStatus status);

    @EntityGraph(attributePaths = {"blocks"})
    List<TimeLetter> findByStatusAndSendAtBefore(TimeLetterStatus status, LocalDateTime dateTime);
}