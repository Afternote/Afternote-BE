package com.afternote.domain.receiver.model;

import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "time_letter_receiver")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeLetterReceiver extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_letter_id", nullable = false)
    private TimeLetter timeLetter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @Column(name = "delivered_at", nullable = false)
    private LocalDateTime deliveredAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public TimeLetterReceiver(TimeLetter timeLetter, Receiver receiver, LocalDateTime deliveredAt) {
        this.timeLetter = timeLetter;
        this.receiver = receiver;
        this.deliveredAt = deliveredAt;
    }

    /**
     * 읽음 처리 (멱등성 보장: readAt이 null일 때만 설정)
     */
    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}
