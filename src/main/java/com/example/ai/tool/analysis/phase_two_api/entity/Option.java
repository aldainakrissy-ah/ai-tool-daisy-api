package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.Data;

@Data
@Embeddable
public class Option {
    private String value;
    @Embedded
    private LocalizedText label;
}

