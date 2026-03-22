package com.afternote.domain.mindrecord.service;

import com.afternote.domain.mindrecord.diary.model.Diary;
import com.afternote.domain.mindrecord.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.dto.PatchMindRecordRequest;
import com.afternote.domain.mindrecord.dto.PostMindRecordRequest;
import com.afternote.domain.mindrecord.model.MindRecord;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DiaryService {

    private final DiaryRepository diaryRepository;

    public void create(MindRecord record, PostMindRecordRequest request) {
        Diary diary = Diary.create(record);
        diaryRepository.save(diary);
    }

    public void update(MindRecord record, PatchMindRecordRequest request) {
        Diary diary = diaryRepository.findByMindRecord(record)
                .orElseThrow(() -> new CustomException(ErrorCode.MIND_RECORD_NOT_FOUND));
    }
}