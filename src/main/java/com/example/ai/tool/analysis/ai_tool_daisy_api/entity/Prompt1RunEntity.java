package com.example.ai.tool.analysis.ai_tool_daisy_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One background run of Prompt 1 (experiment flow, no File Search), with everything needed to
 * trace and compare it: the settings and document versions it used, the OpenAI response IDs,
 * token usage and cost. The result JSON is stored only when the run is {@code VALID}.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "prompt1_run", indexes = {
        @Index(name = "idx_prompt1_run_created_at", columnList = "created_at"),
        @Index(name = "idx_prompt1_run_client_id", columnList = "client_id"),
        @Index(name = "idx_prompt1_run_professional_name", columnList = "professional_name")})
public class Prompt1RunEntity {

    public enum Status { QUEUED, RUNNING, VALID, FAILED, INTERRUPTED }

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** {@code two-call} (Steps 0–5, 6–14) or {@code turns} (Jan's turn-based route); null on older runs = two-call. */
    @Column(name = "flow", length = 16)
    private String flow;

    @Column(name = "model")
    private String model;

    @Column(name = "reasoning_effort")
    private String reasoningEffort;

    @Column(name = "package_profile")
    private String packageProfile;

    /** The release of the release store the run used; null when it ran on local files. */
    @Column(name = "release_id", length = 36)
    private String releaseId;

    /** The release name, e.g. P1-v0.136. */
    @Column(name = "release_name", length = 64)
    private String releaseName;

    @Column(name = "system_message_file")
    private String systemMessageFile;

    @Column(name = "system_message_hash", length = 64)
    private String systemMessageHash;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "schema_hash", length = 64)
    private String schemaHash;

    /** Document code → version, pinned when the run was submitted (JSON). */
    @Column(name = "document_versions", columnDefinition = "text")
    private String documentVersions;

    /** Intake file names in upload order (JSON); the file contents are never stored. */
    @Column(name = "intake_file_names", columnDefinition = "text")
    private String intakeFileNames;

    /** SHA-256 over the intake file names and contents, to recognise identical input. */
    @Column(name = "intake_hash", length = 64)
    private String intakeHash;

    @Column(name = "primary_ie_ref", length = 16)
    private String primaryIeRef;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "cached_input_tokens")
    private Long cachedInputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "estimated_cost_usd")
    private Double estimatedCostUsd;

    /** Per-call usage (JSON). */
    @Column(name = "calls", columnDefinition = "text")
    private String calls;

    /** Notes from the run (JSON list). */
    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /** The full control JSON (output_controle.json); only for VALID runs. */
    @Column(name = "result", columnDefinition = "text")
    private String result;

    /** The production JSON (output.json, schema production v1); only for VALID runs, since backend v07. */
    @Column(name = "production_result", columnDefinition = "text")
    private String productionResult;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    /** The portal client of a run started for a client (POST /analyze/prompt1/runs); null for experiment runs. */
    @Column(name = "client_id", length = 64)
    private String clientId;

    /** The client's name, as the apps look up the result in prompt1_results. */
    @Column(name = "client_name")
    private String clientName;

    @Column(name = "professional_id", length = 64)
    private String professionalId;

    @Column(name = "professional_name")
    private String professionalName;

    /** The phase the run is in (A1, A2, A3, B, C, D) or the last one it reached. */
    @Column(name = "current_turn", length = 8)
    private String currentTurn;
}
