package com.ems.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RiskPointDto {
    private String timestamp;

    @JsonProperty("risk_level")
    private String riskLevel;

    private String reason;
    private String evidence;

    /** 새 가이드 형식의 권장 조치 — 각 시점별 단일 action. */
    private String action;
}
