package com.example.ai.tool.analysis.ai_tool_daisy_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One version of a Prompt 1 reference document (Primer, Framework, R3, R4, documents 05–07).
 * Versions are never edited; a new version is loaded alongside and marked active.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "knowledge_document",
        uniqueConstraints = @UniqueConstraint(columnNames = {"document_code", "version"}))
public class KnowledgeDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Document code as used by the source build, e.g. "R4", "01", "07". */
    @Column(name = "document_code", nullable = false)
    private String documentCode;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** SHA-256 of the original source file, as recorded by the source build. */
    @Column(name = "source_hash", nullable = false)
    private String sourceHash;

    /** Identifier of the chunking build the sections were reconstructed from. */
    @Column(name = "source_build")
    private String sourceBuild;

    @Column(name = "section_count", nullable = false)
    private int sectionCount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "loaded_at", nullable = false)
    private LocalDateTime loadedAt;

    @PrePersist
    protected void onCreate() {
        loadedAt = LocalDateTime.now();
    }
}
