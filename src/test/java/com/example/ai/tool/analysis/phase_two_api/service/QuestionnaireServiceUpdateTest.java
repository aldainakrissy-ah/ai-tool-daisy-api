package com.example.ai.tool.analysis.phase_two_api.service;

import com.example.ai.tool.analysis.phase_two_api.entity.Question;
import com.example.ai.tool.analysis.phase_two_api.entity.Questionnaire;
import com.example.ai.tool.analysis.phase_two_api.entity.Section;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireDto;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionRepository;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionnaireRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Updating the MyHealth intake (adult) in place, as PUT /api/v1/questionnaires/{id}/upload does: Jan's questions
 * 5.8–5.10 are added with the IDs of the file, and the questions already there keep their IDs (their answers
 * refer to them).
 */
class QuestionnaireServiceUpdateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Questionnaire> table = new HashMap<>();
    private final Set<String> otherQuestionIds = new HashSet<>();
    private QuestionnaireService service;
    private QuestionnaireLoaderService loader;

    @BeforeEach
    void setUp() {
        QuestionnaireRepository questionnaires = mock(QuestionnaireRepository.class);
        when(questionnaires.existsById(anyString())).thenAnswer(call -> table.containsKey(call.<String>getArgument(0)));
        when(questionnaires.findById(anyString())).thenAnswer(call -> Optional.ofNullable(table.get(call.<String>getArgument(0))));
        when(questionnaires.save(any())).thenAnswer(call -> {
            Questionnaire questionnaire = call.getArgument(0);
            table.put(questionnaire.getId(), questionnaire);
            return questionnaire;
        });
        QuestionRepository questions = mock(QuestionRepository.class);
        // a question ID is taken when another questionnaire has it, or when it is already stored here
        when(questions.existsById(anyString())).thenAnswer(call -> otherQuestionIds.contains(call.<String>getArgument(0))
                || table.values().stream().flatMap(q -> q.getSections().stream()).flatMap(s -> s.getQuestions().stream())
                .anyMatch(q -> q.getId().equals(call.getArgument(0))));
        service = new QuestionnaireService(questionnaires, questions);
        loader = new QuestionnaireLoaderService(new DefaultResourceLoader(), objectMapper, service);
    }

    private JsonNode intakeFile() throws Exception {
        return objectMapper.readTree(getClass().getClassLoader().getResourceAsStream("questionnaires/my-health-intake-adult.json"));
    }

    /** The intake as it was before 5.8–5.10 (what staging and production have now). */
    private JsonNode intakeWithoutNewQuestions() throws Exception {
        ObjectNode file = (ObjectNode) intakeFile();
        for (JsonNode section : file.get("sections")) {
            ArrayNode questions = (ArrayNode) section.get("questions");
            for (int i = questions.size() - 1; i >= 0; i--) {
                if (List.of("rel-q8", "rel-q9", "rel-q10").contains(questions.get(i).get("id").asText())) {
                    questions.remove(i);
                }
            }
        }
        return file;
    }

    private Section relationships() {
        return table.get("myhealth-intake").getSections().stream()
                .filter(section -> section.getId().equals("relationships")).findFirst().orElseThrow();
    }

    @Test
    void theNewQuestionsAreAddedWithTheirIdsAndTheOldOnesAreKept() throws Exception {
        service.createQuestionnaire(loader.parseJsonToQuestionnaireDto(intakeWithoutNewQuestions()));
        Question oldQ7 = relationships().getQuestions().get(6);
        assertEquals(7, relationships().getQuestions().size());

        QuestionnaireDto file = loader.parseJsonToQuestionnaireDto(intakeFile());
        service.updateQuestionnaire("myhealth-intake", file);

        List<Question> questions = relationships().getQuestions();
        assertEquals(List.of("rel-q1", "rel-q2", "rel-q3", "rel-q4", "rel-q5", "rel-q6", "rel-q7", "rel-q8", "rel-q9", "rel-q10"),
                questions.stream().map(q -> q.getId()).toList());
        assertSame(oldQ7, questions.get(6), "an existing question is kept (its answers refer to it)");
        Question q8 = questions.get(7);
        assertEquals("likert-scale-5", q8.getType());
        assertEquals(List.of("0", "1", "2", "3", "4"), q8.getOptions().stream().map(o -> o.getValue()).toList());
        assertEquals(72, table.get("myhealth-intake").getSections().stream().mapToInt(s -> s.getQuestions().size()).sum());

        // applying the same file again changes nothing
        service.updateQuestionnaire("myhealth-intake", file);
        assertEquals(10, relationships().getQuestions().size());
        assertSame(q8, relationships().getQuestions().get(7));
    }

    @Test
    void aNewQuestionWhoseIdIsUsedElsewhereGetsARandomIdInstead() throws Exception {
        service.createQuestionnaire(loader.parseJsonToQuestionnaireDto(intakeWithoutNewQuestions()));
        otherQuestionIds.add("rel-q9");

        service.updateQuestionnaire("myhealth-intake", loader.parseJsonToQuestionnaireDto(intakeFile()));

        List<String> ids = relationships().getQuestions().stream().map(q -> q.getId()).toList();
        assertEquals("rel-q8", ids.get(7));
        assertNotEquals("rel-q9", ids.get(8), "another questionnaire's question is never taken over");
        assertEquals("rel-q10", ids.get(9));
    }
}
