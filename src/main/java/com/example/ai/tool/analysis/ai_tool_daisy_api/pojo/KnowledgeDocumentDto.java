package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentDto {
    private String documentCode;
    private String version;
    private String fileName;
    private String sourceHash;
    private String sourceBuild;
    private int sectionCount;
    private boolean active;
    private LocalDateTime loadedAt;
}
