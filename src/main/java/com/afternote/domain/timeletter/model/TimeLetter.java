package com.afternote.domain.timeletter.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "time_letters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeLetter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 작성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 100)
    private String title;

    // 발송 예정 시간
    @Column(name = "send_at")
    private LocalDateTime sendAt;

    // 실제 발송 완료 시간
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeLetterStatus status = TimeLetterStatus.DRAFT;

    // 본문 블록 목록
    @OneToMany(mappedBy = "timeLetter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("blockOrder ASC")
    private final List<TimeLetterBlock> blocks = new ArrayList<>();

    @Builder
    public TimeLetter(User user, String title, LocalDateTime sendAt, TimeLetterStatus status) {
        this.user = user;
        this.title = title;
        this.sendAt = sendAt;
        this.status = status != null ? status : TimeLetterStatus.DRAFT;
    }

    public List<TimeLetterBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public void update(String title, LocalDateTime sendAt, TimeLetterStatus status) {
        validateModifiable();

        if (title != null) this.title = title;
        if (sendAt != null) this.sendAt = sendAt;
        if (status != null) this.status = status;
    }

    public void replaceBlocks(List<TimeLetterBlock> newBlocks) {
        validateModifiable();

        this.blocks.clear();

        if (newBlocks == null || newBlocks.isEmpty()) {
            return;
        }

        for (TimeLetterBlock block : newBlocks) {
            addBlock(block);
        }
    }

    private void addBlock(TimeLetterBlock block) {
        block.setTimeLetter(this);
        this.blocks.add(block);
    }

    public boolean isModifiable() {
        return this.status != TimeLetterStatus.SENT;
    }

    private void validateModifiable() {
        if (!isModifiable()) {
            throw new IllegalStateException("이미 발송된 타임레터는 수정할 수 없습니다.");
        }
    }

    public void markAsSent() {
        if (this.status == TimeLetterStatus.SENT) {
            return;
        }

        this.status = TimeLetterStatus.SENT;
        this.deliveredAt = LocalDateTime.now();
    }
}