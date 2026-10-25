package com.afternote.domain.notification.model;

import com.afternote.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "push_installations", uniqueConstraints = @UniqueConstraint(
        name = "uk_push_installation_user", columnNames = {"user_id", "installation_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushInstallation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "installation_id", nullable = false, length = 36)
    private String installationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationPermission permission;

    public PushInstallation(User user, String installationId, NotificationPermission permission) {
        this.user = user;
        this.installationId = installationId;
        this.permission = permission;
    }
    public void updatePermission(NotificationPermission permission) { this.permission = permission; }
}
