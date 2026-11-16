package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseEntity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptReleaseRepository extends JpaRepository<PromptReleaseEntity, String> {

    Optional<PromptReleaseEntity> findFirstByPromptAndStatus(String prompt, Status status);

    List<PromptReleaseEntity> findByPromptAndStatus(String prompt, Status status);

    Optional<PromptReleaseEntity> findByPromptAndName(String prompt, String name);

    List<PromptReleaseEntity> findByPromptOrderByCreatedAtDesc(String prompt);

    List<PromptReleaseEntity> findAllByOrderByCreatedAtDesc();
}
