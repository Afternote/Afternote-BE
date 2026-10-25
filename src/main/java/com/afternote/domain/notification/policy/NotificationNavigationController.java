package com.afternote.domain.notification.policy;

import com.afternote.global.common.ApiResponse;
import com.afternote.global.exception.*;
import com.afternote.global.resolver.UserId;
import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.domain.receiver.model.*;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.EntityManager;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Schema;

@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400", description="잘못된 요청 (1400)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401", description="인증 필요"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="본인 대상 또는 링크를 찾을 수 없음")
})
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/notifications")
public class NotificationNavigationController {
    private final NotificationDispatchRepository notifications;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    public record Target(Map<String,String> data, boolean deleted,
            @Schema(nullable=true) String toast, String backDestination) { }
    @Operation(summary = "알림 탭 목적지 확인", description = "로그인 후 본인 알림 ID로 호출합니다. 삭제된 항목은 목록과 토스트를, 이미 답한 질문은 해당 기록 상세를 반환합니다. 앱은 종료 상태에도 이 결과로 이동하고 뒤로가기는 HOME으로 구성합니다. 외부 수신자 알림은 payload의 수신자 경로/웹 링크를 사용합니다.")
    @GetMapping("/{id}/target")
    @Transactional(readOnly = true)
    public ApiResponse<Target> target(@Parameter(hidden=true) @UserId Long userId, @PathVariable Long id) {
        var n = notifications.findByIdAndUserId(id,userId).filter(v -> !v.isExternalRecipient() && v.getVisibleAt()!=null
                && v.getStatus()!=NotificationDispatch.Status.CANCELLED).orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_ALREADY_DELETED));
        Map<String,String> data;
        try { data = new HashMap<>(mapper.readValue(n.getPayloadJson(), new TypeReference<Map<String,String>>() { })); }
        catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        String destination = data.getOrDefault("destination","HOME"); boolean exists = true; String fallback = "HOME";
        if (destination.equals("TIME_LETTER_DETAIL")) {
            var t = entityManager.find(TimeLetter.class,Long.valueOf(data.get("timeLetterId")));
            exists = t != null && t.getUser().getId().equals(userId); fallback = "TIME_LETTER_LIST";
        } else if (destination.equals("RECEIVER_DETAIL") || destination.equals("TIME_LETTER_COMPOSE")) {
            var receiver = entityManager.find(Receiver.class,Long.valueOf(data.get("receiverId")));
            exists = receiver != null && receiver.getUserId().equals(userId); fallback="RECEIVER_LIST";
        } else if (destination.equals("INVITATION_DETAIL")) {
            var i = entityManager.find(ReceiverInvitation.class,Long.valueOf(data.get("invitationId")));
            exists = i != null && i.getInviterUserId().equals(userId); fallback="RECEIVER_LIST";
        } else if (destination.equals("DAILY_QUESTION")) {
            var question = entityManager.find(UserDailyQuestion.class,Long.valueOf(data.get("questionId")));
            exists = question != null && question.getUser().getId().equals(userId); fallback="RECORD_LIST";
            if (exists && question.isAnswered() && !question.isDraft()) {
                data.put("destination","RECORD_DETAIL");data.put("recordType","DAILY_QUESTION");data.put("recordId",question.getId().toString());data.put("action","VIEW");
            }
        } else if (destination.equals("RECORD_DETAIL")) {
            String entity = switch(data.getOrDefault("recordType","")) { case "DIARY" -> "Diary"; case "DEEP_THOUGHT" -> "DeepThought"; case "DAILY_QUESTION" -> "UserDailyQuestion"; default -> null; };
            exists = entity != null && entityManager.createQuery("select count(r) from " + entity + " r where r.id=:id and r.user.id=:owner and r.isDraft=false",Long.class)
                    .setParameter("id",Long.valueOf(data.get("recordId"))).setParameter("owner",userId).getSingleResult()>0; fallback="RECORD_LIST";
        }
        if (destination.equals("RETENTION_CONFIRMATION") && data.containsKey("cycle")) {
            var user=entityManager.find(com.afternote.domain.user.model.User.class,userId);
            if (user != null && !Objects.toString(user.getLastActiveAt(),"").equals(data.get("cycle"))) {
                data.put("action","VIEW");data.put("retentionState","CANCELLED");
            }
        }
        if (!exists) return ApiResponse.success(new Target(Map.of("destination",fallback,"action","VIEW"),true,"삭제된 항목이에요","HOME"));
        return ApiResponse.success(new Target(data,false,null,"HOME"));
    }
}
