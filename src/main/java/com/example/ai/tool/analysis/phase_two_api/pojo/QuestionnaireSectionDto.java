package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSectionDto {
    private Long id;
    private String sectionId;
    private String title;
    private Map<String, String> titleTranslations;
    private Integer sortOrder;
    private List<QuestionDto> questions;
}
