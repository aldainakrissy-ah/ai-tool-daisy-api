package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class Option {
    @Column(columnDefinition = "varchar(255)")
    private String value;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "en", column = @Column(name = "label_en", columnDefinition = "text")),
            @AttributeOverride(name = "nl", column = @Column(name = "label_nl", columnDefinition = "text"))
    })
    private LocalizedText label;
}
