package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1TurnAnswers.RefusedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Prompt1TurnAnswersTest {

    private Prompt1TurnConfig config;

    @BeforeEach
    void setUp() throws Exception {
        config = Prompt1TestFiles.turnConfig();
    }

    private static String c(String canon, String cells, String decisions) {
        return "```heta_canon.tsv\n" + canon + "\n```\n```heta_cells.tsv\n" + cells + "\n```\n```a3_decisions.tsv\n"
                + decisions + "\n```\n";
    }

    private static final String CANON = "22.27\tC1\tcontribution\tR3:22.27.4:22.7\tmotivatie\tyes\tpast\n"
            + "22.27\tS1\tsignal\t—\t—\tno\t—";
    private static final String CELLS = "22.27\tC1\tfunctional_correspondence\tS\tM001\t—\t—\tsteunt";
    private static final String DIRECT = "22.27\tDopamine\tdirect\tfunctional_correspondence\tC1\tM001\tdirect via C1";

    @Test
    void aValidCAnswerIsAppendedWithSignalsFilledAndValuesCleaned() {
        String answer = c(CANON.replace("\tyes\t", "\t​yes\t"), CELLS, DIRECT);
        Prompt1TurnAnswers.Ingested ingested = Prompt1TurnAnswers.ingestC(config, "22.27", answer,
                Map.of("heta_canon.tsv", "header\n"), List.of("onbeloond gevoel"), "Dopamine");

        assertEquals(3, ingested.files().size());
        assertTrue(ingested.files().get(0).append());
        String canon = ingested.files().get(0).content();
        assertFalse(canon.startsWith("heta_ref"), "no header when the file exists");
        assertTrue(canon.contains("22.27\tS1\tsignal\tR3:22.27.5.2\tonbeloond gevoel\tno"), "signal filled from the list");
        assertTrue(canon.contains("\tyes\tpast"), "zero-width character removed");
        assertTrue(ingested.files().get(1).content().startsWith("heta_ref\tcanonical_id"), "header when the file is new");
    }

    @Test
    void cAnswersThatBreakARuleAreRefused() {
        Map<String, String> files = Map.of();
        List<String> signals = List.of("onbeloond gevoel");
        assertRefused("answer lacks block(s): a3_decisions.tsv",
                () -> Prompt1TurnAnswers.ingestC(config, "22.27", "```heta_canon.tsv\nx\n```\n```heta_cells.tsv\nx\n```", files, signals, "Dopamine"));
        assertRefused("row for 22.31 instead of 22.27",
                () -> Prompt1TurnAnswers.ingestC(config, "22.27", c(CANON, CELLS, DIRECT.replace("22.27\tDop", "22.31\tDop")), files, signals, "Dopamine"));
        assertRefused("a3_decisions.tsv: 2 rows instead of 1",
                () -> Prompt1TurnAnswers.ingestC(config, "22.27", c(CANON, CELLS, DIRECT + "\n" + DIRECT), files, signals, "Dopamine"));
        assertRefused("columns instead of 8",
                () -> Prompt1TurnAnswers.ingestC(config, "22.27", c(CANON, CELLS + "\textra", DIRECT), files, signals, "Dopamine"));
        assertRefused("missing S2",
                () -> Prompt1TurnAnswers.ingestC(config, "22.27", c(CANON, CELLS, DIRECT), files, List.of("a", "b"), "Dopamine"));
        assertRefused("PART without gap",
                () -> Prompt1TurnAnswers.ingestC(config, "22.27", c(CANON, CELLS.replace("\tS\t", "\tPART\t"),
                        DIRECT.replace("\tdirect\t", "\tindirect\t")), files, signals, "Dopamine"));
    }

    @Test
    void theDecisionIsProjectedFromTheCellsAndTheRouteFromTheCanonicalId() {
        // the C instruction asks for — in the four 12.4 columns; the backend fills them (v04z)
        String dashes = "22.27\t—\t—\t—\t—\tM001\tdirect via C1";
        Prompt1TurnAnswers.Ingested ingested = Prompt1TurnAnswers.ingestC(config, "22.27",
                c(CANON, CELLS.replace("functional_correspondence", "canonical_signalence"), dashes),
                Map.of(), List.of("onbeloond gevoel"), "Dopamine");
        assertTrue(ingested.files().get(1).content().contains("22.27\tC1\tfunctional_correspondence\tS"), "route from C1");
        assertTrue(ingested.files().get(2).content()
                .contains("22.27\tDopamine\tdirect\tfunctional_correspondence\tfunctional_correspondence\tM001\tdirect via C1"));
        assertTrue(ingested.notes().stream().anyMatch(note -> note.contains("canonical_signalence")));

        // a wrong classification is replaced by the projection, with a note
        Prompt1TurnAnswers.Ingested wrong = Prompt1TurnAnswers.ingestC(config, "22.27",
                c(CANON, CELLS, DIRECT.replace("\tdirect\t", "\tindirect\t")), Map.of(), List.of("onbeloond gevoel"), "Dopamine");
        assertTrue(wrong.files().get(2).content().contains("\tdirect\t"));
        assertTrue(wrong.notes().stream().anyMatch(note -> note.startsWith("12.4-projectie wijkt af")
                && note.contains("classification: 'indirect' → direct")));
    }

    @Test
    void aRepairPatchChangesAddsAndDeletesRowsByKey() {
        String answer = "```links.tsv\nlink_id\tfrom_meaning_id\tto_meaning_id\trelation_type\n"
                + "L001\tM001\tM002\tsteers\nL002\tM002\tM003\tcarries\n```\n\n"
                + "```ie_ftf_cells.tsv\nseq\tie_ref\tftf_id\telement_id\tstatus\n1\t22.2\tFTF_001\t22.2-A1-E1\tS\n2\t22.2\tFTF_001\t22.2-A1-Q1\tS\n```";
        String patch = "```links.tsv\nlink_id\tfrom_meaning_id\tto_meaning_id\trelation_type\n"
                + "L001\tM001\tM002\timpedes\nL002\tDELETE\nL003\tM004\tM001\tcarries\n```\n"
                + "```ie_ftf_cells.tsv\nseq\tie_ref\tftf_id\telement_id\tstatus\n9\t22.2\tFTF_001\t22.2-A1-C1\tNE\n"
                + "7\t22.2\tFTF_001\t22.2-A1-E1\tPART\n```\n```priority.tsv\nnot in the answer\n```";
        Prompt1TurnAnswers.Merged merged = Prompt1TurnAnswers.mergePatch(answer, patch);
        Map<String, String> blocks = Prompt1TurnAnswers.blocks(merged.answer());
        assertEquals("link_id\tfrom_meaning_id\tto_meaning_id\trelation_type\nL001\tM001\tM002\timpedes\nL003\tM004\tM001\tcarries\n",
                blocks.get("links.tsv"));
        assertEquals("seq\tie_ref\tftf_id\telement_id\tstatus\n1\t22.2\tFTF_001\t22.2-A1-E1\tPART\n"
                + "2\t22.2\tFTF_001\t22.2-A1-Q1\tS\n3\t22.2\tFTF_001\t22.2-A1-C1\tNE\n", blocks.get("ie_ftf_cells.tsv"),
                "a changed row keeps its seq, a new row gets the next one");
        assertEquals(List.of("links.tsv: 1 gewijzigd, 1 toegevoegd, 1 verwijderd",
                "ie_ftf_cells.tsv: 1 gewijzigd, 1 toegevoegd, 0 verwijderd"), merged.notes());
    }

    @Test
    void aPatchThatCannotBeAppliedIsNoRepairAndNeverCrashes() {
        String patch = "```links.tsv\nlink_id\tfrom_meaning_id\tto_meaning_id\trelation_type\nL001\tM001\tM002\timpedes\n```";
        // the refused block has a broken header (no link_id column) or is empty: skipped, no note
        assertEquals(List.of(), Prompt1TurnAnswers.mergePatch("```links.tsv\nid\tfrom\tto\trelation\nL001\tM001\tM002\tsteers\n```",
                patch).notes());
        assertEquals(List.of(), Prompt1TurnAnswers.mergePatch("```links.tsv\n```", patch).notes());
        // every patch row has the wrong number of columns: nothing applied, so no note (the full correction follows)
        assertEquals(List.of(), Prompt1TurnAnswers.mergePatch(
                "```links.tsv\nlink_id\tfrom_meaning_id\tto_meaning_id\trelation_type\nL001\tM001\tM002\tsteers\n```",
                "```links.tsv\nlink_id\tfrom_meaning_id\nL001\tM001\textra\tcolumns\tmore\n```").notes());
    }

    @Test
    void contextOfADominantDirectionNeedsALinkOrASharedObservation() {
        Map<String, String> blocks = new java.util.HashMap<>();
        blocks.put("meanings.tsv", "meaning_id\tobservation_id\nM001\tOBS_001\nM002\tOBS_001\nM003\tOBS_002\nM004\tOBS_003\nM005\tOBS_004\n");
        blocks.put("directions.tsv", "direction_id\tmember_ids\tcontext_ids\nFD_001\tM001\tM002,M003,M004\nFD_002\tM005\tM003\n");
        blocks.put("priority.tsv", "direction_id\tpriority_status\tcontext_meaning_ids\nFD_001\tdominant\tM005\nFD_002\tsecondary\t—\n");
        blocks.put("links.tsv", "link_id\tfrom_meaning_id\tto_meaning_id\nL001\tM003\tM001\n");
        // M002 shares the member's observation, M003 has a link to the member; M004 and M005 have neither.
        // FD_002 is not dominant, so its context is not checked.
        assertEquals(List.of("FD_001: M004", "FD_001: M005"), Prompt1TurnAnswers.unconnectedDominantContext(blocks));
    }

    @Test
    void aRowBrokenByALineBreakIsJoined() {
        Map<String, String> blocks = new java.util.LinkedHashMap<>();
        String header = config.headers().get("a1_selection.tsv");
        blocks.put("a1_selection.tsv", header + "\n7.2D\t22.7\tprimary\tdominant\n7.3\t22.3\tsecondary\tsteunt de\n ontwikkeling\n");
        List<String> notes = new java.util.ArrayList<>();
        Prompt1TurnAnswers.joinBrokenLines(config, blocks, List.of("a1_selection.tsv"), notes);
        assertEquals(header + "\n7.2D\t22.7\tprimary\tdominant\n7.3\t22.3\tsecondary\tsteunt de ontwikkeling\n",
                blocks.get("a1_selection.tsv"));
        assertEquals(1, notes.size());
        assertTrue(notes.get(0).endsWith(": 7.3"));
    }

    @Test
    void otherTurnsCheckBlocksHeadersAndColumns() {
        Map<String, String> files = Map.of("priority.tsv", config.headers().get("priority.tsv") + "\n");
        assertRefused("answer lacks block(s): a1_selection.tsv",
                () -> Prompt1TurnAnswers.ingest(config, "A3", "no blocks", files));
        assertRefused("a1_selection.tsv: header differs from doc 18",
                () -> Prompt1TurnAnswers.ingest(config, "A3", "```a1_selection.tsv\nstep\tie\n```", files));
        assertRefused("a1_selection.tsv: 3 columns instead of 4",
                () -> Prompt1TurnAnswers.ingest(config, "A3",
                        "```a1_selection.tsv\nstep\tie_ref\tfinding\tbasis\n7.2D\t22.7\tprimary\n```", files));

        assertRefused("Write the row 7.2D | <ie_ref> | primary",
                () -> Prompt1TurnAnswers.ingest(config, "A3", "```a1_selection.tsv\nstep\tie_ref\tfinding\tbasis\n```", files));
        Prompt1TurnAnswers.Ingested ok = Prompt1TurnAnswers.ingest(config, "A3",
                "```a1_selection.tsv\nstep\tie_ref\tfinding\tbasis\n7.2D\t22.7\tprimary\tdominant\n```", files);
        assertEquals("5", ok.closeSteps());
        assertEquals("step\tie_ref\tfinding\tbasis\n7.2D\t22.7\tprimary\tdominant\n", ok.files().get(0).content());
    }

    @Test
    void a1RepairsJoinQualificationsDropBareOnesAndSelfLinks() {
        String header = config.headers().get("meanings.tsv");
        Map<String, String> blocks = new java.util.LinkedHashMap<>();
        blocks.put("meanings.tsv", header + "\nM001\tOBS_001\tOBS_001\tprocess\tfunctional_contribution\tmoe\tMoe\tintensity\terg\tb\n"
                + "M002\tOBS_002\tOBS_002\tprocess\tfunctional_contribution\tmoe\tMoe\tpolarity\tb");
        blocks.put("links.tsv", config.headers().get("links.tsv") + "\nL001\tM001\tM001\tcause\tOBS_001\tx\tlocal");
        List<String> notes = Prompt1TurnAnswers.repairA1Format(blocks, null);

        assertTrue(blocks.get("meanings.tsv").contains("\tintensity=erg\tb"), "category<TAB>wording joined");
        assertTrue(blocks.get("meanings.tsv").contains("\tMoe\t—\tb"), "bare category dropped");
        assertFalse(blocks.get("links.tsv").contains("L001"), "self link dropped");
        assertEquals(3, notes.size());
    }

    @Test
    void qualificationsSplitOverSeveralColumnsAreJoinedOnlyWithKnownCategories() {
        String header = config.headers().get("meanings.tsv");
        String start = "\tOBS_001\tOBS_001\tprocess\tfunctional_contribution\tmoe\tMoe\t";
        Map<String, String> blocks = new java.util.LinkedHashMap<>();
        blocks.put("meanings.tsv", header
                + "\nM001" + start + "frequency\tvaak;;manner\tlangzaam\tbasis"
                + "\nM002" + start + "frequency=soms;;situation_or_trigger\tthuis;;manner=rustig\tbasis"
                + "\nM003" + start + "duration_or_onset\tsinds jaren;;uncertainty\tmisschien\tbasis"
                + "\nM004" + start + "mood\tslecht;;manner\tlangzaam\tbasis"
                + "\nM005" + start + "frequency\tvaak\tlangzaam\tbasis");
        List<String> notes = Prompt1TurnAnswers.repairA1Format(blocks, null);
        List<String> rows = blocks.get("meanings.tsv").lines().toList();

        assertTrue(rows.get(1).endsWith("\tfrequency=vaak;;manner=langzaam\tbasis"), "two extra columns joined");
        assertTrue(rows.get(2).endsWith("\tfrequency=soms;;situation_or_trigger=thuis;;manner=rustig\tbasis"));
        assertTrue(rows.get(3).endsWith("\tduration_or_onset=sinds jaren;;uncertainty=misschien\tbasis"));
        assertEquals(11, rows.get(4).split("\t", -1).length, "unknown category 'mood': row left for the check");
        assertEquals(11, rows.get(5).split("\t", -1).length, "wording without a category: row left for the check");
        assertTrue(notes.get(0).endsWith("M001, M002, M003"));
    }

    /**
     * The A1 answers our live runs 2 and 3 (10 Nov) had refused, with their run's observations
     * (a1_poging1_&lt;run&gt;.txt and a1_observations_&lt;run&gt;.tsv in PROMPT1_A1_RETRY_DIR): with the
     * extended repair they pass ingest without a correction. Skipped when the folder is not set.
     */
    @Test
    void theRefusedA1AnswersOfOurLiveRunsNowPass() throws Exception {
        String dir = System.getenv("PROMPT1_A1_RETRY_DIR");
        org.junit.jupiter.api.Assumptions.assumeTrue(dir != null && java.nio.file.Files.isDirectory(java.nio.file.Path.of(dir)),
                "PROMPT1_A1_RETRY_DIR not set");
        int checked = 0;
        try (var list = java.nio.file.Files.list(java.nio.file.Path.of(dir))) {
            for (java.nio.file.Path answer : list.filter(p -> p.getFileName().toString().startsWith("a1_poging1_")).sorted().toList()) {
                String run = answer.getFileName().toString().replace("a1_poging1_", "").replace(".txt", "");
                String observations = java.nio.file.Files.readString(answer.resolveSibling("a1_observations_" + run + ".tsv"));
                Prompt1TurnAnswers.Ingested ingested = Prompt1TurnAnswers.ingest(config, "A1",
                        java.nio.file.Files.readString(answer), Map.of("observations.tsv", observations));
                assertTrue(ingested.notes().stream().anyMatch(note -> note.startsWith("kwalificatie 'categorie<tab>tekst'")), run);
                System.out.printf("refused A1 answer of run %s now accepted; repairs: %s%n", run,
                        ingested.notes().stream().map(note -> note.substring(0, note.indexOf(':'))).toList());
                checked++;
            }
        }
        assertEquals(2, checked);
    }

    @Test
    void stepTwoNeedsOneDominantDirectionButNotAllDominant() {
        String admission = config.headers().get("cts_admission.tsv") + "\nFD_001\tadmitted\tx\tx\tx\t—\t—\nFD_002\tadmitted\tx\tx\tx\t—\t—\n";
        String noDominant = config.headers().get("priority.tsv") + "\nFD_001\tnon_dominant\t—\t—\t—\t—\t—\tb\nFD_002\tnon_dominant\t—\t—\t—\t—\t—\tb\n";
        String allDominant = noDominant.replace("non_dominant", "dominant");
        String one = noDominant.replaceFirst("non_dominant", "dominant");

        assertRefused("no dominant direction", () -> Prompt1TurnAnswers.checkDominance(admission, noDominant));
        assertRefused("all 2 admitted directions are dominant", () -> Prompt1TurnAnswers.checkDominance(admission, allDominant));
        assertDoesNotThrow(() -> Prompt1TurnAnswers.checkDominance(admission, one));
    }

    private static void assertRefused(String expected, org.junit.jupiter.api.function.Executable call) {
        RefusedException e = assertThrows(RefusedException.class, call);
        assertTrue(e.getMessage().contains(expected), "expected '" + expected + "' in: " + e.getMessage());
    }
}
