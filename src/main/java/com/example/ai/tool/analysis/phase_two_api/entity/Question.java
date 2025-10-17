package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "questions", indexes = {
        @Index(name = "idx_question_section", columnList = "section_id"),
        @Index(name = "idx_question_sort_order", columnList = "sort_order")
})
@EqualsAndHashCode(exclude = { "section", "options", "columns" })
@ToString(exclude = { "section" })
public class Question {

    @Id
    @Column(columnDefinition = "varchar(255)")
    private String id;

    @Column(columnDefinition = "varchar(100)")
    private String type;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "en", column = @Column(name = "text_en", columnDefinition = "text")),
            @AttributeOverride(name = "nl", column = @Column(name = "text_nl", columnDefinition = "text"))
    })
    private LocalizedText text;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id", foreignKey = @ForeignKey(name = "fk_question_options")), indexes = @Index(name = "idx_question_options", columnList = "question_id"))
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 100)
    private List<Option> options = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_columns", joinColumns = @JoinColumn(name = "question_id", foreignKey = @ForeignKey(name = "fk_question_columns")), indexes = @Index(name = "idx_question_columns", columnList = "question_id"))
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 20)
    private List<QuestionColumn> columns = new ArrayList<>();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "min", column = @Column(name = "validation_min", nullable = true)),
            @AttributeOverride(name = "max", column = @Column(name = "validation_max", nullable = true)),
            @AttributeOverride(name = "required", column = @Column(name = "validation_required", nullable = true)),
            @AttributeOverride(name = "pattern", column = @Column(name = "validation_pattern", columnDefinition = "text", nullable = true)),
            @AttributeOverride(name = "minLength", column = @Column(name = "validation_min_length", nullable = true)),
            @AttributeOverride(name = "maxLength", column = @Column(name = "validation_max_length", nullable = true)),
            @AttributeOverride(name = "minDate", column = @Column(name = "validation_min_date", nullable = true)),
            @AttributeOverride(name = "maxDate", column = @Column(name = "validation_max_date", nullable = true))
    })
    private ValidationRules validation;

    @Column(name = "max_names", nullable = true)
    private Integer maxNames;

    @Column(name = "required", nullable = true)
    private Boolean required = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", foreignKey = @ForeignKey(name = "fk_question_section"))
    private Section section;

    @Column(name = "sort_order", nullable = true)
    private Integer sortOrder;
}
