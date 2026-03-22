package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReceivedRepository extends JpaRepository<Receiver, Long> {

	List<Receiver> findByIdIn(List<Long> ids);

}
