package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRulesDto {
    private Integer min;
    private Integer max;
    private Boolean required;
    private String pattern;
    private Integer minLength;
    private Integer maxLength;
    private String minDate;
    private String maxDate;
}