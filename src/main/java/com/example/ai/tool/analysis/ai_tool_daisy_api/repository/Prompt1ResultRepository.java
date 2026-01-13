package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface Prompt1ResultRepository extends  JpaRepository<Prompt1ResultEntity, Long> {
    List<Prompt1ResultEntity> findByProfessionalId(String professionalId);
    List<Prompt1ResultEntity> findByProfessionalIdAndPatientId(String professionalId, String patientId);
    List<Prompt1ResultEntity> findByPatientId(String patientId);

    @Modifying
    @Transactional
    @Query(
            value = "INSERT INTO prompt1_results (professional_id, patient_id, result_json) VALUES (:professionalId, :patientId, cast(:resultJson as jsonb))",
            nativeQuery = true
    )
    void saveAsJson(
            @Param("professionalId") String professionalId,
            @Param("patientId") String patientId,
            @Param("resultJson") String resultJson
    );
}
