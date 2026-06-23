package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt2Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1ResultRepository;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.services.blocking.ResponseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionnaireAnalysisServicePrompt2Test {

    @Mock
    private OpenAIClient client;

    @Mock
    private ResponseService responseService;

    @Mock
    private Prompt1ResultRepository prompt1ResultRepository;

    private QuestionnaireAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new QuestionnaireAnalysisService(client, prompt1ResultRepository);
        ReflectionTestUtils.setField(service, "vectorStoreId", "vs_test");
        ReflectionTestUtils.setField(service, "prompt2Id", "prompt2_test_id");
        ReflectionTestUtils.setField(service, "prompt2Version", "1");
        lenient().when(client.responses()).thenReturn(responseService);
    }

    private Response mockAiResponse(String jsonText) {
        ResponseOutputText outputText = mock(ResponseOutputText.class);
        when(outputText.text()).thenReturn(jsonText);
        ResponseOutputMessage.Content content = mock(ResponseOutputMessage.Content.class);
        when(content.asOutputText()).thenReturn(outputText);
        ResponseOutputMessage message = mock(ResponseOutputMessage.class);
        when(message.content()).thenReturn(List.of(content));
        ResponseOutputItem item = mock(ResponseOutputItem.class);
        when(item.message()).thenReturn(Optional.of(message));
        Response response = mock(Response.class);
        when(response.output()).thenReturn(List.of(item));
        return response;
    }

    private static MultipartFile validFile() {
        return new MockMultipartFile("files", "report.pdf", "application/pdf", "dummy-content".getBytes());
    }

    @Test
    void generatePrompt2Analysis_nullFiles_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.generatePrompt2Analysis(null));
        verify(client, never()).responses();
        verify(prompt1ResultRepository, never()).save(any());
    }

    @Test
    void generatePrompt2Analysis_emptyFiles_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.generatePrompt2Analysis(List.of()));
        verify(client, never()).responses();
        verify(prompt1ResultRepository, never()).save(any());
    }

    @Test
    void generatePrompt2Analysis_fileWithNullOriginalFilename_throwsIllegalArgumentException() {
        MultipartFile fileWithoutName = mock(MultipartFile.class);
        when(fileWithoutName.isEmpty()).thenReturn(false);
        when(fileWithoutName.getOriginalFilename()).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.generatePrompt2Analysis(List.of(fileWithoutName)));
        verify(client, never()).responses();
        verify(prompt1ResultRepository, never()).save(any());
    }

    @Test
    void generatePrompt2Analysis_happyPath_returnsResultAndPersists() throws Exception {
        String json = "{"
                + "\"prompt_id\":\"Prompt_2\","
                + "\"current_date\":\"2025-02-02\","
                + "\"client-name\":\"Thorwald Veneberg\","
                + "\"professional-name\":\"Wim Gelderblom\","
                + "\"A1\":{\"primary_ie_ref\":\"22.3\"}"
                + "}";
        Response aiResponse = mockAiResponse(json);
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(aiResponse);

        Prompt2Result result = service.generatePrompt2Analysis(List.of(validFile()));

        assertThat(result.getPromptId()).isEqualTo("Prompt_2");
        assertThat(result.getClientName()).isEqualTo("Thorwald Veneberg");
        assertThat(result.getProfessionalName()).isEqualTo("Wim Gelderblom");
        assertThat(result.getA1().getPrimaryIeRef()).isEqualTo("22.3");

        ArgumentCaptor<Prompt1ResultEntity> captor = ArgumentCaptor.forClass(Prompt1ResultEntity.class);
        verify(prompt1ResultRepository).save(captor.capture());
        Prompt1ResultEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getPromptType()).isEqualTo("Prompt_2");
        assertThat(savedEntity.getProfessionalId()).isEqualTo("Wim Gelderblom");
        assertThat(savedEntity.getPatientId()).isEqualTo("Thorwald Veneberg");
        assertThat(savedEntity.getResultJson()).isNotBlank();
    }

    @Test
    void generatePrompt2Analysis_missingPromptIdInAiJson_defaultsToPromptType2() throws Exception {
        String json = "{"
                + "\"current_date\":\"2025-02-02\","
                + "\"client-name\":\"Thorwald Veneberg\","
                + "\"professional-name\":\"Wim Gelderblom\""
                + "}";
        Response aiResponse = mockAiResponse(json);
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(aiResponse);

        Prompt2Result result = service.generatePrompt2Analysis(List.of(validFile()));

        assertThat(result.getPromptId()).isEqualTo("Prompt_2");
    }

    @Test
    void generatePrompt2Analysis_blankAiResponseText_throwsRuntimeException() {
        Response aiResponse = mockAiResponse("   ");
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(aiResponse);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.generatePrompt2Analysis(List.of(validFile())));
        assertThat(ex.getMessage()).isEqualTo("OpenAI returned empty response for Prompt2");
        verify(prompt1ResultRepository, never()).save(any());
    }

    @Test
    void persistPrompt2Result_withNullProfessionalAndClientName_savesWithoutThrowing() throws Exception {
        Prompt2Result result = new Prompt2Result();
        result.setPromptId("Prompt_2");

        service.persistPrompt2Result(result);

        ArgumentCaptor<Prompt1ResultEntity> captor = ArgumentCaptor.forClass(Prompt1ResultEntity.class);
        verify(prompt1ResultRepository).save(captor.capture());
        Prompt1ResultEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getProfessionalId()).isNull();
        assertThat(savedEntity.getPatientId()).isNull();
        assertThat(savedEntity.getPromptType()).isEqualTo("Prompt_2");
    }
}
