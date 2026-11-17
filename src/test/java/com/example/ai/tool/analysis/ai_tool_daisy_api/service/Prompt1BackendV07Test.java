package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Jan's backend v06a–v07 (16 Nov 2026): the core filter completed from the yes rows (D13), the production JSON
 * helpers and schema check (D14), and the backend version a release must be made for.
 */
class Prompt1BackendV07Test {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HEAD = "ie_ref\tftf_id\tpasses\telement_ids\tmeaning_ids\tbasis";

    @Test
    void theCoreFilterIsCompletedWithNoRowsInIeAndFtfOrder() {
        String yesOnly = HEAD + "\n"
                + "22.3\tFTF_005\tyes\t22.3-A1-E1\tM001\tbasis\n"
                + "22.2\tFTF_009\tno\t—\t—\twritten no\n"   // a written no row is kept as written
                + "22.9\tFTF_005\tyes\t22.9-A1-E1\tM002\toutside the grid\n";  // not one of the IEs: kept at the end

        Prompt1TurnRules.CompletedCoreFilter completed =
                Prompt1TurnRules.completeCoreFilter(yesOnly, List.of("FTF_005", "FTF_009"), List.of("22.2", "22.3"));

        assertEquals(String.join("\n", HEAD,
                "22.2\tFTF_005\tno\t—\t—\t—",
                "22.2\tFTF_009\tno\t—\t—\twritten no",
                "22.3\tFTF_005\tyes\t22.3-A1-E1\tM001\tbasis",
                "22.3\tFTF_009\tno\t—\t—\t—",
                "22.9\tFTF_005\tyes\t22.9-A1-E1\tM002\toutside the grid"), completed.text());
        assertEquals(2, completed.added());
    }

    @Test
    void theAdmittedFtfsFollowCtsAdmission() {
        String cts = "direction_id\tdecision\tbasis\nFD_009\tadmitted\tx\nFD_002\texcluded\tx\nFD_005\tadmitted\tx\n";
        assertEquals(List.of("FTF_009", "FTF_005"), Prompt1TurnRules.admittedFtfs(cts));
        assertEquals("FTF_016", Prompt1Tsv.ftfOf("FD_016"));
        assertEquals("FD_016", Prompt1Tsv.directionOf("FTF_016"));
    }

    @Test
    void cleanRemovesWhatTheSchemaForbids() {
        assertEquals("score (0–4) of (x) kleiner dan 3", Prompt1ProductionJson.clean(" score [0–4] of {x} <3 "));
        assertEquals("a groter dan b", Prompt1ProductionJson.clean("a>b"));
    }

