package com.example.ai.tool.analysis.phase_two_api.repository;

import com.example.ai.tool.analysis.phase_two_api.entity.QuestionnaireResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionnaireResponseRepository extends JpaRepository<QuestionnaireResponse, Long> {
    
    List<QuestionnaireResponse> findByUserId(String userId);
    
    List<QuestionnaireResponse> findByUserIdAndStatus(String userId, QuestionnaireResponse.ResponseStatus status);
    
    Optional<QuestionnaireResponse> findBySessionId(String sessionId);
    
    @Query("SELECT qr FROM QuestionnaireResponse qr LEFT JOIN FETCH qr.questionResponses WHERE qr.sessionId = :sessionId")
    Optional<QuestionnaireResponse> findBySessionIdWithResponses(@Param("sessionId") String sessionId);
    
    @Query("SELECT qr FROM QuestionnaireResponse qr WHERE qr.questionnaire.questionnaireId = :questionnaireId AND qr.userId = :userId ORDER BY qr.startedAt DESC")
    List<QuestionnaireResponse> findByQuestionnaireIdAndUserId(@Param("questionnaireId") String questionnaireId, @Param("userId") String userId);
}
