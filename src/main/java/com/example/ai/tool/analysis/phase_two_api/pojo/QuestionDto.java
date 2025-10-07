package com.example.ai.tool.analysis.phase_two_api.pojo;

import com.example.ai.tool.analysis.phase_two_api.entity.Question;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    private Long id;
    private String questionId;
    private String text;
    private Map<String, String> textTranslations;
    private Question.QuestionType type;
    private List<QuestionOptionDto> options;
    private Map<String, Object> validationRules;
    private Integer sortOrder;
    private Boolean isRequired;
    private List<QuestionColumnDto> columns;
}
