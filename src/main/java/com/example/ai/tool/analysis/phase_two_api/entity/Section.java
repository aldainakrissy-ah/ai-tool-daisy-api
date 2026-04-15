package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "sections", indexes = {
        @Index(name = "idx_section_order", columnList = "sort_order")
})
@BatchSize(size = 50)
public class Section {
    @Id
    @Column(columnDefinition = "varchar(255)")
    private String id;

    @Column(name = "sort_order", nullable = true)
    private Integer sortOrder;

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

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 100)
    private List<Question> questions = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Section))
            return false;
        Section section = (Section) o;
        return Objects.equals(id, section.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
