package com.ems.server.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 과거 기록 사이드바 + 모달 상세보기 용 DTO.
 *
 *  - 사이드바 카드에는 id, currentPower, riskLevel, hasGuide 만 표시
 *  - 카드 클릭 시 모달에 timestamp + context 전체(temp/humidity/production/workers/quarter/...) 표시
 *  → 한 번 fetch로 모달에 필요한 데이터까지 다 받아두는 게 단순
 */
@Getter
@Setter
@NoArgsConstructor
public class RecordSummaryDto {
    private Long id;
    private String timestamp;
    private Double currentPower;

    // context (모달용)
    private Double temp;
    private Double humidity;
    private Double production;
    private Double workers;
    private Integer quarter;
    private Boolean isWeekend;
    private Boolean isHoliday;

    // 위험도
    private String riskLevel;          // 가이드 기준 최종 위험도
    private String recordRiskLevel;    // record 자체 위험도 (참고)
    private boolean hasGuide;
}
