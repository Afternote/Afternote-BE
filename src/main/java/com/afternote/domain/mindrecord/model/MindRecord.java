package com.afternote.domain.mindrecord.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "mind_records")
@Getter
@NoArgsConstructor
public class MindRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 작성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 기록 타입 (DAILY_QUESTION / DIARY / DEEP_THOUGHT)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MindRecordType type;

    // 리스트, 캘린더에 노출되는 제목
    @Column(nullable = false, length = 100)
    private String title;

    // 기록 날짜 (캘린더 기준 날짜)
    @Column(nullable = false)
    private LocalDate recordDate;

    // 임시저장 여부
    @Column(nullable = false)
    private Boolean isDraft;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    public static MindRecord create(
            User user,
            MindRecordType type,
            String title,
            LocalDate recordDate,
            String content,
            boolean isDraft
    ) {
        MindRecord record = new MindRecord();
        record.user = user;
        record.type = type;
        record.title = title;
        record.recordDate = recordDate;
        record.content = content;
        record.isDraft = isDraft;
        return record;
    }

    public void updateCommon(
            String title,
            LocalDate recordDate,
            Boolean isDraft,
            String content
    ) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }

        if (recordDate != null) {
            this.recordDate = recordDate;
        }

        if (isDraft != null) {
            this.isDraft = isDraft;
        }
        if (content != null) {
            this.content = content;
        }
    }
}