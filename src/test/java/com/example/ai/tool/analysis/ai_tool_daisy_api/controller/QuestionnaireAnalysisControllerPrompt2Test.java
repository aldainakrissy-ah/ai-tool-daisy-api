package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt2Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.DatabaseHealthService;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.QuestionnaireAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionnaireAnalysisControllerPrompt2Test {

    @Mock
    private QuestionnaireAnalysisService questionnaireAnalysisService;

    @Mock
    private DatabaseHealthService databaseHealthService;

    @InjectMocks
    private QuestionnaireAnalysisController controller;

    private static MultipartFile validFile() {
        return new MockMultipartFile("files", "report.pdf", "application/pdf", "dummy-content".getBytes());
    }

    @Test
    void analyzePrompt2_nullFiles_returns400AndSkipsService() throws Exception {
        ResponseEntity<Prompt2Result> response = controller.analyzePrompt2(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(questionnaireAnalysisService, never()).generatePrompt2Analysis(any());
    }

    @Test
    void analyzePrompt2_emptyFiles_returns400AndSkipsService() throws Exception {
        ResponseEntity<Prompt2Result> response = controller.analyzePrompt2(List.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(questionnaireAnalysisService, never()).generatePrompt2Analysis(any());
    }

    @Test
    void analyzePrompt2_happyPath_returns200WithBody() throws Exception {
        List<MultipartFile> files = List.of(validFile());
        Prompt2Result expected = new Prompt2Result();
        expected.setPromptId("Prompt_2");
        expected.setClientName("Thorwald Veneberg");
        expected.setProfessionalName("Wim Gelderblom");
        when(questionnaireAnalysisService.generatePrompt2Analysis(files)).thenReturn(expected);

        ResponseEntity<Prompt2Result> response = controller.analyzePrompt2(files);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(questionnaireAnalysisService).generatePrompt2Analysis(files);
    }

    @Test
    void analyzePrompt2_serviceThrowsJsonProcessingException_propagates() throws Exception {
        List<MultipartFile> files = List.of(validFile());
        JsonProcessingException toThrow = new JsonProcessingException("boom") {
        };
        when(questionnaireAnalysisService.generatePrompt2Analysis(files)).thenThrow(toThrow);

        assertThrows(JsonProcessingException.class, () -> controller.analyzePrompt2(files));
    }
}
