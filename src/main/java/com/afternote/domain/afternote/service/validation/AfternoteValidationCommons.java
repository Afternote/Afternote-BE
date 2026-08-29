package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.dto.LeaveMessageBlock;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;

import java.util.List;

public final class AfternoteValidationCommons {

    private static final int MAX_LEAVE_MESSAGE_BLOCKS = 20;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_BODY_LENGTH = 2000;

    private AfternoteValidationCommons() {
    }

    public static void validateRequiredReceivers(AfternoteCreateRequest request) {
        List<AfternoteCreateRequest.ReceiverRequest> receivers = request.getReceivers();
        if (receivers == null || receivers.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }
        validateReceiverIds(receivers);
    }

    public static void validateOptionalReceivers(AfternoteCreateRequest request) {
        List<AfternoteCreateRequest.ReceiverRequest> receivers = request.getReceivers();
        if (receivers == null || receivers.isEmpty()) {
            return;
        }
        validateReceiverIds(receivers);
    }

    public static void validateLeaveMessage(List<LeaveMessageBlock> leaveMessage) {
        if (leaveMessage == null || leaveMessage.isEmpty()) {
            return;
        }
        if (leaveMessage.size() > MAX_LEAVE_MESSAGE_BLOCKS) {
            throw new CustomException(ErrorCode.LEAVE_MESSAGE_TOO_MANY);
        }
        for (LeaveMessageBlock block : leaveMessage) {
            if (block == null) {
                throw new CustomException(ErrorCode.LEAVE_MESSAGE_BODY_REQUIRED);
            }
            String body = block.getBody();
            if (body == null || body.isBlank()) {
                throw new CustomException(ErrorCode.LEAVE_MESSAGE_BODY_REQUIRED);
            }
            if (body.length() > MAX_BODY_LENGTH) {
                throw new CustomException(ErrorCode.LEAVE_MESSAGE_BODY_TOO_LONG);
            }
            String title = block.getTitle();
            if (title != null && title.length() > MAX_TITLE_LENGTH) {
                throw new CustomException(ErrorCode.LEAVE_MESSAGE_TITLE_TOO_LONG);
            }
        }
    }

    private static void validateReceiverIds(List<AfternoteCreateRequest.ReceiverRequest> receivers) {
        for (AfternoteCreateRequest.ReceiverRequest receiver : receivers) {
            if (receiver == null) {
                continue;
            }
            if (receiver.getReceiverId() == null) {
                throw new CustomException(ErrorCode.GALLERY_RECEIVER_ID_REQUIRED);
            }
        }
    }
}