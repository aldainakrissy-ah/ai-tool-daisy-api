package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class LocalizedText {
    private String en;
    private String nl;
}

