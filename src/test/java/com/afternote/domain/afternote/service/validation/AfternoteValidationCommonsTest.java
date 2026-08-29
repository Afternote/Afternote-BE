package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.dto.LeaveMessageBlock;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AfternoteValidationCommonsTest {

    @Test
    @DisplayName("leaveMessage null/empty 허용")
    void leaveMessage_NullOrEmpty_Ok() {
        assertThatCode(() -> AfternoteValidationCommons.validateLeaveMessage(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> AfternoteValidationCommons.validateLeaveMessage(List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("body blank 이면 실패")
    void leaveMessage_BlankBody_Fail() {
        assertThatThrownBy(() -> AfternoteValidationCommons.validateLeaveMessage(List.of(
                LeaveMessageBlock.builder().title("t").body("  ").build()
        )))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.LEAVE_MESSAGE_BODY_REQUIRED));
    }

    @Test
    @DisplayName("블록 21개 초과 실패")
    void leaveMessage_TooMany_Fail() {
        List<LeaveMessageBlock> blocks = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            blocks.add(LeaveMessageBlock.builder().title("t" + i).body("body" + i).build());
        }

        assertThatThrownBy(() -> AfternoteValidationCommons.validateLeaveMessage(blocks))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.LEAVE_MESSAGE_TOO_MANY));
    }

    @Test
    @DisplayName("title 없이도 body 있으면 성공")
    void leaveMessage_OptionalTitle_Ok() {
        assertThatCode(() -> AfternoteValidationCommons.validateLeaveMessage(List.of(
                LeaveMessageBlock.builder().title(null).body("본문만").build()
        ))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("receivers 원소 null은 건너뛰고 성공")
    void optionalReceivers_NullElement_Ok() {
        List<AfternoteCreateRequest.ReceiverRequest> receivers = new ArrayList<>();
        receivers.add(null);
        receivers.add(new AfternoteCreateRequest.ReceiverRequest(1L));
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                null, null, null, null, null, receivers, null, true);

        assertThatCode(() -> AfternoteValidationCommons.validateOptionalReceivers(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("receiver 객체인데 receiverId 없으면 1607")
    void optionalReceivers_MissingReceiverId_Fail() {
        AfternoteCreateRequest request = new AfternoteCreateRequest(
                null, null, null, null, null,
                List.of(new AfternoteCreateRequest.ReceiverRequest(null)),
                null, true);

        assertThatThrownBy(() -> AfternoteValidationCommons.validateOptionalReceivers(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.GALLERY_RECEIVER_ID_REQUIRED));
    }
}
