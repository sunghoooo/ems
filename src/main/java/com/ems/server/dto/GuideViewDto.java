package com.ems.server.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사이드바/메인 박스 모달용 — 한 시점의 해설 정보(riskLevel 포함).
 * 해당 시점의 가이드가 없을 때만 백엔드가 404 를 응답한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class GuideViewDto {
    private Long id;
    private String recordTimestamp;
    private String riskLevel;
    private String reason;
    private String evidence;
    private String action;
    private String llmModel;
    private String createdAt;
}
