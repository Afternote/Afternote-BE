package com.afternote.domain.receiver.model;

import com.afternote.domain.afternote.model.AfternoteReceiver;
import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "receiver",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_receiver_owner_accepted_user",
                columnNames = {"user_id", "accepted_user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Receiver extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 초대 수락으로 등록된 회원 ID. 기존 수동 등록 수신자는 null이다. */
    @Column(name = "accepted_user_id")
    private Long acceptedUserId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String relation;

    @Column(length = 20)
    private String phone;

    @Column(length = 50)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "auth_code", unique = true, length = 36)
    private String authCode;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @OneToMany(mappedBy = "receiver", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AfternoteReceiver> afternoteReceivers = new ArrayList<>();

    @Builder
    public Receiver(String name, String relation, String phone, String email, String message, Long userId) {
        this.name = name;
        this.relation = relation;
        this.phone = phone;
        this.email = email;
        this.message = message;
        this.userId = userId;
        this.sortOrder = 0;
        this.authCode = UUID.randomUUID().toString();
    }

    public static Receiver fromAcceptedInvitation(User user, Long inviterUserId) {
        Receiver receiver = new Receiver(
                user.getName(),
                null,
                user.getPhone(),
                user.getEmail(),
                null,
                inviterUserId
        );
        receiver.acceptedUserId = user.getId();
        return receiver;
    }

    public void updateMessage(String message) {
        this.message = message;
    }

    public void updateRelation(String relation) {
        this.relation = relation;
    }

    public void updateInfo(String name, String relation, String phone, String email) {
        this.name = name;
        this.relation = relation;
        this.phone = phone;
        this.email = email;
    }
}
