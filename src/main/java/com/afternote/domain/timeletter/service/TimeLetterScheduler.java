package com.afternote.domain.timeletter.service;

import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeLetterScheduler {

    private final TimeLetterRepository timeLetterRepository;

    /**
     * 매 1분마다 발송 시각이 지난 예약 타임레터를 SENT 상태로 변경한다.
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void updateScheduledToSent() {
        LocalDateTime processedAt = LocalDateTime.now();
        int updatedCount = timeLetterRepository.markDueDateLettersAsSent(processedAt);

        if (updatedCount > 0) {
            log.info("타임레터 {}건을 SENT 상태로 변경", updatedCount);
        }
    }
}
