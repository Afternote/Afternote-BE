# 비결제 알림 코드 안내

2026-10-25 / `codex/firebase-notifications`

서버의 A~E 알림 생성·G 발송 정책·H 목적지 데이터와 확인 API를 구현했다. 결제 F는 제외한다. 실제 알림톡 공급자 어댑터·계정·승인 템플릿, 앱 적용 및 실기기 동작, 보관 확인 웹의 본인 로그인 UI 연결은 완료로 간주하지 않는다. 대리인용 웹 확인은 서버 화면과 POST 접수까지 제공한다.

## 실행 흐름

1. `engagement/EngagementNotificationScheduler`가 활성 사용자를 ID 순서로 읽고 `EngagementNotificationService.plan()`이 A/C/B 후보를 저장한다. `RecordFacts`는 Diary·DeepThought·UserDailyQuestion의 최종 작성만 합산한다. `published_at`은 첫 최종 저장 시각이며 수정/임시저장을 새 작성으로 세지 않는다.
2. `reminder/TimeLetterReminderScheduler`가 30/7일 전, `TimeLetterDeliveryScheduler`가 3일 전 미수락·날짜 전달·기존 전달 조건의 충족 결과를 처리한다. 무조건 SENT로 일괄 변경하던 기존 스케줄러는 수신자별 판정으로 전환했다.
3. `receiver/service/ReceiverInvitationService`와 스케줄러가 수락·거절·7일 안내·14일 만료를 저장한다. 지정 초대는 수신자 연락처로 알림톡 대기열에 연결한다. 공유 초대는 기존 방식대로 링크를 반환한다. `UserService`는 관계/이메일이 실제로 바뀌었을 때 인앱 알림만 저장한다.
4. `policy/NotificationPolicyStore`가 사용자 행 잠금 아래 중복·원본 유효성·우선순위·일일 상한·야간 시간을 판정한다. 알려진 오후 6시 C 후보가 있으면 오전 B를 먼저 보내지 않는다. 이미 보낸 일반 알림은 나중의 후보로 소급 교체하지 않는다.
5. `NotificationPolicyWorker`가 DB의 발송 시도를 먼저 확정하고 FCM에 요청한다. `channel/AlimtalkOutbox`는 별도 채널 이력·알림톡 동시/대체 발송·재시도를 관리한다. 외부 호출을 DB 트랜잭션 안에서 수행하지 않는다.
6. 앱 탭 시 `NotificationNavigationController`가 삭제된 대상의 목록/토스트, 이미 답한 데일리 질문의 해당 답변 상세, 이미 취소된 보관 절차의 VIEW 동작을 반환한다. 앱은 로그인 후 이 API를 호출하고 뒤로가기는 HOME으로 구성해야 한다.

위 경로에서 `engagement`, `reminder`, `policy`, `channel`의 공통 상위 경로는 `src/main/java/com/afternote/domain/notification/`다.

## 문구·목적지 위치

| 알림 | 코드 |
|---|---|
| A 미접속·대리인 / B 데일리·주간 / C 복귀 | `NotificationKind`: 문구, 카테고리, 채널, destination, action |
| 타임레터 30/7일 | `TimeLetterReminderType` |
| 수신자 수락·거절·미수락·만료 | `ReceiverInvitationService.notifyOwner()` |
| 타임레터 3일 전·성공·실패·수신자 전달 완료 | `TimeLetterDeliveryService` |
| 목적지 허용 목록·필수 ID | `dto/PushPayload` |
| 현재 상태에 따른 탭 목적지 | `NotificationNavigationController` |

특정 기록은 `recordType + recordId`, 질문은 사용자에게 할당된 `questionId + date`, 수신자는 `receiverId`, 편지는 `timeLetterId`다. 문구를 분석해서 화면을 고르지 않는다. 강조 동작은 `action`, 보조 동작은 `secondaryAction`이다.

## 확정된 운영 규칙

- 주간 목표 기본 3회. 주간 안내는 금요일의 사용자 선택 시각, 기본 18:00.
- 14일 최종 미작성: 금요일 주 1회. 21일 최종 미작성: B 중단. 새 최종 기록이 생기면 자동 상태를 회복하지만 사용자 OFF는 바꾸지 않는다. 기존 마음의 기록 푸시 OFF도 존중한다.
- C는 B OFF와 별개이며 한국 시간 18:00, 달력 월 최대 2건의 발송 대상을 예약한다. 인앱만 남은 후보는 월 외부 발송 건수에 포함하지 않는다.
- A는 앱 방문 기준으로 6/12/18/24개월, 대리인 없는 24개월 이후는 6개월마다 보관 안내다. 대리인 접수 뒤 첫 발송 가능일부터 7일간 일일 안내한다. 22시 이후 접수는 다음 날 아침부터 7회이며 같은 아침에 두 회차를 겹쳐 만들지 않는다. 이 코드에서 기록을 공개하거나 기존 전달 설정을 바꾸지 않는다.
- 일반 푸시는 A > D > E > C > B 중 하루 한 건. 수락·전달 완료·편지 발송 실패는 일일 상한 예외다. F enum은 호환용 예약 값이며 결제 이벤트를 구현하지 않았다.
- 22:00~08:00은 오전 8시로 이월한다. 사건형도 적용하며 B만 사용자가 고른 시각을 유지한다.
- 주간은 월~일, 월 상한은 달력 월, 날짜/시간은 Asia/Seoul. 월말/윤년은 달력 덧셈으로 판정하며 2/29 생일은 평년에 2/28로 안내한다.

## 앱에 제공하는 API

`/api/v1` 기준이며 별도 표시가 없으면 본인 로그인이 필요하다.

