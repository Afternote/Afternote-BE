package com.afternote.domain.user.model;

import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "user_providers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"})
)
@Getter
@NoArgsConstructor
public class UserProvider extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id")
    private String providerId;

    @Builder
    public UserProvider(User user, AuthProvider provider, String providerId) {
        this.user = user;
        this.provider = provider;
        this.providerId = providerId;
    }

    public void updateProviderId(String providerId) {
        if (providerId != null && !providerId.isBlank()) {
            this.providerId = providerId;
        }
    }
}
