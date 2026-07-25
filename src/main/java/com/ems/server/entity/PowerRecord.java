package com.ems.server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 15분 단위 전력 데이터 한 건. 매 15분마다 분석 서버에서 1건씩 전송된다.
 *
 * AI 가이드(ai_guide)는 이 엔티티의 id 와 같은 값을 PK 로 공유한다 (@MapsId).
 */
@Entity
@Table(name = "power_record",
        indexes = {
                @Index(name = "idx_power_record_timestamp", columnList = "record_timestamp")
        })
@Getter
@Setter
public class PowerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "2021-09-14 14:30:00" 형태. 가이드와의 매칭 키. */
    @Column(name = "record_timestamp")
    private String timestamp;

    // --- power ---
    private Double currentPower;
    private Double predictedNext15min;
    private Double actualNext15min;

    @Column(name = "error_value")
    private Double errorValue;

    private Double absError;

    /** predicted_next_1hour[0..3] (+15·+30·+45·+60). */
    private Double predicted15;
    private Double predicted30;
    private Double predicted45;
    private Double predicted60;

    // --- context ---
    private Double temp;
    private Double humidity;
    private Double production;
    private Double workers;
    private Integer quarter;
    private Boolean isWeekend;
    private Boolean isHoliday;

    // --- risk ---
    private String riskLevel;
    private String riskReason;
    private Double ratioToPeak;
    private Double billPeakBaseline;
    private Double assessLevel;

    @Column(name = "trigger_type")
    private String trigger;

    // --- top-level flags ---
    private Boolean anomaly;
    private String status;

    /** 백엔드 수신 시각. */
    private LocalDateTime receivedAt;

    @PrePersist
    public void prePersist() {
        this.receivedAt = LocalDateTime.now();
    }
}
