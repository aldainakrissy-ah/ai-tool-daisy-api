package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireDto {
    private Long id;
    private String questionnaireId;
    private Map<String, String> title;       // Language map (en -> title, nl -> title)
    private Map<String, String> description; // Language map (en -> desc, nl -> desc)
    private List<QuestionnaireSectionDto> sections;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isActive;
    private Integer version;
}