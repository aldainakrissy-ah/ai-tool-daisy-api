package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.constant.QuestionnaireInstructions;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1ResultRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;
import lombok.AllArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * Service class responsible for processing PDF files and interacting with the OpenAI API.
 */
@Service
@AllArgsConstructor
@Slf4j
public class QuestionnaireAnalysisService {

    private OpenAIClient client;

    @Autowired
    private Prompt1ResultRepository prompt1ResultRepository;

    public static final String FILE_ID = "vs_68ca996f20ec8191974741691b169cae";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Processes the uploaded PDF file, extracts its content, and sends it to the OpenAI API for analysis.
     *
     * @param file the uploaded PDF file as a {@link MultipartFile}.
     * @return the response from the OpenAI API as a {@link String}.
     */
    public Prompt1Result generatePreIntakeAnalysis(MultipartFile file) {

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String content = pdfStripper.getText(document);
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(ChatModel.GPT_5)
                    .addFileSearchTool(Collections.singletonList(FILE_ID))
                    .instructions(QuestionnaireInstructions.PROMPT1_INSTRUCTIONS)
                    .input("Process the intake questionnaire and demographic data with prompt 1. And provide the output as a json object of type Prompt1Result. Here is the content:" + content)
                    .build();

            log.info("Sending request to OpenAI with extracted PDF content: {}", content);
            String response = client.responses()
                    .create(params)
                    .output()
                    .stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(msg -> msg.content().stream())
                    .map(responseOutputText -> responseOutputText.asOutputText().text()).collect(Collectors.joining());
            Prompt1Result result = Prompt1Result.fromJson(response);

            log.info("Successfully received response from OpenAI for patient id: {}", result.getPatientId());
            String json = MAPPER.writeValueAsString(result);
            Prompt1ResultEntity entity = new Prompt1ResultEntity();
            entity.setPatientId(result.getPatientId());
            entity.setResultJson(json);
            prompt1ResultRepository.save(entity);

            return result;

        } catch (Exception e) {
            log.error("Error processing PDF file for healthcare analysis. Error: {}", e.getMessage());
            throw new RuntimeException("Error processing PDF file for healthcare analysis: Error: {}" + e.getMessage());
        }
    }
    /**
     * Retrieves Prompt1 results by patient ID from the database.
     *
     * @param patientId the patient ID to search for.
     * @return a list of {@link Prompt1ResultEntity} matching the patient ID.
     */
    public List<Prompt1ResultEntity> getPrompt1ResultByPatientId(String patientId) {
        log.info("Fetching Prompt1 results for patient id: {}", patientId);
        return prompt1ResultRepository.findByPatientId(patientId);
    }
}