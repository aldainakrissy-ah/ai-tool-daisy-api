package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.KnowledgeDocumentEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.KnowledgeSectionEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.KnowledgeDocumentDto;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.KnowledgeSectionDto;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.KnowledgeDocumentRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.KnowledgeSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Exact-key access to the loaded reference documents. Lookups always use the active version.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeSectionRepository sectionRepository;

    @Transactional(readOnly = true)
    public Optional<KnowledgeSectionDto> getActiveSection(String sectionKey) {
        return sectionRepository.findActiveByKey(sectionKey).map(section -> toDto(section));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentDto> getDocuments() {
        return documentRepository.findAllByOrderByDocumentCodeAscVersionAsc().stream()
                .map(document -> toDto(document))
                .toList();
    }

    /** Keys and headings of the active version of a document, without the section text. */
    @Transactional(readOnly = true)
    public List<KnowledgeSectionDto> getActiveSectionIndex(String documentCode) {
        KnowledgeDocumentEntity document = documentRepository.findByDocumentCode(documentCode).stream()
                .filter(candidate -> candidate.isActive())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No active document with code " + documentCode));
        return sectionRepository.findByDocumentOrderBySeqAsc(document).stream()
                .map(s -> KnowledgeSectionDto.builder()
                        .sectionKey(s.getSectionKey())
                        .heading(s.getHeading())
                        .documentCode(document.getDocumentCode())
                        .documentVersion(document.getVersion())
                        .seq(s.getSeq())
                        .contentHash(s.getContentHash())
                        .build())
                .toList();
    }

    private static KnowledgeSectionDto toDto(KnowledgeSectionEntity section) {
        KnowledgeDocumentEntity document = section.getDocument();
        return KnowledgeSectionDto.builder()
                .sectionKey(section.getSectionKey())
                .heading(section.getHeading())
                .documentCode(document.getDocumentCode())
                .documentVersion(document.getVersion())
                .seq(section.getSeq())
                .content(section.getContent())
                .contentHash(section.getContentHash())
                .build();
    }

    private static KnowledgeDocumentDto toDto(KnowledgeDocumentEntity document) {
        return KnowledgeDocumentDto.builder()
                .documentCode(document.getDocumentCode())
                .version(document.getVersion())
                .fileName(document.getFileName())
                .sourceHash(document.getSourceHash())
                .sourceBuild(document.getSourceBuild())
                .sectionCount(document.getSectionCount())
                .active(document.isActive())
                .loadedAt(document.getLoadedAt())
                .build();
    }
}
