package com.example.afternote.domain.afternote.service.validation;

import com.example.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.example.afternote.global.exception.CustomException;
import com.example.afternote.global.exception.ErrorCode;

import java.util.List;

public final class AfternoteValidationCommons {

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

    private static void validateReceiverIds(List<AfternoteCreateRequest.ReceiverRequest> receivers) {
        for (AfternoteCreateRequest.ReceiverRequest receiver : receivers) {
            if (receiver.getReceiverId() == null) {
                throw new CustomException(ErrorCode.GALLERY_RECEIVER_ID_REQUIRED);
            }
        }
    }
}