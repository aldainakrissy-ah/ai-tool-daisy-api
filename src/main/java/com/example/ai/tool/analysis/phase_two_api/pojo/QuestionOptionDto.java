package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOptionDto {
    private String value;
    private Map<String, String> label;
}
