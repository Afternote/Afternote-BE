package com.afternote.domain.afternote.model;

import com.afternote.domain.afternote.dto.LeaveMessageBlock;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "afternote")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Afternote extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 작성자 (User FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private com.afternote.domain.user.model.User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, length = 20)
    private AfternoteCategoryType categoryType;

    
    @Column(nullable = false, length = 100)
    private String title;

    @ColumnDefault("false")
    @Column(name = "is_draft", nullable = false)
    @Builder.Default
    private Boolean isDraft = false;
    
    @Convert(converter = LeaveMessageListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<LeaveMessageBlock> leaveMessage;

    @Column(name = "sort_order")
    private Integer sortOrder;
    
    @OneToMany(mappedBy = "afternote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AfternoteReceiver> receivers = new ArrayList<>();
    
    @OneToMany(mappedBy = "afternote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AfternoteSecureContent> secureContents = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "afternote_actions", joinColumns = @JoinColumn(name = "afternote_id"))
    @Column(name = "action_name")
    @Builder.Default
    private List<String> actions = new ArrayList<>();
    
    @OneToOne(mappedBy = "afternote", cascade = CascadeType.ALL, orphanRemoval = true)
    private AfternotePlaylist playlist;

    // 업데이트 메서드
    public void update(String title, Integer sortOrder, List<LeaveMessageBlock> leaveMessage, List<String> actions, Boolean isDraft) {
        this.title = title;
        this.sortOrder = sortOrder;
        this.leaveMessage = leaveMessage;
        this.actions.clear();
        if (actions != null) {
            this.actions.addAll(actions);
        }
        if (isDraft != null) {
            this.isDraft = isDraft;
        }
    }


}
