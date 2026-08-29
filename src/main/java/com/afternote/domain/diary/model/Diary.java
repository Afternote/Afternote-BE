package com.afternote.domain.diary.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "diary",
        indexes = @Index(name = "idx_diary_user_entry_date", columnList = "user_id, entry_date")
)
@Getter
@NoArgsConstructor
public class Diary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "today_mood", length = 20, nullable = true)
    private TodayMood todayMood;

    /**
     * 사용자가 고른 기록일. 작성 시각({@code createdAt})과 다를 수 있다.
     * columnDefinition 을 NOT NULL 없이 두어 ddl-auto 가 기존 행에 컬럼을 붙일 수 있게 하고,
     * NOT NULL 은 {@code MysqlSchemaCompatibilityMigrator} 가 created_at 백필 뒤에 적용한다.
     */
    @Column(name = "entry_date", nullable = false, columnDefinition = "date")
    private LocalDate entryDate;

    public static Diary create(
            User user,
            String title,
            String content,
            Boolean isDraft,
            TodayMood todayMood,
            LocalDate entryDate
    ) {
        Diary diary = new Diary();
        diary.user = user;
        diary.title = title;
        diary.content = content;
        diary.isDraft = isDraft;
        diary.todayMood = todayMood;
        diary.entryDate = entryDate;
        return diary;
    }

    public void update(String title, String content, Boolean isDraft, TodayMood todayMood, LocalDate entryDate) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (isDraft != null) {
            this.isDraft = isDraft;
        }
        if (todayMood != null) {
            this.todayMood = todayMood;
        }
        if (entryDate != null) {
            this.entryDate = entryDate;
        }
    }
}
