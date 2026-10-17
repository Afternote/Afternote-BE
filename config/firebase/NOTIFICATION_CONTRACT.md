# Firebase 알림 계약 (G·H·공통)

이 문서는 서버가 제공하는 계약이다. 앱의 실제 화면 전환·배너·삭제 토스트·설치 후 목적지 복원과 알림톡 발송은 별도 클라이언트/공급자 연동이 필요하다. 타임레터 30일·7일 전 알림 생성이 연결되어 있다. 나머지 A~F 이벤트 생성은 후속 작업이다.

## FCM payload

Android는 notification 블록 없이 data-only, Android priority HIGH로 보낸다. 서버가 title/body 문구를 소유한다. data의 모든 값은 문자열이다. 앱은 알림을 직접 그리고 탭 PendingIntent에 payload를 전달해야 한다. 고우선순위는 즉시 전달 보장이 아니며 사용자에게 보이는 알림 용도로만 사용한다. iOS background 표시는 이 Android 계약으로 검증되지 않았다.

필수 공통 키: schemaVersion=`1`, title, body, destination. 목적지 생략은 HOME. type은 이벤트 식별용 선택 키. title/body/schemaVersion과 FCM 예약 키는 extras에서 덮어쓸 수 없다. 잘못된 목적지/ID는 발송 전에 거부한다. 다음 whitelist는 **서버 계약이며 FE 적용 여부는 확인되지 않았다**.

| H 대상 | destination | 필수 추가 data |
|---|---|---|
| 홈 | HOME | — |
| 마음의 기록 목록 | RECORD_LIST | — |
| 특정 기록 / 첫 기록 | RECORD_DETAIL | recordId (양의 Long 문자열) |
| 대리인 설정 | AGENT_SETTINGS | — |
| 보관 확인·절차 취소 | RETENTION_CONFIRMATION | — |
| 대리인용 웹 확인 | AGENT_CONFIRMATION | linkId (서버 발급 불투명 식별자) |
| 데일리 질문 작성·이미 답했으면 상세 | DAILY_QUESTION | questionId (양의 Long 문자열) |
| 주간리포트 | WEEKLY_REPORT | — |
| 쉬운 질문 3개 | EASY_QUESTIONS | — |
| 타임레터 작성·수신자 미리 채움 | TIME_LETTER_COMPOSE | receiverId (양의 Long 문자열) |
| 초대 랜딩 | INVITATION | linkId (서버 발급 불투명 식별자) |
| 수신자 상세 | RECEIVER_DETAIL | receiverId (양의 Long 문자열) |
| 받은 기록 홈 | RECEIVER_HOME | — |
| 편지 상세 | TIME_LETTER_DETAIL | timeLetterId (양의 Long 문자열) |
| 요금제 메인 | PLAN | — |
| Google Play 구독 관리 | SUBSCRIPTION_MANAGEMENT | — |

강조할 버튼·상태·주간 진행은 실제 대상의 현재 상태를 조회해 그린다. 임의 외부 URL을 destination으로 받지 않는다. linkId 발급/검증과 웹 엔드포인트·앱 링크 도메인은 아직 연결되지 않았다. 인증 후 권한을 다시 검증해야 하며 payload 자체가 열람 권한이 되지 않는다.

## 토큰 무효 신호

PUT `/api/v1/users/push-tokens` 기존 token/platform 필드 유지. 선택 `notificationPermission` (`UNKNOWN`, `GRANTED`, `DENIED`)와 `installationId` (설치마다 생성한 UUID, 토큰 회전에도 유지)를 추가했다. 매 실행/로그인/토큰 갱신에 호출한다.

응답은 기존 필드에 `invalidatedAt`, `invalidationReason`, `requiresReset`, `notificationPermission`을 추가한다. FCM UNREGISTERED만 무효 판정하며 행을 삭제하지 않는다. 무효 토큰은 재등록 전까지 발송하지 않는다. 같은 토큰 재등록은 재시도를 허용하지만 직전 무효 신호를 유지한다. 이후 FCM 발송 수락에 성공하면 신호를 지운다. INVALID_ARGUMENT 및 일시 오류는 무효 처리하지 않는다. 토큰이 다른 계정으로 재할당되면 이전 계정의 무효 신호와 설치 연결을 지운다. 새 토큰으로 회전한 경우 이전 토큰 신호를 새 토큰에 복사하지 않는다.

