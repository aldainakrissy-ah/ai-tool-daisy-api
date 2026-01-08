package com.example.ai.tool.analysis.ai_tool_daisy_api;

import com.example.ai.tool.analysis.phase_two_api.entity.Questionnaire;
import com.example.ai.tool.analysis.phase_two_api.entity.QuestionnaireResponse;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionResponseDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireResponseDto;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionnaireRepository;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionnaireResponseRepository;
import com.example.ai.tool.analysis.phase_two_api.service.QuestionnaireResponseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionRepository;


public class QuestionnaireResponseServiceTest {

    @InjectMocks
    private QuestionnaireResponseService questionnaireResponseService;

    @Mock
    private QuestionnaireResponseRepository responseRepository;

    @Mock
    private QuestionnaireRepository questionnaireRepository;

    @Mock
    private QuestionRepository questionRepository;


    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSaveQuestionResponse_createNew() {
        String sessionId = UUID.randomUUID().toString();
        String clientId = "testClient";
        String questionnaireId = "testQuestionnaire";
        QuestionResponseDto responseDto = new QuestionResponseDto();

        when(responseRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(responseRepository.findByUserIdAndQuestionnaireIdAndStatus(clientId, questionnaireId, QuestionnaireResponse.ResponseStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setId(questionnaireId);
        when(questionnaireRepository.findById(questionnaireId)).thenReturn(Optional.of(questionnaire));

        QuestionnaireResponse savedResponse = new QuestionnaireResponse();
        savedResponse.setSessionId(sessionId);
        when(responseRepository.save(any(QuestionnaireResponse.class))).thenReturn(savedResponse);

        QuestionnaireResponseDto result = questionnaireResponseService.saveQuestionResponse(sessionId, clientId, questionnaireId, Collections.singletonList(responseDto));

        assertEquals(sessionId, result.getSessionId());
    }

    @Test
    public void testSaveQuestionResponse_updateExisting() {
        String sessionId = UUID.randomUUID().toString();
        String clientId = "testClient";
        String questionnaireId = "testQuestionnaire";
        QuestionResponseDto responseDto = new QuestionResponseDto();

        QuestionnaireResponse existingResponse = new QuestionnaireResponse();
        existingResponse.setSessionId(sessionId);
        existingResponse.setStartedAt(LocalDateTime.now());

        when(responseRepository.findBySessionId(sessionId)).thenReturn(Optional.of(existingResponse));
        when(responseRepository.save(any(QuestionnaireResponse.class))).thenReturn(existingResponse);

        QuestionnaireResponseDto result = questionnaireResponseService.saveQuestionResponse(sessionId, clientId, questionnaireId, Collections.singletonList(responseDto));

        assertEquals(sessionId, result.getSessionId());
    }

    @Test
    public void testSaveQuestionResponse_updateExistingWithDifferentSessionId() {
        String oldSessionId = UUID.randomUUID().toString();
        String newSessionId = UUID.randomUUID().toString();
        String clientId = "testClient";
        String questionnaireId = "testQuestionnaire";
        QuestionResponseDto responseDto = new QuestionResponseDto();

        QuestionnaireResponse existingResponse = new QuestionnaireResponse();
        existingResponse.setSessionId(oldSessionId);
        existingResponse.setUserId(clientId);
        existingResponse.setQuestionnaire(new Questionnaire());
        existingResponse.getQuestionnaire().setId(questionnaireId);
        existingResponse.setStatus(QuestionnaireResponse.ResponseStatus.IN_PROGRESS);
        existingResponse.setStartedAt(LocalDateTime.now());

        when(responseRepository.findBySessionId(newSessionId)).thenReturn(Optional.empty());
        when(responseRepository.findByUserIdAndQuestionnaireIdAndStatus(clientId, questionnaireId, QuestionnaireResponse.ResponseStatus.IN_PROGRESS))
                .thenReturn(Optional.of(existingResponse));
        when(responseRepository.save(any(QuestionnaireResponse.class))).thenReturn(existingResponse);

        QuestionnaireResponseDto result = questionnaireResponseService.saveQuestionResponse(newSessionId, clientId, questionnaireId, Collections.singletonList(responseDto));

        assertEquals(oldSessionId, result.getSessionId());
    }

    @Test
    public void testSaveQuestionResponse_updateExistingQuestionResponse() {
        String sessionId = UUID.randomUUID().toString();
        String clientId = "testClient";
        String questionnaireId = "testQuestionnaire";
        String questionId = "testQuestion";

        QuestionResponseDto responseDto = new QuestionResponseDto();
        responseDto.setQuestionId(questionId);
        responseDto.setAnswerText("new answer");

        QuestionnaireResponse existingResponse = new QuestionnaireResponse();
        existingResponse.setSessionId(sessionId);
        existingResponse.setStartedAt(LocalDateTime.now());
        
        com.example.ai.tool.analysis.phase_two_api.entity.QuestionResponse existingQuestionResponse = new com.example.ai.tool.analysis.phase_two_api.entity.QuestionResponse();
        com.example.ai.tool.analysis.phase_two_api.entity.Question question = new com.example.ai.tool.analysis.phase_two_api.entity.Question();
        question.setId(questionId);
        existingQuestionResponse.setQuestion(question);
        existingQuestionResponse.setAnswerText("old answer");
        existingResponse.getQuestionResponses().add(existingQuestionResponse);

        when(responseRepository.findBySessionId(sessionId)).thenReturn(Optional.of(existingResponse));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(responseRepository.save(any(QuestionnaireResponse.class))).thenReturn(existingResponse);

        QuestionnaireResponseDto result = questionnaireResponseService.saveQuestionResponse(sessionId, clientId, questionnaireId, Collections.singletonList(responseDto));

        assertEquals(1, result.getQuestionResponses().size());
        assertEquals("new answer", result.getQuestionResponses().get(0).getAnswerText());
    }

    @Test
    public void testDeleteQuestionnaireResponse() {
        String clientId = "testClient";
        String questionnaireId = "testQuestionnaire";

        questionnaireResponseService.deleteQuestionnaireResponse(clientId, questionnaireId);

        org.mockito.Mockito.verify(responseRepository).deleteByUserIdAndQuestionnaireId(clientId, questionnaireId);
    }
}
