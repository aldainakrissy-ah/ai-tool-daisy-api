package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "question_id", nullable = false)
    private String questionId;
    
    @Column(nullable = false)
    private String text;
    
    @Column(name = "text_translations", columnDefinition = "jsonb")
    private String textTranslations;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;
    
    @Column(name = "options", columnDefinition = "jsonb")
    private String options;
    
    @Column(name = "validation_rules", columnDefinition = "jsonb")
    private String validationRules;
    
    @Column(name = "sort_order")
    private Integer sortOrder;
    
    @Column(name = "is_required")
    private Boolean isRequired = false;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private QuestionnaireSection section;
    
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuestionColumn> columns;
    
    public enum QuestionType {
        TEXT,
        TEXTAREA,
        YES_NO,
        LIKERT_SCALE_3,
        LIKERT_SCALE_4,
        LIKERT_SCALE_5,
        MULTIPLE_CHOICE,
        SINGLE_CHOICE,
        NUMBER,
        DATE,
        TIME,
        TABLE
    }
}
