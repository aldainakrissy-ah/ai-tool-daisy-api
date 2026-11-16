package com.example.ai.tool.analysis.ai_tool_daisy_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One working file of a turn-based Prompt 1 run (doc 18 format: a TSV file, {@code output.json},
 * or a turn's prompt and answer), stored per run so the run can be checked, compared with Jan's
 * runs and exported. Holds client-derived text: never log it.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "prompt1_run_file",
        uniqueConstraints = @UniqueConstraint(name = "uk_prompt1_run_file", columnNames = {"run_id", "name"}),
        indexes = @Index(name = "idx_prompt1_run_file_run", columnList = "run_id"))
public class Prompt1RunFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
