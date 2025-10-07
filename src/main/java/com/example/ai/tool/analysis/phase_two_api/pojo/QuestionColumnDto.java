package com.example.ai.tool.analysis.phase_two_api.pojo;

import com.example.ai.tool.analysis.phase_two_api.entity.QuestionColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionColumnDto {
    private Long id;
    private String columnId;
    private String label;
    private Map<String, String> labelTranslations;
    private QuestionColumn.ColumnType type;
    private Integer sortOrder;
}
