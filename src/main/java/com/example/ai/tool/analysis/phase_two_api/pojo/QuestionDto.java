package com.example.ai.tool.analysis.phase_two_api.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionDto {
    private String id;
    private String type;
    private LocalizedTextDto text;
    private Boolean required = false;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<OptionDto> options = new ArrayList<>();
}
