package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireDto {
    private String id;
    private LocalizedTextDto title;
    private LocalizedTextDto description;
    private LocalizedTextDto instructions;
    private List<SectionDto> sections = new ArrayList<>();
    private Boolean isActive = true;
    private Integer version = 1;
}