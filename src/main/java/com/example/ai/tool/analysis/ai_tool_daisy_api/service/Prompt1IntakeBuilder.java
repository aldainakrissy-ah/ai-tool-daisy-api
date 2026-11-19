package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.example.ai.tool.analysis.phase_three_api.service.ClientDataDumpService;
import com.example.ai.tool.analysis.phase_three_api.service.ClientDataFormattingService;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionResponseDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireResponseDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.SectionDto;
import com.example.ai.tool.analysis.phase_two_api.service.QuestionnaireResponseService;
import com.example.ai.tool.analysis.phase_two_api.service.QuestionnaireService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The two client files of a Prompt 1 run, built from our own databases instead of an uploaded PDF or JSON, in the
 * form of Jan's files (personal data first):
 * <ul>
 *   <li>{@code general_client_info}: the portal data of the client (looked up by email) without contact details,
 *       request for help and technical columns, plus {@code client_name};</li>
 *   <li>{@code intake_questionnaire}: the client's latest saved MyHealth Intake (Adult), whatever its status, in
 *       Jan's sections → subsections (2.1–2.8) → questions {@code {id, question, answer}}, numbered by the numbers in
 *       the server's own question texts; unanswered questions are left out.</li>
 * </ul>
 * Cheapest first: our own intake tables, then the portal client lookup by email (two queries) with its checks, and
 * only then the portal dump (one query per table, on a remote database). Logs counts only, never names, emails or
 * answers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Prompt1IntakeBuilder {

    /** The MyHealth Intake (Adult). */
    static final String INTAKE_ID = "myhealth-intake";

    /** Personal data Jan's files leave out: the client's contact details and request for help, and the header. */
    private static final Set<String> LEFT_OUT = Set.of("title", "contact_details", "request_for_help");
    /**
     * Fields left out at any depth: contact data (also of others, e.g. a specialist's address and phone; the
     * formatting service copies the client's email into bio) and database columns that say nothing about the client.
     */
    private static final Set<String> LEFT_OUT_FIELDS = Set.of("email", "address", "telephone_country_code",
            "telephone_number", "id", "client_id", "client_bio_id", "client_gid_id", "created_at", "updated_at",
            "deleted_at");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final QuestionnaireResponseService responseService;
    private final QuestionnaireService questionnaireService;
    private final ClientDataDumpService dumpService;
    private final ClientDataFormattingService formattingService;
    private final ObjectMapper objectMapper;

    /** The two client files and the client's name they were built for (the name the apps find the result by). */
    public record ClientFiles(List<IntakeFile> files, String clientName) {
    }

    /**
     * @param clientName the client's name as the app shows it; checked against the portal. Null or blank = the portal
     *                   client's own name (e.g. a start from the intake app, which only knows the ID and email)
     * @throws Prompt1ClientDataException 409 when the intake has no answers yet, or the email matches more than one
     *                                    portal client or a client with another name, or the portal client has no
     *                                    name; 404 when no portal client has this email
     * @throws Prompt1NotReadyException   when the questionnaire is not on this server
     */
    public ClientFiles build(String clientId, String email, String clientName) {
        QuestionnaireResponseDto response = responseService.getUserResponsesByQuestionnaire(INTAKE_ID, clientId).stream()
                .filter(r -> r.getQuestionResponses() != null && !r.getQuestionResponses().isEmpty())
                .findFirst()
                .orElseThrow(() -> new Prompt1ClientDataException(HttpStatus.CONFLICT,
                        "The client has not filled in the MyHealth Intake (Adult) yet."));
        QuestionnaireDto questionnaire = questionnaireService.getQuestionnaireById(INTAKE_ID)
                .orElseThrow(() -> new Prompt1NotReadyException("The questionnaire " + INTAKE_ID + " is not on this server.", null));

        List<Map<String, Object>> clients = dumpService.findClientsByEmail(email);
        if (clients.isEmpty()) {
            throw new Prompt1ClientDataException(HttpStatus.NOT_FOUND, "No portal client has this email.");
        }
        if (clients.size() > 1) {
            throw new Prompt1ClientDataException(HttpStatus.CONFLICT,
                    "This email belongs to " + clients.size() + " portal clients; their data cannot be told apart.");
        }
        String portalName = portalName(clients.get(0));
        String name = clientName == null || clientName.isBlank() ? portalName : clientName.strip();
        if (name.isBlank()) {
            throw new Prompt1ClientDataException(HttpStatus.CONFLICT, "The portal client has no name to save the result under.");
        }
        // the app's client ID is not the portal's, so the name tells whether the email is this client's
        if (!sameName(portalName, name)) {
            throw new Prompt1ClientDataException(HttpStatus.CONFLICT,
                    "The email belongs to a portal client with another name than clientName.");
        }
        ObjectNode personal = personalDocument(formattingService.formatClientData(dumpService.getClientDataDump(clients)),
                name);
        ObjectNode intake = intakeDocument(questionnaire, response);
        log.info("Prompt 1 client files built: intake response {} with {} saved answer(s), personal data with {} part(s)",
                response.getId(), response.getQuestionResponses().size(), personal.path("structured_data").size());
        return new ClientFiles(List.of(new IntakeFile("personal_data.json", bytes(personal)),
                new IntakeFile("intake_questionnaire.json", bytes(intake))), name);
    }

    /** Jan's intake form from the questionnaire definition and one saved response. */
    ObjectNode intakeDocument(QuestionnaireDto questionnaire, QuestionnaireResponseDto response) {
        Map<String, QuestionResponseDto> answers = new HashMap<>();
        response.getQuestionResponses().forEach(r -> answers.put(r.getQuestionId(), r));

        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("document_type", "intake_questionnaire");
        doc.put("title", IntakeAnswers.dutch(questionnaire.getTitle()));
        ArrayNode sections = doc.putObject("structured_data").putArray("sections");
        Map<String, ObjectNode> top = new LinkedHashMap<>();
        for (SectionDto section : questionnaire.getSections()) {
            ArrayNode questions = answeredQuestions(section, answers);
            if (questions.isEmpty()) {
                continue;
            }
            IntakeAnswers.Numbered title = IntakeAnswers.split(IntakeAnswers.dutch(section.getTitle()));
            String id = title.number() != null ? title.number() : section.getId();
            int dot = id.indexOf('.');
            if (title.number() != null && dot > 0) {  // 2.1–2.8 are subsections of section 2, as in Jan's form
                ObjectNode parent = top.computeIfAbsent(id.substring(0, dot), key -> newSection(sections, key));
                ObjectNode sub = ((ArrayNode) parent.get("subsections")).addObject();
                sub.put("id", id);
                sub.put("title", title.text());
                sub.set("questions", questions);
            } else {
                ObjectNode own = top.computeIfAbsent(id, key -> newSection(sections, key));
                if (!own.has("title") && title.text() != null) {  // also when its subsections came first
                    own.put("title", title.text());
                }
                ((ArrayNode) own.get("questions")).addAll(questions);
            }
        }
        return doc;
    }

    /** The section's answered questions as {id, question, answer}, numbered by their texts. */
    private ArrayNode answeredQuestions(SectionDto section, Map<String, QuestionResponseDto> answers) {
        ArrayNode questions = objectMapper.createArrayNode();
        for (QuestionDto question : section.getQuestions()) {
            String answer = IntakeAnswers.answer(question, answers.get(question.getId()));
            if (answer != null) {
                IntakeAnswers.Numbered text = IntakeAnswers.split(IntakeAnswers.dutch(question.getText()));
                ObjectNode q = questions.addObject();
                q.put("id", text.number() != null ? text.number() : question.getId());
                q.put("question", text.text());
                q.put("answer", answer);
            }
        }
        return questions;
    }

    private static ObjectNode newSection(ArrayNode sections, String id) {
        ObjectNode section = sections.addObject();
        section.put("id", id);
        section.putArray("subsections");
        section.putArray("questions");
        return section;
    }

    /** First and family name as the apps show them; a missing part is left out (never the text "null"). */
    static String portalName(Map<String, Object> client) {
        return String.join(" ", java.util.stream.Stream.of(client.get("first_name"), client.get("family_name"))
                .filter(part -> part != null)
                .map(part -> String.valueOf(part))
                .toList());
    }

    /** The same name, ignoring case and extra spaces. */
    static boolean sameName(String a, String b) {
        return normalized(a).equals(normalized(b));
    }

    private static String normalized(String name) {
        return SPACES.matcher(String.valueOf(name).strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /** The portal data as Jan's personal data file, with client_name first. */
    ObjectNode personalDocument(Map<String, Object> formatted, String clientName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("client_name", clientName);
        formatted.forEach((key, value) -> {
            if (!LEFT_OUT.contains(key)) {
                data.put(key, plain(value));
            }
        });
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("document_type", "general_client_info");
        doc.set("structured_data", objectMapper.valueToTree(data));
        return doc;
    }

    /** Copies maps and lists without the left-out fields; dates as ISO text (2026-11-18), so Step 12 can read them. */
    private static Object plain(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, v) -> {
                if (!LEFT_OUT_FIELDS.contains(String.valueOf(key))) {
                    out.put(String.valueOf(key), plain(v));
                }
            });
            return out;
        }
        if (value instanceof Collection<?> list) {
            return list.stream().map(item -> plain(item)).toList();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof TemporalAccessor temporal) {
            return temporal.toString();
        }
        return value;
    }

    private byte[] bytes(ObjectNode doc) {
        try {
            return objectMapper.writeValueAsBytes(doc);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write a client file: " + e.getOriginalMessage(), e);
        }
    }
}
