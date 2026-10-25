# A~H 서버 반영표 (결제 F 제외)

2026-10-25. 최종 로컬 검증: 일반 539개 / MySQL 65개, 실패·오류·건너뜀 0. 아래의 구현은 서버 코드 기준이다. 실제 공급자/앱/웹 본인 로그인 연동은 별도 확인 대상으로 남긴다. 핵심 DB 흐름은 `NonBillingNotificationFlowTest` 및 동일 시나리오의 `NonBillingNotificationFlowMySqlTest`가 검증한다.

| 명세 | 구현 위치 | 검증/상태 |
|---|---|---|
| A 앱 방문 리셋, 30일 복귀 문맥 보존 | NotificationActivityService, EngagementNotificationService.entry | aSixMonthsResetsOnEntryAndReturnContextSurvivesActivityCapture |
| A 6/12/18개월 | EngagementNotificationService.inactive, NotificationKind | aTwelveEighteenAndRepeatingTwentyFourMonthsUseCorrectChannelAndLinks, aSixMonthsResetsOnEntryAndReturnContextSurvivesActivityCapture |
| A 24개월 대리인/미지정 6개월 반복 | inactive, NotificationLink | delegateConfirmationProducesSevenNoticesAndNeverChangesDeliverySettings, 위 반복 테스트 |
| A 대리인 접수·첫 발송 가능일부터 7일 안내·기존 설정 유지 | confirm, RETENTION_WARNING, NotificationProfile | delegateConfirmationProducesSevenNoticesAndNeverChangesDeliverySettings, lateNightDelegateConfirmationStartsSevenDistinctMorningNotices |
| A 기존 회차 링크·대리인 변경·탈퇴/휴면 차단 | validLink, EngagementNotificationGuard, 공통 활성 검사 | 링크 만료/앱 복귀 취소, 기존 정책 inactive 테스트. 실제 연락처/공급자 대체는 실발송 확인 필요 |
| B 설정 시간·요일·목표, 작성/임시저장 | settings, daily, RecordFacts, published_at | fridayUsesGoalThreeAndRealAssignedQuestionWithWeeklyCount, finalWritingAfterEnqueueCancelsDailyButDraftDoesNotCount |
| B 첫 기록 다음 날 | FIRST_NEXT_DAY | firstRecordNextDayWinsOverFridayProgress |
| B 금요일 주간 안내·4주 달성 | WEEKLY_PROGRESS, FOUR_WEEKS | fourCompletedGoalWeeksProduceOneReportAndUnselectedWeekdaysDoNotSend |
| B 14일 하향·21일 중단·최종 작성 후 회복·수동 OFF 유지 | daily, EngagementNotificationGuard | fourteenDayReductionAndTwentyOneDayStopRecoverOnlyWhenManualToggleAllows, stoppedBRecoversAfterFinalWritingButNeverTurnsManualOffOn |
| C 작년/3개월/첫 기록 1주년·종류+ID | returns, recordData | cStillRunsWithBOffAndTwoPerCalendarMonth, 실제 기록 목적지 확인. 월말/윤년은 코드의 달력 계산 기준 |
| C 14일 수신자/미전달 개수·수신자 없음 총 기록 | returns, undelivered | sixteenDaysWithoutWritingUsesFridayOnlyAndCCountUsesAssignedRecords |
| C 30일 대리인 미지정 | NO_DELEGATE | 생성/전송 전 idle==30 및 대리인 재검사. 신규 자동 생성 코드 연결 |
| C 생일 당일 작성·수신자 미리 채움 | NotificationAnniversary, ANNIVERSARY | anniversaryRequiresOwnedReceiverAndInvalidCalendarDateIsRejected |
| C 편지 30일 전 중복 방지 | E의 TimeLetterReminderService만 생성 | 기존 thirtyDayReminderIsOneEEventNotDuplicatedAsCReturnHook |
| C B OFF와 별개·18시·월 2건·오전 B 충돌 | returns, 월 후보/선택 검사, PolicyStore 미래 후보 | known18HourMemoryPreventsMorningDailyAndMonthlyCapAllowsOnlyTwo, cStillRunsWithBOffAndTwoPerCalendarMonth, simultaneousMonthlyPlanningCreatesOneCEvent |
| D 초대 링크/지정 수신자 알림톡 대기 | ReceiverInvitationService.create/targeted | ReceiverInvitationFlowTest/MySqlTest. 실제 알림톡 공급자 연결 필요 |
| D 수락/거절 | acceptInvitation, declineInvitation | 수락 멱등·상한 예외·거절 반복·동시 지정 수락 테스트 |
| D 7일 안내·14일 만료 | ReceiverInvitationScheduler/processPending | 7일 단일 생성, 수락 후 취소, 정확한 만료 경계 테스트 |
| D 관계·이메일 수정 인앱만 | UserService, ReceiverChangeNotifications | 값이 실제 바뀔 때 IN_APP 생성. 기존 UserService 회귀 테스트 |
| D 실제 전달 완료 알림톡 대기 | TimeLetterDeliveryService.recipientDelivered/fulfilledCondition | 부분 전달/재시도 테스트. 실제 알림톡 미검증 |
| E 30/7일 안내·예약/삭제/OFF 변경 | TimeLetterReminderService/Guard | 기존 ReminderFlowTest/MySqlTest |
| E 3일 전 미수락 | reminder3, TimeLetterDeliveryGuard | threeDayUnacceptedReminderCancelsIfReceiverAcceptsBeforeSend |
| E 성공/실패·일부 성공·재시도 | deliver, LetterDeliveryRun, delivery-status API | partialLetterDeliveryAndRetryDoNotRedeliverPreviouslySuccessfulRecipient, failedLetterDoesNotBecomeSentAndIsIdempotentAcrossSchedulerRuns |
| E 실제 전달 전 본문 비공개 | ReceivedTimeLetterResponse/ReceivedService | dateLetterWithNoActualDeliveryDoesNotRevealContentEvenAfterScheduledTime |
| G 권한 거부·토큰 만료 신호 | 기존 설치/토큰 API, FcmPushSender | 기존 FcmPushSenderTest, 실제 배너는 앱 확인 필요 |
| G 채널별 이력·대체·확정 거절 재시도·불명 결과 재발송 방지 | AlimtalkOutbox, PushDeliveryResult | talkRetryOnlyReplaysExplicitRejectionAndUnknownOutcomeIsNeverReplayed. 실제 업체 어댑터 없음 |
| G 일일 한 건·우선순위·사건 예외·야간 | PolicyStore, NotificationTiming | 기존 PolicyTest/MySqlTest와 미래 C/B, laterSettingsRevisionCancelsQueuedBAndNightBStillUsesChosenTime |
| G 30일 복귀 쉬운 질문 3개 | entry, Activity capture | 복귀 문맥 테스트. 실제 앱 화면 교체는 클라이언트 확인 필요 |
| H 모든 생성기의 목적지·강조 동작 | NotificationKind, reminder enum, source producers, PushPayload | 생성 payload 테스트와 코드 안내서 참조 |
| H 특정 질문 이미 답함·삭제 항목 목록+토스트 | NotificationNavigationController | tapOfAnsweredQuestionOpensThatActualAnswer, notificationTapResolvesDeletedLetterToList |
| H 대리인 웹 접수·보관 취소 API | NotificationConfirmationWebController, LinkController | delegateWebPageEscapesTokenAndOnlyPostConfirms 및 링크/취소 DB 테스트. 본인 웹 로그인 UI 연결은 남음 |
| H 앱 종료/로그인/뒤로가기 HOME/스토어 설치 후 복원 | payload, target API, backDestination=HOME 계약 | 앱 실기기 검증·설치 후 복원 제공자/도메인 연결 대기 |
| Swagger·HTTP | NonBillingNotificationApiTest, 기존 생성 Docs 테스트 | 생성된 문서·로그인·요청 검증·null 성공/오류 계약 검증 |
| 기동 데이터 보정 전 발송 보류 | NotificationReadiness | startupKeepsQueuedSendsUntilDataUpgradeHasCompleted |
| 기존 DB 보존 | 수동 SQL, NotificationDataMigrator | manualUpgradePreservesExistingNotificationAndBackfillsOnlyFinalRecords, automaticDataMigrationIsVersionedAndPreservesAlreadyPublishedTimestamps |

자동 테스트는 외부 발송을 모의 처리한다. 실제 휴대전화 수신 또는 앱 화면 전환을 통과한 것으로 읽으면 안 된다.
