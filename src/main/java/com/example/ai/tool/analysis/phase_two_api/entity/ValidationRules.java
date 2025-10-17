package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRules {
    @Column(name = "validation_min", nullable = true)
    private Integer min;

    @Column(name = "validation_max", nullable = true)
    private Integer max;

    @Column(name = "validation_required", nullable = true)
    private Boolean required;

    @Column(name = "validation_pattern", columnDefinition = "text", nullable = true)
    private String pattern;

    @Column(name = "validation_min_length", nullable = true)
    private Integer minLength;

    @Column(name = "validation_max_length", nullable = true)
    private Integer maxLength;

    @Column(name = "validation_min_date", nullable = true)
    private String minDate;

    @Column(name = "validation_max_date", nullable = true)
    private String maxDate;
}
