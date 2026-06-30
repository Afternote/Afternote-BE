package com.afternote.domain.delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 사후 전달 조건 관련 이메일 알림 발송.
 * - 본인확인 알림(발신자): 미사용 기간 도달 시 "곧 전달됩니다" 안내
 * - 전달 알림(수신자): 조건 충족(열람 가능) 안내
 * 발송 실패는 흐름을 막지 않도록 로깅만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryNotificationService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 발신자 본인에게 본인확인 알림을 보낸다 (미사용 기간 도달 → 7일 유예 시작).
     */
    public void sendConfirmationToOwner(String ownerEmail, String ownerName) {
        if (!StringUtils.hasText(ownerEmail)) {
            return;
        }

        String name = StringUtils.hasText(ownerName) ? ownerName : "회원";
        String body = name + "님, 안녕하세요.\n\n"
                + "설정하신 미사용 기간이 경과하여, 곧 기록이 수신자에게 전달될 예정입니다.\n"
                + "활동 중이시라면 앱에 접속해 주세요. 7일 이내 활동이 없으면 전달이 진행됩니다.\n\n"
                + "- AfterNote";

        send(ownerEmail, "[AfterNote] 회원님의 기록이 곧 전달됩니다", body);
    }

    /**
     * 수신자에게 열람 가능 알림을 보낸다 (조건 충족).
     */
    public void sendDeliveredToReceiver(String receiverEmail, String receiverName, String senderName) {
        if (!StringUtils.hasText(receiverEmail)) {
            return;
        }

        String rName = StringUtils.hasText(receiverName) ? receiverName : "수신자";
        String sName = StringUtils.hasText(senderName) ? senderName : "발신자";
        String body = rName + "님, 안녕하세요.\n\n"
                + sName + "님이 남긴 기록을 이제 열람하실 수 있습니다.\n"
                + "AfterNote에서 확인해 주세요.\n\n"
                + "- AfterNote";

        send(receiverEmail, "[AfterNote] 전달된 기록이 도착했습니다", body);
    }

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            message.setFrom(senderEmail);
            javaMailSender.send(message);
        } catch (Exception e) {
            log.warn("전달 조건 알림 이메일 발송 실패 (to={}): {}", to, e.getMessage());
        }
    }
}
