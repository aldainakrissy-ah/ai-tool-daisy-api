package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.Data;

import java.util.List;

@Data
public class QuestionnaireDto {
    private String id;
    private LocalizedTextDto title;
    private LocalizedTextDto description;
    private List<SectionDto> sections;
    private Boolean isActive;
    private Integer version;
}