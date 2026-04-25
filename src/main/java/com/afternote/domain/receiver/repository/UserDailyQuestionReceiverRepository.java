package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.UserDailyQuestionReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDailyQuestionReceiverRepository extends JpaRepository<UserDailyQuestionReceiver, Long> {
}