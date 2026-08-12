package com.afternote.domain.receiver.model;

import com.afternote.domain.diary.model.Diary;
import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "diary_receiver", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"diary_id", "receiver_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryReceiver extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id", nullable = false)
    private Diary diary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @Builder
    public DiaryReceiver(Diary diary, Receiver receiver) {
        this.diary = diary;
        this.receiver = receiver;
    }
}
