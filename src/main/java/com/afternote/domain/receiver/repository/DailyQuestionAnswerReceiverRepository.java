package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.DailyQuestionAnswerReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyQuestionAnswerReceiverRepository extends JpaRepository<DailyQuestionAnswerReceiver, Long> {
}