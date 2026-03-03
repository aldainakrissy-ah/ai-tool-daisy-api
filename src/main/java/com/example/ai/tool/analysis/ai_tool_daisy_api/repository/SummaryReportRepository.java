package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.SummaryReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SummaryReportRepository extends JpaRepository<SummaryReportEntity, Long> {

    SummaryReportEntity findByDocumentId(String documentId);

}
