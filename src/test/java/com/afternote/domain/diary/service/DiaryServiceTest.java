package com.afternote.domain.diary.service;

import com.afternote.domain.diary.dto.DiaryCreateRequest;
import com.afternote.domain.diary.dto.DiaryResponse;
import com.afternote.domain.diary.dto.DiaryUpdateRequest;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.service.MindRecordReceiverService;
import com.afternote.domain.mindrecord.emotion.EmotionAnalysisPolicy;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.sanitizer.MindRecordContentMediaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    @InjectMocks
    private DiaryService diaryService;

    @Mock
    private DiaryRepository diaryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MindRecordContentMediaService mindRecordContentMediaService;
    @Mock
    private DiaryReceiverRepository diaryReceiverRepository;
    @Mock
    private MindRecordReceiverService mindRecordReceiverService;
    @Mock
    private EmotionAnalysisPolicy emotionAnalysisPolicy;

    @Test
    @DisplayName("생성 시 date를 기록일로 저장한다")
    void createDiary_persistsRequestedDate() {
        User user = sampleUser();
        LocalDate requested = LocalDate.now(DiaryService.SEOUL).minusDays(3);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(mindRecordContentMediaService.prepareContentForSave(eq(1L), any())).willReturn("c");
        given(diaryRepository.save(any(Diary.class))).willAnswer(invocation -> {
            Diary diary = invocation.getArgument(0);
            ReflectionTestUtils.setField(diary, "id", 10L);
            ReflectionTestUtils.setField(diary, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(diary, "updatedAt", LocalDateTime.now());
            return diary;
        });
        given(mindRecordReceiverService.replaceDiaryReceivers(eq(1L), any(), any(), anyBoolean()))
                .willReturn(List.of());
        given(emotionAnalysisPolicy.allowAnalysis(eq(1L), eq(EmotionSourceType.DIARY), eq(10L), eq(requested)))
                .willReturn(true);

        DiaryCreateRequest request = new DiaryCreateRequest(
                "t", "c", false, TodayMood.HAPPY, requested, null);

        DiaryResponse response = diaryService.createDiary(1L, request);

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryDate()).isEqualTo(requested);
        assertThat(response.date()).isEqualTo(requested);
    }

    @Test
    @DisplayName("생성 시 date 생략이면 오늘(Asia/Seoul)로 저장한다")
    void createDiary_defaultsToToday() {
        User user = sampleUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(mindRecordContentMediaService.prepareContentForSave(eq(1L), any())).willReturn("c");
        given(diaryRepository.save(any(Diary.class))).willAnswer(invocation -> {
            Diary diary = invocation.getArgument(0);
            ReflectionTestUtils.setField(diary, "id", 10L);
            ReflectionTestUtils.setField(diary, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(diary, "updatedAt", LocalDateTime.now());
            return diary;
        });
        given(mindRecordReceiverService.replaceDiaryReceivers(eq(1L), any(), any(), anyBoolean()))
                .willReturn(List.of());
        given(emotionAnalysisPolicy.allowAnalysis(eq(1L), eq(EmotionSourceType.DIARY), eq(10L), any()))
                .willReturn(true);

        DiaryCreateRequest request = new DiaryCreateRequest(
                "t", "c", false, TodayMood.HAPPY, null, null);

        diaryService.createDiary(1L, request);

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryDate()).isEqualTo(LocalDate.now(DiaryService.SEOUL));
    }

    @Test
    @DisplayName("미래 기록일은 400/2101")
    void createDiary_futureDate_rejected() {
        User user = sampleUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        DiaryCreateRequest request = new DiaryCreateRequest(
                "t", "c", false, TodayMood.HAPPY,
                LocalDate.now(DiaryService.SEOUL).plusDays(1), null);

        assertThatThrownBy(() -> diaryService.createDiary(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DIARY_INVALID_DATE);
    }

    @Test
    @DisplayName("수정 시 date를 기록일로 반영한다")
    void updateDiary_changesEntryDate() {
        User user = sampleUser();
        LocalDate original = LocalDate.now(DiaryService.SEOUL).minusDays(5);
        LocalDate updated = LocalDate.now(DiaryService.SEOUL).minusDays(1);
        Diary diary = Diary.create(user, "t", "c", false, TodayMood.HAPPY, original);
        ReflectionTestUtils.setField(diary, "id", 10L);
        ReflectionTestUtils.setField(diary, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(diary, "updatedAt", LocalDateTime.now());

        given(diaryRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(diary));
        given(mindRecordReceiverService.getDiaryReceivers(10L)).willReturn(List.of());

        DiaryUpdateRequest request = new DiaryUpdateRequest(null, null, null, null, updated, null);
        DiaryResponse response = diaryService.updateDiary(1L, 10L, request);

        assertThat(diary.getEntryDate()).isEqualTo(updated);
        assertThat(response.date()).isEqualTo(updated);
    }

    @Test
    @DisplayName("닫힌 주 기록일은 감정 분석을 요청하지 않는다")
    void createDiary_closedWeek_skipsAnalysis() {
        User user = sampleUser();
        LocalDate requested = LocalDate.now(DiaryService.SEOUL).minusDays(10);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(mindRecordContentMediaService.prepareContentForSave(eq(1L), any())).willReturn("c");
        given(diaryRepository.save(any(Diary.class))).willAnswer(invocation -> {
            Diary diary = invocation.getArgument(0);
            ReflectionTestUtils.setField(diary, "id", 10L);
            ReflectionTestUtils.setField(diary, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(diary, "updatedAt", LocalDateTime.now());
            return diary;
        });
        given(mindRecordReceiverService.replaceDiaryReceivers(eq(1L), any(), any(), anyBoolean()))
                .willReturn(List.of());
        given(emotionAnalysisPolicy.allowAnalysis(eq(1L), eq(EmotionSourceType.DIARY), eq(10L), eq(requested)))
                .willReturn(false);

        diaryService.createDiary(1L, new DiaryCreateRequest(
                "t", "c", false, TodayMood.HAPPY, requested, null));

        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(
                org.mockito.ArgumentMatchers.isA(
                        com.afternote.domain.mindrecord.emotion.event.DiaryEmotionAnalysisRequestedEvent.class));
    }

    @Test
    @DisplayName("임시저장은 제목·본문·기분 없이 저장하고 todayMood는 null")
    void createDiary_draftOmitsFormalFields() {
        User user = sampleUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(mindRecordContentMediaService.prepareContentForSave(eq(1L), any())).willReturn(null);
        given(diaryRepository.save(any(Diary.class))).willAnswer(invocation -> {
            Diary diary = invocation.getArgument(0);
            ReflectionTestUtils.setField(diary, "id", 11L);
            ReflectionTestUtils.setField(diary, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(diary, "updatedAt", LocalDateTime.now());
            return diary;
        });
        given(mindRecordReceiverService.replaceDiaryReceivers(eq(1L), any(), any(), anyBoolean()))
                .willReturn(List.of());

        DiaryResponse response = diaryService.createDiary(1L, new DiaryCreateRequest(
                null, null, true, null, null, null));

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEmpty();
        assertThat(captor.getValue().getContent()).isEmpty();
        assertThat(captor.getValue().getIsDraft()).isTrue();
        assertThat(captor.getValue().getTodayMood()).isNull();
        assertThat(response.isDraft()).isTrue();
        assertThat(response.todayMood()).isNull();
    }

    @Test
    @DisplayName("정식 등록은 todayMood 없으면 400/1400")
    void createDiary_publishedMissingTodayMood_rejected() {
        User user = sampleUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> diaryService.createDiary(1L, new DiaryCreateRequest(
                "t", "c", false, null, null, null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("임시저장을 정식 등록으로 바꿀 때 기분이 없으면 400/1400")
    void updateDiary_publishWithoutTodayMood_rejected() {
        User user = sampleUser();
        Diary diary = Diary.create(user, "", "", true, null, LocalDate.now(DiaryService.SEOUL));
        ReflectionTestUtils.setField(diary, "id", 10L);
        ReflectionTestUtils.setField(diary, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(diary, "updatedAt", LocalDateTime.now());
        given(diaryRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(diary));

        DiaryUpdateRequest request = new DiaryUpdateRequest("제목", "본문", false, null, null, null);

        assertThatThrownBy(() -> diaryService.updateDiary(1L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(diary.getIsDraft()).isTrue();
    }

    private static User sampleUser() {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
