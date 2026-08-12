package com.afternote.domain.afternote.model;

import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "afternote_secure_content")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AfternoteSecureContent extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "afternote_id", nullable = false)
    private Afternote afternote;

    @Column(name = "key_name", nullable = false, length = 50)
    private String keyName;

    @Column(name = "encrypted_value", columnDefinition = "TEXT")
    private String encryptedValue;
}
