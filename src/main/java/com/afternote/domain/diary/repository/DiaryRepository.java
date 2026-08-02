package com.afternote.domain.diary.repository;

import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
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
}
