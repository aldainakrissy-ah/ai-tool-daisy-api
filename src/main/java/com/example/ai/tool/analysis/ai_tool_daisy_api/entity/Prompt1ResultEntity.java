package com.example.ai.tool.analysis.ai_tool_daisy_api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;


@Data
@Entity
@NoArgsConstructor
@Table(name = "prompt1_results")
public class Prompt1ResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "professional_id", nullable = false)
    private String professionalId;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "result_json", columnDefinition = "jsonb", nullable = false)
    private String resultJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
