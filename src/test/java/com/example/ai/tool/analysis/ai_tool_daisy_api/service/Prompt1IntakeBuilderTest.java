package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.example.ai.tool.analysis.phase_three_api.service.ClientDataDumpService;
import com.example.ai.tool.analysis.phase_three_api.service.ClientDataFormattingService;
import com.example.ai.tool.analysis.phase_two_api.pojo.LocalizedTextDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionResponseDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireResponseDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.SectionDto;
import com.example.ai.tool.analysis.phase_two_api.service.QuestionnaireResponseService;
import com.example.ai.tool.analysis.phase_two_api.service.QuestionnaireService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The client files built from our databases: Jan's intake form from the server's questionnaire (numbers from the
 * question texts, also with random IDs and extra questions), personal data without contact details, and the order
 * and number of database calls per build.
 */
class Prompt1IntakeBuilderTest {

    private QuestionnaireResponseService responseService;
    private QuestionnaireService questionnaireService;
    private ClientDataDumpService dumpService;
    private ClientDataFormattingService formattingService;
    private Prompt1IntakeBuilder builder;
    private QuestionnaireDto questionnaire;

    @BeforeEach
    void setUp() throws Exception {
        responseService = mock(QuestionnaireResponseService.class);
        questionnaireService = mock(QuestionnaireService.class);
        dumpService = mock(ClientDataDumpService.class);
        formattingService = mock(ClientDataFormattingService.class);
        builder = new Prompt1IntakeBuilder(responseService, questionnaireService, dumpService, formattingService,
                Prompt1TestFiles.LENIENT);
        questionnaire = productionLike();
        when(questionnaireService.getQuestionnaireById(Prompt1IntakeBuilder.INTAKE_ID)).thenReturn(Optional.of(questionnaire));
    }

    /** The repo's MyHealth Intake (Adult) as production has it: random IDs in 2.3 and an extra question 4.7. */
    private static QuestionnaireDto productionLike() throws Exception {
        QuestionnaireDto q = Prompt1TestFiles.intakeQuestionnaire();
        for (SectionDto section : q.getSections()) {
            for (QuestionDto question : section.getQuestions()) {
                if (String.valueOf(question.getText().getEn()).startsWith("2.3.")) {
                    question.setId("emotional-q" + UUID.randomUUID());
                }
            }
            if (String.valueOf(section.getTitle().getNl()).startsWith("4.")) {
                QuestionDto extra = new QuestionDto();
                extra.setId("timeline-q7-random");
                extra.setType("text");
                extra.setText(new LocalizedTextDto("4.7 An extra question", "4.7 Een extra vraag"));
                section.getQuestions().add(extra);
            }
        }
        return q;
    }

    private String idOf(String number) {
        return questionnaire.getSections().stream().flatMap(s -> s.getQuestions().stream())
                .filter(q -> number.equals(IntakeAnswers.number(q.getText().getNl())))
                .map(q -> q.getId()).findFirst().orElseThrow();
    }

    private QuestionResponseDto answer(String number, Consumer<QuestionResponseDto> fill) {
        QuestionResponseDto r = new QuestionResponseDto();
        r.setQuestionId(idOf(number));
        fill.accept(r);
        return r;
    }

    private QuestionnaireResponseDto filled() {
        QuestionnaireResponseDto response = new QuestionnaireResponseDto();
        response.setId(7L);
        response.setQuestionResponses(List.of(
                answer("1.1", r -> r.setAnswerText("Moe maar hoopvol")),
                answer("1.2", r -> r.setAnswerText("  ")),
                answer("2.1.1", r -> r.setAnswerBoolean(true)),
                answer("2.1.2", r -> r.setAnswerText("moderate")),
                answer("2.3.1", r -> r.setAnswerBoolean(false)),
                answer("4.7", r -> r.setAnswerText("Extra antwoord")),
                answer("5.8", r -> r.setAnswerText("3")),
                answer("5.9", r -> r.setAnswerText("0"))));
        return response;
    }

    private static QuestionnaireResponseDto empty() {
        QuestionnaireResponseDto response = new QuestionnaireResponseDto();
        response.setId(8L);
        response.setQuestionResponses(List.of());
        return response;
    }

    private static Map<String, Object> portalClient(String firstName, String familyName) {
        return Map.of("id", 1L, "first_name", firstName, "family_name", familyName);
    }

    /** A build that is refused, with its status. */
    private HttpStatus refused(String email) {
        return assertThrows(Prompt1ClientDataException.class, () -> builder.build("c-1", email, "Test Client")).getStatus();
    }

