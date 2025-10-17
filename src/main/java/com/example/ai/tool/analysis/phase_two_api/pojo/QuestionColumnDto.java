package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionColumnDto {
    private String value;
    private LocalizedTextDto label;
    private Integer sortOrder;
}
