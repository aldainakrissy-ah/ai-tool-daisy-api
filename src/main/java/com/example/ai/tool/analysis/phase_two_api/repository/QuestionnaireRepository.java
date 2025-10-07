package com.example.ai.tool.analysis.phase_two_api.repository;

import com.example.ai.tool.analysis.phase_two_api.entity.Questionnaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionnaireRepository extends JpaRepository<Questionnaire, Long> {
    
    Optional<Questionnaire> findByQuestionnaireId(String questionnaireId);
    
    List<Questionnaire> findByIsActiveTrue();
    
    @Query("SELECT q FROM Questionnaire q WHERE q.isActive = true ORDER BY q.createdAt DESC")
    List<Questionnaire> findActiveQuestionnairesOrderByCreatedDate();
    
    @Query("SELECT q FROM Questionnaire q LEFT JOIN FETCH q.sections s LEFT JOIN FETCH s.questions qu LEFT JOIN FETCH qu.columns WHERE q.questionnaireId = :questionnaireId AND q.isActive = true")
    Optional<Questionnaire> findByQuestionnaireIdWithDetails(@Param("questionnaireId") String questionnaireId);
    
    boolean existsByQuestionnaireId(String questionnaireId);
}
