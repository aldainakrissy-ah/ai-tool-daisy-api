package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "questionnaires")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Questionnaire {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "questionnaire_id", unique = true, nullable = false)
    private String questionnaireId;
    
    @Column(nullable = false, columnDefinition = "jsonb")
    private String title;  // JSON object with language keys (en, nl)
    
    @Column(columnDefinition = "jsonb")
    private String description;  // JSON object with language keys (en, nl)
    
    @OneToMany(mappedBy = "questionnaire", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuestionnaireSection> sections;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "version")
    private Integer version = 1;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}