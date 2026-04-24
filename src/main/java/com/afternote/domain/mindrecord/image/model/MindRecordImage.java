package com.afternote.domain.mindrecord.image.model;

import com.afternote.domain.mindrecord.model.MindRecord;
import com.afternote.domain.timeletter.model.MediaType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "mind_record_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MindRecordImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mind_record_id", nullable = false)
    private MindRecord mindRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MindRecordImage(MindRecord mindRecord, MediaType mediaType, String imageUrl) {
        this.mindRecord = mindRecord;
        this.mediaType = mediaType != null ? mediaType : MediaType.IMAGE;
        this.imageUrl = imageUrl;
    }
}
