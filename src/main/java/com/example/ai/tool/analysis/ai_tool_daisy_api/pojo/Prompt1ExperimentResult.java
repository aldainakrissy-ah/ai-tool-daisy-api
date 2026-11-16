package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of a Prompt 1 experiment run (Jan's turn-based route): settings, token usage per
 * OpenAI call and in total, notes, and the final JSON result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prompt1ExperimentResult {
    private String model;
    private String reasoningEffort;
    /** The route and release, e.g. "TURNS v0.136, preset standard". */
    private String packageProfile;
    private String systemMessageFile;
    /** SHA-256 of the system message text and of the schema file this run used. */
    private String systemMessageHash;
    private String schemaName;
    private String schemaHash;
    /** Document code → version the run read its source keys from. */
    @Builder.Default
    private Map<String, String> documentVersions = new LinkedHashMap<>();
    private String primaryIeRef;
    @Builder.Default
    private List<CallUsage> calls = new ArrayList<>();
    private CallUsage total;
    /** What happened when it did not run to Step 14, or how a setting was handled. */
    @Builder.Default
    private List<String> notes = new ArrayList<>();
    /** Set when the run stopped: the turn and the reason. */
    private String error;
    /** The final JSON (output.json), assembled by the backend. */
    private JsonNode result;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallUsage {
        private String call;
        private String responseId;
        private String status;
        private String runStatus;
        private Integer completedThroughStep;
        private long durationMs;
        private long inputTokens;
        private long cachedInputTokens;
        /** Reported by OpenAI for models with prompt-cache writes; null when not reported. */
        private Long cacheWriteTokens;
        private long outputTokens;
        private long reasoningTokens;
        /** From the configured prices; uncached input, cached input, cache writes and output priced separately. */
        private Double estimatedCostUsd;
    }
}
