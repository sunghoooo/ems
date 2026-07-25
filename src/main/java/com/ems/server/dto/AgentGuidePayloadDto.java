package com.ems.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 분석 서버가 보내는 가이드 envelope.
 *
 * 사용하는 필드는 guide.risk_points[] 뿐이고, 종합 정보(summary, operator_message,
 * recommended_actions 등)는 무시한다. "각 시점별 해설만" 정책.
 * risk_points 에는 정상(NORMAL) 시점의 해설도 포함되며, 정상/위험은 riskLevel 로 구분한다.
 */
@Getter
@Setter
public class AgentGuidePayloadDto {
    private String type;       // "agent_guide"

    @JsonProperty("llm_model")
    private String llmModel;

    @JsonProperty("created_at")
    private String createdAt;

    private GuideContentDto guide;
}
