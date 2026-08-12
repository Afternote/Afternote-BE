package com.afternote.domain.timeletter.model;

import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "time_letter_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeLetterMedia extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_letter_id", nullable = false)
    private TimeLetter timeLetter;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(name = "media_url", nullable = false, length = 1000)
    private String mediaUrl;

    @Builder
    public TimeLetterMedia(TimeLetter timeLetter, MediaType mediaType, String mediaUrl) {
        this.timeLetter = timeLetter;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
    }
}
