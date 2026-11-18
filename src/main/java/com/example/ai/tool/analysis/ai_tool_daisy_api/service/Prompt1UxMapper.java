package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result.B3Item;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result.IeRefTitleItem;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result.QuestionnaireItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * A VALID turn-based run in the result format the apps read today ({@code Prompt1Result}, saved in
 * {@code prompt1_results}), so the existing result screen shows it. The IEs and the B3 tier come from the control
 * JSON (refs and titles as separate fields there); the teleonic pattern, A3, B1 and B2 from the production JSON,
 * which already holds them in the app's shape. The parts Jan removed from Prompt 1 stay empty: A2, the secondary
 * dynamics, the uncertainty and the B3 source IE.
 */
final class Prompt1UxMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<QuestionnaireItem>> ITEMS = new TypeReference<>() {
    };

    private Prompt1UxMapper() {
    }

    static Prompt1Result result(JsonNode control, JsonNode production, String professionalName) {
        Prompt1Result result = new Prompt1Result();
        result.setPromptId(production.path("prompt_id").asText("Prompt_1"));
        result.setDate(production.path("date").asText(null));
        result.setClientName(production.path("client-name").asText(null));
        result.setProfessionalName(professionalName);

        JsonNode a1 = control.path("a1");
        Prompt1Result.A1 out1 = new Prompt1Result.A1();
        out1.setPrimaryIeRef(a1.path("primary_ie").path("ie_ref").asText(null));
        out1.setPrimaryIeTitle(title(a1.path("primary_ie")));
        List<IeRefTitleItem> secondary = new ArrayList<>();
        for (JsonNode ie : a1.path("secondary_ie")) {
            IeRefTitleItem item = new IeRefTitleItem();
            item.setIeRef(ie.path("ie_ref").asText(null));
            item.setIeTitle(title(ie));
            secondary.add(item);
        }
        out1.setSecondaryIe(secondary);
        out1.setTeleonicPattern(production.path("A1").path("teleonic_pattern").asText(null));
        out1.setSecondaryDynamics(List.of());
        result.setA1(out1);

        Prompt1Result.A2 a2 = new Prompt1Result.A2();
        a2.setIeClusters(List.of());
        a2.setFields(List.of());
        a2.setDomains(List.of());
        result.setA2(a2);

        Prompt1Result.A3 a3 = new Prompt1Result.A3();
        a3.setDirect(texts(production.path("A3").path("direct")));
        a3.setIndirect(texts(production.path("A3").path("indirect")));
        result.setA3(a3);

        result.setB1(MAPPER.convertValue(production.path("B1"), ITEMS));
        result.setB2(MAPPER.convertValue(production.path("B2"), ITEMS));
        List<B3Item> b3 = new ArrayList<>();
        for (JsonNode b : control.path("b3")) {
            B3Item item = new B3Item();
            item.setNr(b.path("nr").isNumber() ? b.path("nr").asInt() : null);
            item.setHetaId(b.path("heta_id").asText(null));
            item.setHetaJson(b.path("heta_json").asText(null));
            item.setTier(b.path("tier").asText(null));
            b3.add(item);
        }
        result.setB3(b3);
        return result;
    }

    /** The IE title cleaned as in the production JSON. */
    private static String title(JsonNode ie) {
        return ie.has("ie_title") ? Prompt1ProductionJson.clean(ie.path("ie_title").asText()) : null;
    }

    static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(x -> out.add(x.asText()));
        return out;
    }
}
