package com.afternote.domain.mindrecord.emotion;

import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.mindrecord.weekly.model.WeeklyReport;
import com.afternote.domain.mindrecord.weekly.repository.WeeklyReportRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EmotionAnalysisPolicyTest {

    @Mock
    private WeeklyReportRepository weeklyReportRepository;
    @Mock
    private EmotionRepository emotionRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    @DisplayName("생성자가 둘이면 Spring은 @Autowired 2-arg 로 빈을 만든다")
    void springInstantiatesTwoArgConstructor() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(WeeklyReportRepository.class, () -> weeklyReportRepository);
        ctx.registerBean(EmotionRepository.class, () -> emotionRepository);
        ctx.register(EmotionAnalysisPolicy.class);
        ctx.refresh();
        try {
            assertThat(ctx.getBean(EmotionAnalysisPolicy.class)).isNotNull();
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("다음 주 월요일 02:00 이전이면 리포트가 있어도 주가 열려 있다")
    void weekOpen_beforeMonday2am() {
        LocalDate weekMonday = LocalDate.of(2026, 8, 3);
        Clock clock = clockAt("2026-08-10T01:00:00+09:00");
        EmotionAnalysisPolicy policy = new EmotionAnalysisPolicy(
                weeklyReportRepository, emotionRepository, clock);
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceId(1L, EmotionSourceType.DIARY, 10L))
                .willReturn(Optional.empty());

        assertThat(policy.isWeekClosed(1L, weekMonday.plusDays(1))).isFalse();
        assertThat(policy.allowAnalysis(1L, EmotionSourceType.DIARY, 10L, weekMonday.plusDays(1))).isTrue();
    }

    @Test
    @DisplayName("월요일 02:00 이후이고 리포트가 있으면 신규 분석을 막는다")
    void weekClosed_skipsNewAnalysis() {
        LocalDate weekMonday = LocalDate.of(2026, 8, 3);
        Clock clock = clockAt("2026-08-10T02:30:00+09:00");
        EmotionAnalysisPolicy policy = new EmotionAnalysisPolicy(
                weeklyReportRepository, emotionRepository, clock);
        given(weeklyReportRepository.findByUserIdAndStartDate(1L, weekMonday.atStartOfDay()))
                .willReturn(Optional.of(WeeklyReport.create(
                        user, weekMonday.atStartOfDay(), weekMonday.plusDays(6).atTime(23, 59, 59), "요약", "[]")));
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceId(1L, EmotionSourceType.DIARY, 10L))
                .willReturn(Optional.empty());

        assertThat(policy.isWeekClosed(1L, LocalDate.of(2026, 8, 5))).isTrue();
        assertThat(policy.allowAnalysis(1L, EmotionSourceType.DIARY, 10L, LocalDate.of(2026, 8, 5))).isFalse();
    }

    @Test
    @DisplayName("닫힌 주라도 PENDING 재시도는 허용한다")
    void weekClosed_allowsPendingRetry() {
        LocalDate weekMonday = LocalDate.of(2026, 8, 3);
        Clock clock = clockAt("2026-08-10T02:30:00+09:00");
        EmotionAnalysisPolicy policy = new EmotionAnalysisPolicy(
                weeklyReportRepository, emotionRepository, clock);
        given(weeklyReportRepository.findByUserIdAndStartDate(1L, weekMonday.atStartOfDay()))
                .willReturn(Optional.of(WeeklyReport.create(
                        user, weekMonday.atStartOfDay(), weekMonday.plusDays(6).atTime(23, 59, 59), "요약", "[]")));
        Emotion pending = Emotion.createPending(user, EmotionSourceType.DIARY, 10L, LocalDateTime.now());
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceId(1L, EmotionSourceType.DIARY, 10L))
                .willReturn(Optional.of(pending));

        assertThat(policy.allowAnalysis(1L, EmotionSourceType.DIARY, 10L, LocalDate.of(2026, 8, 5))).isTrue();
    }

    @Test
    @DisplayName("재분석은 24시간 이내면 거부한다")
    void reanalysis_cooldown() {
        Clock clock = clockAt("2026-08-10T12:00:00+09:00");
        EmotionAnalysisPolicy policy = new EmotionAnalysisPolicy(
                weeklyReportRepository, emotionRepository, clock);
        Emotion succeeded = Emotion.createSucceeded(
                user, EmotionSourceType.DIARY, 10L, "기쁨", LocalDateTime.of(2026, 8, 10, 2, 0));
        ReflectionTestUtils.setField(succeeded, "lastAttemptAt", LocalDateTime.of(2026, 8, 10, 2, 0));
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceId(1L, EmotionSourceType.DIARY, 10L))
                .willReturn(Optional.of(succeeded));

        assertThat(policy.allowAnalysis(1L, EmotionSourceType.DIARY, 10L, LocalDate.of(2026, 8, 10)))
                .isFalse();
    }

    @Test
    @DisplayName("재분석은 24시간이 지나면 허용한다")
    void reanalysis_afterCooldown() {
        Clock clock = clockAt("2026-08-11T12:00:00+09:00");
        EmotionAnalysisPolicy policy = new EmotionAnalysisPolicy(
                weeklyReportRepository, emotionRepository, clock);
        Emotion succeeded = Emotion.createSucceeded(
                user, EmotionSourceType.DIARY, 10L, "기쁨", LocalDateTime.of(2026, 8, 10, 2, 0));
        ReflectionTestUtils.setField(succeeded, "lastAttemptAt", LocalDateTime.of(2026, 8, 10, 2, 0));
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceId(1L, EmotionSourceType.DIARY, 10L))
                .willReturn(Optional.of(succeeded));

        assertThat(policy.allowAnalysis(1L, EmotionSourceType.DIARY, 10L, LocalDate.of(2026, 8, 11)))
                .isTrue();
    }

    private static Clock clockAt(String offsetDateTime) {
        Instant instant = Instant.parse(offsetDateTime);
        return Clock.fixed(instant, EmotionAnalysisPolicy.SEOUL);
    }
}
