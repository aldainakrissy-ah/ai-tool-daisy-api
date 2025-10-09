package com.example.ai.tool.analysis.phase_two_api.repository;

import com.example.ai.tool.analysis.phase_two_api.entity.Questionnaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionnaireRepository extends JpaRepository<Questionnaire, String> {
    Optional<Questionnaire> findById(String id);
    List<Questionnaire> findAll();
    @Query("SELECT DISTINCT q FROM Questionnaire q " +
           "LEFT JOIN FETCH q.sections s " +
           "LEFT JOIN FETCH s.questions qu " +
           "LEFT JOIN FETCH qu.options o " +
           "WHERE q.id = :id")
    Optional<Questionnaire> findByIdWithDetails(@Param("id") String id);
    boolean existsById(String id);
}