    @Test
    void intakeHasJansFormNumberedByTheQuestionTexts() {
        JsonNode doc = builder.intakeDocument(questionnaire, filled());
        assertEquals("intake_questionnaire", doc.path("document_type").asText());
        JsonNode sections = doc.path("structured_data").path("sections");
        assertEquals(List.of("1", "2", "4", "5"), ids(sections));
        assertEquals("Algemene oriëntatie en welbevinden", sections.get(0).path("title").asText());
        assertEquals(List.of("1.1"), ids(sections.get(0).path("questions")));  // 1.2 is blank: left out
        assertEquals("Moe maar hoopvol", sections.get(0).path("questions").get(0).path("answer").asText());

        JsonNode two = sections.get(1);
        assertEquals(0, two.path("questions").size());
        assertEquals(List.of("2.1", "2.3"), ids(two.path("subsections")));
        JsonNode energy = two.path("subsections").get(0);
        assertEquals("Energie en vermoeidheid", energy.path("title").asText());
        assertEquals(List.of("2.1.1", "2.1.2"), ids(energy.path("questions")));
        assertEquals("Heb je klachten?", energy.path("questions").get(0).path("question").asText());
        assertEquals("Ja", energy.path("questions").get(0).path("answer").asText());
        assertEquals("Matig", energy.path("questions").get(1).path("answer").asText());
        JsonNode emotional = two.path("subsections").get(1).path("questions");
        assertEquals("2.3.1", emotional.get(0).path("id").asText());  // found by number, not by its random ID
        assertEquals("Nee", emotional.get(0).path("answer").asText());

        assertEquals(List.of("4.7"), ids(sections.get(2).path("questions")));
        JsonNode five = sections.get(3).path("questions");
        assertEquals(List.of("5.8", "5.9"), ids(five));
        assertEquals("3", five.get(0).path("answer").asText());
        assertEquals("0", five.get(1).path("answer").asText());
    }

    @Test
    void personalDataWithoutContactDetailsRequestForHelpOrTechnicalColumns() {
        Map<String, Object> bio = new LinkedHashMap<>();
        bio.put("id", 12L);
        bio.put("client_id", 34L);
        bio.put("date_of_birth", java.sql.Date.valueOf("1980-05-17"));
        bio.put("first_name", "Test");
        bio.put("email", "test@example.org");
        bio.put("created_at", java.sql.Timestamp.valueOf("2026-01-02 03:04:05"));
        Map<String, Object> formatted = new LinkedHashMap<>();
        formatted.put("title", "General client info for Test Client");
        formatted.put("contact_details", Map.of("telephone_number", "0600000000"));
        formatted.put("bio", bio);
        formatted.put("request_for_help", "Hulpvraag");
        formatted.put("allergies", List.of(Map.of("id", 1L, "option_allergy_id", "Pollen")));
        Map<String, Object> specialist = new LinkedHashMap<>();
        specialist.put("for_what", "Rug");
        specialist.put("name", "Dr. Test");
        specialist.put("address", "Straat 1");
        specialist.put("telephone_country_code", "+31");
        specialist.put("telephone_number", "0100000000");
        formatted.put("specialists", List.of(specialist));

        JsonNode doc = builder.personalDocument(formatted, "Test Client");
        assertEquals("general_client_info", doc.path("document_type").asText());
        JsonNode data = doc.path("structured_data");
        assertEquals(List.of("client_name", "bio", "allergies", "specialists"), fieldNames(data));
        assertEquals("Test Client", data.path("client_name").asText());
        assertEquals(List.of("date_of_birth", "first_name"), fieldNames(data.path("bio")));
        assertEquals("1980-05-17", data.path("bio").path("date_of_birth").asText());  // readable by Step 12
        assertEquals(List.of("option_allergy_id"), fieldNames(data.path("allergies").get(0)));
        assertEquals(List.of("for_what", "name"), fieldNames(data.path("specialists").get(0)));  // no address or phone
    }

    @Test
    void namesMatchIgnoringCaseAndSpaces() {
        assertTrue(Prompt1IntakeBuilder.sameName("Test  Client ", "test client"));
        assertFalse(Prompt1IntakeBuilder.sameName("Test Client", "Other Client"));
        Map<String, Object> noFamilyName = new HashMap<>();
        noFamilyName.put("first_name", "Test");
        noFamilyName.put("family_name", null);
        assertEquals("Test", Prompt1IntakeBuilder.portalName(noFamilyName));  // never "Test null"
        assertEquals("Test Client", Prompt1IntakeBuilder.portalName(portalClient("Test", "Client")));
    }

