package com.afternote.domain.notification.engagement;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Entity
@Getter
@NoArgsConstructor
@Table(name = "notification_anniversary", indexes = @Index(name = "idx_anniversary_owner", columnList = "user_id"))
public class NotificationAnniversary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id") private Long userId;
    @Column(name = "receiver_id") private Long receiverId;
    @Column(name = "anniversary_month") private int month;
    @Column(name = "anniversary_day") private int day;
    public NotificationAnniversary(Long owner, Long receiver, int month, int day) {
        userId = owner; receiverId = receiver; this.month = month; this.day = day;
    }
}
