package com.example.ai.tool.analysis.ai_tool_daisy_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One file of a release. The content of the prompt files (system message, doc 18, schema, B2 matrix, turn
 * configuration) is stored here; a reference document is stored in the knowledge tables instead, so its row
 * here has no content, only its code, version and hash. The content is confidential: it is never logged and
 * never returned by the API.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "prompt_release_file",
        uniqueConstraints = @UniqueConstraint(columnNames = {"release_id", "file_name"}),
        indexes = @Index(name = "idx_prompt_release_file_release", columnList = "release_id"))
public class PromptReleaseFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "release_id", nullable = false, length = 36)
    private String releaseId;

    /** system_message, doc18, schema, b2_matrix, turn_config or reference. */
    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** The document code of a reference document (01, 02, …, R5). */
    @Column(name = "document_code", length = 8)
    private String documentCode;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** The file text; null for a reference document (in the knowledge tables). */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", columnDefinition = "text")
    private String content;
}
