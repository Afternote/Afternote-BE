package com.afternote.domain.deepthought.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "deep_thought_tag",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"deep_thought_id", "tag"})
        }
)
@Getter
@NoArgsConstructor
public class DeepThoughtTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deep_thought_id", nullable = false)
    private DeepThought deepThought;

    @Column(name = "tag", nullable = false, length = 30)
    private String title;

    public static DeepThoughtTag create(DeepThought deepThought, String title) {
        DeepThoughtTag tag = new DeepThoughtTag();
        tag.deepThought = deepThought;
        tag.title = title;
        return tag;
    }
}
