package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    private String id;
    private String type;
    private LocalizedTextDto text;
    private List<OptionDto> options = new ArrayList<>();
    private List<QuestionColumnDto> columns = new ArrayList<>();
    private ValidationRulesDto validation;
    private Integer maxNames;
    private Boolean required = false;
    private Integer sortOrder;
}
