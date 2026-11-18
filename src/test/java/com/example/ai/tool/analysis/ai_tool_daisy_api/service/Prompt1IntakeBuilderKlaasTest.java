package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionResponseDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jan's Klaas intake saved through our questionnaire and rebuilt by the builder gives the same Step 0 inventory
 * questions (number, answer, order) as Jan's own file. Compares IDs and counts in its messages, never answers;
 * skipped without Jan's files (PROMPT1_INTAKE_DIR).
 */
class Prompt1IntakeBuilderKlaasTest {

    private static final ObjectMapper MAPPER = Prompt1TestFiles.LENIENT;

    @Test
    void klaasRebuiltGivesTheSameInventoryQuestions() throws Exception {
        Path dir = Prompt1TestFiles.dir("PROMPT1_INTAKE_DIR");
        Map<String, IntakeFile> bySource = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                IntakeFile file = new IntakeFile(path.getFileName().toString(), Files.readAllBytes(path));
                bySource.put(Prompt1TurnInventory.sourceType(MAPPER.readTree(file.content())), file);
            }
        }
        IntakeFile personal = bySource.get("personal_data");
        IntakeFile jans = bySource.get("intake_questionnaire");
        assertNotNull(personal, "no personal data file in PROMPT1_INTAKE_DIR");
        assertNotNull(jans, "no intake questionnaire in PROMPT1_INTAKE_DIR");

        QuestionnaireDto questionnaire = Prompt1TestFiles.intakeQuestionnaire();
        Map<String, QuestionDto> byNumber = new HashMap<>();
        questionnaire.getSections().forEach(s -> s.getQuestions().forEach(q ->
                byNumber.put(IntakeAnswers.number(IntakeAnswers.dutch(q.getText())), q)));

        // the answers Jan's inventory uses, saved as the intake app saves a text answer
        List<Map<String, String>> expected = questions(List.of(personal, jans));
        List<QuestionResponseDto> saved = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map<String, String> row : expected) {
            QuestionDto question = byNumber.get(row.get("source_label"));
            if (question == null) {
                missing.add(row.get("source_label"));
                continue;
            }
            QuestionResponseDto response = new QuestionResponseDto();
            response.setQuestionId(question.getId());
            response.setAnswerText(answerOf(row));
            saved.add(response);
        }
        assertEquals(List.of(), missing, "questions of Jan's intake not in our questionnaire");
        QuestionnaireResponseDto response = new QuestionnaireResponseDto();
        response.setQuestionResponses(saved);

        Prompt1IntakeBuilder builder = new Prompt1IntakeBuilder(null, null, null, null, MAPPER);
        IntakeFile ours = new IntakeFile("intake_questionnaire.json",
                MAPPER.writeValueAsBytes(builder.intakeDocument(questionnaire, response)));
        List<Map<String, String>> actual = questions(List.of(personal, ours));

        assertEquals(expected.stream().map(r -> r.get("source_label")).toList(),
                actual.stream().map(r -> r.get("source_label")).toList(), "question numbers and order");
        List<String> differentAnswers = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            if (!answerOf(expected.get(i)).equals(answerOf(actual.get(i)))) {
                differentAnswers.add(expected.get(i).get("source_label"));
            }
        }
        assertEquals(List.of(), differentAnswers, "answers that differ");
    }

    /** The question rows of the Step 0 inventory, in order. */
    private static List<Map<String, String>> questions(List<IntakeFile> files) {
        String observations = Prompt1TurnInventory.build(files).observations();
        return Prompt1Tsv.rows(observations).stream()
                .filter(row -> "question_response".equals(row.get("record_type")))
                .toList();
    }

    /** The answer of an inventory question row (its value is {question, answer}). */
    private static String answerOf(Map<String, String> row) throws IOException {
        return MAPPER.readTree(row.get("source_value")).path("answer").asText();
    }
}
