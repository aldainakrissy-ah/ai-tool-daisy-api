package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.KnowledgeDocumentEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.KnowledgeSectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSectionRepository extends JpaRepository<KnowledgeSectionEntity, Long> {

    /** The section with this key in the active version of its document. */
    @Query("SELECT s FROM KnowledgeSectionEntity s JOIN FETCH s.document d "
            + "WHERE s.sectionKey = :sectionKey AND d.active = true")
    Optional<KnowledgeSectionEntity> findActiveByKey(@Param("sectionKey") String sectionKey);

    List<KnowledgeSectionEntity> findByDocumentOrderBySeqAsc(KnowledgeDocumentEntity document);

    @Modifying
    @Query("DELETE FROM KnowledgeSectionEntity s WHERE s.document = :document")
    void deleteByDocument(@Param("document") KnowledgeDocumentEntity document);
}
