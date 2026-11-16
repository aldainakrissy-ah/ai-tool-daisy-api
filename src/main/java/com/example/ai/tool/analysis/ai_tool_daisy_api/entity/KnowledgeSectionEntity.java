package com.example.ai.tool.analysis.ai_tool_daisy_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One keyed section of a reference document, e.g. R4:22.11:01 (IE 22.11 Functional Role).
 * Sections follow the document's own keys and headings and hold the complete section text.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "knowledge_section",
        uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "section_key"}),
        indexes = @Index(name = "idx_knowledge_section_key", columnList = "section_key"))
public class KnowledgeSectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocumentEntity document;

    @Column(name = "section_key", nullable = false, length = 512)
    private String sectionKey;

    @Column(name = "heading", columnDefinition = "text")
    private String heading;

    /** Position of the section in source order, starting at 1. */
    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;
}
