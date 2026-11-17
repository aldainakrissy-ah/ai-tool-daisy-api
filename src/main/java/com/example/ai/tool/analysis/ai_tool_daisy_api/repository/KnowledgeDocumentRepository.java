package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, Long> {
    Optional<KnowledgeDocumentEntity> findByDocumentCodeAndVersion(String documentCode, String version);
    List<KnowledgeDocumentEntity> findByDocumentCode(String documentCode);
    List<KnowledgeDocumentEntity> findAllByOrderByDocumentCodeAscVersionAsc();
}
