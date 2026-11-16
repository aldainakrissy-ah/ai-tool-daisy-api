package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.DocumentSections;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.KeyText;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1TurnAnswers.FileWrite;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1TurnAnswers.Ingested;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Jan's run (pg_run31, release v06) replayed through our answer checks: his recorded model answers
 * (prompts/antwoord_&lt;turn&gt;.txt) go through our ingest turn by turn, with the backend steps in
 * between (Step 4 membership, Steps 6–7, signals), and every working file and the final JSON must
 * equal what his backend wrote. No OpenAI call; client data is never printed.
 */
class Prompt1TurnAnswersReplayTest {

    @Test
    void jansRecordedAnswersGiveJansWorkingFilesAndFinalJson() throws Exception {
        Path sources = dir("KNOWLEDGE_MARKDOWN_DIR");
        Path replay = dir("PROMPT1_REPLAY_DIR");
        Prompt1TurnConfig config = Prompt1TestFiles.turnConfig();
        Map<String, DocumentSections> documents = Prompt1SourcePackageService.selectVersions(
                Prompt1SourcePackageServiceTest.readLikeTheLoader(sources), Map.of());

        Map<String, String> files = new LinkedHashMap<>();
        for (String server : List.of("manifest.tsv", "observations.tsv", "observation_audit.tsv")) {
            files.put(server, jan(replay, server));
        }

        for (String turn : List.of("A1", "A2", "A3")) {
            apply(files, Prompt1TurnAnswers.ingest(config, turn, answer(replay, turn), files), turn);
            if (turn.equals("A2")) {
                Map<String, String> elem = new HashMap<>();
                for (KeyText key : Prompt1SourcePackageService.readKeys(documents,
                        config.ies().stream().map(ie -> "R5:ELEM:" + ie).toList())) {
                    elem.put(key.key().substring("R5:ELEM:".length()), key.text());
                }
                Prompt1TurnRules.Membership membership = Prompt1TurnRules.deriveIeCandidates(config.ies(), elem,
                        files.get("ie_core_filter.tsv"), files.get("ie_ftf_cells.tsv"), files.get("cts_admission.tsv"),
                        files.get("priority.tsv"));
                assertTrue(membership.complete(), "membership complete after A2");
                files.put("ie_membership.tsv", membership.membershipTsv());
                files.put("a1_ie_candidates.tsv", membership.candidatesTsv());
            }
        }

        Prompt1TurnRules.Selection selection = Prompt1TurnRules.selection(files.get("a1_selection.tsv"));
        List<String> keys = new ArrayList<>(List.of("R4:INDEX:" + selection.primary()));
        List<String> ies = new ArrayList<>(List.of(selection.primary()));
        ies.addAll(selection.secondaries());
        ies.forEach(ie -> keys.addAll(List.of("R4:" + ie + ":01", "R5:A2:" + ie)));
        Map<String, String> texts = new HashMap<>();
        Prompt1SourcePackageService.readKeys(documents, keys).forEach(key -> texts.put(key.key(), key.text()));
        List<String> hetas = Prompt1TurnRules.a3Candidates(selection.primary(), texts.get("R4:INDEX:" + selection.primary()));
        // turn B by the backend (Steps 6–8, v04z)
        files.put("a2_fields.tsv", Prompt1TurnRules.a2Fields(config.headers().get("a2_fields.tsv"), selection, texts));
        files.put("a3_candidates.tsv", Prompt1TurnRules.a3CandidatesTsv(config.headers().get("a3_candidates.tsv"),
                selection.primary(), hetas));
        files.put("a3_evidence_basis.tsv", Prompt1TurnRules.evidenceBasis(config.headers().get("a3_evidence_basis.tsv"),
                selection, files.get("a1_ie_candidates.tsv"), files.get("ie_core_filter.tsv"), files.get("priority.tsv"),
                files.get("directions.tsv"), files.get("links.tsv"),
                files.get("ie_membership.tsv"), files.get("meanings.tsv"), new ArrayList<>()));

        for (String heta : hetas) {
            List<KeyText> r3 = Prompt1SourcePackageService.readKeys(documents, List.of("R3:" + heta + ".5.2", "R3:" + heta + ".2"));
            apply(files, Prompt1TurnAnswers.ingestC(config, heta, answer(replay, "C_" + heta), files,
                    Prompt1TurnRules.r3Signals(r3.get(0).content()), Prompt1TurnOutput.r3HetaName(heta, r3.get(1).text())),
                    "C_" + heta);
        }

        // turn D by the backend (B2 from the matrix, v06): its answer must equal the one Jan's backend wrote
        Prompt1TurnOutput.B2 b2 = Prompt1TurnOutput.b2FromMatrix(config, files.get("a3_decisions.tsv"),
                files.get("a3_candidates.tsv"), files.get("heta_canon.tsv"), files.get("heta_cells.tsv"),
                Prompt1TurnOutput.b2Matrix(Files.readString(sources.resolve("07_B2_matrix.tsv")), config.b2Order()));
        sameText("antwoord_D.txt", answer(replay, "D"), b2.answer());
        String runDate = new ObjectMapper().readTree(Files.readString(replay.resolve("output.json"))).path("current_run_date").asText();
        // turns A1–B closed Steps 0–8 (the runner writes this in tasks.tsv through Prompt1RunFiles.closeSteps)
        StringBuilder tasks = new StringBuilder("step\tstatus\n");
        for (int step = 0; step <= 8; step++) {
            tasks.append(step).append("\tclosed\n");
        }
        files.put("tasks.tsv", tasks.toString());
        Ingested d = Prompt1TurnAnswers.ingestD(config, b2.answer(), files, replay.getFileName().toString(), runDate,
                key -> keyText(documents, key));
        apply(files, d, "D");

        List<String> compared = new ArrayList<>();
        try (var list = Files.list(replay)) {
            for (Path file : list.filter(path -> path.toString().endsWith(".tsv")).sorted().toList()) {
                String name = file.getFileName().toString();
                if (List.of("tasks.tsv", "retrieval_log.tsv", "usage.tsv").contains(name)) {
                    continue;
                }
                assertTrue(files.containsKey(name), name + " was not written by our replay");
                sameText(name, Files.readString(file), files.get(name));
                compared.add(name);
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode janOutput = mapper.readTree(Files.readString(replay.resolve("output.json")));
        List<String> differences = new ArrayList<>();
        diff("", janOutput, d.output(), differences);
        assertTrue(differences.isEmpty(), "output.json differs at: " + differences);
        System.out.printf(replay.getFileName() + " answers replayed: %d working files identical to Jan's %s, output.json identical "
                + "(A3 %s, B3 %s)%n", compared.size(), compared, d.output().path("a3").findValuesAsText("classification"),
                d.output().path("b3").findValuesAsText("heta_ref"));
    }

    /** The text of a source key, as the runner reads it (null when it is not found). */
    static String keyText(Map<String, DocumentSections> documents, String key) {
        KeyText text = Prompt1SourcePackageService.readKeys(documents, List.of(key)).get(0);
        return text.found() ? text.text() : null;
    }

    private static void apply(Map<String, String> files, Ingested ingested, String turn) {
        for (FileWrite write : ingested.files()) {
            files.put(write.name(), write.append() ? files.getOrDefault(write.name(), "") + write.content() : write.content());
        }
        if (!ingested.notes().isEmpty()) {
            System.out.printf("%s repairs: %d note(s)%n", turn, ingested.notes().size());
        }
    }

    /** JSON paths where the two trees differ (values are never printed). */
    private static void diff(String path, JsonNode a, JsonNode b, List<String> out) {
        if (a == null || b == null || a.getNodeType() != b.getNodeType()) {
            out.add(path.isEmpty() ? "/" : path);
            return;
        }
        if (a.isObject()) {
            Set<String> names = new TreeSet<>();
            a.fieldNames().forEachRemaining(names::add);
            b.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> diff(path + "/" + name, a.get(name), b.get(name), out));
        } else if (a.isArray()) {
            if (a.size() != b.size()) {
                out.add(path + " (size " + a.size() + " vs " + b.size() + ")");
                return;
            }
            for (int i = 0; i < a.size(); i++) {
                diff(path + "[" + i + "]", a.get(i), b.get(i), out);
            }
        } else if (!a.equals(b)) {
            out.add(path);
        }
    }

    /** Equal text, compared line by line; a difference names only the line and column, never the content. */
    private static void sameText(String file, String expected, String actual) {
        List<String> a = expected.replace("\r\n", "\n").strip().lines().filter(line -> !line.isBlank()).toList();
        List<String> b = actual.replace("\r\n", "\n").strip().lines().filter(line -> !line.isBlank()).toList();
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (!a.get(i).equals(b.get(i))) {
                String[] x = a.get(i).split("\t", -1);
                String[] y = b.get(i).split("\t", -1);
                int column = 0;
                while (column < Math.min(x.length, y.length) && x[column].equals(y[column])) {
                    column++;
                }
                fail(file + " differs at line " + (i + 1) + ", column " + (column + 1) + " (Jan " + x.length
                        + " columns, ours " + y.length + ")");
            }
        }
        assertEquals(a.size(), b.size(), file + " line count");
    }

    private static String answer(Path replay, String turn) throws Exception {
        return Files.readString(replay.resolve("prompts/antwoord_" + turn + ".txt"));
    }

    private static String jan(Path replay, String file) throws Exception {
        return Files.readString(replay.resolve(file));
    }

    private static Path dir(String variable) {
        String value = System.getenv(variable);
        assumeTrue(value != null && Files.isDirectory(Path.of(value)), variable + " not set");
        return Path.of(value);
    }
}
