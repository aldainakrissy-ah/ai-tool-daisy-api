package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;
import lombok.AllArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Collections;
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
    public String generatePreIntakeAnalysis(MultipartFile file) {
        try {
            PDDocument document = PDDocument.load(file.getInputStream());
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String content = pdfStripper.getText(document);
            document.close();
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(ChatModel.GPT_5)
                    .addFileSearchTool(Collections.singletonList(FILE_ID))
                    .input("Daisy, can you analyse the data and present the outcome according to the 1.3a pre-intake template " + content)
                    .instructions("Generate the response in the format of a healthcare pre-intake analysis report in a json format.")
                    .build();

            return client.responses()
                    .create(params)
                    .output()
                    .stream()
                    .flatMap(output -> output.message().stream())
                    .flatMap(message -> message.content().stream())
                    .map(responseOutputText -> responseOutputText.asOutputText().text())
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.error("Error processing PDF file for healthcare analysis", e);
            throw new RuntimeException("Error processing PDF file for healthcare analysis: " + e.getMessage());
        }
    }
}