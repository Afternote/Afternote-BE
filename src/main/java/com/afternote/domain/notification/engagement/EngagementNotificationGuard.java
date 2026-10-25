package com.afternote.domain.notification.engagement;

import com.afternote.domain.notification.policy.*;
import com.afternote.domain.user.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EngagementNotificationGuard implements NotificationDispatchGuard {
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final NotificationProfileRepository profiles;
    private final RecordFacts facts;
    @Override public boolean allows(NotificationDispatch n, LocalDateTime now) {
        try {
            Map<String, String> d = mapper.readValue(n.getPayloadJson(), new TypeReference<>() { });
            NotificationKind kind;
            try { kind = NotificationKind.valueOf(d.getOrDefault("type", "")); }
            catch (IllegalArgumentException ex) { return true; }
            var user = entityManager.find(User.class, n.getUserId());
            var p = profiles.findById(n.getUserId()).orElse(null);
            if (user == null || p == null) return false;
            if (kind.category == NotificationCategory.A) {
                if (!Objects.toString(user.getLastActiveAt(), "").equals(d.get("cycle"))) return false;
                if (d.containsKey("delegateVersion") && !Objects.toString(p.getDelegateVersion(), "").equals(d.get("delegateVersion"))) return false;
                if (kind == NotificationKind.DELEGATE_CONTACT && (!p.hasDelegate()
                        || (p.getConfirmedAt() != null && Objects.toString(p.getConfirmedCycle(),"").equals(d.get("cycle"))))) return false;
                if (kind == NotificationKind.RETENTION_WARNING) {
                    return p.getConfirmedAt() != null && p.getConfirmedAt().toString().equals(d.get("confirmedAt"))
                            && !now.toLocalDate().isBefore(NotificationTiming.allowedAt(NotificationCategory.A,p.getConfirmedAt()).toLocalDate())
                            && now.toLocalDate().isBefore(NotificationTiming.allowedAt(NotificationCategory.A,p.getConfirmedAt()).toLocalDate().plusDays(7));
                }
                return true;
            }
            LocalDate date = LocalDate.parse(d.get("date"));
            if (!now.toLocalDate().equals(date)) return false;
            var rows = facts.all(n.getUserId());
            long idle = RecordFacts.idleDays(rows, user.getCreatedAt(), date);
            if (kind.category == NotificationCategory.B) {
                if (!p.isDailyEnabled() || !user.isMindRecordPushEnabled() || !p.selected(date.getDayOfWeek())
                        || !Long.toString(p.getRevision()).equals(d.get("settingsRevision"))
                        || RecordFacts.count(rows, date, date.plusDays(1)) > 0 || idle >= 21
                        || (idle >= 14 && date.getDayOfWeek() != DayOfWeek.FRIDAY)) return false;
                if (d.containsKey("questionId")) {
                    var q = entityManager.find(com.afternote.domain.dailyquestion.model.UserDailyQuestion.class, Long.valueOf(d.get("questionId")));
                    return q != null && q.getUser().getId().equals(n.getUserId()) && q.getQuestionDate().equals(date) && !q.isAnswered();
                }
                return true;
            }
            Long used = entityManager.createQuery("select count(x) from NotificationDispatch x where x.userId = :owner and x.category = :category "
                    + "and x.id <> :id and x.selectedDate >= :start and x.selectedDate < :end", Long.class)
                    .setParameter("owner", n.getUserId()).setParameter("category", NotificationCategory.C).setParameter("id", n.getId())
                    .setParameter("start", date.withDayOfMonth(1)).setParameter("end", date.withDayOfMonth(1).plusMonths(1)).getSingleResult();
            if (used >= 2) return false;
            if (d.containsKey("recordId")) {
                return rows.stream().anyMatch(r -> r.id().toString().equals(d.get("recordId")) && r.type().equals(d.get("recordType")));
            }
            if (kind == NotificationKind.NO_DELEGATE) return idle == 30 && !p.hasDelegate();
            if (kind == NotificationKind.RECORD_COUNT || kind == NotificationKind.UNDELIVERED) return idle == 14;
            if (kind == NotificationKind.ANNIVERSARY) {
                var a = entityManager.find(NotificationAnniversary.class, Long.valueOf(d.get("anniversaryId")));
                var receiver = entityManager.find(com.afternote.domain.receiver.model.Receiver.class, Long.valueOf(d.get("receiverId")));
                return a != null && a.getUserId().equals(n.getUserId()) && receiver != null
                        && receiver.getUserId().equals(n.getUserId()) && MonthDay.of(a.getMonth(), a.getDay()).atYear(date.getYear()).equals(date);
            }
            return true;
        } catch (java.io.IOException | RuntimeException ex) { return false; }
    }
}
