package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveQuestionnaireResponseRequest {
    private String clientId;
    private String questionnaireId;
    private List<QuestionResponseDto> responses;
}
