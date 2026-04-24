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

    @Column(length = 10)
    private String emotion;

    public static Diary create(User user, String title, String content, Boolean isDraft, String imageUrl, String emotion) {
        Diary diary = new Diary();
        diary.user = user;
        diary.title = title;
        diary.content = content;
        diary.isDraft = isDraft;
        diary.imageUrl = imageUrl;
        diary.emotion = emotion;
        return diary;
    }
}