`requiresReset=true`이면 앱은 재설정 안내 배너를 표시한다. 이는 현재 권한 거부와 별개다. 서버는 앱의 실제 OS 권한을 읽을 수 없으므로 클라이언트가 정확한 권한을 보고해야 한다.

## 토큰 없는 설치의 권한

PUT `/api/v1/users/push-installations`

```json
{"installationId":"d92560f1-ad73-4bfb-8759-017c61ad5168","permission":"DENIED"}
```

인증된 사용자와 설치 UUID 조합으로 저장한다. 토큰 없이도 호출 가능하다. 응답 `installationId`, `permission`, `showPermissionBanner`를 앱에서 사용한다. 매 실행 및 권한 변경 시 보고하고, 토큰 등록에도 같은 installationId를 포함한다. 연결된 설치의 권한이 토큰의 과거 권한보다 우선한다. 다른 설치의 정상 토큰 발송을 막지 않는다. 레거시 클라이언트는 UNKNOWN으로 유지한다. 탈퇴 시 토큰과 설치 권한을 함께 제거한다.

권한 거부 기기는 푸시를 생략한다. 발송 결과는 FCM 수락 수·실패 수·권한 거부 존재 여부·FCM 설정 여부를 반환한다. 하나라도 정상 기기로 발송됐으면 전체 실패로 간주하지 않는다. 토큰 로그·예외 원문을 기록하지 않는다.

## 공통 클라이언트 책임

- 종료 상태 탭도 같은 payload로 목표 화면을 열고 뒤로가기는 홈으로 구성한다.
- 로그아웃이면 payload를 안전하게 보존해 로그인 후 목표를 조회한다.
- 삭제된 대상이면 해당 목록 + `삭제된 항목이에요` 토스트를 표시한다.
- 알림톡 앱 열기 → 미설치 시 Play Store → 첫 실행 목적지 복원은 검증된 앱 링크/설치 후 링크 복원 제공자 연동이 필요하다. 이 서버 변경만으로 동작하지 않는다.
- 30일 이상 미접속 복귀 UI의 질문 3개 선택은 앱 구현 및 실제 질문 조회 연결이 필요하다.

## 배포

기존 데이터베이스에는 `deploy/sql/20261013_push_token_invalidation.sql`을 **한 번** 적용한다. 기존 토큰의 push_enabled 기본값은 1, 권한 기본값은 UNKNOWN이다. 이 스크립트는 테이블/컬럼이 이미 자동 생성된 DB에는 그대로 재실행하지 않는다. FCM 자격 증명이나 운영 사용자 실발송 없이 단위 테스트로 검증한다.

## G 공통 발송 창구

서비스 이벤트 생성자는 `NotificationPolicyStore.enqueue(userId, eventKey, category, event, title, body, data, requestedAt)`를 호출한다. `eventKey`는 사용자별 동일 사건을 식별하며 재요청은 같은 행을 반환한다. 타임레터 30일·7일 전 이벤트는 연결되어 있으며 나머지 실제 이벤트 생성 호출은 후속 기능에서 연결한다. 관리자 테스트 푸시는 진단 목적이며 이 발송 정책을 우회한다.

- DB에 QUEUED → READY → DISPATCHING → 결과 상태를 저장하고 30초 간격으로 처리한다. 사건 발생형은 enqueue 트랜잭션 커밋 직후에도 처리한다.
- Asia/Seoul 기준 22:00~08:00은 다음 08:00으로 이월한다. B는 요청한 시간을 유지한다. 사건 발생형도 야간 이월을 적용한다.
- 같은 처리 시점에 도래한 일반 알림은 A > F > D > E > C > B, 같은 등급은 예정 시각 순으로 고른다. 하루 한 건의 일반 알림 **발송 시도**를 예약하고 나머지는 IN_APP_ONLY로 저장한다. 나중에 생성된 고우선순위 알림이 이미 보낸 알림을 취소하거나 교체하지 않는다.
- ACCEPTANCE, PAYMENT_FAILURE, DELIVERY_COMPLETE, SEND_FAILURE는 하루 상한에서 제외한다.
- 권한 거부·토큰 없음·푸시 실패는 ALIMTALK_PENDING과 사유를 남긴다. 알림톡 공급자 연결이 없어 실제 알림톡은 발송하지 않는다. 상태는 공급자 발송 완료를 뜻하지 않는다.
- 발송 직전 사용자의 ACTIVE 여부를 다시 확인한다. 휴면·탈퇴 사용자는 CANCELLED이며 인박스 배지에서 제외한다.
- DISPATCHING을 커밋한 뒤 외부 발송을 호출한다. 호출 중 서버가 종료되면 결과가 불확실하므로 자동 재발송하지 않고 DELIVERY_OUTCOME_PENDING으로 남긴다. 이 상태의 운영 복구 정책은 공급자 결과 확인과 함께 추가해야 한다.

