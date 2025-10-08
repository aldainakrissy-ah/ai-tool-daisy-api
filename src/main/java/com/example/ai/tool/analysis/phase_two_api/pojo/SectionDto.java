package com.example.ai.tool.analysis.phase_two_api.pojo;

import lombok.Data;
import java.util.List;

@Data
public class SectionDto {
    private String id;
    private LocalizedTextDto title;
    private List<QuestionDto> questions;
}

