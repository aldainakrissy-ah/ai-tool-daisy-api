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
public class LocalizedText {
    @Column(columnDefinition = "text", nullable = true)
    private String en;

    @Column(columnDefinition = "text", nullable = true)
    private String nl;
}
