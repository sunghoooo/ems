package com.ems.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GuideContentDto {
    private String summary;

    @JsonProperty("overall_status")
    private String overallStatus;

    @JsonProperty("key_findings")
    private List<String> keyFindings;

    @JsonProperty("risk_points")
    private List<RiskPointDto> riskPoints;

    @JsonProperty("recommended_actions")
    private List<ActionDto> recommendedActions;

    @JsonProperty("operator_message")
    private String operatorMessage;
}
