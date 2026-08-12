package com.afternote.domain.receiver.model;

import com.afternote.domain.deepthought.model.DeepThought;
import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "deep_thought_receiver", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"deep_thought_id", "receiver_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeepThoughtReceiver extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deep_thought_id", nullable = false)
    private DeepThought deepThought;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @Builder
    public DeepThoughtReceiver(DeepThought deepThought, Receiver receiver) {
        this.deepThought = deepThought;
        this.receiver = receiver;
    }
}
