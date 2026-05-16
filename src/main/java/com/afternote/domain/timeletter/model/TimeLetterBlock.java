package com.afternote.domain.timeletter.model;

import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "time_letter_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeLetterBlock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_letter_id", nullable = false)
    private TimeLetter timeLetter;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false)
    private TimeLetterBlockType blockType;

    @Column(name = "block_order", nullable = false)
    private Integer blockOrder;

    @Lob
    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Builder
    public TimeLetterBlock(
            TimeLetter timeLetter,
            TimeLetterBlockType blockType,
            Integer blockOrder,
            String textContent,
            String url,
            String mimeType
    ) {
        this.timeLetter = timeLetter;
        this.blockType = blockType;
        this.blockOrder = blockOrder;
        this.textContent = textContent;
        this.url = url;
        this.mimeType = mimeType;
    }

}