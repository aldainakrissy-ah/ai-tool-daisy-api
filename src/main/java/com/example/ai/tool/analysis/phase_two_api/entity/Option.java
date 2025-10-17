package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class Option {
    private String value;
    private Integer sortOrder;

    @Embedded
    private LocalizedText label;
}
