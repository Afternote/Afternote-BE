package com.afternote.domain.deepthought.model;

import com.afternote.domain.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "deep_thought_category",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "title"})
        }
)
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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static DeepThoughtCategory create(User user, String title) {
        DeepThoughtCategory category = new DeepThoughtCategory();
        category.user = user;
        category.title = title;
        return category;
    }

    public void updateTitle(String title) {
        this.title = title;
    }
}
