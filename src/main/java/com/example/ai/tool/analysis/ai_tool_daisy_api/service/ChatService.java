package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
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

//    @Autowired
//    private Prompt1ResultRepository prompt1ResultRepository;

    public static final String FILE_ID = "vs_68ca996f20ec8191974741691b169cae";

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
                    .instructions("{\n" +
                            "  \"patient_id\": \"Extract the patient ID from the content.\",\n" +
                            "  \"analysis\": {\n" +
                            "    \"preliminary_tpd_hypothesis\": \"Formulate a preliminary TPD hypothesis\",\n" +
                            "    \"dysregulation_fields\": [\"Identify the suspected teleonic dysregulation fields.\"],\n" +
                            "    \"signs_symptoms_clusters\": [\"Describe the main signs & symptoms clusters.\"],\n" +
                            "    \"heta_terms_usage\": \"Use HETA terms only as substantive interpretation, not as new proposals.\",\n" +
                            "    \"data_gaps\": [\"Identify any significant data gaps.\"]\n" +
                            "  },\n" +
                            "  \"routing\": {\n" +
                            "    \"additional_questionnaires\": [\"Propose which additional validated questionnaires are necessary.\"],\n" +
                            "    \"ethos_variant\": \"Decide which ETHOS variant (Adult, Child, Hybrid) is most appropriate.\",\n" +
                            "    \"heta_modules\": [\"Propose which HETA modules (Daisy modular questionnaires) should be activated.\"]\n" +
                            "  }\n" +
                            "}")

                    .input("Process the intake questionnaire and demographic data with prompt 1. And provide the output as a json object of type Prompt1Result. Here is the content: " + content)
                    .build();

            log.info("Sending request to OpenAI with extracted PDF content: {}", content);
            String response = client.responses()
                    .create(params)
                    .output()
                    .stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(msg -> msg.content().stream())
                    .map(responseOutputText -> responseOutputText.asOutputText().text()).collect(Collectors.joining());

//            Prompt1ResultEntity prompt1ResultEntity = new Prompt1ResultEntity();
//            prompt1ResultEntity.setPatientId(result.getAnalysis().getPatientId());
//            prompt1ResultEntity.setResponseJson(response);
//
//            prompt1ResultRepository.save(prompt1ResultEntity);

            return Prompt1Result.fromJson(response);

        } catch (Exception e) {
            log.error("Error processing PDF file for healthcare analysis", e);
            throw new RuntimeException("Error processing PDF file for healthcare analysis: " + e.getMessage());
        }
    }

//    public List<Prompt1ResultEntity> getPrompt1ResultByPatientId(String patientId) {
//        return prompt1ResultRepository.findByPatientId(patientId);
//    }
}