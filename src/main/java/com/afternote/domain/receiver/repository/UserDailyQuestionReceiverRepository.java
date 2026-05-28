package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.UserDailyQuestionReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDailyQuestionReceiverRepository extends JpaRepository<UserDailyQuestionReceiver, Long> {

    List<UserDailyQuestionReceiver> findByUserDailyQuestionIdAndReceiverIdIn(Long userDailyQuestionId, List<Long> receiverIds);

    void deleteByUserDailyQuestionId(Long userDailyQuestionId);
}
