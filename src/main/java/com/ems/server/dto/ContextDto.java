package com.ems.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContextDto {
    private Double temp;
    private Double humidity;
    private Double production;
    private Double workers;
    private Integer quarter;

    @JsonProperty("is_weekend")
    private Boolean isWeekend;

    @JsonProperty("is_holiday")
    private Boolean isHoliday;
}
