package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "questionnaire_responses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireResponse {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionnaire_id")
    private Questionnaire questionnaire;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResponseStatus status;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "language_code")
    private String languageCode;
    
    @OneToMany(mappedBy = "questionnaireResponse", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuestionResponse> questionResponses;
    
    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ResponseStatus.IN_PROGRESS;
        }
    }
    
    public enum ResponseStatus {
        IN_PROGRESS,
        COMPLETED,
        ABANDONED
    }
}
