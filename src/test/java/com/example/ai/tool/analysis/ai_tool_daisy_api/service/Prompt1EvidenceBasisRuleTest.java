package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Jan's Step 8 backend rule against his run (pg_run31, written by his backend rule since v04z)
 * (PROMPT1_REPLAY_DIR) and, when PROMPT1_LIVE_RUNS_DIR is set, our live runs of 10 Nov (one folder
 * per run). Meanings, roles, directions and FTFs are compared; link differences are reported.
 * Client text is never printed.
 */
class Prompt1EvidenceBasisRuleTest {

    private static final List<String> ID_COLUMNS = List.of("meaning_id", "role", "direction_id", "ftf_id");

    @Test
    void ruleGivesTheMeaningsAndRolesOfJansRun() throws Exception {
        String value = System.getenv("PROMPT1_REPLAY_DIR");
        assumeTrue(value != null && Files.isDirectory(Path.of(value)), "PROMPT1_REPLAY_DIR not set");
        Result result = compare(Path.of(value));
        assertEquals(List.of(), result.idDifferences, "meanings, roles, directions and FTFs");
        assertEquals(List.of(), result.copyDifferences, "copy columns from meanings.tsv");
        // since v04z Jan's backend writes the evidence basis with this rule, so the links are equal too
        assertEquals(List.of(), result.linkDifferences, "link columns");
        System.out.printf("%s: %d rows, identical to Jan's backend (meanings, roles, directions, FTFs, links, copies)%n",
                Path.of(value).getFileName(), result.rows);
    }

    @Test
    void ruleOnOurLiveRuns() throws Exception {
        String value = System.getenv("PROMPT1_LIVE_RUNS_DIR");
        assumeTrue(value != null && Files.isDirectory(Path.of(value)), "PROMPT1_LIVE_RUNS_DIR not set");
        try (var list = Files.list(Path.of(value))) {
            for (Path run : list.filter(path -> Files.isDirectory(path)).sorted().toList()) {
                Result result = compare(run);
                System.out.printf("live run %s: rule %d rows vs model %d rows; id differences %s; links %s%n",
                        run.getFileName(), result.rows, result.modelRows, result.idDifferences, result.linkDifferences);
            }
        }
    }

    private record Result(int rows, int modelRows, List<String> idDifferences, List<String> linkDifferences,
                          List<String> copyDifferences) {
    }

    private static Result compare(Path run) throws Exception {
        Prompt1TurnConfig config = Prompt1TurnConfig.load(null);
        Prompt1TurnRules.Selection selection = Prompt1TurnRules.selection(read(run, "a1_selection.tsv"));
        String ours = Prompt1TurnRules.evidenceBasis(config.headers().get("a3_evidence_basis.tsv"), selection,
                read(run, "a1_ie_candidates.tsv"), read(run, "ie_core_filter.tsv"), read(run, "priority.tsv"),
                read(run, "directions.tsv"), read(run, "links.tsv"),
                read(run, "ie_membership.tsv"), read(run, "meanings.tsv"), new ArrayList<>());
        List<Map<String, String>> rule = Prompt1Tsv.rows(ours);
        List<Map<String, String>> model = Prompt1Tsv.rows(read(run, "a3_evidence_basis.tsv"));
        List<String> ids = new ArrayList<>();
        List<String> links = new ArrayList<>();
        List<String> copies = new ArrayList<>();
        for (int i = 0; i < Math.max(rule.size(), model.size()); i++) {
            Map<String, String> r = i < rule.size() ? rule.get(i) : Map.of();
            Map<String, String> m = i < model.size() ? model.get(i) : Map.of();
            String rIds = String.join("|", ID_COLUMNS.stream().map(c -> r.getOrDefault(c, "")).toList());
            String mIds = String.join("|", ID_COLUMNS.stream().map(c -> m.getOrDefault(c, "")).toList());
            if (!rIds.equals(mIds)) {
                ids.add("row " + (i + 1) + ": rule " + rIds + " / model " + mIds);
                continue;
            }
            String rLink = r.get("link_id") + " " + r.get("relation");
            String mLink = m.get("link_id") + " " + m.get("relation");
            if (!rLink.equals(mLink)) {
                links.add(r.get("meaning_id") + " (" + r.get("role") + "): rule " + rLink + " / model " + mLink);
            }
            for (String column : List.of("observation_ids", "source_excerpt", "recognized_meaning", "qualifications")) {
                if (!Objects.equals(r.get(column), m.get(column))) {
                    copies.add(r.get("meaning_id") + " " + column);
                }
            }
        }
        return new Result(rule.size(), model.size(), ids, links, copies);
    }

    private static String read(Path run, String file) throws Exception {
        return Files.readString(run.resolve(file));
    }
}
