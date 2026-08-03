package com.afternote.domain.receiver.service;

import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.DeepThoughtReceiverRepository;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.UserDailyQuestionReceiverRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MindRecordReceiverServiceTest {

    @InjectMocks
    private MindRecordReceiverService mindRecordReceiverService;

    @Mock private ReceiverRepository receiverRepository;
    @Mock private DiaryReceiverRepository diaryReceiverRepository;
    @Mock private DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    @Mock private UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;

    @Test
    @DisplayName("데일리질문 수신자 교체 시 기존 연결 삭제 후 저장")
    void replaceUserDailyQuestionReceivers_DeletesBeforeSave() {
        UserDailyQuestion question = mock(UserDailyQuestion.class);
        given(question.getId()).willReturn(24L);

        Receiver receiver = Receiver.builder().name("kim").relation("아들").userId(18L).build();
        ReflectionTestUtils.setField(receiver, "id", 7L);
        given(receiverRepository.findAllById(List.of(7L))).willReturn(List.of(receiver));

        mindRecordReceiverService.replaceUserDailyQuestionReceivers(18L, question, List.of(7L), false);

        InOrder inOrder = inOrder(userDailyQuestionReceiverRepository);
        inOrder.verify(userDailyQuestionReceiverRepository).deleteByUserDailyQuestionId(24L);
        inOrder.verify(userDailyQuestionReceiverRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("일기 수신자 교체 시 기존 연결 삭제 후 저장")
    void replaceDiaryReceivers_DeletesBeforeSave() {
        Diary diary = mock(Diary.class);
        given(diary.getId()).willReturn(10L);

        Receiver receiver = Receiver.builder().name("kim").relation("아들").userId(18L).build();
        ReflectionTestUtils.setField(receiver, "id", 7L);
        given(receiverRepository.findAllById(List.of(7L))).willReturn(List.of(receiver));

        mindRecordReceiverService.replaceDiaryReceivers(18L, diary, List.of(7L), false);

        InOrder inOrder = inOrder(diaryReceiverRepository);
        inOrder.verify(diaryReceiverRepository).deleteByDiaryId(10L);
        inOrder.verify(diaryReceiverRepository).saveAll(anyList());
    }
}
