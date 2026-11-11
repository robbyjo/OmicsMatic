/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jlinalg.sequence.StringTieGtf;

/** Typed source settings for short-read assembly, reference estimation and transcript merge. */
final class StringTieRecipe {
    private StringTieRecipe() { }
    enum Mode { ASSEMBLE, ESTIMATE, MERGE }
    enum Strand { UNSTRANDED, FR_FIRSTSTRAND, FR_SECONDSTRAND }
    record Request(String profile, String version, Mode mode, String bam, String sizes, String guide, List<String> merge,
                   String genomeBuild, Strand strand, StringTieBam.Policies bamPolicies, StringTieGtf.PopulationPolicy population,
                   int threads, Map<String, String> parameters, Map<String, Long> limits) {
        Request { merge = List.copyOf(merge); parameters = java.util.Collections.unmodifiableMap(new TreeMap<>(parameters)); limits = Map.copyOf(limits); }
        long capacity(String name) { return limits.get(name); }
        String mergeMember(int index) { return String.format(java.util.Locale.ROOT, "inputs/merge-%04d.gtf", index + 1); }
        List<String> arguments(String binary, String inputRoot) {
            var command = new ArrayList<String>(); command.add(binary);
            if (mode == Mode.MERGE) command.add("--merge"); else command.add(inputRoot + "/inputs/reads.bam");
            if (guide != null) command.addAll(List.of("-G", inputRoot + "/inputs/guide.gtf"));
            if (mode == Mode.ESTIMATE) command.addAll(List.of("-e", "-A", "gene-abundance.tsv", "-B"));
            if (strand != Strand.UNSTRANDED) command.add(strand == Strand.FR_FIRSTSTRAND ? "--rf" : "--fr");
            if ("DISABLE".equals(parameters.get("trimming"))) command.add("-t");
            var flags = Map.of("minimum_length", "-m", "minimum_isoform_fraction", "-f", "minimum_coverage", "-c", "minimum_FPKM", "-F", "minimum_TPM", "-T");
            for (String name : List.of("minimum_length", "minimum_isoform_fraction", "minimum_coverage", "minimum_FPKM", "minimum_TPM")) {
                String value = parameters.get(name); if (value != null) command.addAll(List.of(flags.get(name), value));
            }
            command.addAll(List.of("-p", Integer.toString(threads), "-o", "transcripts.gtf"));
            if (mode == Mode.MERGE) for (int i = 0; i < merge.size(); i++) command.add(inputRoot + "/" + mergeMember(i));
            return List.copyOf(command);
        }
    }
    static Request read(Path source, boolean captured, Runnable check) throws IOException {
        check.run(); Map<?, ?> raw = NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(source, 2097152)), 2097152, 100000));
        NextflowFiles.keys(raw, "schema_version", "profile", "mode", "bam", "reference_sizes", "guide", "merge_inputs", "genome_build", "strand", "bam_policies", "population", "threads", "parameters", "limits");
        NextflowFiles.integer(raw.get("schema_version"), 1, 1);
        String profile = text(raw.get("profile")), version = text(StringTieSetupEvidence.profile(profile).get("version")); Mode mode = choice(Mode.class, raw.get("mode"));
        String sizes = source(raw.get("reference_sizes"), captured), bam = nullablePath(raw.get("bam"), captured), guide = nullablePath(raw.get("guide"), captured);
        var merge = new ArrayList<String>(); for (Object value : NextflowFiles.list(raw.get("merge_inputs"))) { require(merge.size() < 1024, "StringTie merge input capacity exceeded"); merge.add(source(value, captured)); }
        require(mode == Mode.MERGE ? bam == null && !merge.isEmpty() : bam != null && merge.isEmpty(), "Declare exactly the BAM or complete merge-GTF input population for this mode");
        require(mode != Mode.ESTIMATE || guide != null, "StringTie reference-only estimation requires an explicit complete guide");
        String build = text(raw.get("genome_build")); require(!build.isBlank() && build.length() <= 4096 && build.chars().noneMatch(c -> c < 32 || c == 127), "Declare a bounded genome-build label; it does not authenticate a reference assembly");
        Strand strand = choice(Strand.class, raw.get("strand")); require(mode != Mode.MERGE || strand == Strand.UNSTRANDED, "Merge has no alignment strand override");
        if (version.equals("1.3.0") && strand != Strand.UNSTRANDED) throw new StringTieRuntime.Unavailable("Exact StringTie1.3.0 lacks the requested --rf/--fr interface; select a supported explicit edition or skip this dependent fit, never substitute automatically");
        StringTieBam.Policies policies = null;
        if (raw.get("bam_policies") != null) {
            Map<?, ?> p = NextflowFiles.object(raw.get("bam_policies")); NextflowFiles.keys(p, "missing_NH", "missing_XS", "sort_header");
            policies = new StringTieBam.Policies(choice(StringTieBam.MissingNh.class, p.get("missing_NH")), choice(StringTieBam.MissingXs.class, p.get("missing_XS")), choice(StringTieBam.SortHeader.class, p.get("sort_header")));
        }
        require((policies != null) == (mode != Mode.MERGE), "Explicit BAM NH/XS/sort policies are required only for alignment modes");
        StringTieGtf.PopulationPolicy population = raw.get("population") == null ? null : choice(StringTieGtf.PopulationPolicy.class, raw.get("population"));
        require((population != null) == (mode == Mode.ESTIMATE), "Explicit COMPLETE or RETAIN_OMISSIONS population belongs to estimation only");
        int threads = (int) NextflowFiles.integer(raw.get("threads"), 1, 1024);
        Map<?, ?> p = NextflowFiles.object(raw.get("parameters")); NextflowFiles.keys(p, "trimming", "minimum_length", "minimum_isoform_fraction", "minimum_coverage", "minimum_FPKM", "minimum_TPM");
        var parameters = new TreeMap<String, String>(); String trimming = text(p.get("trimming")); require(java.util.Set.of("ORIGINAL_DEFAULT", "DISABLE").contains(trimming), "Declare ORIGINAL_DEFAULT or DISABLE trimming"); parameters.put("trimming", trimming);
        require(mode == Mode.ASSEMBLE || trimming.equals("ORIGINAL_DEFAULT"), "Disabling coverage trimming applies only to assembly");
        for (String name : List.of("minimum_length", "minimum_isoform_fraction", "minimum_coverage", "minimum_FPKM", "minimum_TPM")) {
            Object value = p.get(name); String token = null;
            if (value != null) {
                require(mode != Mode.ESTIMATE && (mode == Mode.MERGE || !java.util.Set.of("minimum_FPKM", "minimum_TPM").contains(name)), "Threshold does not apply to the requested StringTie operation: " + name);
                if (name.equals("minimum_length")) token = Long.toString(NextflowFiles.integer(value, 1, Integer.MAX_VALUE));
                else {
                    token = value instanceof NextflowParameters.NumberToken number ? number.text() : value.toString();
                    require(value instanceof Number || value instanceof NextflowParameters.NumberToken, "StringTie thresholds must be JSON numbers, not strings or flags");
                    BigDecimal number; try { number = new BigDecimal(token); } catch (NumberFormatException error) { throw new IOException("Invalid exact threshold token", error); }
                    require(token.length() <= 100 && number.signum() >= 0 && number.compareTo(BigDecimal.valueOf(name.equals("minimum_isoform_fraction") ? 1 : 1000000000)) <= 0 && Math.abs((long)number.scale()) <= 100, "Unsupported bounded StringTie threshold"); token = number.toPlainString();
                }
            }
            parameters.put(name, token);
        }
        var limits = capacities(NextflowFiles.object(raw.get("limits"))); require(threads <= limits.get("threads"), "Requested StringTie threads exceed the explicit process-thread capacity");
        return new Request(profile, version, mode, bam, sizes, guide, merge, build, strand, policies, population, threads, parameters, limits);
    }
    private static Map<String, Long> capacities(Map<?, ?> raw) throws IOException {
        var result = new TreeMap<String, Long>(Map.ofEntries(Map.entry("input_bytes", 8589934592L), Map.entry("bam_decoded_bytes", 34359738368L), Map.entry("bam_records", 10000000L),
            Map.entry("gtf_lines", 10000000L), Map.entry("gtf_features", 5000000L), Map.entry("gtf_transcripts", 500000L), Map.entry("gtf_exons_per_transcript", 100000L), Map.entry("gtf_attributes", 1000L),
            Map.entry("line_characters", 1048576L), Map.entry("text_characters", 268435456L), Map.entry("table_rows", 20000000L), Map.entry("topology_work", 1000000000L), Map.entry("references", 100000L),
            Map.entry("metadata_bytes", 16777216L), Map.entry("wall_seconds", 86400L), Map.entry("cpu_seconds", 86400L), Map.entry("address_space_bytes", 17179869184L), Map.entry("file_bytes", 68719476736L),
            Map.entry("open_files", 4096L), Map.entry("workspace_bytes", 274877906944L), Map.entry("files", 100000L), Map.entry("processes", 16L), Map.entry("threads", 4096L), Map.entry("sampled_rss_bytes", 8589934592L), Map.entry("poll_millis", 100L)));
        require(result.keySet().containsAll(raw.keySet()), "Unknown StringTie capacity");
        for (var entry : raw.entrySet()) result.put(text(entry.getKey()), NextflowFiles.integer(entry.getValue(), 1, 1000000000000L));
        try {
            gtf(result); tables(result); bam(result); process(result);
            require(result.get("metadata_bytes") >= 1024 && result.get("metadata_bytes") <= 67108864 && result.get("input_bytes") <= 1099511627776L && result.get("workspace_bytes") >= 16777216, "Invalid complete StringTie metadata/input/workspace capacities");
        } catch (IllegalArgumentException | ArithmeticException invalid) { throw new IOException("Unsupported complete StringTie capacities", invalid); }
        return Map.copyOf(result);
    }
    static RsemProcess.Limits process(Map<String, Long> v) { return new RsemProcess.Limits(Math.multiplyExact(v.get("wall_seconds"), 1000), v.get("address_space_bytes"), v.get("cpu_seconds"), v.get("file_bytes"), Math.toIntExact(v.get("open_files")), v.get("workspace_bytes"), Math.toIntExact(v.get("files")), Math.toIntExact(v.get("processes")), Math.toIntExact(v.get("threads")), v.get("sampled_rss_bytes"), Math.toIntExact(v.get("poll_millis"))); }
    static StringTieGtf.Limits gtf(Map<String, Long> v) { return new StringTieGtf.Limits(v.get("gtf_lines"), Math.toIntExact(v.get("gtf_features")), Math.toIntExact(v.get("gtf_transcripts")), Math.toIntExact(v.get("gtf_exons_per_transcript")), Math.toIntExact(v.get("gtf_attributes")), Math.toIntExact(v.get("line_characters")), v.get("text_characters")); }
    static org.jlinalg.sequence.StringTieTables.Limits tables(Map<String, Long> v) { return new org.jlinalg.sequence.StringTieTables.Limits(v.get("table_rows"), Math.toIntExact(v.get("line_characters")), v.get("text_characters"), Math.toIntExact(v.get("gtf_features")), v.get("topology_work")); }
    static StringTieBam.Limits bam(Map<String, Long> v) { return new StringTieBam.Limits(v.get("input_bytes"), v.get("bam_decoded_bytes"), v.get("bam_records"), 16777216, 16777216, Math.toIntExact(v.get("references")), 1000000, 65535, 4096, 1000000); }
    static String opaque(String path) throws IOException { require(path.startsWith("/") && path.length() > 1 && path.chars().noneMatch(c -> c < 32 || c == 127 || c == '\\'), "Recorded StringTie producer paths must be absolute Linux text"); for (String segment : path.substring(1).split("/", -1)) require(!segment.isEmpty() && !segment.equals(".") && !segment.equals(".."), "Noncanonical recorded StringTie producer path"); return path; }
    private static String source(Object raw, boolean captured) throws IOException { String value = text(raw); if (captured) return opaque(value); Path path = Path.of(value); require(path.isAbsolute() && path.normalize().equals(path), "Require absolute normalized StringTie input paths"); return value; }
    private static String nullablePath(Object raw, boolean captured) throws IOException { return raw == null ? null : source(raw, captured); }
    private static <E extends Enum<E>> E choice(Class<E> type, Object value) throws IOException { try { return Enum.valueOf(type, text(value)); } catch (IllegalArgumentException error) { throw new IOException("Unsupported explicit StringTie " + type.getSimpleName(), error); } }
    private static String text(Object value) throws IOException { return NextflowFiles.text(value); }
    private static void require(boolean value, String reason) throws IOException { if (!value) throw new IOException(reason); }
}
