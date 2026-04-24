package com.afternote.domain.diary.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "diary")
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
    @Column(name = "today_mood", length = 20)
    private TodayMood todayMood;

    public static Diary create(User user, String title, String content, Boolean isDraft, String imageUrl, TodayMood todayMood) {
        Diary diary = new Diary();
        diary.user = user;
        diary.title = title;
        diary.content = content;
        diary.isDraft = isDraft;
        diary.imageUrl = imageUrl;
        diary.todayMood = todayMood;
        return diary;
    }

    public void update(String title, String content, Boolean isDraft, String imageUrl, TodayMood todayMood) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (isDraft != null) {
            this.isDraft = isDraft;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        if (todayMood != null) {
            this.todayMood = todayMood;
        }
    }
}
