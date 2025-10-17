package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questionnaires")
@Data
@NoArgsConstructor
@AllArgsConstructor
@BatchSize(size = 20)
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
        @OrderBy("sortOrder ASC")
        @BatchSize(size = 50)
        private List<Section> sections = new ArrayList<>();

        @Column(name = "is_active")
        private Boolean isActive = true;

        @Column(name = "version")
        private Integer version = 1;
}