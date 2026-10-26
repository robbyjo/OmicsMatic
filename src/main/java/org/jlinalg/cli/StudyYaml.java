/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/** Bounded safe YAML reader for study-pipeline artifacts. */
final class StudyYaml {
    static final int CODE_POINT_LIMIT = 16_000_000;

    private StudyYaml() { }

    static Object load(Reader input) {
        return reader().load(input);
    }

    static Object load(String input) {
        return reader().load(input);
    }

    static Map<String, Object> loadMap(Path path) throws IOException {
        return loadMap(path, CODE_POINT_LIMIT);
    }

    static Map<String, Object> loadArtifact(Path path) throws IOException {
        if (Files.size(path) > 128L * 1024 * 1024)
            throw new IOException("Generated study artifact exceeds 128 MiB: " + path);
        return loadMap(path, 128 * 1024 * 1024);
    }

    /** Opt-in reader for source contracts whose decimal tokens must not round through double. */
    static Map<String, Object> loadExactNumericArtifact(Path path) throws IOException {
        if (Files.size(path) > 128L * 1024 * 1024)
            throw new IOException("Source artifact exceeds 128 MiB: " + path);
        try { return loadMap(path, 128 * 1024 * 1024, true); }
        catch (YAMLException invalid) {
            throw new IOException("Invalid exact-numeric source artifact: " + invalid.getMessage(), invalid);
        }
    }

    private static Map<String, Object> loadMap(Path path, int codePointLimit) throws IOException {
        return loadMap(path, codePointLimit, false);
    }

    private static Map<String, Object> loadMap(Path path, int codePointLimit, boolean exact) throws IOException {
        Object value;
        try (Reader input = Files.newBufferedReader(path,
                StandardCharsets.UTF_8)) {
            value = reader(codePointLimit, exact).load(input);
        }
        if (!(value instanceof Map<?, ?> raw))
            throw new IOException("Expected YAML mapping: " + path);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Yaml reader() {
        return reader(CODE_POINT_LIMIT);
    }

    private static Yaml reader(int codePointLimit) {
        return reader(codePointLimit, false);
    }

    private static Yaml reader(int codePointLimit, boolean exact) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(80);
        options.setCodePointLimit(codePointLimit);
        return new Yaml(exact ? new ExactConstructor(options) : new SafeConstructor(options));
    }

    private static final class ExactConstructor extends SafeConstructor {
        ExactConstructor(LoaderOptions options) {
            super(options);
            yamlConstructors.put(Tag.FLOAT, new AbstractConstruct() {
                @Override public Object construct(Node node) {
                    String token = ((ScalarNode) node).getValue().replace("_", "");
                    try { return new BigDecimal(token); }
                    catch (NumberFormatException invalid) {
                        throw new YAMLException("Expected a finite decimal numeric token", invalid);
                    }
                }
            });
        }
    }
}
