package com.afternote.domain.mindrecord.emotion.service;

import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionAnalysisStatus;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmotionServiceTest {

    @InjectMocks
    private EmotionService emotionService;

    @Mock
    private EmotionRepository emotionRepository;
    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emotionService, "maxRetry", 5);
    }

    @Test
    @DisplayName("ensurePending 은 PENDING 행을 생성한다")
    void ensurePending_CreatesRow() {
        User user = sampleUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceId(1L, EmotionSourceType.DIARY, 10L))
                .willReturn(Optional.empty());
        given(emotionRepository.save(any(Emotion.class))).willAnswer(inv -> inv.getArgument(0));

        emotionService.ensurePending(1L, EmotionSourceType.DIARY, 10L, LocalDateTime.now());

        ArgumentCaptor<Emotion> captor = ArgumentCaptor.forClass(Emotion.class);
        verify(emotionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmotionAnalysisStatus.PENDING);
        assertThat(captor.getValue().getEmotionCategory()).isNull();
    }

    @Test
    @DisplayName("허용 목록 감정이면 SUCCEEDED")
    void markSucceeded_Allowlist() {
        User user = sampleUser(1L);
        Emotion pending = Emotion.createPending(user, EmotionSourceType.DIARY, 10L, LocalDateTime.now());
        ReflectionTestUtils.setField(pending, "id", 1L);
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceId(1L, EmotionSourceType.DIARY, 10L))
                .willReturn(Optional.of(pending));
        given(emotionRepository.save(any(Emotion.class))).willAnswer(inv -> inv.getArgument(0));

        emotionService.markSucceeded(1L, EmotionSourceType.DIARY, 10L, "슬픔");

        assertThat(pending.getStatus()).isEqualTo(EmotionAnalysisStatus.SUCCEEDED);
        assertThat(pending.getEmotionCategory()).isEqualTo("슬픔");
    }

    @Test
    @DisplayName("허용 목록 밖이면 실패 시도로 기록")
    void markSucceeded_RejectsNonAllowlist() {
        User user = sampleUser(1L);
        Emotion pending = Emotion.createPending(user, EmotionSourceType.DIARY, 10L, LocalDateTime.now());
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceId(1L, EmotionSourceType.DIARY, 10L))
                .willReturn(Optional.of(pending));
        given(emotionRepository.save(any(Emotion.class))).willAnswer(inv -> inv.getArgument(0));

        emotionService.markSucceeded(1L, EmotionSourceType.DIARY, 10L, "HAPPY");

        assertThat(pending.getStatus()).isEqualTo(EmotionAnalysisStatus.PENDING);
        assertThat(pending.getRetryCount()).isEqualTo(1);
        assertThat(pending.getEmotionCategory()).isNull();
        verify(emotionRepository, times(1)).save(pending);
    }

    @Test
    @DisplayName("재시도 상한 도달 시 FAILED")
    void recordFailedAttempt_MaxRetry() {
        User user = sampleUser(1L);
        Emotion pending = Emotion.createPending(user, EmotionSourceType.DIARY, 10L, LocalDateTime.now());
        ReflectionTestUtils.setField(pending, "retryCount", 4);
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceId(1L, EmotionSourceType.DIARY, 10L))
                .willReturn(Optional.of(pending));
        given(emotionRepository.save(any(Emotion.class))).willAnswer(inv -> inv.getArgument(0));

        emotionService.recordFailedAttempt(1L, EmotionSourceType.DIARY, 10L);

        assertThat(pending.getRetryCount()).isEqualTo(5);
        assertThat(pending.getStatus()).isEqualTo(EmotionAnalysisStatus.FAILED);
    }

    private static User sampleUser(Long id) {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
