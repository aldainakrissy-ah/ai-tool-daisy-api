package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "question_columns", indexes = @Index(name = "idx_question_columns_question", columnList = "question_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "question")
public class QuestionColumn {

    public enum ColumnType {
        TEXT,
        NUMBER,
        DATE,
        TIME,
        DATETIME,
        BOOLEAN,
        MULTIPLE_CHOICE,
        SINGLE_CHOICE
    }

    @Id
    @Column(length = 100)
    private String id;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "en", column = @Column(name = "label_en", columnDefinition = "text")),
            @AttributeOverride(name = "nl", column = @Column(name = "label_nl", columnDefinition = "text"))
    })
    private LocalizedText label;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ColumnType type;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", foreignKey = @ForeignKey(name = "fk_question_columns_question"))
    private Question question;
}
