package com.afternote.domain.mindrecord.diary.model;

import com.afternote.domain.mindrecord.model.MindRecord;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "diary")
@Getter
@NoArgsConstructor
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 공통 기록
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mind_record_id", nullable = false, unique = true)
    private MindRecord mindRecord;

    public static Diary create(MindRecord mindRecord) {
        Diary record = new Diary();
        record.mindRecord = mindRecord;
        return record;
    }

}