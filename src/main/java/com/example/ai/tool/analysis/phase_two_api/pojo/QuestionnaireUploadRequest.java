package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireUploadRequest {
    private String questionnaireId;
    private String userId;
    private String languageCode;
}
