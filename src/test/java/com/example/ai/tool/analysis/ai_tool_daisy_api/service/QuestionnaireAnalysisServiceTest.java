package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.ResponseCreateParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The fallback retry of the stored-prompt analyses (Prompts 1 legacy, 2 and 3). */
class QuestionnaireAnalysisServiceTest {

    @Test
    void theFallbackRetrySendsNeitherTemperatureNorTopP() throws Exception {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .temperature(0.0)
                .topP(1.0)
                .input("test")
                .maxOutputTokens(6000)
                .store(true)
                .build();

        JsonNode retry = body(QuestionnaireAnalysisService.fallbackParams(params, "gpt-5"));

        assertEquals("gpt-5", retry.path("model").asText());
        assertFalse(retry.has("temperature"), "reasoning models reject temperature, even as null: " + retry);
        assertFalse(retry.has("top_p"), "reasoning models reject top_p, even as null: " + retry);
        assertEquals(6000, retry.path("max_output_tokens").asInt(), "the rest of the request is kept");
        assertTrue(retry.path("store").asBoolean());
        assertEquals(0.0, body(params).path("temperature").asDouble(), "the first call is unchanged");
    }

    private static JsonNode body(ResponseCreateParams params) throws Exception {
        return ObjectMappers.jsonMapper().readTree(ObjectMappers.jsonMapper().writeValueAsString(params._body()));
    }
}
