package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromptReleaseFileRepository extends JpaRepository<PromptReleaseFileEntity, Long> {

    List<PromptReleaseFileEntity> findByReleaseIdOrderByFileNameAsc(String releaseId);
}
