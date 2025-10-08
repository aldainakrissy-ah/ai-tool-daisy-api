package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "questionnaires")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Questionnaire {
    
    @Id
    private String id;

    @Embedded
    private LocalizedText title;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "en", column = @Column(name = "description_en")),
        @AttributeOverride(name = "nl", column = @Column(name = "description_nl"))
    })
    private LocalizedText description;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "questionnaire_id")
    private List<Section> sections;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "version")
    private Integer version = 1;
}