| API | 용도 |
|---|---|
| GET/PUT `/users/me/notification-preferences` | 시간·요일·목표·사용자 ON/OFF |
| GET/PUT/DELETE `/users/me/notification-preferences/delegate` | 대리인 조회·지정·변경·해제 |
| GET/POST `/users/me/notification-preferences/anniversaries` | 수신자 생일 조회/등록 |
| DELETE `/users/me/notification-preferences/anniversaries/{id}` | 본인 기념일 삭제 |
| POST `/users/me/notification-preferences/entry` | 앱 실행, 이전 방문을 보존한 30일 복귀 질문 3개와 절차 취소 결과 |
| GET `/users/notifications/{id}/target` | 현재 상태를 반영한 목적지·삭제 토스트·backDestination=HOME |
| GET `/time-letters/{id}/delivery-status` | 수신자별 실제 전달 상태/시각 |
| POST `/time-letters/{id}/retry-delivery` | 실패 수신자 재평가. UUID idempotencyKey 필요 |
| GET `/notification-links/{token}` | 공개: 해시 검증·만료·현재 회차 확인, 내용/연락처 미노출 |
| POST `/notification-links/{token}/confirm` | 공개: 대리인 확인 목적의 비밀 토큰으로만 접수 |
| POST `/notification-links/{token}/cancel` | 로그인한 본인의 보관 확인 링크로만 취소 |

웹 대리인 확인: GET `/notification-confirmations/{token}`, 명시적 버튼의 POST `/{token}/confirm`. GET으로 접수하지 않으므로 링크 미리보기만으로 절차가 시작되지 않는다. 본인 보관 확인 웹의 로그인 UI와 앱 열기/스토어 복원은 클라이언트 연결이 남아 있다.

설정 예:

```json
{"enabled":true,"time":"18:00","weekdays":["MONDAY","WEDNESDAY","FRIDAY"],"weeklyGoal":3}
```

`weeklyGoal=3`은 목표 기록 수다. 알림 요일은 `weekdays`로 따로 고른다. 신규 기본 요일은 전체 요일이다. `time`의 초·소수초는 0만 허용한다.

## 실패·재시도·전달 상태

- FCM 수락, 알림톡 공급자 접수, 앱 읽음은 서로 다르다. `PUSH_SENT`/`ALIMTALK_SENT`는 공급자 수락을 뜻하며 실제 표시/읽음을 보장하지 않는다.
- 채널마다 `notification_channel_attempt` 한 행으로 중복을 막고 시도 횟수와 결과를 남긴다. 알림톡의 명시적인 재시도 가능 거절만 5분/10분 후 최대 총 3회 시도한다. 공급자 부재는 대기하며 1시간 뒤 설정을 재확인한다. 결과 불명/호출 중 종료는 자동 재발송하지 않는다.
- UNREGISTERED만 토큰을 무효 처리한다. 잘못된 메시지·인증/구성 장애·일시 거절·결과 불명을 구분하고 설정/메시지 오류를 무조건 알림톡으로 돌리지 않는다.
- 알림톡 어댑터가 없는 현재 구성은 `ALIMTALK_NOT_CONFIGURED` 대기다. 연락처가 없으면 `NO_CONTACT`다. 공급자 모의 성공을 실제 발송 완료로 표시하지 않는다.
- 외부 대리인/초대 수신자 메시지는 발신자의 인앱 목록과 배지에 노출하지 않는다. 발송 직전 계정 상태·원본·대리인 버전·현재 연락처를 다시 검증한다.
- DATE 편지는 실제로 계정에 연결된 활성 수신자의 열람 가능 상태를 확인한 뒤 delivered_at을 저장한다. 모두 실패하면 SCHEDULED를 유지하고 같은 자동 작업을 중복 실행하지 않는다. 일부 성공이면 SENT로 수정 잠금을 유지하며, 수신자별 상태로 나머지 실패를 확인한다. 재시도는 이미 전달된 대상을 다시 보내지 않는다.
- DATE 본문은 예약 시각 경과와 실제 delivered_at을 모두 만족해야 공개한다. deliveredAt은 전달 전 null이며 Swagger에도 nullable로 반영했다. POST_DEATH는 기존 전달 조건을 그대로 적용한다.

## 배포와 남은 확인

- 수동 관리 MySQL: `deploy/sql/20261025_non_billing_notifications.sql`을 기존 20261022 스키마 위에 한 번 적용한다. ddl-auto가 이미 생성한 환경에는 중복 적용하지 않는다.
- `NotificationReadiness`가 ApplicationReadyEvent까지 자동 알림 작업을 보류하므로 기동 데이터 보정이 완료되기 전에 외부 알림을 보내지 않는다.
- 자동 관리 MySQL: Hibernate의 컬럼/테이블 생성 후 `NotificationDataMigrator`가 버전 행 잠금 아래 데이터만 한 번 보정한다. 기존 최종 기록은 과거 첫 최종 저장 시각을 복원할 수 없어 created_at을 근사값으로 사용한다. 이후 저장은 실제 첫 최종 시각을 유지한다.
- 앱의 새 목적지/entry 호출/종료·로그인·뒤로가기/배너/삭제 토스트/설치 후 복원을 실기기에서 확인해야 한다. 앱 저장소를 확인하지 않고 기존 동작이 없다고 단정하지 않는다.
- 알림톡은 사용 업체, 계정, 승인 템플릿/버튼/변수, 결과 콜백 서명/조회 규격이 확인되면 `AlimtalkGateway` 실제 어댑터를 연결하고 실수신을 확인해야 한다. 카카오 공유 초대 커밋 #289는 이 자동 전송 API가 아니다.
- 운영 DB 적용·실기기·실제 공급자 호출은 이 브랜치의 자동 테스트에 포함하지 않는다.
