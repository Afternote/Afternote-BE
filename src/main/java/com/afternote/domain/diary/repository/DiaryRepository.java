package com.afternote.domain.diary.repository;

import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

	@Query("SELECT d.id FROM Diary d WHERE d.user.id = :userId")
	List<Long> findIdsByUserId(@Param("userId") Long userId);

	Optional<Diary> findByIdAndUserId(Long diaryId, Long userId);

	List<Diary> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
			Long userId,
			LocalDateTime start,
			LocalDateTime end
	);

	List<Diary> findByUserIdAndIsDraftTrueAndCreatedAtBetweenOrderByCreatedAtDesc(
			Long userId,
			LocalDateTime start,
			LocalDateTime end
	);

	List<Diary> findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
			Long userId,
			LocalDateTime startInclusive,
			LocalDateTime endExclusive
	);

	long countByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
			Long userId,
			LocalDateTime startInclusive,
			LocalDateTime endExclusive
	);

	@Query("SELECT d.todayMood FROM Diary d WHERE d.user.id = :userId AND d.isDraft = false "
			+ "AND d.createdAt >= :startInclusive AND d.createdAt < :endExclusive")
	List<TodayMood> findTodayMoodsByUserIdAndCreatedAtRange(
			@Param("userId") Long userId,
			@Param("startInclusive") LocalDateTime startInclusive,
			@Param("endExclusive") LocalDateTime endExclusive
	);

	/** 감정 행이 없는 최종 일기 (userId, diaryId). 백필용. */
	@Query(value = """
			SELECT d.user_id, d.id
			FROM diary d
			LEFT JOIN emotions e
			  ON e.user_id = d.user_id AND e.source_type = 'DIARY' AND e.source_id = d.id
			WHERE d.is_draft = 0 AND e.id IS NULL
			ORDER BY d.id ASC
			""", nativeQuery = true)
	List<Object[]> findFinalDiariesMissingEmotion(Pageable pageable);
}
