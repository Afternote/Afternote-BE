package com.afternote.domain.mindrecord.weekly.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 월요일 02:00(Asia/Seoul)에 직전 주(월~일) 주간 리포트를 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyMindRecordScheduler {

    private final WeeklyMindRecordService weeklyMindRecordService;

    @Scheduled(
            cron = "${afternote.weekly-report.cron:0 0 2 * * MON}",
            zone = "${afternote.weekly-report.zone:Asia/Seoul}"
    )
    public void generateLastWeekReports() {
        int created = weeklyMindRecordService.generateLastWeekReports();
        log.info("[WeeklySummary] scheduler finished created={}", created);
    }
}
