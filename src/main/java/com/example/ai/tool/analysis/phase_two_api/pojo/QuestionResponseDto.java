package com.example.ai.tool.analysis.phase_two_api.pojo;

import jakarta.persistence.criteria.CriteriaBuilder.In;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponseDto {
    private Long id;
    private String questionId;
    private String questionText;
    private Integer questionOrder;
    private String answerText;
    private Double answerNumber;
    private Object answerJson;
    private Boolean answerBoolean;
}
