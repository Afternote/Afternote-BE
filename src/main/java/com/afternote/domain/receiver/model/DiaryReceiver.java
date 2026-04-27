package com.afternote.domain.receiver.model;

import com.afternote.domain.diary.model.Diary;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "diary_receiver", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"diary_id", "receiver_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryReceiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id", nullable = false)
    private Diary diary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public DiaryReceiver(Diary diary, Receiver receiver) {
        this.diary = diary;
        this.receiver = receiver;
    }
}