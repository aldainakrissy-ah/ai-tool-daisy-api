package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.DocumentSections;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Our turn messages against the prompts Jan's backend sent in his run (pg_run31). Everything outside the
 * source passages must be identical. The passages must hold the same lines in the same order: Jan's
 * index repeats the heading path at the start of each chunk and drops markdown '#', so his may have
 * extra heading lines. Client data is never printed.
 */
class Prompt1TurnMessagesReplayTest {

    private static final String KEYS_START = "\n=== STEP 1 KEYS ===\n";
    private static final String DOC18_START = "\n\n=== WORKING-FILE FORMAT";

    @Test
    void a1EqualsJansPromptApartFromRepeatedHeadingLines() throws Exception {
        Path sources = Prompt1TestFiles.dir("KNOWLEDGE_MARKDOWN_DIR");
        Path replay = Prompt1TestFiles.dir("PROMPT1_REPLAY_DIR");
        Prompt1TurnConfig config = Prompt1TestFiles.turnConfig();
        Map<String, DocumentSections> documents = Prompt1SourcePackageService.selectVersions(
                Prompt1SourcePackageServiceTest.readLikeTheLoader(sources), Map.of());
        String jan = Files.readString(replay.resolve("prompts/A1.txt"));
        String runDate = jan.lines().filter(line -> line.startsWith("CURRENT_RUN_DATE: ")).findFirst().orElseThrow()
                .substring("CURRENT_RUN_DATE: ".length());

        String ours = Prompt1TurnMessages.a1(config, replay.getFileName().toString(), runDate,
                Files.readString(replay.resolve("manifest.tsv")), Files.readString(replay.resolve("observations.tsv")),
                Prompt1SourcePackageService.readKeys(documents, Prompt1TurnMessages.A1_STEP1_KEYS),
                Prompt1SourcePackageService.readKeys(documents, Prompt1TurnMessages.A1_STEP2_KEYS),
                Prompt1TurnSystemMessage.doc18Part(Files.readString(sources.resolve("18_werkbestanden_format.md")),
                        "A1", config));

        int janKeys = jan.indexOf(KEYS_START);
        int ourKeys = ours.indexOf(KEYS_START);
        assertTrue(janKeys > 0 && ourKeys > 0);
        assertTrue(jan.substring(0, janKeys).equals(ours.substring(0, ourKeys)),
                "A1 up to the Step 1 keys differs at character " + firstDifference(jan.substring(0, janKeys),
                        ours.substring(0, ourKeys)));
        int janDoc18 = jan.indexOf(DOC18_START);
        int ourDoc18 = ours.indexOf(DOC18_START);
        assertEquals(jan.substring(janDoc18), ours.substring(ourDoc18), "A1 doc 18 part");

        List<String> janLines = normalised(jan.substring(janKeys, janDoc18));
        List<String> ourLines = normalised(ours.substring(ourKeys, ourDoc18));
        int matched = 0;
        int j = 0;
        for (String line : ourLines) {
            while (j < janLines.size() && !janLines.get(j).equals(line)) {
                j++;
            }
            assertTrue(j < janLines.size(), "source passage line " + (matched + 1) + " of ours not found in Jan's order: "
                    + line.substring(0, Math.min(60, line.length())));
            j++;
            matched++;
        }
        System.out.printf("A1: %,d characters (Jan %,d); source passages: our %d lines all in Jan's %d lines, in order%n",
                ours.length(), jan.length(), ourLines.size(), janLines.size());
    }

    /**
     * Turns A2, A3 and every C built from Jan's closed working files and our knowledge
     * tables. Each of our lines must appear in Jan's prompt, in order; Jan's extra lines may only be
     * the heading lines his index repeats (lines that also occur elsewhere in his prompt or are
     * heading paths). Turns B and D have no model call since release v06.
     */
    @Test
    void laterTurnsEqualJansPromptsApartFromRepeatedHeadingLines() throws Exception {
        Path sources = Prompt1TestFiles.dir("KNOWLEDGE_MARKDOWN_DIR");
        Path replay = Prompt1TestFiles.dir("PROMPT1_REPLAY_DIR");
        Prompt1TurnConfig config = Prompt1TestFiles.turnConfig();
        Map<String, DocumentSections> documents = Prompt1SourcePackageService.selectVersions(
                Prompt1SourcePackageServiceTest.readLikeTheLoader(sources), Map.of());
        String doc18 = Files.readString(sources.resolve("18_werkbestanden_format.md"));
        Map<String, String> files = new HashMap<>();
        try (var list = Files.list(replay)) {
            for (Path file : list.filter(path -> path.toString().endsWith(".tsv")).toList()) {
                files.put(file.getFileName().toString(), Files.readString(file));
            }
        }
        Prompt1TurnMessages.Sources keys = new Prompt1TurnMessages.Sources() {
            @Override
            public List<Prompt1SourcePackageService.KeyText> keys(String step, List<String> names) {
                return Prompt1SourcePackageService.readKeys(documents, names);
            }

            @Override
            public Prompt1SourcePackageService.KeyText r4Lookup(String step) {
                return Prompt1SourcePackageService.r4Lookup(documents);
            }
        };
        List<String> hetas = Prompt1Tsv.rows(files.get("a3_candidates.tsv")).stream().map(row -> row.get("heta_ref")).toList();

        compare("A2", replay, Prompt1TurnMessages.a2(config, replay.getFileName().toString(), files, keys,
                Prompt1TurnSystemMessage.doc18Part(doc18, "A2", config)));
        compare("A3", replay, Prompt1TurnMessages.a3(config, replay.getFileName().toString(), files, keys,
                Prompt1TurnSystemMessage.doc18Part(doc18, "A3", config)));
        for (String heta : hetas) {
            compare("C_" + heta, replay, Prompt1TurnMessages.c(config, replay.getFileName().toString(), heta, files, keys));
        }
    }

