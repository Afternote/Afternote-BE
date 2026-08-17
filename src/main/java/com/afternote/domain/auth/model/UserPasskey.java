package com.afternote.domain.auth.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "user_passkeys",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_passkeys_credential_id", columnNames = "credential_id"),
        indexes = @Index(name = "idx_user_passkeys_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPasskey extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "credential_id", nullable = false, columnDefinition = "VARBINARY(255)")
    private byte[] credentialId;

    @Lob
    @Column(name = "attested_credential_data", nullable = false)
    private byte[] attestedCredentialData;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(name = "uv_initialized")
    private Boolean uvInitialized;

    @Column(name = "backup_eligible")
    private Boolean backupEligible;

    @Column(name = "backup_state")
    private Boolean backupState;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Builder
    public UserPasskey(
            User user,
            byte[] credentialId,
            byte[] attestedCredentialData,
            long signCount,
            Boolean uvInitialized,
            Boolean backupEligible,
            Boolean backupState,
            String displayName
    ) {
        this.user = user;
        this.credentialId = credentialId;
        this.attestedCredentialData = attestedCredentialData;
        this.signCount = signCount;
        this.uvInitialized = uvInitialized;
        this.backupEligible = backupEligible;
        this.backupState = backupState;
        this.displayName = displayName;
    }

    public void updateSignCount(long signCount) {
        this.signCount = signCount;
    }
}
