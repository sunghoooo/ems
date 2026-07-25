package com.ems.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RiskDto {
    private String level;
    private String reason;

    @JsonProperty("ratio_to_peak")
    private Double ratioToPeak;

    @JsonProperty("bill_peak_baseline")
    private Double billPeakBaseline;

    @JsonProperty("assess_level")
    private Double assessLevel;

    private String trigger;
}