    @Test
    void buildReadsEachSourceOnceCheapestFirstAndPersonalDataFirstInTheFiles() {
        when(responseService.getUserResponsesByQuestionnaire(Prompt1IntakeBuilder.INTAKE_ID, "c-1"))
                .thenReturn(List.of(empty(), filled()));  // newest first: the newest one without answers is skipped
        List<Map<String, Object>> clients = List.of(portalClient("Test", "Client"));
        Map<String, List<Map<String, Object>>> dump = Map.of("clients", clients);
        when(dumpService.findClientsByEmail("test@example.org")).thenReturn(clients);
        when(dumpService.getClientDataDump(clients)).thenReturn(dump);
        when(formattingService.formatClientData(dump)).thenReturn(new LinkedHashMap<>(Map.of("bio", Map.of(
                "date_of_birth", "1980-05-17"))));

        List<IntakeFile> files = builder.build("c-1", "test@example.org", "Test Client");

        assertEquals(List.of("personal_data.json", "intake_questionnaire.json"), files.stream().map(f -> f.name()).toList());
        var order = inOrder(responseService, questionnaireService, dumpService, formattingService);
        order.verify(responseService).getUserResponsesByQuestionnaire(Prompt1IntakeBuilder.INTAKE_ID, "c-1");
        order.verify(questionnaireService).getQuestionnaireById(Prompt1IntakeBuilder.INTAKE_ID);
        order.verify(dumpService).findClientsByEmail("test@example.org");
        order.verify(dumpService).getClientDataDump(clients);
        order.verify(formattingService).formatClientData(dump);
        verifyNoMoreInteractions(responseService, questionnaireService, dumpService, formattingService);

        // the backend accepts the two files as a run's client data
        Prompt1TurnInventory.Inventory inventory = Prompt1TurnInventory.build(files);
        Prompt1TurnInventory.requireClientData(inventory);
        List<String> labels = Prompt1Tsv.rows(inventory.observations()).stream().map(o -> o.get("source_label")).toList();
        assertTrue(labels.containsAll(List.of("client_name", "date_of_birth", "1.1", "2.1.1", "4.7", "5.8", "5.9")), labels.toString());
    }

    @Test
    void noFilledIntakeIs409WithoutAskingThePortal() {
        when(responseService.getUserResponsesByQuestionnaire(any(), any())).thenReturn(List.of(empty()));
        assertEquals(HttpStatus.CONFLICT, refused("test@example.org"));
        verifyNoInteractions(dumpService, formattingService, questionnaireService);
    }

    @Test
    void unknownSharedOrOtherClientsEmailIsRefusedBeforeTheDump() {
        when(responseService.getUserResponsesByQuestionnaire(any(), any())).thenReturn(List.of(filled()));
        when(dumpService.findClientsByEmail("none@example.org")).thenReturn(List.of());
        when(dumpService.findClientsByEmail("two@example.org")).thenReturn(
                List.of(portalClient("Test", "Client"), portalClient("Test", "Client")));
        when(dumpService.findClientsByEmail("other@example.org")).thenReturn(List.of(portalClient("Other", "Client")));

        assertEquals(HttpStatus.NOT_FOUND, refused("none@example.org"));
        assertEquals(HttpStatus.CONFLICT, refused("two@example.org"));
        // another client's email: their portal data must not be mixed with this client's intake
        assertEquals(HttpStatus.CONFLICT, refused("other@example.org"));
        Prompt1ClientDataException other = assertThrows(Prompt1ClientDataException.class,
                () -> builder.build("c-1", "other@example.org", "Test Client"));
        assertFalse(other.getMessage().contains("Other") || other.getMessage().contains("@"));  // no personal data
        verify(dumpService, never()).getClientDataDump(anyList());  // the expensive dump never runs
        verifyNoInteractions(formattingService);
    }

    @Test
    void aMissingQuestionnaireIsNotReady() {
        when(responseService.getUserResponsesByQuestionnaire(any(), any())).thenReturn(List.of(filled()));
        when(questionnaireService.getQuestionnaireById(Prompt1IntakeBuilder.INTAKE_ID)).thenReturn(Optional.empty());
        assertThrows(Prompt1NotReadyException.class, () -> builder.build("c-1", "test@example.org", "Test Client"));
        verifyNoInteractions(dumpService);
    }

    private static List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(x -> out.add(x.path("id").asText()));
        return out;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.fieldNames().forEachRemaining(name -> out.add(name));
        return out;
    }
}
