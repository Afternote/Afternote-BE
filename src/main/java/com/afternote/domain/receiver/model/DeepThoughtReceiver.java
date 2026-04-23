package com.afternote.domain.receiver.model;

import com.afternote.domain.mindrecord.thought.model.DeepThought;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "deep_thought_receiver", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"deep_thought_id", "receiver_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeepThoughtReceiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deep_thought_id", nullable = false)
    private DeepThought deepThought;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public DeepThoughtReceiver(DeepThought deepThought, Receiver receiver) {
        this.deepThought = deepThought;
        this.receiver = receiver;
    }
}