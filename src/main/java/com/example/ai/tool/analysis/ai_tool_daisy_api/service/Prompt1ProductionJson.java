package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The production JSON of Prompt 1 (Jan's backend v07, D14): {@code output.json} built from the full control JSON
 * ({@code output_controle.json}) and checked against the production schema
 * ({@code Structured_JSON_Prompt_1_production_v1.json}) before it is written. Ported from {@code daisy_preload.py}
 * {@code production_output}, {@code clean} and {@code schema_errors}; the schema check covers exactly the keywords
 * Jan's checker covers, so both backends accept and refuse the same output.
 */
public final class Prompt1ProductionJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    private Prompt1ProductionJson() {
    }

    /**
     * {@code production_output}: client name from the intake (structured_data.client_name), IEs as "&lt;ref&gt; &lt;title&gt;",
     * the teleonic pattern = identity and functional directionality of the dominant FTF(s) of Steps 1–2, A3 direct and
     * indirect as HETA_JSON_IDENTIFIER, B1/B2 items {nr, label, ID, qest_json}, B3 items {nr, heta_id, heta_json}.
     *
     * @throws IllegalStateException when the intake has no client name
     */
    public static ObjectNode production(JsonNode control, String observations) {
        String name = Prompt1Tsv.rows(observations).stream()
                .filter(o -> "client_name".equals(o.get("source_label")))
                .map(o -> o.getOrDefault("source_value", "")).findFirst().orElse("");
        name = Prompt1Tsv.strip(name.strip(), "\"").strip();
        if (name.isEmpty()) {
            throw new IllegalStateException("output.json: client_name (structured_data.client_name) not found in observations.tsv");
        }
        Function<JsonNode, String> ie = x -> clean((x.path("ie_ref").asText() + " " + x.path("ie_title").asText()).strip());
        List<String> pattern = new ArrayList<>();
        for (JsonNode ftf : control.path("ftfs")) {
            if ("dominant".equals(ftf.path("priority_status").asText())) {
                String identity = Prompt1Tsv.rstrip(ftf.path("identity").asText().strip(), ".:;").strip();
                pattern.add(clean(identity + ": " + ftf.path("functional_directionality").asText().strip()));
            }
        }
        JsonNode hetaJson = control.path("heta_json");
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode out = f.objectNode();
        out.put("prompt_id", "Prompt_1");
        out.put("date", control.path("current_run_date").asText());
        out.put("client-name", clean(name));
        ObjectNode a1 = out.putObject("A1");
        a1.put("primary_ie", ie.apply(control.path("a1").path("primary_ie")));
        ArrayNode secondary = a1.putArray("secondary_ie");
        control.path("a1").path("secondary_ie").forEach(x -> secondary.add(ie.apply(x)));
        a1.put("teleonic_pattern", String.join(" | ", pattern));
        ObjectNode a3 = out.putObject("A3");
        for (String classification : List.of("direct", "indirect")) {
            ArrayNode ids = a3.putArray(classification);
            for (JsonNode h : control.path("a3")) {
                String ref = h.path("heta_ref").asText();
                if (classification.equals(h.path("classification").asText()) && hetaJson.has(ref)) {
                    ids.add(hetaJson.get(ref).asText());
                }
            }
        }
        out.set("B1", items(control.path("b1")));
        out.set("B2", items(control.path("b2").path("items")));
        ArrayNode b3 = out.putArray("B3");
        for (JsonNode b : control.path("b3")) {
            ObjectNode item = b3.addObject();
            item.set("nr", b.path("nr"));
            item.set("heta_id", b.path("heta_id"));
            item.set("heta_json", b.path("heta_json"));
        }
        return out;
    }

    private static ArrayNode items(JsonNode list) {
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        for (JsonNode i : list) {
            ObjectNode item = out.addObject();
            item.set("nr", i.path("nr"));
            item.put("label", clean(i.path("label").asText()));
            item.set("ID", i.path("ID"));
            item.set("qest_json", i.path("qest_json"));
        }
        return out;
    }

    /** {@code clean}: the schema's clean_string forbids &lt; &gt; [ ] { }; brackets become round, &lt; and &gt; become words. */
    public static String clean(String value) {
        String v = value.replace('[', '(').replace(']', ')').replace('{', '(').replace('}', ')');
        return v.replace("<", " kleiner dan ").replace(">", " groter dan ").replace("  ", " ").strip();
    }

    /** The JSON Schema inside the production schema file ({"name", "strict", "schema"}). */
    public static JsonNode schema(String schemaFileText) {
        try {
            JsonNode schema = MAPPER.readTree(schemaFileText).path("schema");
            if (!schema.isObject()) {
                throw new IllegalArgumentException("the schema file has no \"schema\" object (expected {name, strict, schema})");
            }
            return schema;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("the schema file is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * {@code schema_errors}: checks data against the production schema with the JSON Schema keywords it uses (type,
     * required, additionalProperties, properties, items, enum, pattern, minLength, maxLength, minItems, maxItems,
     * minimum, maximum, $ref). Returns the messages; empty when the data conforms.
     */
    public static List<String> schemaErrors(JsonNode data, JsonNode root) {
        List<String> errors = new ArrayList<>();
        check(data, root, root, "$", errors);
        return errors;
    }

    private static void check(JsonNode v, JsonNode sc, JsonNode root, String path, List<String> errors) {
        while (sc.has("$ref")) {
            String ref = sc.get("$ref").asText();
            JsonNode target = ref.startsWith("#/$defs/") ? root.path("$defs").path(ref.substring("#/$defs/".length()))
                    : MissingNode.getInstance();
            if (!target.isObject()) {  // fail closed: an unknown reference would otherwise accept anything
                errors.add(path + ": schema $ref " + ref + " not found in $defs");
                return;
            }
            sc = target;
        }
        if (sc.has("type") && !sc.get("type").isTextual()) {  // fail closed: e.g. a list of types is not supported
            errors.add(path + ": schema type " + sc.get("type") + " not supported (one type name expected)");
            return;
        }
        String type = sc.path("type").asText(null);
        if (type != null && !hasType(v, type)) {
            errors.add(path + ": expected " + type + ", got " + typeName(v));
            return;
        }
        if (sc.has("enum")) {
            boolean found = false;
            for (JsonNode option : sc.get("enum")) {
                found |= option.equals(v);
            }
            if (!found) {
                errors.add(path + ": " + repr(v) + " not in enum");
            }
        }
        if (v.isTextual()) {
            String s = v.asText();
            int length = s.codePointCount(0, s.length());
            if (length < sc.path("minLength").asInt(0) || length > sc.path("maxLength").asInt(Integer.MAX_VALUE)) {
                errors.add(path + ": length " + length + " out of range");
            }
            if (sc.has("pattern")) {
                String p = sc.get("pattern").asText();
                if (!PATTERNS.computeIfAbsent(p, key -> Pattern.compile(key)).matcher(s).find()) {
                    errors.add(path + ": '" + head(s, 60) + "'"
                            + " does not match " + head(p, 40));
                }
            }
        }
        if (v.isNumber()) {
            double n = v.asDouble();
            if (n < sc.path("minimum").asDouble(-1e18) || n > sc.path("maximum").asDouble(1e18)) {
                errors.add(path + ": " + v + " out of range");
            }
        }
        if (v.isArray()) {
            if (v.size() < sc.path("minItems").asInt(0) || v.size() > sc.path("maxItems").asInt(Integer.MAX_VALUE)) {
                errors.add(path + ": " + v.size() + " items out of range");
            }
            JsonNode items = sc.has("items") ? sc.get("items") : JsonNodeFactory.instance.objectNode();
            for (int i = 0; i < v.size(); i++) {
                check(v.get(i), items, root, path + "[" + i + "]", errors);
            }
        }
        if (v.isObject()) {
            JsonNode props = sc.path("properties");
            sc.path("required").forEach(k -> {
                if (!v.has(k.asText())) {
                    errors.add(path + ": missing " + k.asText());
                }
            });
            if (sc.has("additionalProperties") && sc.get("additionalProperties").isBoolean()
                    && !sc.get("additionalProperties").asBoolean()) {
                v.fieldNames().forEachRemaining(k -> {
                    if (!props.has(k)) {
                        errors.add(path + ": extra key " + k);
                    }
                });
            }
            for (Map.Entry<String, JsonNode> e : v.properties()) {
                if (props.has(e.getKey())) {
                    check(e.getValue(), props.get(e.getKey()), root, path + "." + e.getKey(), errors);
                }
            }
        }
    }

    /** Python's isinstance for the JSON types (a boolean is no integer or number). */
    private static boolean hasType(JsonNode v, String type) {
        return switch (type) {
            case "object" -> v.isObject();
            case "array" -> v.isArray();
            case "string" -> v.isTextual();
            case "integer" -> v.isIntegralNumber();
            case "number" -> v.isNumber();
            case "boolean" -> v.isBoolean();
            case "null" -> v.isNull();
            default -> false;  // fail closed: a type name the checker does not know
        };
    }

    private static String typeName(JsonNode v) {
        if (v.isObject()) {
            return "dict";
        }
        if (v.isArray()) {
            return "list";
        }
        if (v.isTextual()) {
            return "str";
        }
        if (v.isBoolean()) {
            return "bool";
        }
        if (v.isIntegralNumber()) {
            return "int";
        }
        if (v.isNumber()) {
            return "float";
        }
        return "NoneType";
    }

    private static String repr(JsonNode v) {
        return v.isTextual() ? "'" + v.asText() + "'" : v.toString();
    }

    private static String head(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }
}
