package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "questionnaire_sections")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSection {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "section_id", nullable = false)
    private String sectionId;
    
    @Column(nullable = false)
    private String title;
    
    @Column(name = "title_translations", columnDefinition = "jsonb")
    private String titleTranslations;
    
    @Column(name = "sort_order")
    private Integer sortOrder;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionnaire_id")
    private Questionnaire questionnaire;
    
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Question> questions;
}
