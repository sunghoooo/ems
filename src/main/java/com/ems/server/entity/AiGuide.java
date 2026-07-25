package com.ems.server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 분석 서버 가이드의 risk_points 배열을 풀어서 각 시점별로 저장한 row.
 *
 * 종합 정보(summary, operator_message, recommended_actions 등)는 저장하지 않는다 —
 * "15분 간격 데이터마다 그 시점의 해설만" 보여주는 정책.
 *
 * PK 를 PowerRecord.id 와 공유한다 (@MapsId).
 * → ai_guide.id = power_record.id (같은 번호 매칭)
 *
 * 정상(NORMAL) 시점도 해설이 저장되며, 정상/위험 구분은 riskLevel 필드로 한다.
 * (매칭 실패 등으로 해당 시점의 가이드가 없을 때만, 프론트가
 *  "정상 운영 — 별도 위험 신호 없음" 메시지를 fallback 으로 표시한다.)
 */
@Entity
@Table(name = "ai_guide")
@Getter
@Setter
public class AiGuide {

    @Id
    private Long id;   // = PowerRecord.id

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private PowerRecord record;

    // --- 이 시점의 해설 (가이드 risk_points 한 건) ---
    private String riskLevel;     // "NORMAL" / "WATCH" / "WARN" / "CRITICAL"

    @Column(columnDefinition = "TEXT")
    private String reason;        // "예측 전력량의 비정상적인 스파이크"

    @Column(columnDefinition = "TEXT")
    private String evidence;      // "예측 전력량 158.90 kW (...)"

    @Column(columnDefinition = "TEXT")
    private String action;        // "비핵심 설비 가동 즉시 중단 ..."

    // --- 추적용 메타 (생략 가능) ---
    private String llmModel;      // "gemini-2.5-flash"
    private String createdAt;     // 가이드 envelope 의 created_at
    private LocalDateTime receivedAt;

    @PrePersist
    public void prePersist() {
        this.receivedAt = LocalDateTime.now();
    }
}
