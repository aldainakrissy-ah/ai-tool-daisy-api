package com.example.ai.tool.analysis.ai_tool_daisy_api.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "prompt1_results")
public class Prompt1ResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Lob
    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;
}
