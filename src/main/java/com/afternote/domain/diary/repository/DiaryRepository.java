package com.afternote.domain.diary.repository;

import com.afternote.domain.diary.model.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

	Optional<Diary> findByIdAndUserId(Long diaryId, Long userId);

	List<Diary> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
			Long userId,
			LocalDateTime start,
			LocalDateTime end
	);
}
