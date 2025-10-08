package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @deprecated This entity is deprecated and will be removed. Use {@link Section} instead.
 */
@Entity
@Table(name = "questionnaire_sections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(forRemoval = true, since = "1.0")
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
    
    // Removing the OneToMany relationship that's causing conflicts
    // Questions are now associated with Section entity
}

// This entity has been deprecated and replaced by Section.
// Please use Section.java for all future references.
