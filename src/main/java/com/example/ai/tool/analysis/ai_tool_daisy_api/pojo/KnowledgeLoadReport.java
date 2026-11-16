package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of loading reference documents into the knowledge tables.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeLoadReport {
    private String sourceFile;
    private String sourceBuild;
    @Builder.Default
    private List<DocumentResult> documents = new ArrayList<>();

    /** True when every document loaded (or was unchanged) and no expected key is missing. */
    public boolean isComplete() {
        return documents.stream().allMatch(d ->
                (d.getStatus() == Status.LOADED || d.getStatus() == Status.UNCHANGED)
                        && d.getMissingKeys().isEmpty());
    }

    public enum Status { LOADED, UNCHANGED, CONFLICT }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentResult {
        private String documentCode;
        private String version;
        private String fileName;
        private Status status;
        private int sectionCount;
        private String message;
        @Builder.Default
        private List<String> missingKeys = new ArrayList<>();
        @Builder.Default
        private List<String> duplicateKeys = new ArrayList<>();
    }
}
