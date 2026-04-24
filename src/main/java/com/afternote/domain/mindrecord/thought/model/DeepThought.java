package com.afternote.domain.mindrecord.thought.model;

import com.afternote.domain.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "deep_thought")
@Getter
@NoArgsConstructor
public class DeepThought {

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

    // 깊은 생각 카테고리
    @Column(length = 50, nullable = false)
    private String category;

    @Column(length = 10)
    private String emotion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static DeepThought create(
            User user,
            String title,
            String content,
            Boolean isDraft,
            String imageUrl,
            String category,
            String emotion
    ) {
        DeepThought record = new DeepThought();
        record.user = user;
        record.title = title;
        record.content = content;
        record.isDraft = isDraft;
        record.imageUrl = imageUrl;
        record.category = category;
        record.emotion = emotion;
        return record;
    }
}