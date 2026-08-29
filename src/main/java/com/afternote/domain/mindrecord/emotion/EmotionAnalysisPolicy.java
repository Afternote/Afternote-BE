package com.afternote.domain.mindrecord.emotion;

import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionAnalysisStatus;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.mindrecord.weekly.repository.WeeklyReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * 감정 분석 요청을 막을지 판단한다.
 * <ul>
 *   <li>지난 주이면서 주간 리포트가 이미 있으면 신규 분석 금지 (PENDING 재시도는 허용)</li>
 *   <li>재분석(SUCCEEDED)은 같은 기록당 24시간에 1회</li>
 *   <li>FAILED는 당일 2~3회 후 멈추고, 열린 주면 매일 01:00에 PENDING으로 리셋해 다시 시도</li>
 *   <li>이번 주는 리포트 캐시가 있어도 분석을 막지 않음 (월요일 02:00 이전)</li>
 * </ul>
 */
@Slf4j
@Component
public class EmotionAnalysisPolicy {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public static final Duration REANALYSIS_COOLDOWN = Duration.ofHours(24);
    static final int WEEK_CLOSE_HOUR = 2;

    private final WeeklyReportRepository weeklyReportRepository;
    private final EmotionRepository emotionRepository;
    private final Clock clock;

    public EmotionAnalysisPolicy(
            WeeklyReportRepository weeklyReportRepository,
            EmotionRepository emotionRepository
    ) {
        this(weeklyReportRepository, emotionRepository, Clock.system(SEOUL));
    }

    EmotionAnalysisPolicy(
            WeeklyReportRepository weeklyReportRepository,
            EmotionRepository emotionRepository,
            Clock clock
    ) {
        this.weeklyReportRepository = weeklyReportRepository;
        this.emotionRepository = emotionRepository;
        this.clock = clock;
    }

    public boolean allowAnalysis(
            Long userId,
            EmotionSourceType sourceType,
            Long sourceId,
            LocalDate recordDate
    ) {
        Optional<Emotion> existing = emotionRepository.findByUserIdAndSourceTypeAndSourceId(
                userId, sourceType, sourceId);
        boolean pendingRetry = existing
                .filter(e -> e.effectiveStatus() == EmotionAnalysisStatus.PENDING)
                .isPresent();

        if (recordDate != null && isWeekClosed(userId, recordDate)) {
            if (pendingRetry) {
                return true;
            }
            log.info("[EmotionPolicy] skip closed week userId={} sourceType={} sourceId={} date={}",
                    userId, sourceType, sourceId, recordDate);
            return false;
        }

        if (existing.isEmpty() || pendingRetry) {
            return true;
        }

        Emotion emotion = existing.get();
        LocalDateTime last = emotion.getLastAttemptAt() != null
                ? emotion.getLastAttemptAt()
                : emotion.getCreatedAt();
        if (last == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (last.plus(REANALYSIS_COOLDOWN).isAfter(now)) {
            log.info("[EmotionPolicy] skip reanalysis cooldown userId={} sourceType={} sourceId={}",
                    userId, sourceType, sourceId);
            return false;
        }
        return true;
    }

    /**
     * 해당 기록일이 속한 주가 마감됐는지.
     * 마감 시각은 그 다음 월요일 02:00(Asia/Seoul)이며, 그때 주간 리포트가 있어야 닫힌다.
     */
    public boolean isWeekClosed(Long userId, LocalDate recordDate) {
        if (userId == null || recordDate == null) {
            return false;
        }
        LocalDate weekMonday = recordDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime closeAt = weekMonday.plusWeeks(1).atTime(WEEK_CLOSE_HOUR, 0);
        if (LocalDateTime.now(clock).isBefore(closeAt)) {
            return false;
        }
        return weeklyReportRepository.findByUserIdAndStartDate(userId, weekMonday.atStartOfDay()).isPresent();
    }
}
