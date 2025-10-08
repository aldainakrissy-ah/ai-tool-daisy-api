package com.example.ai.tool.analysis.phase_two_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "sections")
public class Section {
    @Id
    private String id;

    @Embedded
    private LocalizedText title;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<Question> questions = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Section)) return false;
        Section section = (Section) o;
        return Objects.equals(id, section.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
