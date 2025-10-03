package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface Prompt1ResultRepository extends  JpaRepository<Prompt1ResultEntity, Long> {
    List<Prompt1ResultEntity> findByPatientId(String patientId);
}
