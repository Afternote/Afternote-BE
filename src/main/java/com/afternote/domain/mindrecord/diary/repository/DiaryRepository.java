package com.afternote.domain.mindrecord.diary.repository;

import com.afternote.domain.mindrecord.diary.model.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {
}