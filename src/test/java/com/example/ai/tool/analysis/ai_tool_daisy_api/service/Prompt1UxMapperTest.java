package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jan's pg_run34 output in the result format the apps read today: the same codes as the production JSON (IEs, A3,
 * B1–B3) plus the B3 tier from the control JSON, and the result parses back as the app's {@code Prompt1Result}.
 * Compares codes only; skipped without Jan's files (PROMPT1_REPLAY_DIR).
 */
class Prompt1UxMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void pgRun34HasTheSameCodesAsTheProductionJson() throws Exception {
        Path replay = Prompt1TestFiles.dir("PROMPT1_REPLAY_DIR");
        JsonNode control = MAPPER.readTree(Files.readString(replay.resolve("output_controle.json")));
        JsonNode production = MAPPER.readTree(Files.readString(replay.resolve("output.json")));

        Prompt1Result result = Prompt1UxMapper.result(control, production, "Professional");

        assertEquals("Prompt_1", result.getPromptId());
        assertEquals(production.path("date").asText(), result.getDate());
        assertEquals(production.path("client-name").asText(), result.getClientName());
        assertEquals("Professional", result.getProfessionalName());

        Prompt1Result.A1 a1 = result.getA1();
        assertEquals("22.7", a1.getPrimaryIeRef());
        assertEquals(production.path("A1").path("primary_ie").asText(), a1.getPrimaryIeRef() + " " + a1.getPrimaryIeTitle());
        List<String> secondary = new ArrayList<>();
        a1.getSecondaryIe().forEach(ie -> secondary.add(ie.getIeRef() + " " + ie.getIeTitle()));
        assertEquals(Prompt1UxMapper.texts(production.path("A1").path("secondary_ie")), secondary);
        assertEquals(production.path("A1").path("teleonic_pattern").asText(), a1.getTeleonicPattern());
        assertTrue(a1.getSecondaryDynamics().isEmpty());

        assertTrue(result.getA2().getIeClusters().isEmpty());
        assertEquals(Prompt1UxMapper.texts(production.path("A3").path("direct")), result.getA3().getDirect());
        assertEquals(Prompt1UxMapper.texts(production.path("A3").path("indirect")), result.getA3().getIndirect());

        assertItems(production.path("B1"), result.getB1());
        assertItems(production.path("B2"), result.getB2());
        assertEquals(production.path("B3").size(), result.getB3().size());
        for (int i = 0; i < result.getB3().size(); i++) {
            Prompt1Result.B3Item item = result.getB3().get(i);
            JsonNode expected = production.path("B3").get(i);
            assertEquals(expected.path("nr").asInt(), item.getNr());
            assertEquals(expected.path("heta_id").asText(), item.getHetaId());
            assertEquals(expected.path("heta_json").asText(), item.getHetaJson());
            assertEquals(control.path("b3").get(i).path("tier").asText(), item.getTier());
        }

        // the app's format: it reads back as the same Prompt1Result
        assertEquals(result, Prompt1Result.fromJson(result.toJson()));
    }

    private static void assertItems(JsonNode expected, List<Prompt1Result.QuestionnaireItem> items) {
        assertEquals(expected.size(), items.size());
        for (int i = 0; i < items.size(); i++) {
            assertEquals(expected.get(i).path("nr").asInt(), items.get(i).getNr());
            assertEquals(expected.get(i).path("ID").asText(), items.get(i).getId());
            assertEquals(expected.get(i).path("label").asText(), items.get(i).getLabel());
            assertEquals(expected.get(i).path("qest_json").asText(), items.get(i).getQestJson());
        }
    }

}
