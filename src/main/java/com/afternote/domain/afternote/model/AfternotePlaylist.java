package com.afternote.domain.afternote.model;

import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "afternote_playlist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AfternotePlaylist extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "afternote_id", nullable = false, unique = true)
    private Afternote afternote;
    
    @Column(nullable = false, length = 100)
    private String title;
    
    @Column(length = 500)
    private String atmosphere;
    
    @Column(name = "memorial_photo_url", length = 1000)
    private String memorialPhotoUrl;
    
    @Embedded
    private MemorialVideo memorialVideo;
    
    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AfternotePlaylistItem> items = new ArrayList<>();
    
    /**
     * PATCH 업데이트: null이 아닌 필드만 업데이트
     */
    public void update(String atmosphere, String memorialPhotoUrl, MemorialVideo memorialVideo) {
        if (atmosphere != null) {
            this.atmosphere = atmosphere;
        }
        if (memorialPhotoUrl != null) {
            this.memorialPhotoUrl = memorialPhotoUrl;
        }
        if (memorialVideo != null) {
            this.memorialVideo = memorialVideo;
        }
    }
    
    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Builder
    public static class MemorialVideo {
        
        @Column(name = "video_url", length = 1000)
        private String videoUrl;
        
        @Column(name = "thumbnail_url", length = 1000)
        private String thumbnailUrl;
    }
}
