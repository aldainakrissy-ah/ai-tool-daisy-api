package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireDto {
    private Long id;
    private String questionnaireId;
    private String title;
    private String description;
    private Map<String, String> titleTranslations;
    private Map<String, String> descriptionTranslations;
    private List<QuestionnaireSectionDto> sections;
    private Boolean isActive;
    private Integer version;
}
