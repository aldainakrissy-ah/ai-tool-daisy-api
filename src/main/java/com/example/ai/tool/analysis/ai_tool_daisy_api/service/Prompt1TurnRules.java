package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fixed rules of the turn-based Prompt 1 route that the backend executes instead of the model,
 * ported from Jan's {@code playground_server.py} and {@code daisy_preload.py}:
 * Step 4 IE membership ({@code derive_ie_candidates}), Step 6 A2 fields ({@code build_a2}),
 * Step 7 candidate HETAs ({@code a3_candidate_list}), the fixed signal list per HETA
 * ({@code r3_signals}), and the selections the turn messages need ({@code selection},
 * {@code dominant_ftfs}, {@code partial_fit}, {@code in_play}).
 */
public final class Prompt1TurnRules {

    /** One element row of R5:ELEM: "- 22.7-A1-E1 | role | required". */
    private static final Pattern ELEM_ROW = Pattern.compile(
            "-\\s*(\\d+\\.\\d+-A(\\d+)-([EQC])\\d+)\\s*\\|\\s*(\\w+)\\s*\\|\\s*(required|not required)",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern INDEX_MEMBERS = Pattern.compile("members:\\s*\\n((?:\\s*22\\.\\d+\\s*\\n)+)");
    private static final Pattern HETA_REF = Pattern.compile("22\\.\\d+");
    private static final Pattern R4_TITLE = Pattern.compile("KEY: R4:\\S+ \\| IE \\S+ (.+?) \\|");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?:])\\s+(?=[A-Z])");
    private static final Pattern DOES_NOT = Pattern.compile("\\bdoes not\\b");

    static final String MEMBERSHIP_HEADER = "ie_ref\tftf_id\talt_id\tdominant\tmembership_basis";
    static final String CANDIDATES_HEADER = "ie_ref\trepresented_ftf_ids\trepresented_dominant_ftf_ids\tprimary_eligible";

    private Prompt1TurnRules() {
    }

    // ---- Step 4: IE membership ---------------------------------------------------------------------------

    /** One candidate IE of Step 4. */
    public record IeCandidate(String ieRef, List<String> representedFtfIds, List<String> representedDominantFtfIds,
                              boolean primaryEligible) {
    }

    /** The outcome of Step 4: both server files, or the rows the model still has to add. */
    public record Membership(String membershipTsv, String candidatesTsv, List<IeCandidate> candidates,
                             List<String> missing) {
        public boolean complete() {
            return missing.isEmpty();
        }
    }

    /**
     * SM Step 4 point 5: membership follows from the recorded cells. An IE is a member for an
     * admitted FTF when, for one alternative its passing elements belong to, every required row
     * (E, Q, C) of R5:ELEM has status S. A candidate is primary-eligible when it represents a
     * dominant FTF.
     *
     * @param elemTexts IE ref → text of its R5:ELEM key
     * @param admission cts_admission.tsv, or null (then every direction in priority.tsv counts)
     */
    public static Membership deriveIeCandidates(List<String> ies, Map<String, String> elemTexts, String coreFilter,
                                                String cells, String admission, String priority) {
        List<Map<String, String>> core = stripped(Prompt1Tsv.rows(coreFilter));
        List<Map<String, String>> cellRows = stripped(Prompt1Tsv.rows(cells));
        List<Map<String, String>> prio = stripped(Prompt1Tsv.rows(priority));
        Set<Integer> dominantDirections = new HashSet<>();
        for (Map<String, String> p : prio) {
            if ("dominant".equals(p.get("priority_status"))) {
                dominantDirections.add(Prompt1Tsv.number(p.get("direction_id")));
            }
        }
        List<Integer> ftfNumbers = new ArrayList<>();
        if (admission != null) {
            for (Map<String, String> a : stripped(Prompt1Tsv.rows(admission))) {
                if ("admitted".equals(a.get("decision"))) {
                    ftfNumbers.add(Prompt1Tsv.number(a.get("direction_id")));
                }
            }
        } else {
            TreeSet<Integer> numbers = new TreeSet<>();
            for (Map<String, String> p : prio) {
                Integer n = Prompt1Tsv.number(p.get("direction_id"));
                if (n != null) {
                    numbers.add(n);
                }
            }
            ftfNumbers.addAll(numbers);
        }
        List<String> ftfs = ftfNumbers.stream().filter(n -> n != null).map(n -> String.format("FTF_%03d", n)).toList();
        Map<String, Map<String, String>> coreByIeFtf = new HashMap<>();
        for (Map<String, String> row : core) {
            coreByIeFtf.put(row.get("ie_ref") + "|" + row.get("ftf_id"), row);
        }
        Map<String, Map<String, String>> cellByKey = new HashMap<>();
        for (Map<String, String> row : cellRows) {
            cellByKey.put(row.get("ie_ref") + "|" + row.get("ftf_id") + "|" + row.get("element_id"), row);
        }

        List<String> missing = new ArrayList<>();
        List<List<String>> members = new ArrayList<>();
        List<IeCandidate> candidates = new ArrayList<>();
        for (String ie : ies) {
            Map<String, List<String>> required = new LinkedHashMap<>();
            Map<String, String> elementAlternative = new HashMap<>();
            Matcher m = ELEM_ROW.matcher(elemTexts.getOrDefault(ie, ""));
            while (m.find()) {
                String rowId = m.group(1);
                if (!rowId.startsWith(ie + "-")) {
                    continue;
                }
                if ("required".equals(m.group(5))) {
                    required.computeIfAbsent(m.group(2), alt -> new ArrayList<>()).add(rowId);
                }
                if ("E".equals(m.group(3))) {
                    elementAlternative.put(rowId, m.group(2));
                }
            }
            if (required.isEmpty()) {
                missing.add(ie + ": geen vereiste rijen gevonden in R5:ELEM:" + ie + " (servercontrole)");
                continue;
            }
            List<String> represented = new ArrayList<>();
            List<String> representedDominant = new ArrayList<>();
            for (String ftf : ftfs) {
                Map<String, String> filter = coreByIeFtf.get(ie + "|" + ftf);
                if (filter == null) {
                    missing.add("kernfilter " + ie + " × " + ftf);
                    continue;
                }
                if (!"yes".equals(filter.get("passes"))) {
                    continue;
                }
                List<String> alternatives = new ArrayList<>();
                for (String element : Prompt1Tsv.ids(filter.get("element_ids"))) {
                    String alt = elementAlternative.get(element);
                    if (alt == null) {
                        missing.add("kernfilter " + ie + " × " + ftf + ": " + element + " is geen element-ID van R5:ELEM:" + ie);
                    } else if (!alternatives.contains(alt)) {
                        alternatives.add(alt);
                    }
                }
                if (alternatives.isEmpty()) {
                    missing.add("kernfilter " + ie + " × " + ftf + ": passes = yes zonder geldige element_ids");
                }
                alternatives.sort(Comparator.comparingInt(alt -> Integer.parseInt(alt)));
                List<String> memberAlternatives = new ArrayList<>();
                List<String> bases = new ArrayList<>();
                for (String alt : alternatives) {
                    boolean allSupported = true;
                    List<String> altBases = new ArrayList<>();
                    for (String rowId : required.getOrDefault(alt, List.of())) {
                        Map<String, String> cell = cellByKey.get(ie + "|" + ftf + "|" + rowId);
                        if (cell == null) {
                            missing.add("cel " + ie + " × " + ftf + " × " + rowId);
                            allSupported = false;
                            continue;
                        }
                        String status = cell.get("status");
                        if (!List.of("S", "NE", "C").contains(status)) {
                            missing.add("cel " + ie + " × " + ftf + " × " + rowId + ": ongeldige status " + status);
                            allSupported = false;
                            continue;
                        }
                        if (!"S".equals(status)) {
                            allSupported = false;
                        } else {
                            altBases.add(cell.getOrDefault("basis", ""));
                        }
                    }
                    if (allSupported) {
                        memberAlternatives.add("A" + alt);
                        bases.addAll(altBases);
                    }
                }
                if (!memberAlternatives.isEmpty()) {
                    boolean dominant = dominantDirections.contains(Prompt1Tsv.number(ftf));
                    represented.add(ftf);
                    if (dominant) {
                        representedDominant.add(ftf);
                    }
                    String basis = String.join(" | ", bases.stream()
                            .filter(b -> !b.isEmpty() && !Prompt1Tsv.EMPTY.equals(b)).toList());
                    members.add(List.of(ie, ftf, String.join(",", memberAlternatives), dominant ? "yes" : "no",
                            basis.isEmpty() ? Prompt1Tsv.EMPTY : basis));
                }
            }
            if (!represented.isEmpty()) {
                candidates.add(new IeCandidate(ie, represented, representedDominant, !representedDominant.isEmpty()));
            }
        }
        if (!missing.isEmpty()) {
            return new Membership(null, null, List.of(), missing);
        }
        StringBuilder membership = new StringBuilder(MEMBERSHIP_HEADER).append('\n');
        for (List<String> row : members) {
            membership.append(String.join("\t", row.stream().map(value -> clean(value)).toList())).append('\n');
        }
        StringBuilder candidateText = new StringBuilder(CANDIDATES_HEADER).append('\n');
        for (IeCandidate c : candidates) {
            candidateText.append(c.ieRef()).append('\t').append(String.join(",", c.representedFtfIds())).append('\t')
                    .append(c.representedDominantFtfIds().isEmpty() ? Prompt1Tsv.EMPTY
                            : String.join(",", c.representedDominantFtfIds()))
                    .append('\t').append(c.primaryEligible() ? "yes" : "no").append('\n');
        }
        return new Membership(membership.toString(), candidateText.toString(), candidates, List.of());
    }

    // ---- Step 5 selections and the rows later turns receive ----------------------------------------------

    /** The A1 selection of a1_selection.tsv: the primary IE (row 7.2D) and the secondaries (rows 7.3). */
    public record Selection(String primary, List<String> secondaries) {
    }

    public static Selection selection(String a1Selection) {
        List<String> primary = new ArrayList<>();
        List<String> secondaries = new ArrayList<>();
        boolean noPrimary = false;
        for (Map<String, String> row : Prompt1Tsv.rows(a1Selection)) {
            String step = row.getOrDefault("step", "");
            String finding = row.getOrDefault("finding", "").strip();
            if (step.startsWith("7.2D") && "primary".equals(finding)) {
                primary.add(row.get("ie_ref"));
            } else if (step.startsWith("7.3") && "secondary".equals(finding)) {
                secondaries.add(row.get("ie_ref"));
            }
            noPrimary |= finding.contains("no_primary");
        }
        if (primary.isEmpty() && noPrimary) {
            throw new IllegalStateException("No primary IE in a1_selection.tsv (finding no_primary…): since SM v0.128 "
                    + "Step 5 selects a partial-fit primary IE (7.1); turn A3 did not follow that rule");
        }
        if (primary.size() != 1) {
            throw new IllegalStateException("a1_selection.tsv: expected exactly one row 7.2D | <ie> | primary");
        }
        return new Selection(primary.get(0), secondaries);
    }

    /** The FTFs of the dominant directions (FD_016 → FTF_016). */
    public static List<String> dominantFtfs(String priority) {
        List<String> ftfs = new ArrayList<>();
        for (Map<String, String> row : Prompt1Tsv.rows(priority)) {
            if ("dominant".equals(row.getOrDefault("priority_status", "").strip())) {
                String id = row.get("direction_id");
                ftfs.add("FTF_" + id.substring(id.indexOf('_') + 1));
            }
        }
        return ftfs;
    }

    /**
     * SM 7.1: only when no candidate IE is primary-eligible, each IE with the dominant FTFs that pass
     * its core filter; empty otherwise.
     */
    public static Map<String, List<String>> partialFit(String candidates, String priority, String coreFilter) {
        if (Prompt1Tsv.rows(candidates).stream()
                .anyMatch(row -> "yes".equals(row.getOrDefault("primary_eligible", "").strip()))) {
            return Map.of();
        }
        Set<String> dominant = new HashSet<>(dominantFtfs(priority));
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map<String, String> row : Prompt1Tsv.rows(coreFilter)) {
            if (dominant.contains(row.get("ftf_id")) && "yes".equals(row.getOrDefault("passes", "").strip())) {
                out.computeIfAbsent(row.get("ie_ref"), ie -> new ArrayList<>()).add(row.get("ftf_id"));
            }
        }
        return out;
    }

    /** The directions and meanings that take part after Step 3 (Jan's {@code in_play}). */
    public record InPlay(Set<String> directions, Set<String> meanings) {
    }

    public static InPlay inPlay(String admission, String directions, String links) {
        Set<String> admitted = new HashSet<>();
        for (Map<String, String> row : Prompt1Tsv.rows(admission)) {
            if ("admitted".equals(row.getOrDefault("decision", "").strip())) {
                admitted.add(row.get("direction_id"));
            }
        }
        List<Map<String, String>> directionRows = Prompt1Tsv.rows(directions);
        Set<String> meanings = new HashSet<>();
        for (Map<String, String> d : directionRows) {
            if (admitted.contains(d.get("direction_id"))) {
                meanings.addAll(Prompt1Tsv.ids(d.get("member_ids")));
                meanings.addAll(Prompt1Tsv.ids(d.get("context_ids")));
            }
        }
        for (Map<String, String> link : Prompt1Tsv.rows(links)) {
            if (meanings.contains(link.get("from_meaning_id")) || meanings.contains(link.get("to_meaning_id"))) {
                meanings.add(link.get("from_meaning_id"));
                meanings.add(link.get("to_meaning_id"));
            }
        }
        Set<String> inPlay = new HashSet<>();
        for (Map<String, String> d : directionRows) {
            Set<String> used = new HashSet<>(Prompt1Tsv.ids(d.get("member_ids")));
            used.addAll(Prompt1Tsv.ids(d.get("context_ids")));
            used.retainAll(meanings);
            if (admitted.contains(d.get("direction_id")) || !used.isEmpty()) {
                inPlay.add(d.get("direction_id"));
            }
        }
        return new InPlay(inPlay, meanings);
    }

    // ---- Steps 6 and 7 -------------------------------------------------------------------------------------

    /**
     * SM Step 7: the candidate HETAs are every and only the refs listed under "members" in
     * R4:INDEX:&lt;primary&gt;, in that order.
     */
    public static List<String> a3Candidates(String primary, String indexText) {
        Matcher m = INDEX_MEMBERS.matcher(indexText + "\n");
        List<String> refs = new ArrayList<>();
        if (m.find()) {
            Matcher ref = HETA_REF.matcher(m.group(1));
            while (ref.find()) {
                refs.add(ref.group());
            }
        }
        if (refs.isEmpty()) {
            throw new IllegalStateException("Step 7: no HETA members found in R4:INDEX:" + primary);
        }
        return refs;
    }

    public static String a3CandidatesTsv(String header, String primary, List<String> refs) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < refs.size(); i++) {
            rows.add(List.of(String.valueOf(i + 1), primary, refs.get(i)));
        }
        return Prompt1Tsv.text(header, rows);
    }

    /**
     * SM Step 6 (a read-only copy): the R5:A2 rows of the primary and every secondary IE, with the
     * A1 role, the IE title from the R4 key line and the first sentence of R4:&lt;ie&gt;:01 that says
     * what the IE does.
     *
     * @param keyTexts key → full key text ("KEY: … | …" line first) for R4:&lt;ie&gt;:01 and R5:A2:&lt;ie&gt;
     */
    public static String a2Fields(String header, Selection selection, Map<String, String> keyTexts) {
        List<String[]> roles = new ArrayList<>();
        roles.add(new String[]{selection.primary(), "primary"});
        for (String ie : selection.secondaries()) {
            roles.add(new String[]{ie, "secondary"});
        }
        List<List<String>> rows = new ArrayList<>();
        for (String[] role : roles) {
            String ie = role[0];
            String r4 = keyTexts.get("R4:" + ie + ":01");
            String r5 = keyTexts.get("R5:A2:" + ie);
            if (r4 == null || r5 == null) {
                throw new IllegalStateException("Step 6: source key R4:" + ie + ":01 or R5:A2:" + ie + " not found");
            }
            List<String> lines = r4.strip().lines().filter(line -> !line.isBlank()).toList();
            Matcher title = R4_TITLE.matcher(lines.get(0));
            String ieTitle = title.find() ? title.group(1).strip() : "";
            List<String> body = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.startsWith("R4 —")) {
                    break;
                }
                body.add(line.strip());
            }
            List<String> sentences = Arrays.stream(SENTENCE_END.split(String.join(" ", body)))
                    .map(s -> s.strip()).filter(s -> !s.isEmpty()).toList();
            List<String> positive = sentences.stream().filter(s -> !DOES_NOT.matcher(s).find()).toList();
            String description = !positive.isEmpty() ? positive.get(0) : !sentences.isEmpty() ? sentences.get(0) : "";
            description = Prompt1Tsv.rstrip(description, ":").strip();
            int n = 0;
            for (String raw : r5.lines().toList()) {
                String line = raw.strip();
                if (!line.startsWith("- FIELD_" + ie + "_")) {
                    continue;
                }
                String[] cells = line.substring(2).split(" \\| ", -1);
                if (cells.length != 6) {
                    throw new IllegalStateException("Step 6: R5:A2:" + ie + " row not in 6 parts: "
                            + line.substring(0, Math.min(80, line.length())));
                }
                rows.add(List.of(cells[0].strip(), ie, role[1], cells[2].strip(), cells[3].strip(), cells[4].strip(),
                        cells[5].strip(), ieTitle, description));
                n++;
            }
            if (n == 0) {
                throw new IllegalStateException("Step 6: R5:A2:" + ie + " has no field rows");
            }
        }
        return Prompt1Tsv.text(header, rows);
    }

    // ---- Step 8: the evidence basis (Jan's backend rule, v04z–v05c) ---------------------------------------

    /** Links whose relation_type or scope marks them as uncertain (links.tsv has no uncertain column). */
    private static final java.util.regex.Pattern UNCERTAIN_LINK = java.util.regex.Pattern.compile(
            "\\b(onzeker|uncertain|mogelijk|misschien|wellicht|possibly)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * SM Step 8 as a fixed rule, so turn B needs no model call (Jan's {@code evidence_basis_text}, v04z–v05c).
     * Roles, in this order:
     * <ul>
     *   <li>constitutive: every member of each dominant FTF the primary IE represents
     *       (represented_dominant_ftf_ids; a partial-fit primary, 7.1: the dominant FTFs with passes = yes
     *       for it in ie_core_filter.tsv), FTFs in number order;</li>
     *   <li>context: every context_id of those directions, with the first link to a member of the
     *       direction, otherwise "—" (the same observation); v05c: a context_id with neither has no
     *       established connection and is left out (reported in {@code skipped});</li>
     *   <li>linked_context: the other end of a link from a constitutive meaning, when that meaning sits in
     *       another direction and is not yet included; uncertain links are skipped;</li>
     *   <li>primary_ie_context: every member of the primary's other FTFs (represented_ftf_ids; a
     *       partial-fit primary: its FTFs in ie_membership.tsv), with the first link that touches it.</li>
     * </ul>
     * Each meaning once; direction_id = the direction it enters through plus every other direction that
     * holds it (member or context). The copy columns are filled from meanings.tsv.
     *
     * @param skipped   receives "Mnnn (FD_x)" for each context left out (v05c)
     */
    public static String evidenceBasis(String header, Selection selection, String candidates, String coreFilter,
                                       String priority, String directions, String links, String membership,
                                       String meanings, List<String> skipped) {
        String primary = selection.primary();
        Map<String, Map<String, String>> candidate = new LinkedHashMap<>();
        Prompt1Tsv.rows(candidates).forEach(row -> candidate.put(row.get("ie_ref"), row));
        Set<String> dominant = new HashSet<>();
        Prompt1Tsv.rows(priority).stream().filter(r -> "dominant".equals(r.get("priority_status").strip()))
                .forEach(r -> dominant.add("FTF_" + r.get("direction_id").split("_")[1]));
        List<String> dominantFtfs = new ArrayList<>();
        List<String> allFtfs = new ArrayList<>();
        if (candidate.values().stream().anyMatch(r -> "yes".equals(r.getOrDefault("primary_eligible", "").strip()))) {
            if (!candidate.containsKey(primary)) {
                throw new IllegalStateException("Step 8: primary " + primary + " is not in a1_ie_candidates.tsv");
            }
            dominantFtfs.addAll(Prompt1Tsv.ids(candidate.get(primary).get("represented_dominant_ftf_ids")));
            allFtfs.addAll(Prompt1Tsv.ids(candidate.get(primary).get("represented_ftf_ids")));
        } else {  // partial-fit primary (7.1)
            Prompt1Tsv.rows(coreFilter).stream().filter(r -> primary.equals(r.get("ie_ref"))
                            && "yes".equals(r.get("passes").strip()) && dominant.contains(r.get("ftf_id")))
                    .forEach(r -> dominantFtfs.add(r.get("ftf_id")));
            Prompt1Tsv.rows(membership).stream().filter(r -> primary.equals(r.get("ie_ref")))
                    .forEach(r -> allFtfs.add(r.get("ftf_id")));
        }
        Comparator<String> byNumber = Comparator.comparingInt(ftf -> Prompt1Tsv.digits(ftf));
        List<String> domf = new ArrayList<>(new LinkedHashSet<>(dominantFtfs));
        domf.sort(byNumber);
        List<String> allf = new ArrayList<>(new LinkedHashSet<>(allFtfs));
        allf.sort(byNumber);
        if (domf.isEmpty()) {
            throw new IllegalStateException("Step 8: the primary " + primary + " represents no dominant FTF");
        }
        Map<String, Map<String, String>> byDirection = new LinkedHashMap<>();
        Prompt1Tsv.rows(directions).forEach(row -> byDirection.put(row.get("direction_id"), row));
        List<Map<String, String>> linkRows = Prompt1Tsv.rows(links);
        Map<String, String> observationOf = new HashMap<>();
        Prompt1Tsv.rows(meanings).forEach(row -> observationOf.put(row.get("meaning_id"), row.get("observation_id")));
        Map<String, List<String>> holds = new LinkedHashMap<>();
        Map<String, List<String>> contextOf = new LinkedHashMap<>();
        byDirection.forEach((id, d) -> {
            Prompt1Tsv.ids(d.get("member_ids")).forEach(m -> holds.computeIfAbsent(m, k -> new ArrayList<>()).add(id));
            Prompt1Tsv.ids(d.get("context_ids")).forEach(m -> contextOf.computeIfAbsent(m, k -> new ArrayList<>()).add(id));
        });
        for (String ftf : domf) {
            if (!byDirection.containsKey(directionOf(ftf))) {
                throw new IllegalStateException("Step 8: " + directionOf(ftf) + " not in directions.tsv");
            }
        }

        List<List<String>> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        java.util.function.BiFunction<String, Set<String>, Map<String, String>> touching = (meaning, others) -> linkRows.stream()
                .filter(l -> {
                    String from = l.get("from_meaning_id");
                    String to = l.get("to_meaning_id");
                    if (!meaning.equals(from) && !meaning.equals(to)) {
                        return false;
                    }
                    if (others == null) {
                        return true;
                    }
                    Set<String> ends = new HashSet<>(Arrays.asList(from, to));
                    ends.remove(meaning);
                    return ends.stream().anyMatch(e -> others.contains(e));
                }).findFirst().orElse(null);
        java.util.function.Consumer<List<String>> add = ids -> {  // meaning, role, direction, ftf, link, relation
            String meaning = ids.get(0);
            if (!seen.add(meaning)) {
                return;
            }
            LinkedHashSet<String> refs = new LinkedHashSet<>(List.of(ids.get(2)));
            refs.addAll(holds.getOrDefault(meaning, List.of()));
            refs.addAll(contextOf.getOrDefault(meaning, List.of()));
            rows.add(List.of(meaning, ids.get(1), String.join(",", refs), ids.get(3), ids.get(4), ids.get(5)));
        };

        List<String> constitutive = new ArrayList<>();
        for (String ftf : domf) {
            for (String meaning : Prompt1Tsv.ids(byDirection.get(directionOf(ftf)).get("member_ids"))) {
                constitutive.add(meaning);
                add.accept(List.of(meaning, "constitutive", directionOf(ftf), ftf, Prompt1Tsv.EMPTY, Prompt1Tsv.EMPTY));
            }
        }
        for (String ftf : domf) {
            Map<String, String> direction = byDirection.get(directionOf(ftf));
            Set<String> own = new HashSet<>(Prompt1Tsv.ids(direction.get("member_ids")));
            Set<String> ownObservations = new HashSet<>();
            own.forEach(m -> ownObservations.add(observationOf.get(m)));
            for (String meaning : Prompt1Tsv.ids(direction.get("context_ids"))) {
                Map<String, String> link = touching.apply(meaning, own);
                if (link == null && !ownObservations.contains(observationOf.get(meaning))) {
                    skipped.add(meaning + " (" + directionOf(ftf) + ")");  // v05c: no established connection
                    continue;
                }
                add.accept(List.of(meaning, "context", directionOf(ftf), ftf,
                        link == null ? Prompt1Tsv.EMPTY : link.get("link_id"),
                        link == null ? Prompt1Tsv.EMPTY : link.get("relation_type")));
            }
        }
        for (String meaning : constitutive) {
            for (Map<String, String> link : linkRows) {
                String from = link.get("from_meaning_id");
                String to = link.get("to_meaning_id");
                if (!meaning.equals(from) && !meaning.equals(to)) {
                    continue;
                }
                String other = meaning.equals(from) ? to : from;
                if (seen.contains(other) || UNCERTAIN_LINK.matcher(link.get("relation_type") + " "
                        + link.getOrDefault("scope", "")).find()) {
                    continue;
                }
                List<String> where = holds.containsKey(other) ? holds.get(other) : contextOf.get(other);
                if (where == null || where.isEmpty()) {
                    continue;
                }
                seen.add(other);
                rows.add(List.of(other, "linked_context", String.join(",", where), Prompt1Tsv.EMPTY, link.get("link_id"),
                        link.get("relation_type")));
            }
        }
        for (String ftf : allf) {
            if (domf.contains(ftf) || !byDirection.containsKey(directionOf(ftf))) {
                continue;
            }
            for (String meaning : Prompt1Tsv.ids(byDirection.get(directionOf(ftf)).get("member_ids"))) {
                Map<String, String> link = touching.apply(meaning, null);
                add.accept(List.of(meaning, "primary_ie_context", directionOf(ftf), ftf,
                        link == null ? Prompt1Tsv.EMPTY : link.get("link_id"),
                        link == null ? Prompt1Tsv.EMPTY : link.get("relation_type")));
            }
        }

        List<String> columns = List.of(header.split("\t", -1));
        List<String> idColumns = List.of("meaning_id", "role", "direction_id", "ftf_id", "link_id", "relation");
        List<List<String>> out = new ArrayList<>();
        for (List<String> ids : rows) {
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < idColumns.size(); i++) {
                row.put(idColumns.get(i), ids.get(i));
            }
            out.add(columns.stream().map(column -> row.getOrDefault(column, Prompt1Tsv.EMPTY)).toList());
        }
        return Prompt1TurnAnswers.fillEvidenceCopies(meanings, Prompt1Tsv.text(header, out)) + "\n";
    }

    /** FTF_016 → FD_016 (an FTF has the number of its direction). */
    private static String directionOf(String ftf) {
        return "FD_" + ftf.substring(ftf.indexOf('_') + 1);
    }

    // ---- Step 9 signals ------------------------------------------------------------------------------------

    /**
     * SM v0.133 12.1: the signals of a HETA are the listed lines of R3:&lt;heta&gt;.5.2, one per line in
     * source order: the lines after the sentence ending in ':' up to the closing "These signals …"
     * sentence. Empty when the section does not have that form.
     *
     * @param sectionContent the text of R3:&lt;heta&gt;.5.2 without its KEY line
     */
    public static List<String> r3Signals(String sectionContent) {
        List<String> lines = sectionContent.lines().map(line -> line.strip()).filter(line -> !line.isEmpty()).toList();
        if (lines.isEmpty() || !lines.get(0).endsWith(":")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.startsWith("These signals") || line.startsWith("#") || line.startsWith("KEY:")) {
                break;
            }
            out.add(Prompt1Tsv.rstrip(line, ";.").strip());
        }
        return out;
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    /** Whitespace collapsed to single spaces (Jan's server {@code clean}). */
    private static String clean(String value) {
        return String.join(" ", value.strip().split("\\s+"));
    }

    /** Rows with every value stripped, as Jan's server reads its files. */
    private static List<Map<String, String>> stripped(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            row.replaceAll((column, value) -> value == null ? "" : value.strip());
        }
        return rows;
    }
}
