package com.afternote.domain.notification.policy;

import com.afternote.domain.notification.engagement.*;
import com.afternote.domain.notification.channel.*;
import com.afternote.domain.notification.reminder.*;
import com.afternote.domain.notification.service.FcmPushSender;
import com.afternote.domain.notification.dto.PushDeliveryResult;
import com.afternote.domain.user.model.*;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.receiver.model.*;
import com.afternote.domain.receiver.repository.*;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.dailyquestion.model.DailyQuestion;
import com.afternote.domain.dailyquestion.repository.DailyQuestionRepository;
import com.afternote.domain.timeletter.model.*;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.global.exception.CustomException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production producers + source guards + persisted policy + channel attempts, without external sends. */
class NonBillingNotificationFlowTest {
    private AnnotationConfigApplicationContext context;
    private EngagementNotificationService service;
    private TimeLetterDeliveryService delivery;
    private NotificationDispatchRepository dispatches;
    private NotificationPolicyWorker worker;
    private TransactionTemplate tx;
    private EntityManager em;
    private User owner;
    private TestClock clock;
    private FcmPushSender sender;
    private AlimtalkGateway talk;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 16, 12, 0);
    protected DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, this::dataSource);
        context.register(NotificationPolicyTest.Config.class, FeatureConfig.class); context.refresh();
        service = context.getBean(EngagementNotificationService.class); delivery = context.getBean(TimeLetterDeliveryService.class);
        dispatches = context.getBean(NotificationDispatchRepository.class); worker = context.getBean(NotificationPolicyWorker.class);
        em = context.getBean("entityManager", EntityManager.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        clock = context.getBean(TestClock.class); sender = context.getBean(FcmPushSender.class); talk = context.getBean(AlimtalkGateway.class);
        lenient().when(sender.sendToUserWithResult(anyLong(), anyString(), anyString(), anyMap())).thenReturn(new PushDeliveryResult(1,0,false,true));
        lenient().when(talk.send(anyString(), anyString(), anyString(), anyString(), anyMap())).thenReturn(new AlimtalkGateway.Result(AlimtalkGateway.Outcome.ACCEPTED, "provider-id"));
        owner = user("작성자", "01011112222");
        var question = new DailyQuestion(); question.setContent("오늘의 질문"); context.getBean(DailyQuestionRepository.class).saveAndFlush(question);
    }
    @AfterEach void close() { context.close(); }
    private User user(String name, String phone) {
        var u = context.getBean(UserRepository.class).saveAndFlush(User.builder().email(UUID.randomUUID()+"@test.com").name(name)
                .phone(phone).status(UserStatus.ACTIVE).provider(AuthProvider.LOCAL).build());
        tx.executeWithoutResult(s -> em.createQuery("update User u set u.createdAt = :joined, u.lastActiveAt = :now where u.id = :id")
                .setParameter("joined", START.minusYears(3)).setParameter("now", START).setParameter("id", u.getId()).executeUpdate());
        return u;
    }
    private Diary record(LocalDateTime at, boolean draft) {
        var d = context.getBean(DiaryRepository.class).saveAndFlush(Diary.create(owner, "기록", "내용", draft, null, at.toLocalDate()));
        tx.executeWithoutResult(s -> em.createQuery("update Diary d set d.createdAt = :at, d.publishedAt = :published where d.id = :id")
                .setParameter("at", at).setParameter("published", draft ? null : at).setParameter("id", d.getId()).executeUpdate());
        return d;
    }
    private void lastVisit(LocalDateTime at) { tx.executeWithoutResult(s -> em.createQuery("update User u set u.lastActiveAt = :at where u.id = :id").setParameter("at", at).setParameter("id", owner.getId()).executeUpdate()); }
    private List<NotificationDispatch> category(NotificationCategory c) { return dispatches.findAll().stream().filter(n -> n.getCategory()==c).toList(); }
    private void plan(LocalDateTime at) { clock.at(at); service.plan(owner.getId(), at); }
    private Receiver receiver(User accepted) {
        var r = Receiver.builder().userId(owner.getId()).name("수신자").phone("01033334444").build();
        if (accepted != null) r.connectAcceptedUser(accepted.getId());
        return context.getBean(ReceiverRepository.class).saveAndFlush(r);
    }
    private TimeLetter letter(LocalDateTime send, Receiver... targets) {
        var l = context.getBean(TimeLetterRepository.class).saveAndFlush(TimeLetter.builder().user(owner).title("편지").sendAt(send).status(TimeLetterStatus.SCHEDULED).build());
        for (var r : targets) context.getBean(TimeLetterReceiverRepository.class).saveAndFlush(TimeLetterReceiver.builder().timeLetter(l).receiver(r).build());
        return l;
    }
    @Test void fridayUsesGoalThreeAndRealAssignedQuestionWithWeeklyCount() {
        record(START.minusDays(2), false); record(START.minusDays(3), false);
        plan(START); plan(START);
        assertThat(category(NotificationCategory.B)).singleElement().satisfies(n -> {
            assertThat(n.getBody()).isEqualTo("이번 주에 2번 남기셨어요.");
            assertThat(n.getPayloadJson()).contains("questionId", "WEEKLY_PROGRESS", "weeklyGoal", "2026-10-16");
            assertThat(n.getDueAt()).isEqualTo(START.withHour(18));
        });
    }
    @Test void finalWritingAfterEnqueueCancelsDailyButDraftDoesNotCount() {
        record(START.minusDays(2), false); record(START, true); plan(START);
        assertThat(category(NotificationCategory.B)).hasSize(1);
        record(START.plusHours(1), false); clock.at(START.withHour(18)); worker.process(owner.getId());
        assertThat(category(NotificationCategory.B)).singleElement().extracting(NotificationDispatch::getStatus).isEqualTo(NotificationDispatch.Status.CANCELLED);
        verifyNoInteractions(sender);
    }
    @Test void firstRecordNextDayWinsOverFridayProgress() {
        record(START.minusDays(1), false); plan(START);
        assertThat(category(NotificationCategory.B)).singleElement().satisfies(n -> assertThat(n.getPayloadJson()).contains("FIRST_NEXT_DAY"));
    }
    @Test void fourteenDayReductionAndTwentyOneDayStopRecoverOnlyWhenManualToggleAllows() {
        record(START.minusDays(15), false); plan(START.minusDays(1));
        assertThat(category(NotificationCategory.B)).isEmpty();
    }
    @Test void stoppedBRecoversAfterFinalWritingButNeverTurnsManualOffOn() {
        record(START.minusDays(21), false); plan(START);
        assertThat(category(NotificationCategory.B)).isEmpty();
        record(START.minusDays(1), false); plan(START);
        assertThat(category(NotificationCategory.B)).hasSize(1);
        service.settings(owner.getId(), false, LocalTime.of(18,0), EnumSet.allOf(DayOfWeek.class), 3);
        plan(START.plusDays(1));
        assertThat(category(NotificationCategory.B)).hasSize(1);
    }
    @Test void known18HourMemoryPreventsMorningDailyAndMonthlyCapAllowsOnlyTwo() {
        record(START.minusYears(1), false); record(START.minusDays(2), false);
        service.settings(owner.getId(), true, LocalTime.of(9,0), EnumSet.allOf(DayOfWeek.class), 3);
        plan(START.withHour(8)); clock.at(START.withHour(9)); worker.process(owner.getId()); verifyNoInteractions(sender);
        clock.at(START.withHour(18)); worker.process(owner.getId());
        assertThat(category(NotificationCategory.C)).singleElement().extracting(NotificationDispatch::getStatus).isEqualTo(NotificationDispatch.Status.PUSH_SENT);
        assertThat(category(NotificationCategory.B)).singleElement().extracting(NotificationDispatch::getStatus).isEqualTo(NotificationDispatch.Status.IN_APP_ONLY);
    }
    @Test void cStillRunsWithBOffAndTwoPerCalendarMonth() {
        service.settings(owner.getId(), false, LocalTime.of(18,0), EnumSet.allOf(DayOfWeek.class), 3);
        record(START.minusYears(1), false); record(START.minusYears(1).plusDays(1), false); record(START.minusYears(1).plusDays(2), false);
        for (int day = 0; day < 3; day++) { plan(START.plusDays(day).withHour(18)); worker.process(owner.getId()); }
        assertThat(category(NotificationCategory.C)).hasSize(2); assertThat(category(NotificationCategory.B)).isEmpty();
        assertThat(category(NotificationCategory.C)).allSatisfy(n -> assertThat(n.getPayloadJson()).contains("recordType", "DIARY", "recordId"));
    }
    @Test void aSixMonthsResetsOnEntryAndReturnContextSurvivesActivityCapture() {
        lastVisit(START.minusMonths(6)); plan(START); plan(START);
        assertThat(category(NotificationCategory.A)).hasSize(1);
        tx.executeWithoutResult(s -> { context.getBean(NotificationActivityService.class).capture(owner.getId(), START); context.getBean(UserRepository.class).updateLastActiveAt(owner.getId(), START); });
        var entry = service.entry(owner.getId()); assertThat(entry.showEasyQuestions()).isTrue(); assertThat(entry.easyQuestions()).hasSize(3);
        worker.process(owner.getId());
        assertThat(category(NotificationCategory.A)).singleElement().extracting(NotificationDispatch::getStatus).isEqualTo(NotificationDispatch.Status.CANCELLED);
    }
    @Test void delegateConfirmationProducesSevenNoticesAndNeverChangesDeliverySettings() throws Exception {
        lastVisit(START.minusMonths(24)); service.delegate(owner.getId(), "대리인", "01033334444"); plan(START);
        var contact = category(NotificationCategory.A).get(0); assertThat(contact.isExternalRecipient()).isTrue();
        Map<?,?> data = context.getBean(ObjectMapper.class).readValue(contact.getPayloadJson(), Map.class);
        String token = (String)data.get("linkId"); service.confirm(token); service.confirm(token);
        for (int day=0; day<8; day++) { clock.at(START.plusDays(day)); service.plan(owner.getId(), START.plusDays(day)); service.plan(owner.getId(), START.plusDays(day)); }
        assertThat(category(NotificationCategory.A)).filteredOn(n -> n.getBody().contains("모두 취소")).hasSize(7);
        assertThat(context.getBean(TimeLetterRepository.class).count()).isZero();
        service.entry(owner.getId()); assertThatThrownBy(() -> service.confirm(token)).isInstanceOf(CustomException.class);
    }
    @Test void anniversaryRequiresOwnedReceiverAndInvalidCalendarDateIsRejected() {
        var target = receiver(null);
        assertThatThrownBy(() -> service.anniversary(owner.getId(), target.getId(), 4,31)).isInstanceOf(CustomException.class);
        service.anniversary(owner.getId(), target.getId(), 10,16);
        plan(START); assertThat(category(NotificationCategory.C)).singleElement().satisfies(n -> assertThat(n.getPayloadJson()).contains("TIME_LETTER_COMPOSE", "receiverId"));
        var outsider = user("타인", null);
        assertThatThrownBy(() -> service.anniversary(outsider.getId(), target.getId(),10,16)).isInstanceOf(CustomException.class);
    }
    @Test void threeDayUnacceptedReminderCancelsIfReceiverAcceptsBeforeSend() {
        var target = receiver(null); var l = letter(START.plusDays(3), target);
        delivery.reminder3(l.getId(), START); delivery.reminder3(l.getId(), START);
        assertThat(category(NotificationCategory.E)).hasSize(1);
        var invitee = user("받는 사람", "01033334444");
        tx.executeWithoutResult(s -> em.find(Receiver.class,target.getId()).connectAcceptedUser(invitee.getId()));
        worker.process(owner.getId());
        assertThat(category(NotificationCategory.E)).singleElement().extracting(NotificationDispatch::getStatus).isEqualTo(NotificationDispatch.Status.CANCELLED);
    }
    @Test void partialLetterDeliveryAndRetryDoNotRedeliverPreviouslySuccessfulRecipient() {
        var accepted = receiver(user("받는 사람", "01033334444")); var pending = receiver(null);
        var l = letter(START.minusMinutes(1), accepted, pending);
        var result = delivery.deliver(l.getId(), null, null, START);
        assertThat(result.delivered()).isEqualTo(1); assertThat(result.failed()).isEqualTo(1);
        assertThat(context.getBean(TimeLetterReceiverRepository.class).findByTimeLetterId(l.getId())).filteredOn(t -> t.getReceiver().getId().equals(pending.getId()))
                .singleElement().extracting(TimeLetterReceiver::getDeliveredAt).isNull();
        var another = user("새 수신자", "01055556666");
        tx.executeWithoutResult(s -> em.find(Receiver.class,pending.getId()).connectAcceptedUser(another.getId()));
        UUID key = UUID.randomUUID(); result = delivery.deliver(l.getId(), owner.getId(), key, START);
        assertThat(result.delivered()).isEqualTo(2); assertThat(result.failed()).isZero();
        long count = dispatches.count(); assertThat(delivery.deliver(l.getId(), owner.getId(), key, START)).isEqualTo(result);
        delivery.deliver(l.getId(), owner.getId(), UUID.randomUUID(), START);
        assertThat(dispatches.count()).isEqualTo(count);
        verify(talk, times(2)).send(anyString(), anyString(), eq("CONTENT_DELIVERED"), anyString(), anyMap());
    }
    @Test void failedLetterDoesNotBecomeSentAndIsIdempotentAcrossSchedulerRuns() {
        var l = letter(START.minusMinutes(1), receiver(null));
        delivery.deliver(l.getId(), null,null,START); delivery.deliver(l.getId(),null,null,START);
        assertThat(context.getBean(TimeLetterRepository.class).findById(l.getId()).orElseThrow().getStatus()).isEqualTo(TimeLetterStatus.SCHEDULED);
        assertThat(category(NotificationCategory.E)).hasSize(1);
    }
    @Test void talkRetryOnlyReplaysExplicitRejectionAndUnknownOutcomeIsNeverReplayed() {
        when(talk.send(anyString(),anyString(),anyString(),anyString(),anyMap()))
                .thenReturn(new AlimtalkGateway.Result(AlimtalkGateway.Outcome.RETRYABLE_REJECTION,null))
                .thenReturn(new AlimtalkGateway.Result(AlimtalkGateway.Outcome.ACCEPTED,"ok"));
        context.getBean(NotificationPolicyStore.class).enqueueWithChannels(owner.getId(),"talk-retry",NotificationCategory.D,NotificationEvent.SCHEDULED,
                "안내",Map.of("destination","HOME","type","TEST"),START,NotificationChannel.ALIMTALK,"01033334444",null,true);
        worker.process(owner.getId()); clock.at(START.plusMinutes(5)); context.getBean(AlimtalkOutbox.class).poll();
        verify(talk,times(2)).send(anyString(),anyString(),anyString(),anyString(),anyMap());
        assertThat(dispatches.findAll()).singleElement().extracting(NotificationDispatch::getStatus).isEqualTo(NotificationDispatch.Status.ALIMTALK_SENT);
        when(talk.send(anyString(),anyString(),anyString(),anyString(),anyMap())).thenReturn(new AlimtalkGateway.Result(AlimtalkGateway.Outcome.UNKNOWN,null));
        context.getBean(NotificationPolicyStore.class).enqueueWithChannels(owner.getId(),"talk-unknown",NotificationCategory.D,NotificationEvent.SCHEDULED,
                "안내",Map.of("destination","HOME","type","TEST"),START.plusMinutes(5),NotificationChannel.ALIMTALK,"01033334444",null,true);
        worker.process(owner.getId()); clock.at(START.plusDays(1)); context.getBean(AlimtalkOutbox.class).poll();
        verify(talk,times(3)).send(anyString(),anyString(),anyString(),anyString(),anyMap());
    }
    @Test void fourCompletedGoalWeeksProduceOneReportAndUnselectedWeekdaysDoNotSend() {
        for (int week=1;week<=4;week++) for (int n=0;n<3;n++) record(START.toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(week).plusDays(n).atTime(12,0),false);
        plan(START); plan(START);
        assertThat(category(NotificationCategory.B)).singleElement().satisfies(n -> assertThat(n.getPayloadJson()).contains("FOUR_WEEKS","WEEKLY_REPORT"));
        service.settings(owner.getId(),true,LocalTime.of(18,0),EnumSet.of(DayOfWeek.MONDAY),3);
        plan(START.plusDays(1)); assertThat(category(NotificationCategory.B)).hasSize(1);
    }
    @Test void sixteenDaysWithoutWritingUsesFridayOnlyAndCCountUsesAssignedRecords() {
        var row=record(START.minusDays(14),false);var receiver=receiver(null);
        context.getBean(DiaryReceiverRepository.class).saveAndFlush(DiaryReceiver.builder().diary(row).receiver(receiver).build());
        plan(START);assertThat(category(NotificationCategory.C)).singleElement().satisfies(n -> assertThat(n.getBody()).contains("1개"));
        assertThat(category(NotificationCategory.B)).hasSize(1);
        plan(START.plusDays(1));assertThat(category(NotificationCategory.B)).hasSize(1);
    }
    @Test void laterSettingsRevisionCancelsQueuedBAndNightBStillUsesChosenTime() {
        record(START.minusDays(2),false);plan(START);
        service.settings(owner.getId(),true,LocalTime.of(23,0),EnumSet.allOf(DayOfWeek.class),3);plan(START);
        clock.at(START.withHour(23));worker.process(owner.getId());
        assertThat(category(NotificationCategory.B)).extracting(NotificationDispatch::getStatus)
                .containsExactlyInAnyOrder(NotificationDispatch.Status.CANCELLED,NotificationDispatch.Status.PUSH_SENT);
    }
    @Test void aTwelveEighteenAndRepeatingTwentyFourMonthsUseCorrectChannelAndLinks() throws Exception {
        for(int months:List.of(12,18,24,30)) {
            lastVisit(START.minusMonths(months));plan(START);
        }
        assertThat(category(NotificationCategory.A)).hasSize(4);
        assertThat(category(NotificationCategory.A)).allSatisfy(n -> assertThat(n.effectiveChannel()).isEqualTo(NotificationChannel.PUSH_AND_ALIMTALK));
        assertThat(category(NotificationCategory.A)).filteredOn(n -> n.getBody().contains("보관할지")).hasSize(3);
        var recent=category(NotificationCategory.A).get(3);
        String token=(String)context.getBean(ObjectMapper.class).readValue(recent.getPayloadJson(),Map.class).get("linkId");
        assertThat(service.link(token).purpose()).isEqualTo("RETENTION_CONFIRMATION");
        assertThat(service.cancel(token,owner.getId()).showEasyQuestions()).isTrue();
        assertThatThrownBy(() -> service.link(token)).isInstanceOf(CustomException.class);
    }
    @Test void simultaneousMonthlyPlanningCreatesOneCEvent() throws Exception {
        record(START.minusYears(1),false);service.settings(owner.getId(),false,LocalTime.of(18,0),EnumSet.allOf(DayOfWeek.class),3);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            var one=pool.submit(() -> {start.await();service.plan(owner.getId(),START);return true;});
            var two=pool.submit(() -> {start.await();service.plan(owner.getId(),START);return true;});start.countDown();
            assertThat(one.get(15,TimeUnit.SECONDS)).isTrue();assertThat(two.get(15,TimeUnit.SECONDS)).isTrue();
            assertThat(category(NotificationCategory.C)).hasSize(1);
        } finally {pool.shutdownNow();}
    }
    @Test void notificationTapResolvesDeletedLetterToList() {
        var navigation=new NotificationNavigationController(dispatches,em,context.getBean(ObjectMapper.class));
        var l=letter(START.plusDays(7),receiver(null));
        Long id=context.getBean(NotificationPolicyStore.class).enqueue(owner.getId(),"tap-letter",NotificationCategory.E,NotificationEvent.SCHEDULED,
                "AfterNote","안내",Map.of("destination","TIME_LETTER_DETAIL","timeLetterId",l.getId().toString()),START);
        worker.process(owner.getId());
        tx.executeWithoutResult(t -> {context.getBean(TimeLetterReceiverRepository.class).deleteByTimeLetterId(l.getId());context.getBean(TimeLetterRepository.class).deleteById(l.getId());});
        var target=navigation.target(owner.getId(),id).getData();
        assertThat(target.deleted()).isTrue();assertThat(target.data().get("destination")).isEqualTo("TIME_LETTER_LIST");assertThat(target.toast()).isEqualTo("삭제된 항목이에요");
        assertThatThrownBy(() -> navigation.target(user("타인",null).getId(),id)).isInstanceOf(CustomException.class);
    }
    @Test void tapOfAnsweredQuestionOpensThatActualAnswer() throws Exception {
        record(START.minusDays(2),false);plan(START);clock.at(START.withHour(18));worker.process(owner.getId());
        var notification=category(NotificationCategory.B).get(0);
        Map<?,?> data=context.getBean(ObjectMapper.class).readValue(notification.getPayloadJson(),Map.class);
        Long questionId=Long.valueOf((String)data.get("questionId"));
        tx.executeWithoutResult(t -> em.find(com.afternote.domain.dailyquestion.model.UserDailyQuestion.class,questionId).updateAnswer("답변",false));
        var target=new NotificationNavigationController(dispatches,em,context.getBean(ObjectMapper.class)).target(owner.getId(),notification.getId()).getData();
        assertThat(target.data()).containsEntry("destination","RECORD_DETAIL").containsEntry("recordType","DAILY_QUESTION").containsEntry("recordId",questionId.toString());
    }
    @Test void backgroundPlannerProducesDueNotificationsAndDeliverySchedulerPublishesRealOutcomes() {
        record(START.minusDays(2),false);
        context.getBean(EngagementNotificationScheduler.class).poll();assertThat(category(NotificationCategory.B)).hasSize(1);
        var l=letter(START.minusMinutes(1),receiver(user("수신 회원","01055556666")));
        context.getBean(TimeLetterDeliveryScheduler.class).poll();
        assertThat(delivery.status(owner.getId(),l.getId())).singleElement().satisfies(t -> assertThat(t.status()).isEqualTo("DELIVERED"));
    }
    @Test void thirtyDaysWithoutWritingPromptsDelegateAndCancelsWhenOneIsAssigned() {
        record(START.minusDays(30),false);plan(START);
        assertThat(category(NotificationCategory.C)).singleElement().satisfies(n -> assertThat(n.getPayloadJson()).contains("NO_DELEGATE"));
        service.delegate(owner.getId(),"대리인","01033334444");clock.at(START.withHour(18));worker.process(owner.getId());
        assertThat(category(NotificationCategory.C)).singleElement().extracting(NotificationDispatch::getStatus).isEqualTo(NotificationDispatch.Status.CANCELLED);
    }
    @Test void leapBirthdayFallsOnFebruary28AndRecordIdsCarryTheirOwnType() {
        var target=receiver(null);service.anniversary(owner.getId(),target.getId(),2,29);
        plan(LocalDateTime.of(2026,2,28,18,0));
        assertThat(category(NotificationCategory.C)).singleElement().satisfies(n -> assertThat(n.getPayloadJson()).contains("ANNIVERSARY","TIME_LETTER_COMPOSE"));
        record(START.minusYears(2),false);
        tx.executeWithoutResult(t -> {
            var deep=com.afternote.domain.deepthought.model.DeepThought.create(owner,"생각","내용",false,null,List.of());
            em.persist(deep);em.flush();
            em.createQuery("update DeepThought d set d.publishedAt=:at where d.id=:id").setParameter("at",START.minusMonths(3)).setParameter("id",deep.getId()).executeUpdate();
        });
        plan(START);
        assertThat(category(NotificationCategory.C)).filteredOn(n -> n.getPayloadJson().contains("THREE_MONTHS"))
                .singleElement().satisfies(n -> assertThat(n.getPayloadJson()).contains("DEEP_THOUGHT","recordId"));
    }
    @Test void externalDelegateMessageNeverAppearsInOwnersInboxOrReadEndpoint() {
        lastVisit(START.minusMonths(24));service.delegate(owner.getId(),"대리인","01033334444");plan(START);worker.process(owner.getId());
        var notification=category(NotificationCategory.A).get(0);
        var inbox=new NotificationInboxController(dispatches,context.getBean(ObjectMapper.class));
        assertThat(inbox.list(owner.getId(),0).getData().items()).isEmpty();
        assertThat(inbox.list(owner.getId(),0).getData().unreadCount()).isZero();
        assertThatThrownBy(() -> inbox.read(owner.getId(),notification.getId())).isInstanceOf(CustomException.class);
    }
    @Test void finalizingOldDraftUsesCurrentPublicationTimeAndEditingDoesNotMoveIt() {
        var draft=record(START.minusMonths(2),true);
        var before=LocalDateTime.now(NotificationTiming.ZONE);
        tx.executeWithoutResult(t -> em.find(Diary.class,draft.getId()).update(null,"최종",false,null,null));
        var saved=context.getBean(DiaryRepository.class).findById(draft.getId()).orElseThrow();
        assertThat(saved.getPublishedAt()).isBetween(before.minusSeconds(1),LocalDateTime.now(NotificationTiming.ZONE).plusSeconds(1));
        var first=saved.getPublishedAt();
        tx.executeWithoutResult(t -> em.find(Diary.class,draft.getId()).update("수정",null,null,null,null));
        assertThat(context.getBean(DiaryRepository.class).findById(draft.getId()).orElseThrow().getPublishedAt()).isEqualTo(first);
    }
    @Test void lateNightDelegateConfirmationStartsSevenDistinctMorningNotices() throws Exception {
        clock.at(START.withHour(23));lastVisit(START.minusMonths(24));service.delegate(owner.getId(),"대리인","01033334444");plan(START.withHour(23));
        var contact=category(NotificationCategory.A).get(0);
        String token=(String)context.getBean(ObjectMapper.class).readValue(contact.getPayloadJson(),Map.class).get("linkId");
        service.confirm(token);plan(START.withHour(23));
        assertThat(category(NotificationCategory.A)).filteredOn(n -> n.getBody().contains("모두 취소")).isEmpty();
        for (int day=1;day<=7;day++) {plan(START.plusDays(day).withHour(8));worker.process(owner.getId());}
        assertThat(category(NotificationCategory.A)).filteredOn(n -> n.getBody().contains("모두 취소")).hasSize(7);
        verify(sender,times(7)).sendToUserWithResult(eq(owner.getId()),anyString(),anyString(),anyMap());
        assertThat(dispatches.findById(contact.getId()).orElseThrow().getStatus()).isEqualTo(NotificationDispatch.Status.CANCELLED);
    }
    @Configuration(proxyBeanMethods=false)
    @EnableJpaRepositories(basePackages={"com.afternote.domain.notification.engagement","com.afternote.domain.notification.channel",
            "com.afternote.domain.receiver.repository","com.afternote.domain.diary.repository","com.afternote.domain.dailyquestion.repository","com.afternote.domain.timeletter.repository"})
    @Import({EngagementNotificationService.class,RecordFacts.class,EngagementNotificationGuard.class,NotificationActivityService.class,
            TimeLetterDeliveryService.class,TimeLetterDeliveryGuard.class,AlimtalkOutbox.class,EngagementNotificationScheduler.class,TimeLetterDeliveryScheduler.class})
    static class FeatureConfig {
        @Bean @Primary TestClock scenarioClock() { return new TestClock(); }
        @Bean AlimtalkGateway talkGateway() { return mock(AlimtalkGateway.class); }
    }
    static class TestClock extends Clock {
        private volatile Instant value = START.atZone(NotificationTiming.ZONE).toInstant();
        void at(LocalDateTime at) { value = at.atZone(NotificationTiming.ZONE).toInstant(); }
        @Override public ZoneId getZone() { return NotificationTiming.ZONE; }
        @Override public Clock withZone(ZoneId zone) { return zone.equals(getZone()) ? this : Clock.fixed(value, zone); }
        @Override public Instant instant() { return value; }
    }
}
