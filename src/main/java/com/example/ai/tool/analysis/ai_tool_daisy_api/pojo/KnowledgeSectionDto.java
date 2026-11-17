package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A reference document section as delivered to a Prompt 1 stage or to the Playground.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSectionDto {
    private String sectionKey;
    private String heading;
    private String documentCode;
    private String documentVersion;
    private int seq;
    private String content;
    private String contentHash;
}
