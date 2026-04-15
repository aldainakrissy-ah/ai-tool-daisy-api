package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectionDto {
    private String id;
    private LocalizedTextDto title;
    private LocalizedTextDto description;
    private List<QuestionDto> questions = new ArrayList<>();
    private Integer sortOrder;
}
