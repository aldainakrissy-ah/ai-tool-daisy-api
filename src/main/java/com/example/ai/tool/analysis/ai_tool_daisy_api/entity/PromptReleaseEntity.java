package com.example.ai.tool.analysis.ai_tool_daisy_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One release of a prompt from the release store: its manifest, the result of its upload check and its
 * state. At most one release per prompt is ACTIVE; activating another retires it (rollback = activate an
 * older one). The files of a release are {@link PromptReleaseFileEntity} rows; its reference documents are
 * in the knowledge tables, pinned by the document versions in the manifest.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "prompt_release",
        uniqueConstraints = @UniqueConstraint(columnNames = {"prompt", "name"}),
        indexes = @Index(name = "idx_prompt_release_prompt_status", columnList = "prompt, status"))
public class PromptReleaseEntity {

    public enum Status { DRAFT, ACTIVE, RETIRED }

    @Id
    @Column(name = "id", length = 36)
    private String id;

    /** The prompt the release belongs to, e.g. P1. */
    @Column(name = "prompt", nullable = false, length = 16)
    private String prompt;

    /** The release name from the manifest, e.g. P1-v0.136. */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    /** The manifest as uploaded (JSON). */
    @Column(name = "manifest", nullable = false, columnDefinition = "text")
    private String manifest;

    /** Document code → version of the reference documents this release reads (JSON). */
    @Column(name = "document_versions", columnDefinition = "text")
    private String documentVersions;

    /** The upload check (JSON); a release is only stored when every check passed. */
    @Column(name = "check_report", columnDefinition = "text")
    private String checkReport;

    /** SHA-256 of the uploaded zip. */
    @Column(name = "package_hash", length = 64)
    private String packageHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
}
