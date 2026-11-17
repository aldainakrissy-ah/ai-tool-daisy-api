package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.DocumentSections;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.KeyText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Replays Jan's run pg_run34 (gpt-6.1-terra, SM v0.139) through our fixed rules: given his
 * closed working files and our knowledge sections, each rule must give exactly his server's file.
 * Needs KNOWLEDGE_MARKDOWN_DIR (reference documents), PROMPT1_REPLAY_DIR (pg_run34) and
 * PROMPT1_INTAKE_DIR (the Klaas files); skipped otherwise. Client data is never printed.
 */
class Prompt1TurnRulesReplayTest {

    private static Path replay;
    private static Path intake;
    private static Map<String, DocumentSections> documents;
    private static Prompt1TurnConfig config;

    @BeforeAll
    static void setUp() throws Exception {
        Path sources = Prompt1TestFiles.dir("KNOWLEDGE_MARKDOWN_DIR");
        replay = Prompt1TestFiles.dir("PROMPT1_REPLAY_DIR");
        intake = Prompt1TestFiles.dir("PROMPT1_INTAKE_DIR");
        documents = Prompt1SourcePackageService.selectVersions(
                Prompt1SourcePackageServiceTest.readLikeTheLoader(sources), Map.of());
        config = Prompt1TestFiles.turnConfig();
    }

    @Test
    void step0InventoryEqualsJansServerFiles() throws Exception {
        List<IntakeFile> files = List.of(intakeFile("Klaas_persoonlijke_gegevens zonder hulpvraag.json"),
                intakeFile("Klaas_intake_vragenlijst.json"));

        Prompt1TurnInventory.Inventory inventory = Prompt1TurnInventory.build(files);

        assertSame("manifest.tsv", jan("manifest.tsv"), inventory.manifest());
        assertSame("observations.tsv", jan("observations.tsv"), inventory.observations());
        assertSame("observation_audit.tsv", jan("observation_audit.tsv"), inventory.audit());
        assertEquals(101, inventory.observationCount());
    }

    @Test
    void step4MembershipEqualsJansServerFiles() throws Exception {
        Map<String, String> elem = new HashMap<>();
        for (KeyText key : Prompt1SourcePackageService.readKeys(documents,
                config.ies().stream().map(ie -> "R5:ELEM:" + ie).toList())) {
            assertTrue(key.found(), key.key());
            elem.put(key.key().substring("R5:ELEM:".length()), key.text());
        }

        Prompt1TurnRules.Membership membership = Prompt1TurnRules.deriveIeCandidates(config.ies(), elem,
                jan("ie_core_filter.tsv"), jan("ie_ftf_cells.tsv"), jan("cts_admission.tsv"), jan("priority.tsv"));

        assertTrue(membership.complete(), "missing rows: " + membership.missing().size());
        assertSame("ie_membership.tsv", jan("ie_membership.tsv"), membership.membershipTsv());
        assertEquals(jan("a1_ie_candidates.tsv").strip(), membership.candidatesTsv().strip());
    }

