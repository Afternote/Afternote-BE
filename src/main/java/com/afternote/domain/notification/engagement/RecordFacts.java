package com.afternote.domain.notification.engagement;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecordFacts {
    private final EntityManager entityManager;
    public record Written(String type, Long id, LocalDateTime at) { }
    public List<Written> all(Long userId) {
        List<Written> result = new ArrayList<>();
        add(result, "Diary", "DIARY", userId, "d.isDraft = false");
        add(result, "DeepThought", "DEEP_THOUGHT", userId, "d.isDraft = false");
        add(result, "UserDailyQuestion", "DAILY_QUESTION", userId, "d.isDraft = false and d.isAnswered = true");
        result.sort(Comparator.comparing(Written::at).thenComparing(Written::type).thenComparing(Written::id));
        return result;
    }
    private void add(List<Written> rows, String entity, String type, Long owner, String condition) {
        for (Object[] row : entityManager.createQuery("select d.id, coalesce(d.publishedAt,d.createdAt) from " + entity
                + " d where d.user.id = :owner and " + condition, Object[].class).setParameter("owner", owner).getResultList()) {
            rows.add(new Written(type, (Long)row[0], (LocalDateTime)row[1]));
        }
    }
    public static long count(List<Written> rows, LocalDate from, LocalDate toExclusive) {
        return rows.stream().filter(r -> !r.at().toLocalDate().isBefore(from) && r.at().toLocalDate().isBefore(toExclusive)).count();
    }
    public static long idleDays(List<Written> rows, LocalDateTime joined, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(rows.isEmpty() ? joined.toLocalDate() : rows.get(rows.size()-1).at().toLocalDate(), today));
    }
    public static int streak(List<Written> rows, LocalDate weekStart, int goal) {
        int streak = 0;
        while (streak < 520) {
            LocalDate end = weekStart.minusWeeks(streak);
            if (count(rows, end.minusWeeks(1), end) < goal) break;
            streak++;
        }
        return streak;
    }
}
