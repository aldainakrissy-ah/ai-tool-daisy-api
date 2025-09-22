package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;
import lombok.AllArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


/**
 * Service class responsible for processing PDF files and interacting with the OpenAI API.
 */
@Service
@AllArgsConstructor
@Slf4j
public class ChatService {

    private OpenAIClient client;

    public static final String FILE_ID = "vs_68ca996f20ec8191974741691b169cae";

    /**
     * Processes the uploaded PDF file, extracts its content, and sends it to the OpenAI API for analysis.
     *
     * @param file the uploaded PDF file as a {@link MultipartFile}.
     * @return the response from the OpenAI API as a {@link String}.
     */
    @Async
    public CompletableFuture<String> generatePreIntakeAnalysis(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String content = pdfStripper.getText(document);
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(ChatModel.GPT_5)
                    .addFileSearchTool(Collections.singletonList(FILE_ID))
                    .instructions("Generate the detailed analysis with Provisional TPD Hypothesis,Signs & Symptoms clusters,HETA coverage report,Optional additional data,Gap list,Recommended additional questionnaires")
                    .input("Daisy, can you analyse the data and present the outcome according to the 1.3a pre-intake template and generate in a json format" + content)
                    .build();

            log.info("Sending request to OpenAI with extracted PDF content: {}", content);
            return CompletableFuture.completedFuture(client.responses()
                    .create(params)
                    .output()
                    .stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(msg -> msg.content().stream())
                    .map(responseOutputText -> responseOutputText.asOutputText().text())
                    .collect(Collectors.joining()));

        } catch (Exception e) {
            log.error("Error processing PDF file for healthcare analysis", e);
            throw new RuntimeException("Error processing PDF file for healthcare analysis: " + e.getMessage());
        }
    }
}