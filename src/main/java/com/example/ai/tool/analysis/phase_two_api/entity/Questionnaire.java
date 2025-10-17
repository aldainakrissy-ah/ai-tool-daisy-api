package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "questionnaires")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Questionnaire {

        @Id
        @Column(columnDefinition = "varchar(255)")
        private String id;

        @Embedded
        @AttributeOverrides({
                        @AttributeOverride(name = "en", column = @Column(name = "title_en", columnDefinition = "text")),
                        @AttributeOverride(name = "nl", column = @Column(name = "title_nl", columnDefinition = "text"))
        })
        private LocalizedText title;

        @Embedded
        @AttributeOverrides({
                        @AttributeOverride(name = "en", column = @Column(name = "description_en", columnDefinition = "text")),
                        @AttributeOverride(name = "nl", column = @Column(name = "description_nl", columnDefinition = "text"))
        })
        private LocalizedText description;

        @Embedded
        @AttributeOverrides({
                        @AttributeOverride(name = "en", column = @Column(name = "instructions_en", columnDefinition = "text")),
                        @AttributeOverride(name = "nl", column = @Column(name = "instructions_nl", columnDefinition = "text"))
        })
        private LocalizedText instructions;

        @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
        @JoinColumn(name = "questionnaire_id")
        private Set<Section> sections = new HashSet<>();

        @Column(name = "is_active")
        private Boolean isActive = true;

        @Column(name = "version")
        private Integer version = 1;
}