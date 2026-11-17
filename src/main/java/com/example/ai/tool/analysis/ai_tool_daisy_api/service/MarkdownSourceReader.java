package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the original Prompt 1 reference documents (.md) from a directory, one
 * {@link DoclingChunkReader.SourceDocument} per file, so the splitter and loader are shared
 * with the Docling chunk build.
 * <p>
 * Only files named {@code 01_}, {@code 02_}, {@code 03_}, {@code 05_}, {@code 06_}, {@code 07_},
 * {@code R3_}, {@code R4_} and {@code R5_} are read; the system message and other files in the
 * same directory are skipped. The document code is the filename prefix and the version is the
 * {@code _v<n>} suffix, lowercased. Every version in the directory is read.
 * <ul>
 *   <li>Documents with {@code KEY:} lines, the 05 module list and 07 are one chunk holding the
 *       whole text; the splitter cuts them at their own markers.</li>
 *   <li>Other documents (01, 02, 03 and unkeyed 06) are one chunk per {@code #} heading, with the
 *       heading path built from the heading levels.</li>
 * </ul>
 */
@Component
public class MarkdownSourceReader {

    static final String SOURCE_BUILD = "markdown";
    private static final Pattern FILE_NAME =
            Pattern.compile("^(0[123567]|R[345])_.*?_[vV](\\d+)\\.md$");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern KEY_LINE = Pattern.compile("^KEY:\\s*\\S+");
    private static final Set<String> WHOLE_TEXT_CODES = Set.of("R3", "R4", "R5", "05", "07");

    public List<DoclingChunkReader.SourceDocument> read(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(directory)) {
            files = listing.filter(path -> Files.isRegularFile(path)
                            && FILE_NAME.matcher(path.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }
        List<DoclingChunkReader.SourceDocument> documents = new ArrayList<>(files.size());
        for (Path file : files) {
            documents.add(readFile(file));
        }
        return documents;
    }

    DoclingChunkReader.SourceDocument readFile(Path file) throws IOException {
        return read(file.getFileName().toString(), Files.readAllBytes(file));
    }

    /** True for a file name the reader takes as a reference document (01_…_v4.md, R3_…_v06.md, …). */
    public static boolean isReferenceDocument(String fileName) {
        return FILE_NAME.matcher(fileName).matches();
    }

    /** One reference document from its file name and bytes (e.g. a file of an uploaded release). */
    public DoclingChunkReader.SourceDocument read(String fileName, byte[] bytes) {
        Matcher name = FILE_NAME.matcher(fileName);
        if (!name.matches()) {
            throw new IllegalArgumentException("Not a reference document file name: " + fileName);
        }
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String code = name.group(1);
        List<String> lines = Arrays.asList(text.split("\n", -1));

        List<KnowledgeSectionSplitter.SourceChunk> chunks = WHOLE_TEXT_CODES.contains(code) || hasKeyLines(lines)
                ? List.of(new KnowledgeSectionSplitter.SourceChunk(0, "", text))
                : chunksByHeading(lines);
        return new DoclingChunkReader.SourceDocument(code, "v" + name.group(2), fileName,
                Hashes.sha256(bytes), SOURCE_BUILD, chunks);
    }

    private static boolean hasKeyLines(List<String> lines) {
        return lines.stream().anyMatch(line -> KEY_LINE.matcher(line).lookingAt());
    }

    /** One chunk per heading; the heading line itself is carried by the heading path. */
    static List<KnowledgeSectionSplitter.SourceChunk> chunksByHeading(List<String> lines) {
        List<KnowledgeSectionSplitter.SourceChunk> chunks = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        Deque<Integer> levels = new ArrayDeque<>();
        String currentPath = "";
        StringBuilder body = new StringBuilder();

        for (String line : lines) {
            Matcher heading = HEADING.matcher(line);
            if (!heading.matches() || heading.group(2).isEmpty()) {
                body.append(line).append('\n');
                continue;
            }
            addChunk(chunks, currentPath, body);
            body = new StringBuilder();

            int level = heading.group(1).length();
            while (!levels.isEmpty() && levels.peekLast() >= level) {
                levels.removeLast();
                path.removeLast();
            }
            levels.addLast(level);
            path.addLast(heading.group(2).replaceAll("\\s+", " "));
            currentPath = String.join(" > ", path);
        }
        addChunk(chunks, currentPath, body);
        return chunks;
    }

    private static void addChunk(List<KnowledgeSectionSplitter.SourceChunk> chunks, String headingPath,
                                 StringBuilder body) {
        // a heading always produces a chunk, even an empty one, so it stays visible in the index
        if (!headingPath.isEmpty() || !body.toString().isBlank()) {
            chunks.add(new KnowledgeSectionSplitter.SourceChunk(chunks.size(), headingPath,
                    body.toString().stripTrailing()));
        }
    }
}
