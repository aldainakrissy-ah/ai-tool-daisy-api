package com.example.ai.tool.analysis.phase_two_api.repository;

import com.example.ai.tool.analysis.phase_two_api.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, String> {
    Optional<Question> findById(String id);
}
