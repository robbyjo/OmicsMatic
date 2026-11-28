/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit complete prepared-input roots, original method options and checked capacities. */
final class Pbmm2Request {
    private Pbmm2Request() { }
    record Root(String id, String original) { }
    record Request(String version, String operation, String home, String containerHome, String image,
                   List<Root> resources, String cwd, String reference, String reads,
                   String outputKind, String preset, int threads, String logging,
                   List<String> parameterArguments, long inputBytes, long fileBytes,
                   int files, long records, NextflowProcess.Limits process, Map<?, ?> raw) {
        Request { resources = List.copyOf(resources); parameterArguments = List.copyOf(parameterArguments); }
    }
    static Request read(Path path) throws IOException {
        return parse(object(NextflowParameters.parse(utf8(bytes(path, 4194304)), 4194304, 100000)));
    }
    static Request parse(Map<?, ?> p) throws IOException {
        keys(p, "schema_version", "edition", "operation", "runtime_home", "container_home", "image",
                "resources", "working_directory", "reference", "reads", "output_kind", "preset", "threads",
                "logging", "parameters", "maximum_input_bytes", "maximum_file_bytes", "maximum_files", "maximum_records", "process_limits");
        integer(p.get("schema_version"), 1, 1);
        String version = text(p.get("edition")), operation = text(p.get("operation"));
        Pbmm2Profiles.edition(version);
        if (!Set.of("INDEX", "ALIGN").contains(operation)) throw new IOException("Select original INDEX or ALIGN explicitly");
        var roots = new ArrayList<Root>(); var ids = new HashSet<String>();
        for (Object item : list(p.get("resources"))) {
            var row = object(item); keys(row, "id", "path"); String id = text(row.get("id"));
            if (!id.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}") || !ids.add(id)) throw new IOException("Resource labels must be unique simple metadata tokens");
            String origin = path(row.get("path"));
            for (Root prior : roots) if (inside(origin, prior.original()) || inside(prior.original(), origin)) throw new IOException("Complete input roots must be disjoint; no overlapping or duplicated capture");
            roots.add(new Root(id, origin));
            if (roots.size() > 64) throw new IOException("Complete input-root capacity exceeded");
        }
        if (roots.isEmpty()) throw new IOException("Declare every complete original input/reference root");
        String reference = path(p.get("reference")); requireBound(reference, roots);
        String reads;
        if (operation.equals("INDEX")) {
            if (p.get("reads") != null || !"MMI".equals(p.get("output_kind"))) throw new IOException("INDEX uses reads=null and output_kind=MMI");
            reads = null;
        } else { reads = path(p.get("reads")); requireBound(reads, roots); }
        String kind = text(p.get("output_kind"));
        if (operation.equals("ALIGN") && !Set.of("BAM", "ALIGNMENTSET", "CONSENSUSALIGNMENTSET", "TRANSCRIPTALIGNMENTSET", "DATASTORE").contains(kind)) throw new IOException("Declare an original BAM, native dataset XML or datastore JSON output kind");
        String preset = text(p.get("preset"));
        if (!Set.of("SUBREAD", "CCS", "ISOSEQ", "UNROLLED").contains(preset.toUpperCase(Locale.ROOT))) throw new IOException("Declare an original named preset without inferred read type");
        String cwd = text(p.get("working_directory"));
        if (!cwd.equals("OUTPUT")) { cwd = path(cwd); requireBound(cwd, roots); }
        String logging = text(p.get("logging"));
        if (!Set.of("INFO", "DEBUG", "TRACE").contains(logging)) throw new IOException("Keep source warnings visible with INFO, DEBUG or TRACE logging");
        var options = Pbmm2Profiles.options(version, operation); var arguments = new ArrayList<String>(); var selected = new HashSet<String>();
        for (var entry : object(p.get("parameters")).entrySet()) {
            String name = text(entry.getKey());
            if (Set.of("preset", "j", "num-threads", "report-json").contains(name)) throw new IOException("Preset, thread count and owned report output are bound by the typed request");
            var descriptor = options.get(name);
            if (descriptor == null) throw new NextflowProcess.Unavailable("Original option is not represented by this exact edition profile: " + name + "; no unreported option is substituted");
            String canonical = strings(descriptor.get("names")).get(0);
            if (!selected.add(canonical)) throw new IOException("Repeated original option through aliases: " + name);
            Object value = entry.getValue(); String type = text(descriptor.get("type"));
            String flag = (name.length() == 1 ? "-" : "--") + name;
            if (type.equals("bool")) {
                if (!(value instanceof Boolean enabled)) throw new IOException("Original boolean option needs true/false: " + name);
                if (!enabled) { if (Boolean.TRUE.equals(descriptor.get("default"))) throw new IOException("False cannot be represented by omitting this true-default source flag"); continue; }
                arguments.add(flag); continue;
            }
            String literal;
            if (type.equals("string")) {
                literal = text(value); token(literal);
                if (canonical.equals("enforced-mapping")) { literal = path(value); requireBound(literal, roots); }
            } else if (type.equals("int")) { integer(value, Integer.MIN_VALUE, Integer.MAX_VALUE); literal = number(value); }
            else if (type.equals("float") || type.equals("double")) {
                literal = number(value);
                try { if (!Double.isFinite(new BigDecimal(literal).doubleValue())) throw new NumberFormatException("nonfinite"); }
                catch (NumberFormatException invalid) { throw new IOException("Unassessable original numeric parameter: " + name, invalid); }
            } else throw new IOException("Unrepresented original option type: " + type);
            arguments.add(flag); arguments.add(literal);
        }
        var limits = object(p.get("process_limits"));
        keys(limits, "wall_millis", "address_space_bytes", "cpu_seconds", "file_bytes", "open_files", "workspace_bytes", "file_count", "processes", "threads", "sampled_rss_bytes", "monitor_millis");
        NextflowProcess.Limits process;
        try { process = new NextflowProcess.Limits(integer(limits.get("wall_millis"), 1, 604800000L), integer(limits.get("address_space_bytes"), 16777216, 1099511627776L), integer(limits.get("cpu_seconds"), 1, 604800), integer(limits.get("file_bytes"), 1, 1000000000000L), (int) integer(limits.get("open_files"), 8, 1048576), integer(limits.get("workspace_bytes"), 1, 1000000000000L), (int) integer(limits.get("file_count"), 4, 1000000), (int) integer(limits.get("processes"), 1, 4096), (int) integer(limits.get("threads"), 1, 1048576), integer(limits.get("sampled_rss_bytes"), 1, 1099511627776L), (int) integer(limits.get("monitor_millis"), 1, 1000)); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid complete-input process capacities", invalid); }
        return new Request(version, operation, path(p.get("runtime_home")), path(p.get("container_home")), path(p.get("image")), roots, cwd, reference, reads, kind, preset,
                (int) integer(p.get("threads"), 1, 1024), logging, arguments,
                integer(p.get("maximum_input_bytes"), 1, 1000000000000L), integer(p.get("maximum_file_bytes"), 1, 1000000000000L), (int) integer(p.get("maximum_files"), 1, 1000000), integer(p.get("maximum_records"), 1, 1000000000L), process, p);
    }
    static String number(Object value) throws IOException {
        if (value instanceof NextflowParameters.NumberToken token) return token.text();
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double || value instanceof BigDecimal) return value.toString();
        throw new IOException("Use a literal original JSON number, not a text replacement");
    }
    static void token(String value) throws IOException { if (value.indexOf('\0') >= 0) throw new IOException("NUL cannot be represented in an original argv token"); }
    static String path(Object raw) throws IOException {
        String p = text(raw); token(p);
        if (!p.startsWith("/") || p.length() < 2 || p.endsWith("/") || p.indexOf('\\') >= 0 || p.chars().anyMatch(c -> c < 32 || c == 127)) throw new IOException("Declare a complete canonical absolute Linux path");
        for (String part : p.substring(1).split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Original path aliases must be declared without dot/double-slash components");
        if (p.indexOf(':') >= 0 || p.indexOf(',') >= 0) throw new NextflowProcess.Unavailable("The selected original container bind syntax cannot represent this literal path; analysis skipped without renaming: " + p);
        return p;
    }
    static boolean inside(String child, String parent) { return child.equals(parent) || child.startsWith(parent + "/"); }
    static void requireBound(String path, List<Root> roots) throws IOException {
        if (roots.stream().noneMatch(r -> inside(path, r.original()))) throw new IOException("Declare the complete original resource root for " + path + "; no uncaptured file is inferred");
    }
}
