package com.afternote.domain.notification.engagement;

import com.afternote.domain.notification.policy.*;
import com.afternote.domain.user.model.*;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.dailyquestion.model.*;
import com.afternote.global.exception.*;
import jakarta.persistence.*;
import java.time.*;
import java.time.temporal.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.afternote.domain.notification.engagement.NotificationKind.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngagementNotificationService {
    private final EntityManager entityManager;
    private final UserRepository users;
    private final NotificationProfileRepository profiles;
    private final RecordFacts facts;
    private final NotificationPolicyStore store;
    private final NotificationDispatchRepository dispatches;
    private final Clock clock;
    private static final SecureRandom RANDOM = new SecureRandom();
    public LocalDateTime now() { return LocalDateTime.now(clock.withZone(NotificationTiming.ZONE)); }
    private User lock(Long id) {
        return users.findByIdForPushUpdate(id).filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
    private NotificationProfile profile(Long id) { return profiles.findById(id).orElseGet(() -> profiles.save(new NotificationProfile(id))); }
    public NotificationProfile settings(Long id) {
        if (!users.existsById(id)) throw new CustomException(ErrorCode.USER_NOT_FOUND);
        return profiles.findById(id).orElse(new NotificationProfile(id));
    }
    @Transactional
    public NotificationProfile settings(Long id, boolean enabled, LocalTime time, Set<DayOfWeek> days, int goal) {
        lock(id);
        if (time == null || time.getSecond() != 0 || time.getNano() != 0 || days == null || days.isEmpty() || days.stream().anyMatch(Objects::isNull) || goal < 1 || goal > 7)
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        int mask = days.stream().mapToInt(d -> 1 << (d.getValue()-1)).reduce(0, (a,b) -> a|b);
        var p = profile(id); p.settings(enabled, time, mask, goal); return p;
    }
    @Transactional
    public void delegate(Long id, String name, String phone) {
        lock(id); var p = profile(id);
        if (phone == null) { p.delegate(null, null, null); return; }
        if (name == null || name.isBlank() || name.length() > 100 || !phone.matches("[+]?[0-9][0-9 -]{7,18}"))
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        String normalized = phone.replace(" ", "").replace("-", "");
        var accounts = entityManager.createQuery("select u.id from User u where replace(replace(u.phone, '-', ''), ' ', '') = :phone and u.status = :status", Long.class)
                .setParameter("phone", normalized).setParameter("status", UserStatus.ACTIVE).setMaxResults(2).getResultList();
        Long account = accounts.size() == 1 && !accounts.get(0).equals(id) ? accounts.get(0) : null;
        p.delegate(name.trim(), normalized, account);
    }
    @Transactional
    public NotificationAnniversary anniversary(Long id, Long receiverId, int month, int day) {
        lock(id); requireReceiver(id, receiverId);
        try { MonthDay.of(month, day); } catch (DateTimeException ex) { throw new CustomException(ErrorCode.INVALID_INPUT_VALUE); }
        var a = new NotificationAnniversary(id, receiverId, month, day); entityManager.persist(a); return a;
    }
    public List<NotificationAnniversary> anniversaries(Long id) {
        return entityManager.createQuery("select a from NotificationAnniversary a where a.userId = :id order by a.id", NotificationAnniversary.class)
                .setParameter("id", id).getResultList();
    }
    @Transactional
    public void deleteAnniversary(Long id, Long anniversaryId) {
        lock(id); var a = entityManager.find(NotificationAnniversary.class, anniversaryId);
        if (a == null || !a.getUserId().equals(id)) throw new CustomException(ErrorCode.RESOURCE_ALREADY_DELETED);
        entityManager.remove(a);
    }
    private Receiver requireReceiver(Long owner, Long id) {
        var receiver = entityManager.find(Receiver.class, id);
        if (receiver == null || !receiver.getUserId().equals(owner)) throw new CustomException(ErrorCode.RECEIVER_NOT_FOUND);
        return receiver;
    }
    public record Entry(boolean showEasyQuestions, List<String> easyQuestions, boolean procedureCancelled) { }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Entry entry(Long id) {
        var user = lock(id); var p = profile(id); LocalDateTime now = now();
        p.captureActivity(user.getLastActiveAt(), now);
        Entry response = new Entry(p.isReturnPending(), List.of("오늘 가장 기억에 남는 순간은 무엇인가요?", "요즘 고마운 사람은 누구인가요?", "오늘의 나에게 한마디 남겨볼까요?"), p.isCancellationPending());
        p.acknowledgeEntry();
        users.updateLastActiveAt(id, now);
        return response;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void plan(Long id, LocalDateTime at) {
        User user = lock(id); var p = profile(id); var rows = facts.all(id);
        // Generate higher priority candidates before B, including future C at 18:00.
        inactive(user, p, rows, at);
        returns(user, p, rows, at);
        daily(user, p, rows, at);
    }
    private void daily(User user, NotificationProfile p, List<RecordFacts.Written> rows, LocalDateTime at) {
        LocalDate date = at.toLocalDate();
        if (!user.isMindRecordPushEnabled() || !p.isDailyEnabled() || !p.selected(date.getDayOfWeek())) return;
        if (RecordFacts.count(rows, date, date.plusDays(1)) > 0) return;
        long idle = RecordFacts.idleDays(rows, user.getCreatedAt(), date);
        if (idle >= 21 || (idle >= 14 && date.getDayOfWeek() != DayOfWeek.FRIDAY)) return;
        LocalDate week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int streak = RecordFacts.streak(rows, week, p.getWeeklyGoal());
        NotificationKind kind = streak >= 4 && streak % 4 == 0 && date.getDayOfWeek() == DayOfWeek.FRIDAY
                ? FOUR_WEEKS : !rows.isEmpty() && rows.get(0).at().toLocalDate().plusDays(1).equals(date)
                ? FIRST_NEXT_DAY : date.getDayOfWeek() == DayOfWeek.FRIDAY ? WEEKLY_PROGRESS : DAILY;
        Map<String, String> data = kind.data(); data.put("date", date.toString());
        data.put("settingsRevision", Long.toString(p.getRevision()));
        data.put("weeklyGoal", Integer.toString(p.getWeeklyGoal()));
        data.put("weekStart", week.toString());
        long count = RecordFacts.count(rows, week, week.plusWeeks(1));
        data.put("weeklyCount", Long.toString(count));
        if (kind != FOUR_WEEKS) {
            UserDailyQuestion question = question(user, date);
            if (question == null) return;
            data.put("questionId", question.getId().toString());
        }
        emit(user, "B:" + date + ":" + p.getRevision(), kind, kind.body(count), data, date.atTime(p.getDailyTime()), user.getPhone(), user.getId(), false);
    }
    private UserDailyQuestion question(User user, LocalDate date) {
        var assigned = entityManager.createQuery("select q from UserDailyQuestion q where q.user.id = :owner and q.questionDate = :date order by q.id", UserDailyQuestion.class)
                .setParameter("owner", user.getId()).setParameter("date", date).setMaxResults(1).getResultList();
        if (!assigned.isEmpty()) return assigned.get(0);
        var bank = entityManager.createQuery("select q from DailyQuestion q where q.isActive = true and not exists "
                + "(select a.id from UserDailyQuestion a where a.user.id = :owner and a.dailyQuestion = q) order by q.id", DailyQuestion.class)
                .setParameter("owner", user.getId()).setMaxResults(1).getResultList();
        if (bank.isEmpty()) bank = entityManager.createQuery("select q from DailyQuestion q where q.isActive = true order by q.id", DailyQuestion.class).setMaxResults(1).getResultList();
        if (bank.isEmpty()) return null;
        var q = UserDailyQuestion.builder().user(user).dailyQuestion(bank.get(0)).questionDate(date).content("").isDraft(true).isAnswered(false).build();
        entityManager.persist(q); entityManager.flush(); return q;
    }
    private void returns(User user, NotificationProfile p, List<RecordFacts.Written> rows, LocalDateTime at) {
        LocalDate date = at.toLocalDate();
        if (monthlyCandidates(user.getId(), date) >= 2) return;
        NotificationKind kind = null; Map<String, String> data = null; String body = null;
        if (!rows.isEmpty() && rows.get(0).at().toLocalDate().plusYears(1).equals(date)) {
            kind = FIRST_ANNIVERSARY; data = recordData(kind, rows.get(0)); body = kind.body();
        }
        if (kind == null) for (var row : rows) {
            if (row.at().toLocalDate().plusYears(1).equals(date)) { kind = LAST_YEAR; data = recordData(kind, row); body = kind.body(); break; }
        }
        if (kind == null) for (var row : rows) {
            if (row.at().toLocalDate().plusMonths(3).equals(date)) { kind = THREE_MONTHS; data = recordData(kind, row); body = kind.body(); break; }
        }
        if (kind == null) for (var a : anniversaries(user.getId())) {
            // February 29 is observed on February 28 in non-leap years.
            if (!MonthDay.of(a.getMonth(), a.getDay()).atYear(date.getYear()).equals(date)) continue;
            var receiver = entityManager.find(Receiver.class, a.getReceiverId());
            if (receiver == null || !receiver.getUserId().equals(user.getId())) continue;
            kind = ANNIVERSARY; data = kind.data(); data.put("anniversaryId", a.getId().toString());
            data.put("receiverId", receiver.getId().toString()); body = kind.body(receiver.getName()); break;
        }
        long idle = RecordFacts.idleDays(rows, user.getCreatedAt(), date);
        if (kind == null && idle == 14) {
            var targets = entityManager.createQuery("select r from Receiver r where r.userId = :owner order by r.id", Receiver.class)
                    .setParameter("owner", user.getId()).setMaxResults(1).getResultList();
            kind = targets.isEmpty() ? RECORD_COUNT : UNDELIVERED; data = kind.data();
            // Count records actually assigned to this recipient, not all of the owner's records.
            long pending = targets.isEmpty() ? rows.size() : undelivered(user.getId(), targets.get(0).getId());
            if (kind == UNDELIVERED) { data.put("receiverId", targets.get(0).getId().toString()); body = kind.body(targets.get(0).getName(), pending); }
            else body = kind.body(rows.size());
        }
        if (kind == null && idle == 30 && !p.hasDelegate()) { kind = NO_DELEGATE; data = kind.data(); body = kind.body(); }
        if (kind == null) return;
        data.put("date", date.toString());
        emit(user, "C:" + date, kind, body, data, date.atTime(18, 0), user.getPhone(), user.getId(), false);
    }
    private long undelivered(Long owner, Long receiver) {
        long count = 0;
        for (String entity : List.of("DiaryReceiver", "DeepThoughtReceiver", "UserDailyQuestionReceiver")) {
            String source = entity.equals("DiaryReceiver") ? "diary" : entity.equals("DeepThoughtReceiver") ? "deepThought" : "userDailyQuestion";
            // Assignment plus unfulfilled MIND_RECORD condition means not delivered yet.
            count += entityManager.createQuery("select count(r) from " + entity + " r where r.receiver.id = :receiver and r." + source
                    + ".user.id = :owner and r." + source + ".isDraft = false and not exists "
                    + "(select c.id from DeliveryCondition c where c.receiverId = :receiver and c.contentType = :contentType "
                    + "and c.state = com.afternote.domain.delivery.model.ConditionState.FULFILLED)", Long.class)
                    .setParameter("receiver", receiver).setParameter("owner", owner)
                    .setParameter("contentType", com.afternote.domain.delivery.model.DeliveryContentType.valueOf(
                            entity.equals("DiaryReceiver") ? "DIARY" : entity.equals("DeepThoughtReceiver") ? "DEEP_THOUGHT" : "DAILY_QUESTION")).getSingleResult();
        }
        return count;
    }
    private Map<String, String> recordData(NotificationKind kind, RecordFacts.Written row) {
        var data = kind.data(); data.put("recordType", row.type()); data.put("recordId", row.id().toString()); return data;
    }
    private long monthlyCandidates(Long owner, LocalDate date) {
        return entityManager.createQuery("select count(n) from NotificationDispatch n where n.userId = :owner and n.category = :category "
                + "and n.dueAt >= :start and n.dueAt < :end and n.status not in :excluded", Long.class)
                .setParameter("owner", owner).setParameter("category", NotificationCategory.C)
                .setParameter("start", date.withDayOfMonth(1).atStartOfDay()).setParameter("end", date.withDayOfMonth(1).plusMonths(1).atStartOfDay())
                .setParameter("excluded", List.of(NotificationDispatch.Status.CANCELLED, NotificationDispatch.Status.IN_APP_ONLY)).getSingleResult();
    }
    private void inactive(User user, NotificationProfile p, List<RecordFacts.Written> rows, LocalDateTime at) {
        LocalDateTime cycle = user.getLastActiveAt() == null ? user.getCreatedAt() : user.getLastActiveAt();
        if (cycle == null) return;
        if (p.getConfirmedAt() != null && cycle.equals(p.getConfirmedCycle())) {
            LocalDate firstNotice = NotificationTiming.allowedAt(NotificationCategory.A,p.getConfirmedAt()).toLocalDate();
            long day = ChronoUnit.DAYS.between(firstNotice, at.toLocalDate());
            if (day >= 0 && day < 7) {
                String key = "A:WARNING:" + cycle + ":" + at.toLocalDate();
                if (dispatches.findByUserIdAndEventKey(user.getId(), key).isPresent()) return;
                var data = RETENTION_WARNING.data(); data.put("cycle", cycle.toString());
                data.put("confirmedAt", p.getConfirmedAt().toString());
                data.put("linkId", issueLink(user, p, "RETENTION_CONFIRMATION", cycle, at.plusDays(8)));
                emit(user, key, RETENTION_WARNING, RETENTION_WARNING.body(), data,
                        at.toLocalDate().atTime(8, 0), user.getPhone(), user.getId(), false);
            }
            return; // End only the seven daily notices. Never release content here.
        }
        if (at.isBefore(cycle.plusMonths(6))) return;
        int months = 6;
        while (!at.isBefore(cycle.plusMonths(months + 6L)) && months < 12000) months += 6;
        NotificationKind kind = months == 6 ? INACTIVE_6 : months == 12 ? INACTIVE_12
                : months >= 24 && p.hasDelegate() ? DELEGATE_CONTACT : INACTIVE_18;
        String key = "A:" + cycle + ":" + (kind == DELEGATE_CONTACT ? "DELEGATE:" + p.getDelegateVersion() : months);
        if (dispatches.findByUserIdAndEventKey(user.getId(), key).isPresent()) return;
        var data = kind.data(); data.put("cycle", cycle.toString());
        if (kind == INACTIVE_18 || kind == DELEGATE_CONTACT) {
            data.put("delegateVersion", Objects.toString(p.getDelegateVersion(), ""));
            data.put("linkId", issueLink(user, p, kind.destination, cycle, at.plusDays(30)));
        }
        emit(user, key, kind, kind == INACTIVE_6 ? kind.body(rows.size()) : kind == DELEGATE_CONTACT ? kind.body(user.getName()) : kind.body(),
                data, at, kind == DELEGATE_CONTACT ? p.getDelegatePhone() : user.getPhone(),
                kind == DELEGATE_CONTACT ? p.getDelegateUserId() : user.getId(), kind == DELEGATE_CONTACT);
    }
    private void emit(User user, String key, NotificationKind kind, String body, Map<String, String> data,
            LocalDateTime due, String phone, Long pushRecipient, boolean external) {
        store.enqueueWithChannels(user.getId(), key, kind.category, NotificationEvent.SCHEDULED, body, data, due,
                external && pushRecipient == null ? NotificationChannel.ALIMTALK : kind.channel, phone, pushRecipient, external);
    }
    private String issueLink(User user, NotificationProfile p, String purpose, LocalDateTime cycle, LocalDateTime expiry) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        entityManager.persist(new NotificationLink(hash(raw), user.getId(), purpose, cycle, p.getDelegateVersion(), expiry)); return raw;
    }
    public record LinkInfo(String purpose, boolean confirmed) { }
    public LinkInfo link(String token) {
        var l = validLink(token); return new LinkInfo(l.getPurpose(), l.getConsumedAt() != null);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LinkInfo confirm(String token) {
        var initial = entityManager.find(NotificationLink.class, hash(token));
        if (initial == null) throw new CustomException(ErrorCode.RESOURCE_ALREADY_DELETED);
        lock(initial.getUserId());
        entityManager.refresh(initial, LockModeType.PESSIMISTIC_WRITE);
        var l = validLink(token);
        if (!l.getPurpose().equals("AGENT_CONFIRMATION")) throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        if (l.getConsumedAt() == null) { profile(l.getUserId()).confirm(l.getCycle(), now()); l.consume(now()); }
        return new LinkInfo(l.getPurpose(), true);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Entry cancel(String token, Long owner) {
        lock(owner);
        var link = validLink(token);
        if (!link.getUserId().equals(owner) || !link.getPurpose().equals("RETENTION_CONFIRMATION"))
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        return entry(owner);
    }
    private NotificationLink validLink(String token) {
        var l = entityManager.find(NotificationLink.class, hash(token));
        if (l == null) throw new CustomException(ErrorCode.RESOURCE_ALREADY_DELETED);
        var user = entityManager.find(User.class, l.getUserId()); var p = profiles.findById(l.getUserId()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !l.getExpiresAt().isAfter(now())
                || !l.getCycle().equals(user.getLastActiveAt()) || p == null
                || !Objects.equals(l.getDelegateVersion(), p.getDelegateVersion()))
            throw new CustomException(ErrorCode.RECEIVER_INVITATION_EXPIRED);
        return l;
    }
    private static String hash(String raw) {
        if (raw == null || raw.length() < 40 || raw.length() > 100) throw new CustomException(ErrorCode.RESOURCE_ALREADY_DELETED);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
