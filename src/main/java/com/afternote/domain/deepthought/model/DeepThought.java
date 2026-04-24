package com.afternote.domain.deepthought.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "deep_thought")
@Getter
@NoArgsConstructor
public class DeepThought extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deep_thought_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft;

    @Column(name = "image_url", length = 100)
    private String imageUrl;

    @Column(length = 10, nullable = false)
    private String emotion;

    @OneToMany(mappedBy = "deepThought", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeepThoughtCategory> categories = new ArrayList<>();

    public static DeepThought create(
            User user,
            String title,
            String content,
            Boolean isDraft,
            String imageUrl,
            String emotion
    ) {
        DeepThought record = new DeepThought();
        record.user = user;
        record.title = title;
        record.content = content;
        record.isDraft = isDraft;
        record.imageUrl = imageUrl;
        record.emotion = emotion;
        return record;
    }
}
