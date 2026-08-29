package com.afternote.domain.mindrecord.weekly.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WeeklyMindRecordSchedulerTest {

    @Mock
    private WeeklyMindRecordService weeklyMindRecordService;

    @InjectMocks
    private WeeklyMindRecordScheduler scheduler;

    @Test
    @DisplayName("스케줄 실행 시 직전 주 리포트 생성을 위임한다")
    void delegatesToService() {
        given(weeklyMindRecordService.generateLastWeekReports()).willReturn(3);

        scheduler.generateLastWeekReports();

        verify(weeklyMindRecordService).generateLastWeekReports();
    }
}
