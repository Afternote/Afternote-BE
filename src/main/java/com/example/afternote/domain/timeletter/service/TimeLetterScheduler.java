package com.example.afternote.domain.timeletter.service;

import com.example.afternote.domain.timeletter.model.TimeLetter;
import com.example.afternote.domain.timeletter.model.TimeLetterStatus;
import com.example.afternote.domain.timeletter.repository.TimeLetterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeLetterScheduler {

    private final TimeLetterRepository timeLetterRepository;

    /**
     * 매 1분마다 sendAt이 지난 SCHEDULED 상태의 타임레터를 SENT로 전환
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void updateScheduledToSent() {
        List<TimeLetter> letters = timeLetterRepository
                .findByStatusAndSendAtBefore(TimeLetterStatus.SCHEDULED, LocalDateTime.now());

        if (!letters.isEmpty()) {
            letters.forEach(TimeLetter::markAsSent);
            log.info("타임레터 {}건 상태를 SENT로 전환", letters.size());
        }
    }
}
