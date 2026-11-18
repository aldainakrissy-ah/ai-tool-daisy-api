package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult.CallUsage;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A background Prompt 1 run as returned by the run endpoints. {@code result} is filled only by
 * the single-run endpoint, and only for VALID runs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prompt1RunDto {
    private String runId;
    /** QUEUED, RUNNING, VALID, FAILED or INTERRUPTED. */
    private String status;
    /** two-call or turns. */
    private String flow;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String model;
    private String reasoningEffort;
    private String packageProfile;
    /** The release of the release store the run used, e.g. P1-v0.136; null when it ran on local files. */
    private String releaseName;
    private String systemMessageFile;
    private String systemMessageHash;
    private String schemaName;
    private String schemaHash;
    @Builder.Default
    private Map<String, String> documentVersions = new LinkedHashMap<>();
    @Builder.Default
    private List<String> intakeFiles = new ArrayList<>();
    private String primaryIeRef;
    private Long inputTokens;
    private Long cachedInputTokens;
    private Long outputTokens;
    private Double estimatedCostUsd;
    @Builder.Default
    private List<CallUsage> calls = new ArrayList<>();
    @Builder.Default
    private List<String> notes = new ArrayList<>();
    private String error;
    /** The phase the run is in (A1, A2, A3, B, C, D) or the last one it reached. */
    private String currentTurn;
    /** Set for runs started for a client (POST /analyze/prompt1/runs). */
    private String clientId;
    private String clientName;
    private String professionalId;
    private String professionalName;
    /** The full control JSON (output_controle.json). */
    private JsonNode result;
    /** The production JSON (output.json, schema production v1). */
    private JsonNode productionResult;
}
