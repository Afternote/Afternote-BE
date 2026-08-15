package com.afternote.domain.user.event;

/**
 * 사용자 활동이 발생했음을 알린다.
 * 콘텐츠 트랜잭션 커밋 후 last_active_at 벌크 갱신에 사용한다.
 */
public record UserActivityTouchedEvent(Long userId) {
}
