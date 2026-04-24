package com.afternote.domain.user.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_providers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"})
)
@Getter
@NoArgsConstructor
public class UserProvider {

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

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

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