    @Test
    void step7TitlesAndSignalsEqualJansFiles() throws Exception {
        Prompt1TurnRules.Selection selection = Prompt1TurnRules.selection(jan("a1_selection.tsv"));
        assertEquals("22.7", selection.primary());

        List<String> keys = new ArrayList<>();
        keys.add("R4:INDEX:" + selection.primary());
        for (String ie : ieList(selection)) {
            keys.add("R4:" + ie + ":01");
        }
        Map<String, String> texts = new HashMap<>();
        for (KeyText key : Prompt1SourcePackageService.readKeys(documents, keys)) {
            assertTrue(key.found(), key.key());
            texts.put(key.key(), key.text());
        }

        List<String> refs = Prompt1TurnRules.a3Candidates(selection.primary(), texts.get("R4:INDEX:" + selection.primary()));
        assertEquals(jan("a3_candidates.tsv").strip(), Prompt1TurnRules.a3CandidatesTsv(
                config.headers().get("a3_candidates.tsv"), selection.primary(), refs).strip());
        // v07: the IE titles of the control JSON come from the header of KEY R4:<ie>:01
        com.fasterxml.jackson.databind.JsonNode control = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(jan("output_controle.json")).path("a1");
        Map<String, String> janTitles = new HashMap<>();
        janTitles.put(control.path("primary_ie").path("ie_ref").asText(), control.path("primary_ie").path("ie_title").asText());
        control.path("secondary_ie").forEach(x -> janTitles.put(x.path("ie_ref").asText(), x.path("ie_title").asText()));
        for (String ie : ieList(selection)) {
            assertEquals(janTitles.get(ie), Prompt1TurnOutput.ieTitle(texts.get("R4:" + ie + ":01")), "title of " + ie);
        }

        Map<String, TreeMap<Integer, String>> janSignals = new HashMap<>();
        for (Map<String, String> row : Prompt1Tsv.rows(jan("heta_canon.tsv"))) {
            if ("signal".equals(row.get("kind"))) {
                janSignals.computeIfAbsent(row.get("heta_ref"), h -> new TreeMap<>())
                        .put(Prompt1Tsv.number(row.get("canonical_id")), row.get("excerpt"));
            }
        }
        for (String heta : refs) {
            KeyText section = Prompt1SourcePackageService.readKeys(documents, List.of("R3:" + heta + ".5.2")).get(0);
            List<String> ours = Prompt1TurnRules.r3Signals(section.content());
            List<String> his = new ArrayList<>(janSignals.getOrDefault(heta, new TreeMap<>()).values());
            assertEquals(his, ours, "signals of " + heta);
        }
        System.out.printf(replay.getFileName() + " replay: %d candidate HETAs, %d IE titles, signals per HETA %s%n", refs.size(),
                janTitles.size(), janSignals.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue().size()).sorted().toList());
    }

    /**
     * The six source-text forms Jan's backend parsers rely on (his email of 10 Nov 2026), checked on
     * our key texts for every IE and HETA, so a document or splitter change cannot break them unseen.
     */
    @Test
    void sourceTextKeepsTheFormsJansParsersRelyOn() {
        java.util.regex.Pattern keyLine = java.util.regex.Pattern.compile("^KEY: \\S+ \\| .+");
        java.util.regex.Pattern r4Title = java.util.regex.Pattern.compile("^KEY: R4:\\S+ \\| IE \\S+ (.+?) \\|");
        java.util.regex.Pattern members = java.util.regex.Pattern.compile("members:\\s*\\n((?:\\s*22\\.\\d+\\s*\\n)+)");
        List<String> problems = new ArrayList<>();
        for (String ie : config.ies()) {
            Map<String, KeyText> keys = new HashMap<>();
            Prompt1SourcePackageService.readKeys(documents, List.of("R4:" + ie + ":01", "R4:INDEX:" + ie, "R5:A2:" + ie))
                    .forEach(key -> keys.put(key.key(), key));
            keys.values().forEach(key -> {
                if (!key.found() || !keyLine.matcher(key.text().lines().findFirst().orElse("")).matches()) {
                    problems.add(key.key() + ": no 'KEY: <key> | …' line");
                }
            });
            if (!r4Title.matcher(keys.get("R4:" + ie + ":01").text()).find()) {
                problems.add("R4:" + ie + ":01: first line not 'KEY: R4:… | IE <ref> <title> | …'");
            }
            if (!members.matcher(keys.get("R4:INDEX:" + ie).text() + "\n").find()) {
                problems.add("R4:INDEX:" + ie + ": no 'members:' with one ref per line");
            }
            List<String> fields = keys.get("R5:A2:" + ie).text().lines().map(line -> line.strip())
                    .filter(line -> line.startsWith("- FIELD_" + ie + "_")).toList();
            if (fields.isEmpty() || fields.stream().anyMatch(line -> line.substring(2).split(" \\| ", -1).length != 6)) {
                problems.add("R5:A2:" + ie + ": field rows not '- FIELD_… | …' in 6 parts");
            }
        }
        int hetas = 0;
        for (int i = 24; i <= 66; i++) {
            String heta = "22." + i;
            KeyText signals = Prompt1SourcePackageService.readKeys(documents, List.of("R3:" + heta + ".5.2")).get(0);
            if (!signals.found() || Prompt1TurnRules.r3Signals(signals.content()).isEmpty()
                    || !signals.content().contains("These signals")) {
                problems.add("R3:" + heta + ".5.2: not an intro line ending in ':', signal lines and 'These signals …'");
            }
            KeyText record = Prompt1SourcePackageService.readKeys(documents, List.of("06:" + heta)).get(0);
            if (!record.found() || !record.text().contains("HETA_JSON_IDENTIFIER:")) {
                problems.add("06:" + heta + ": no 'HETA_JSON_IDENTIFIER:'");
            }
            hetas++;
        }
        assertTrue(problems.isEmpty(), "source forms: " + problems);
        System.out.printf("source forms Jan's parsers rely on: OK for %d IEs and %d HETAs%n", config.ies().size(), hetas);
    }

    private static List<String> ieList(Prompt1TurnRules.Selection selection) {
        List<String> ies = new ArrayList<>(List.of(selection.primary()));
        ies.addAll(selection.secondaries());
        return ies;
    }

    /** Equal text, compared line by line; a difference names only the line and column counts, never the content. */
    private static void assertSame(String file, String expected, String actual) {
        List<String> a = expected.replace("\r\n", "\n").strip().lines().toList();
        List<String> b = actual.replace("\r\n", "\n").strip().lines().toList();
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (!a.get(i).equals(b.get(i))) {
                fail(file + " differs at line " + (i + 1) + " (Jan " + a.get(i).split("\t", -1).length + " columns, ours "
                        + b.get(i).split("\t", -1).length + "); first differing column "
                        + firstDifferentColumn(a.get(i), b.get(i)));
            }
        }
        assertEquals(a.size(), b.size(), file + " line count");
    }

    private static int firstDifferentColumn(String a, String b) {
        String[] x = a.split("\t", -1);
        String[] y = b.split("\t", -1);
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            if (!x[i].equals(y[i])) {
                return i + 1;
            }
        }
        return Math.min(x.length, y.length) + 1;
    }

    private static String jan(String file) throws Exception {
        return Files.readString(replay.resolve(file));
    }

    private static IntakeFile intakeFile(String name) throws Exception {
        return new IntakeFile(name, Files.readAllBytes(intake.resolve(name)));
    }

}