    /**
     * Jan's index stores source text differently (it repeats chunk headings, joins list lines into
     * paragraphs, collapses spaces, flattens the R4 lookup table). So: (1) our words appear in his
     * prompt in the same order; (2) every working-file row (a line with a tab) and every block title
     * ("===") of his prompt is in ours, unchanged; the extra words in his are reported.
     */
    private static void compare(String turn, Path replay, String ours) throws Exception {
        String jan = Files.readString(replay.resolve("prompts/" + turn + ".txt"));
        List<String> janWords = words(withoutBlock(jan.lines().toList(), "=== STEP 4: R4 LOOKUP ==="));
        List<String> ourWords = words(withoutBlock(ours.lines().toList(), "=== STEP 4: R4 LOOKUP ==="));
        int j = 0;
        for (int i = 0; i < ourWords.size(); i++) {
            while (j < janWords.size() && !janWords.get(j).equals(ourWords.get(i))) {
                j++;
            }
            if (j >= janWords.size()) {
                String block = "(start)";
                int count = 0;
                for (String line : withoutBlock(ours.lines().toList(), "=== STEP 4: R4 LOOKUP ===")) {
                    if (line.startsWith("===") || line.startsWith("KEY: ")) {
                        block = line.length() > 90 ? line.substring(0, 90) : line;
                    }
                    count += words(List.of(line)).size();
                    if (count > i) {
                        break;
                    }
                }
                fail(turn + ": our word " + (i + 1) + " of " + ourWords.size()
                        + " is not in Jan's prompt in this order; block: " + block);
            }
            j++;
        }
        Set<String> ourLines = new HashSet<>(ours.lines().toList());
        for (String line : jan.lines().toList()) {
            if ((line.contains("\t") || line.startsWith("===")) && !ourLines.contains(line)) {
                fail(turn + ": a " + (line.contains("\t") ? "working-file row" : "block title " + line)
                        + " of Jan's prompt is not in ours");
            }
        }
        System.out.printf("%s: ours %,d characters, Jan %,d; all %,d of our words in Jan's order; Jan has %,d extra words "
                + "(repeated headings); every file row and block title of Jan's is in ours%n",
                turn, ours.length(), jan.length(), ourWords.size(), janWords.size() - ourWords.size());
    }

    /** The words of the lines; markdown table rows are left out (Jan's index flattens tables into "column = value" text). */
    private static List<String> words(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line.strip().startsWith("|")) {
                continue;
            }
            // markdown escapes (QEST\_JSON) are split by Jan's index; '_' counts as its own token on both sides
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[^\\s_]+|_")
                    .matcher(line.replaceFirst("^#+\\s*", "").replace("\\", ""));
            while (m.find()) {
                out.add(m.group());
            }
        }
        return out;
    }

    /** The lines without the block that starts at {@code header} (up to the next "===" line). */
    private static List<String> withoutBlock(List<String> lines, String header) {
        List<String> out = new ArrayList<>();
        boolean skip = false;
        for (String line : lines) {
            if (line.startsWith("===")) {
                skip = line.strip().equals(header);
            }
            if (!skip) {
                out.add(line);
            }
        }
        return out;
    }

    /** Non-empty lines without leading markdown '#', with runs of spaces collapsed (as Jan's index stores text). */
    private static List<String> normalised(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.lines().toList()) {
            String clean = line.replaceFirst("^#+\\s*", "").replaceAll("\\s+", " ").strip();
            if (!clean.isEmpty()) {
                out.add(clean);
            }
        }
        return out;
    }

    private static int firstDifference(String a, String b) {
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return i;
            }
        }
        return Math.min(a.length(), b.length());
    }

}
