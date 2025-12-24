package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "language_code")
    private String languageCode = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ResponseStatus status = ResponseStatus.IN_PROGRESS;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "questionnaireResponse", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuestionResponse> questionResponses = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
    }

    public enum ResponseStatus {
        IN_PROGRESS, COMPLETED, ABANDONED
    }
}
