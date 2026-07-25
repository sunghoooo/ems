package com.ems.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 분석 서버가 매 15분마다 보내는 단일 record 페이로드.
 *
 *  {
 *    "type": "record",
 *    "timestamp": "2021-09-14 14:30:00",
 *    "current_power": 87.0,
 *    "predicted_next_15min": 91.08,
 *    "actual_next_15min": null,
 *    "error": 0.0,
 *    "abs_error": 0.0,
 *    "predicted_next_1hour": [91.08, 90.35, 90.19, 90.20],
 *    "context": { ... },
 *    "risk": { ... },
 *    "anomaly": false,
 *    "status": "normal"
 *  }
 */
@Getter
@Setter
public class RecordPayloadDto {
    private String type;
    private String timestamp;

    @JsonProperty("current_power")
    private Double currentPower;

    @JsonProperty("predicted_next_15min")
    private Double predictedNext15min;

    @JsonProperty("actual_next_15min")
    private Double actualNext15min;

    private Double error;

    @JsonProperty("abs_error")
    private Double absError;

    @JsonProperty("predicted_next_1hour")
    private List<Double> predictedNext1hour;

    private ContextDto context;
    private RiskDto risk;

    private Boolean anomaly;
    private String status;
}
