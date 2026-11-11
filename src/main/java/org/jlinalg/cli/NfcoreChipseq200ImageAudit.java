/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Check every observed task's literal original image/namespace command from immutable objects. */
final class NfcoreChipseq200ImageAudit {
    private NfcoreChipseq200ImageAudit() { }
    static Map<String, Object> audit(NfcoreChipseq200Files data, Path declared) throws IOException {
        byte[] raw; try (var input = NfcoreChipseq200ImageAudit.class.getResourceAsStream("/org/jlinalg/omics/nfcore-chipseq200/images.json")) { if (input == null) throw new IOException("Missing original image profile"); raw = input.readNBytes(17145); }
        if (raw.length != 17144 || !sha(raw).equals("08c51df1b7b00636f4889d5607e155930b0fa34c5ebb8bf1a8baaad1ff536c58")) throw new IOException("Original image profile differs");
        Map<?, ?> profile = object(SimpleJson.parse(utf8(raw))), declaration = object(json(declared, 1048576)); keys(declaration, "schema_version", "images"); integer(declaration.get("schema_version"), 1, 1); Map<?, ?> paths = object(declaration.get("images")), original = object(profile.get("images")); if (!paths.keySet().equals(original.keySet())) throw new IOException("Complete source URI image declaration differs");
        var bindings = new LinkedHashMap<String, String>();
        for (var entry : original.entrySet()) for (String process : strings(object(entry.getValue()).get("process_names"))) { if (bindings.putIfAbsent(process, text(paths.get(entry.getKey()))) != null) throw new IOException("Ambiguous original module/alias container binding"); }
        var counts = new LinkedHashMap<String, Integer>();
        for (var task : data.tasks()) {
            String process = task.process().substring(task.process().lastIndexOf(':') + 1), expected = bindings.get(process); if (expected == null) throw new IOException("Observed original process lacks an exact admitted image: " + task.process());
            String script = utf8(bytes(data.work(task, ".command.run"), 16777216)); String image = container(script);
            if (!expected.equals(image)) throw new IOException("Observed original task used another image: " + task.process() + " / " + task.tag()); counts.merge(process, 1, Math::addExact);
        }
        return Map.of("observed_successful_or_cached_tasks", data.tasks().size(), "complete_task_images_and_namespaces_checked", true, "process_counts", counts, "source_images", original.size(), "image_files_reopened_by_portable_audit", false, "image_byte_admission_scope", "Original source-control runtime verified every complete image before/after execution; this portable component verifies captured command-to-source binding, without authenticating author history.");
    }
    static String container(String script) throws IOException {
        var pattern = java.util.regex.Pattern.compile("\\bsingularity[ \\t]+exec[ \\t]+"); var match = pattern.matcher(script); if (!match.find()) throw new IOException("Original task has no literal singularity exec command"); int start = match.end(); if (match.find()) throw new IOException("Original task has ambiguous multiple container launches"); var input = new Shell(script, start); var flags = new java.util.HashSet<String>();
        for (;;) {
            String token = input.next(); if (token == null) throw new IOException("Original task container command is incomplete");
            if (token.equals("-B") || token.equals("--bind")) { if (input.next() == null) throw new IOException("Original task bind value is absent"); }
            else if (Set.of("--userns", "--no-home", "--pid").contains(token)) { if (!flags.add(token)) throw new IOException("Repeated original namespace flag"); }
            else { if (token.startsWith("-") || !token.startsWith("/") || !flags.equals(Set.of("--userns", "--no-home", "--pid")) || !"/bin/bash".equals(input.next())) throw new IOException("Original task image/namespace launch differs from exact runtime binding"); return token; }
        }
    }
    /** Parse only literal shell words in the fixed executable/option/image prefix; never evaluate shell. */
    private static final class Shell {
        private final String source; private int offset;
        Shell(String source, int offset) { this.source = source; this.offset = offset; }
        String next() throws IOException {
            while (offset < source.length() && (source.charAt(offset) == ' ' || source.charAt(offset) == '\t')) offset++; if (offset == source.length() || source.charAt(offset) == '\n' || source.charAt(offset) == '\r') return null;
            var result = new StringBuilder(); int quote = 0;
            while (offset < source.length()) {
                char c = source.charAt(offset++); if (c == '\n' || c == '\r') throw new IOException("Unassessable newline in original container prefix");
                if (quote == 0 && (c == ' ' || c == '\t')) return result.toString();
                if (quote == '\'') { if (c == '\'') quote = 0; else result.append(c); continue; }
                if (c == '\\') { if (offset == source.length()) throw new IOException("Incomplete original container path escape"); char next = source.charAt(offset++); if (next == '\n' || next == '\r') throw new IOException("Unassessable continued line in original image prefix"); if (quote == '"' && "\\$`\"".indexOf(next) < 0) result.append('\\'); result.append(next); continue; }
                if (c == '\'' && quote == 0 || c == '"') { if (quote == c) quote = 0; else if (quote == 0) quote = c; else result.append(c); continue; }
                if (c == '$' || c == '`' || quote == 0 && ";|&()< >".indexOf(c) >= 0) throw new IOException("Dynamic/unassessable expression in original image prefix"); result.append(c);
            }
            if (quote != 0) throw new IOException("Unterminated original image quote"); return result.toString();
        }
    }
}
