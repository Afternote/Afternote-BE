package com.afternote.domain.receiver.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_receiver")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserReceiver extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @Builder
    public UserReceiver(User user, Receiver receiver) {
        this.user = user;
        this.receiver = receiver;
    }
}
