package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.DiaryReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiaryReceiverRepository extends JpaRepository<DiaryReceiver, Long> {

    List<DiaryReceiver> findByDiaryIdAndReceiverIdIn(Long diaryId, List<Long> receiverIds);

    void deleteByDiaryId(Long diaryId);
}
