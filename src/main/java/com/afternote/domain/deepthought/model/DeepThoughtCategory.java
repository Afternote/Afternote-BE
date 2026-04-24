package com.afternote.domain.deepthought.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "deep_thought_category")
@Getter
@NoArgsConstructor
public class DeepThoughtCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deepthought_category_id")
    private Long id;

    @Column(nullable = false, length = 10)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deep_thought_id", nullable = false)
    private DeepThought deepThought;

    public static DeepThoughtCategory create(DeepThought deepThought, String title) {
        DeepThoughtCategory category = new DeepThoughtCategory();
        category.deepThought = deepThought;
        category.title = title;
        return category;
    }
}