인앱 알림: GET `/api/v1/users/notifications?page=0`은 `unreadCount`와 최대 50개 `items`를 반환한다. 각 item의 `data`는 H 목적지와 파라미터를 담은 문자열 맵이다. PATCH `/api/v1/users/notifications/{id}/read`는 본인 알림만 읽음 처리한다. 로그인 후 배지·알림함은 이 API로 동기화한다. 타임레터 30일·7일 전 알림부터 이 발송 창구를 사용한다.

새 정책 테이블을 수동 관리하는 환경에서는 `deploy/sql/20261014_notification_dispatch.sql`을 적용한다. Hibernate ddl-auto=update가 이미 테이블을 만든 환경에는 중복 적용하지 않는다.

## 확정한 운영 규칙 (2026-10-17)

- B 주간 목표 기본값은 주 3회, 주간 안내는 금요일의 사용자 설정 시각이다.
- B 미작성 일수는 앱 방문이 아닌 최종 기록 작성 기준이다. 14일 미작성 시 주 1회, 21일 미작성 시 B 중단, 기록을 다시 작성하면 B를 재개한다. 사용자가 직접 끈 설정은 자동으로 켜지 않는다.
- C 복귀 알림은 Asia/Seoul 18:00, 월 최대 2회다. B의 OFF/자동 하향과 별도로 판정한다.
- 사건 발생형도 22:00~08:00에는 08:00으로 이월하고 B는 사용자 시각을 유지한다.
- 이 규칙은 확정된 구현 요구사항이다. B/C 자동 생성 기능은 아직 연결되지 않았으므로 현재 작동한다고 간주하지 않는다.

## 첫 실제 알림: 타임레터 30일·7일 전

매 1분마다 DATE/SCHEDULED 편지 중 기준 시각이 도래한 건을 찾는다. 기준 시각은 기존 예약 발송 시각에서 30일 또는 7일을 뺀 시각이며, 별도 발송 시각이 정해지지 않아 예약 시각을 그대로 사용한다. 최초 도래 후 24시간까지만 생성·발송할 수 있고 이후에는 오래된 안내를 보내지 않는다. 야간은 G에 따라 다음 08:00으로 이월한다.

알림 종류는 `TimeLetterReminderType`이 문구·type·destination·action을 함께 관리한다. 30일 전 알림은 C와 E에서 따로 생성하지 않고 E 한 건으로 관리한다. 편지 ID·알림 종류·예약 발송 시각 조합으로 중복을 막는다. 예약 변경은 새 조합으로 처리하며 이전 대기 알림은 발송 전 취소된다. 편지 삭제·DRAFT/POST_DEATH 전환·타임레터 푸시 OFF·안내 유효 기간 만료도 발송 전에 재검사한다.

7일 전 payload 예시:

```json
{"schemaVersion":"1","type":"TIME_LETTER_REMINDER_7D","title":"AfterNote","body":"예약하신 편지가 곧 전달돼요. 지금이면 수정할 수 있어요","destination":"TIME_LETTER_DETAIL","timeLetterId":"123","action":"EDIT","scheduledSendAt":"2026-11-01T15:00:00"}
```

앱은 `destination`과 ID로 편지 상세를 열고, `action=EDIT`이면 수정 동작을 강조한다. 현재 상태와 수정 가능 여부는 상세 API에서 다시 확인한다. `action`은 선택 값이므로 기존 앱은 무시할 수 있다. 실제 앱 라우팅 적용 여부는 이 서버 저장소에서 검증하지 않았다.

관리자 테스트 요청에도 선택 `data`를 추가해 동일한 목적지 연결을 실기기에서 확인할 수 있다. 기존 userId/title/body 요청도 그대로 유지한다. `data` 예: `{"destination":"TIME_LETTER_DETAIL","timeLetterId":"123","action":"EDIT"}`. 유효하지 않은 목적지·필수 ID 누락은 400으로 거부한다. 테스트 알림은 공통 하루 상한과 예약을 우회하는 진단용이다.
