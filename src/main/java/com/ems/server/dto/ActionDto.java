package com.ems.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionDto {
    private String priority;
    private String action;

    @JsonProperty("expected_effect")
    private String expectedEffect;
}
