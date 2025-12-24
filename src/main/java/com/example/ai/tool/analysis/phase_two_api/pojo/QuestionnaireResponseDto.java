package com.example.ai.tool.analysis.phase_two_api.pojo;

import com.example.ai.tool.analysis.phase_two_api.entity.QuestionnaireResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireResponseDto {
    private Long id;
    private String questionnaireId;
    private String userId;
    private String clientId;
    private String sessionId;
    private QuestionnaireResponse.ResponseStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String languageCode;
    private List<QuestionResponseDto> questionResponses;
}
