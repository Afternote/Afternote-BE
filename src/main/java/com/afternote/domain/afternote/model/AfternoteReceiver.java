package com.afternote.domain.afternote.model;

import com.afternote.domain.receiver.model.Receiver;
import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "afternote_receiver")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AfternoteReceiver extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "afternote_id", nullable = false)
    private Afternote afternote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;
}