    @Test
    void schemaErrorsCheckTheKeywordsJansCheckerUses() throws Exception {
        JsonNode schema = MAPPER.readTree("""
                {"type": "object", "required": ["id", "items"], "additionalProperties": false,
                 "properties": {"id": {"$ref": "#/$defs/clean"}, "kind": {"type": "string", "enum": ["a", "b"]},
                                "items": {"type": "array", "maxItems": 2, "items": {"type": "integer", "minimum": 1}}},
                 "$defs": {"clean": {"type": "string", "pattern": "^[^<>\\\\[\\\\]{}]*$", "maxLength": 5}}}""");

        assertEquals(List.of(), Prompt1ProductionJson.schemaErrors(MAPPER.readTree("{\"id\": \"ok\", \"items\": [1, 2]}"), schema));
        List<String> errors = Prompt1ProductionJson.schemaErrors(MAPPER.readTree(
                "{\"id\": \"[too long]\", \"kind\": \"c\", \"items\": [0, true, 3], \"extra\": 1}"), schema);
        assertTrue(errors.contains("$: extra key extra"), errors.toString());
        assertTrue(errors.contains("$.id: length 10 out of range"), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("$.id: '[too long]' does not match")), errors.toString());
        assertTrue(errors.contains("$.kind: 'c' not in enum"), errors.toString());
        assertTrue(errors.contains("$.items: 3 items out of range"), errors.toString());
        assertTrue(errors.contains("$.items[0]: 0 out of range"), errors.toString());
        assertTrue(errors.contains("$.items[1]: expected integer, got bool"), errors.toString());
        assertEquals(List.of("$: missing id", "$: missing items"),
                Prompt1ProductionJson.schemaErrors(MAPPER.readTree("{}"), schema));
    }

    /** A schema form the checker does not know is an error, never a silent pass. */
    @Test
    void anUnknownSchemaFormIsRefusedNotIgnored() throws Exception {
        JsonNode schema = MAPPER.readTree("""
                {"type": "object", "properties": {"a": {"type": ["string", "null"]}, "b": {"$ref": "#/definitions/x"},
                 "c": {"type": "date"}}}""");
        List<String> errors = Prompt1ProductionJson.schemaErrors(MAPPER.readTree("{\"a\": 1, \"b\": 2, \"c\": \"x\"}"), schema);
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("$.a: schema type") && e.contains("not supported")), errors.toString());
        assertTrue(errors.contains("$.b: schema $ref #/definitions/x not found in $defs"), errors.toString());
        assertTrue(errors.contains("$.c: expected date, got str"), errors.toString());
    }

    /** A turn configuration without a text this backend uses is refused when it is read, not partway through a run. */
    @Test
    void aTurnConfigurationWithoutATextItNeedsIsRefused() throws Exception {
        ObjectNode config = (ObjectNode) MAPPER.readTree(Files.readAllBytes(Prompt1TestFiles.turnConfigFile()));
        ((ObjectNode) config.get("texts")).remove("a3YesOnly");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Prompt1TurnConfig.parse(MAPPER.writeValueAsBytes(config)));
        assertTrue(e.getMessage().contains("a3YesOnly"), e.getMessage());
    }

    /** Jan's production output.json of pg_run34 conforms to his schema; breaking it is caught. */
    @Test
    void jansProductionJsonConformsAndABrokenOneDoesNot() throws Exception {
        Path sources = Prompt1TestFiles.dir("KNOWLEDGE_MARKDOWN_DIR");
        Path replay = Prompt1TestFiles.dir("PROMPT1_REPLAY_DIR");
        JsonNode schema = Prompt1TestFiles.productionSchema(sources);
        ObjectNode output = (ObjectNode) MAPPER.readTree(Files.readString(replay.resolve("output.json")));
        assertEquals(List.of(), Prompt1ProductionJson.schemaErrors(output, schema));

        output.put("professional-name", "x");
        ((ObjectNode) output.get("A1")).put("teleonic_pattern", "a < b");
        ((ArrayNode) output.get("A3").get("direct")).add("NOT-A-HETA");
        List<String> errors = Prompt1ProductionJson.schemaErrors(output, schema);
        assertTrue(errors.contains("$: extra key professional-name"), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("$.A1.teleonic_pattern: 'a < b' does not match")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("$.A3.direct[") && e.endsWith("not in enum")), errors.toString());
    }

    /** A release made for another backend (e.g. P1-v0.136, backend v06) is refused, so it cannot run on this code. */
    @Test
    void aReleaseForAnotherBackendIsRefused() throws Exception {
        ObjectNode config = (ObjectNode) MAPPER.readTree(Files.readAllBytes(Prompt1TestFiles.turnConfigFile()));
        assertEquals(Prompt1TurnConfig.BACKEND, Prompt1TurnConfig.parse(MAPPER.writeValueAsBytes(config)).backend());

        config.remove("backend");
        IllegalArgumentException older = assertThrows(IllegalArgumentException.class,
                () -> Prompt1TurnConfig.parse(MAPPER.writeValueAsBytes(config)));
        assertTrue(older.getMessage().contains("made for backend v06 or older"), older.getMessage());
        config.put("backend", "v08");
        assertThrows(IllegalArgumentException.class, () -> Prompt1TurnConfig.parse(MAPPER.writeValueAsBytes(config)));
    }

}
