package com.afternote.domain.mindrecord.weekly.model;

import com.afternote.domain.user.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "weekly_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "keyword_json", nullable = false, columnDefinition = "json")
    private String keywordJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static WeeklyReport create(
            User user,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String summaryText,
            String keywordJson
    ) {
        WeeklyReport report = new WeeklyReport();
        report.user = user;
        report.startDate = startDate;
        report.endDate = endDate;
        report.summaryText = summaryText;
        report.keywordJson = keywordJson;
        return report;
    }
}
