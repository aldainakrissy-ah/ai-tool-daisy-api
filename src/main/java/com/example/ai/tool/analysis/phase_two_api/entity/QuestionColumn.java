package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "question_columns")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionColumn {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "column_id", nullable = false)
    private String columnId;
    
    @Column(nullable = false)
    private String label;
    
    @Column(name = "label_translations", columnDefinition = "jsonb")
    private String labelTranslations;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ColumnType type;
    
    @Column(name = "sort_order")
    private Integer sortOrder;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;
    
    public enum ColumnType {
        TEXT,
        NUMBER,
        DATE,
        TIME,
        EMAIL,
        TEXTAREA
    }
}
