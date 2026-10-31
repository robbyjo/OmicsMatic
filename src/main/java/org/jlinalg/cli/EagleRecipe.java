/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.EagleGeneticMap;
import org.jlinalg.genetics.EagleGenotypes;
import org.jlinalg.genetics.EagleReferenceJoin;

/** Closed VCF/BCF recipes with explicit policies and versioned resource defaults. */
final class EagleRecipe {
    private EagleRecipe() { }
    static final String PROFILE = "linux-x86_64-eagle-2.4.1";
    static final class UnavailableCapability extends IOException {
        private static final long serialVersionUID = 1L;
        UnavailableCapability(String message) { super(message); }
    }
    enum Algorithm { COHORT_AUTO, COHORT_PBWT_ONLY, REFERENCE_PBWT }
    record Source(Path path, String format, Path index, String indexPolicy) { }
    record Parameters(int threads, int conditioningHaplotypes, int pbwtIterations, long seed, Algorithm algorithm) {
        Parameters {
            if (threads < 1 || threads > 1024 || conditioningHaplotypes < 1 || conditioningHaplotypes > 1000000
                    || pbwtIterations < 0 || pbwtIterations > 10 || seed < 0 || seed > 4294967295L || algorithm == null)
                throw new IllegalArgumentException("Require 1..1024 threads, 1..1000000 Kpbwt, 0..10 PBWT iterations and an unsigned32 seed");
        }
    }
    record Spec(Path recipe, String recipeSha256, Path resourceRoot, String operation, String profile,
            EagleGenotypes.Mode mode, String assembly, String inputReview, Path target, Path reference, Path exclusion,
            Path map, Parameters parameters, EagleCohortInputs.Spec cohortSpec, EagleReferenceInputs.Spec referenceSpec,
            EagleProcess.Limits processLimits, Map<String,Source> inputSources, Map<?, ?> source) {
        Spec { inputSources = Map.copyOf(inputSources); source = Map.copyOf(source); }
        void verify() throws IOException {
            if (!AdmixtureInputs.digest(raw(recipe)).equals(recipeSha256)) throw new IOException("Eagle recipe changed during analysis");
        }
        long workspaceBytes() { return mode == EagleGenotypes.Mode.COHORT ? cohortSpec.workspaceBytes() : referenceSpec.workspaceBytes(); }
        EagleVcf.Limits vcfLimits() { return mode == EagleGenotypes.Mode.COHORT ? cohortSpec.vcfLimits() : referenceSpec.vcfLimits(); }
        long retainedBytes() { return mode == EagleGenotypes.Mode.COHORT ? cohortSpec.retainedBytes() : referenceSpec.retainedBytes(); }
    }
    static Spec read(Path supplied) throws IOException {
        try { return decode(supplied); }
        catch (IllegalArgumentException | ArithmeticException invalid) { throw new IOException("Invalid typed Eagle recipe: " + invalid.getMessage(), invalid); }
    }
    private static Spec decode(Path supplied) throws IOException {
        Path recipe = supplied.toAbsolutePath().normalize(); byte[] raw = raw(recipe); Map<?, ?> m = object(SimpleJson.parse(utf8(raw)));
        keys(m, Set.of("schema_version", "operation", "profile", "mode", "resource_root", "assembly", "input_review", "target", "reference", "exclusion", "map", "region", "policies", "parameters", "limits", "process_limits"));
        whole(m, "schema_version", null, 1, 1);
        String operation = choice(m, "operation", Set.of("check", "phase")), profile = text(m, "profile");
        if (!PROFILE.equals(profile)) throw new UnavailableCapability("Requested Eagle profile is unavailable; no historical edition is substituted: " + profile);
        EagleGenotypes.Mode mode = enumeration(m, "mode", EagleGenotypes.Mode.class);
        String assembly = text(m, "assembly"), review = text(m, "input_review");
        Path root = recipe.getParent().resolve(text(m, "resource_root")).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) throw new IOException("Eagle resource_root must be a canonical existing directory");
        var inputSources = new java.util.LinkedHashMap<String,Source>();
        Source targetInput = input(root, object(m.get("target"))); inputSources.put("target",targetInput);
        Path target = targetInput.path(), reference = null, exclusion = null;
        if (mode == EagleGenotypes.Mode.REFERENCE) {
            Source ref = input(root, object(m.get("reference"))); inputSources.put("reference",ref); reference = ref.path();
            if (m.containsKey("exclusion")) { Source exc = input(root, object(m.get("exclusion"))); inputSources.put("exclusion",exc); exclusion = exc.path(); }
        } else if (m.containsKey("reference") || m.containsKey("exclusion")) throw new IOException("Reference and exclusion roles require REFERENCE mode");
        Path map = resolve(root, text(m, "map"));
        Map<?, ?> r = object(m.get("region")); keys(r, Set.of("contig", "chromosome", "chromosome_x", "start", "end", "selection"));
        choice(r, "selection", Set.of("literal-pos-input-subset")); String contig = text(r, "contig");
        int chrom = integer(r, "chromosome", null, 1, Integer.MAX_VALUE), chromX = integer(r, "chromosome_x", null, 1, Integer.MAX_VALUE);
        int start = integer(r, "start", null, 1, Integer.MAX_VALUE), end = integer(r, "end", null, start, Integer.MAX_VALUE);
        Map<?, ?> policy = object(m.get("policies"));
        Set<String> common = Set.of("missing", "partial_missing", "keep_missing_ploidy_x", "outside_map");
        var allowed = new java.util.HashSet<>(common);
        if (mode == EagleGenotypes.Mode.REFERENCE) allowed.addAll(Set.of("allow_reference_alt_swap", "unmatched", "expansion", "reference_fallback", "sample_overlap"));
        keys(policy, allowed);
        var options = new EagleGenotypes.Options(mode, chrom == chromX, enumeration(policy, "missing", EagleGenotypes.Missing.class), bool(policy, "keep_missing_ploidy_x"));
        var partial = enumeration(policy, "partial_missing", EagleCohortInputs.PartialMissing.class);
        var outside = enumeration(policy, "outside_map", EagleGeneticMap.Outside.class);
        Map<?, ?> p = object(m.get("parameters")); keys(p, Set.of("threads", "conditioning_haplotypes", "pbwt_iterations", "seed", "algorithm"));
        var parameters = new Parameters(integer(p, "threads", null, 1, 1024), integer(p, "conditioning_haplotypes", null, 1, 1000000),
                integer(p, "pbwt_iterations", null, 0, 10), whole(p, "seed", null, 0, 4294967295L), enumeration(p, "algorithm", Algorithm.class));
        if ((mode == EagleGenotypes.Mode.REFERENCE) != (parameters.algorithm == Algorithm.REFERENCE_PBWT)) throw new IOException("Algorithm must match the explicit cohort/reference mode");
        Map<?, ?> l = object(m.get("limits"));
        keys(l, Set.of("preset", "maximum_samples", "maximum_variants", "maximum_cells", "maximum_input_bytes", "maximum_decoded_characters", "maximum_line_characters", "maximum_header_characters", "maximum_map_rows", "maximum_selected_map_rows", "maximum_map_characters", "maximum_join_records", "maximum_join_characters", "maximum_join_comparisons", "retained_bytes", "ledger_bytes", "workspace_bytes"));
        choice(l, "preset", Set.of("bounded-vcf-v1"));
        long inputBytes = whole(l, "maximum_input_bytes", 536870912L, 1, 1000000000000L), workspace = whole(l, "workspace_bytes", 17179869184L, 1, 1000000000000L);
        var textLimits = new MinimacVcf.Limits(integer(l, "maximum_samples", 100000, 1, 1000000), whole(l, "maximum_variants", 1000000L, 1, 1000000000),
                whole(l, "maximum_cells", 100000000L, 1, 1000000000000L), inputBytes, whole(l, "maximum_decoded_characters", 8589934592L, 1, 1000000000000L),
                integer(l, "maximum_line_characters", 1048576, 64, 16777216), workspace, 1000000);
        var vcf = new EagleVcf.Limits(textLimits, 10000, whole(l, "maximum_header_characters", 4194304L, 1, 1000000000000L), 1024, 1024, 4194304);
        var maps = new EagleGeneticMap.Limits(whole(l, "maximum_map_rows", 50000000L, 1, 1000000000), integer(l, "maximum_selected_map_rows", 2000000, 2, 50000000), 16384,
                whole(l, "maximum_map_characters", 8589934592L, 1, 1000000000000L), 1000);
        var joins = new EagleReferenceJoin.Limits(integer(l, "maximum_join_records", 10000, 1, 1000000), whole(l, "maximum_join_characters", 4194304L, 1, 1000000000000L),
                whole(l, "maximum_join_comparisons", 10000000L, 1, 1000000000000L));
        long retained = whole(l, "retained_bytes", 536870912L, 1, 1000000000000L), ledger = whole(l, "ledger_bytes", 1073741824L, 1, 1000000000000L);
        EagleCohortInputs.Spec cohort = null; EagleReferenceInputs.Spec ref = null;
        if (mode == EagleGenotypes.Mode.COHORT) cohort = new EagleCohortInputs.Spec(contig, chrom, chromX, start, end, options, partial, outside, maps, vcf, retained, ledger, workspace);
        else ref = new EagleReferenceInputs.Spec(assembly, review, contig, chrom, chromX, start, end, options, bool(policy, "allow_reference_alt_swap"),
                enumeration(policy, "unmatched", EagleReferenceInputs.Unmatched.class), enumeration(policy, "expansion", EagleReferenceInputs.Expansion.class),
                enumeration(policy, "reference_fallback", EagleReferenceInputs.ReferenceFallback.class), enumeration(policy, "sample_overlap", EagleReferenceInputs.SampleOverlap.class),
                partial, outside, maps, vcf, joins, retained, ledger, workspace);
        Map<?, ?> process = object(m.get("process_limits")); keys(process, Set.of("preset", "wall_millis", "address_space_bytes", "cpu_seconds", "file_bytes", "open_files", "workspace_bytes", "file_count", "processes", "threads", "sampled_rss_bytes", "monitor_millis"));
        choice(process, "preset", Set.of("bounded-process-v1"));
        var processLimits = new EagleProcess.Limits(whole(process, "wall_millis", 3600000L, 1, 604800000), whole(process, "address_space_bytes", 8589934592L, 16777216, 1099511627776L),
                whole(process, "cpu_seconds", 3600L, 1, 604800), whole(process, "file_bytes", 4294967296L, 1, 1000000000000L), integer(process, "open_files", 256, 8, 1048576),
                whole(process, "workspace_bytes", workspace, 1, workspace), integer(process, "file_count", 4096, 4, 1000000), integer(process, "processes", 8, 1, 4096),
                integer(process, "threads", Math.max(64, parameters.threads), 1, 1048576), whole(process, "sampled_rss_bytes", 4294967296L, 1, 1099511627776L), integer(process, "monitor_millis", 100, 1, 1000));
        if (parameters.threads > processLimits.threads()) throw new IOException("Requested engine threads exceed the monitored thread capacity");
        return new Spec(recipe, AdmixtureInputs.digest(raw), root, operation, profile, mode, assembly, review, target, reference, exclusion, map, parameters, cohort, ref, processLimits, inputSources, m);
    }
    private static Source input(Path root, Map<?, ?> input) throws IOException {
        keys(input, Set.of("path", "format", "index", "index_policy")); String format = choice(input,"format",Set.of("vcf","bcf"));
        Path path = resolve(root,text(input,"path")), index = null; String policy = "none-regenerate";
        if (input.containsKey("index")) {
            if (!format.equals("bcf")) throw new IOException("Declared source index provenance currently applies only to BCF; VCF stages generate their own indices");
            policy = choice(input,"index_policy",Set.of("snapshot-only-regenerate")); index = resolve(root,text(input,"index"));
            if (path.equals(index)) throw new IOException("BCF data and declared source index must be different files");
        } else if (input.containsKey("index_policy")) throw new IOException("Source index policy requires an explicit source index");
        return new Source(path,format,index,policy);
    }
    private static Path resolve(Path root, String name) throws IOException {
        Path path = root.resolve(name).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new IOException("Eagle input path escapes the declared resource_root"); return path;
    }
    private static byte[] raw(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path) || Files.size(path) > 2097152) throw new IOException("Missing, linked or oversized Eagle recipe");
        try (var stream = Files.newInputStream(path)) { byte[] raw = stream.readNBytes(2097153); if (raw.length > 2097152) throw new IOException("Eagle recipe byte capacity exceeded"); return raw; }
    }
    private static String utf8(byte[] raw) throws IOException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString(); }
    private static Map<?, ?> object(Object value) throws IOException { if (!(value instanceof Map<?, ?> result)) throw new IOException("Expected typed Eagle JSON object"); return result; }
    private static void keys(Map<?, ?> value, Set<String> allowed) throws IOException { for (Object key : value.keySet()) if (!allowed.contains(key)) throw new IOException("Unknown Eagle recipe field: " + key); }
    private static String text(Map<?, ?> value, String key) throws IOException {
        if (!(value.get(key) instanceof String result) || result.isBlank() || result.length() > 4096 || result.codePoints().anyMatch(Character::isISOControl)) throw new IOException("Require explicit single-line Eagle text: " + key); return result;
    }
    private static String choice(Map<?, ?> value, String key, Set<String> choices) throws IOException { String result = text(value, key); if (!choices.contains(result)) throw new IOException("Unsupported Eagle choice for " + key + ": " + result); return result; }
    private static boolean bool(Map<?, ?> value, String key) throws IOException { if (!(value.get(key) instanceof Boolean result)) throw new IOException("Require explicit Eagle boolean: " + key); return result; }
    private static long whole(Map<?, ?> value, String key, Long fallback, long minimum, long maximum) throws IOException {
        Object raw = value.containsKey(key) ? value.get(key) : fallback;
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.longValue() || number.longValue() < minimum || number.longValue() > maximum) throw new IOException("Invalid exact Eagle integer: " + key); return number.longValue();
    }
    private static int integer(Map<?, ?> value, String key, Integer fallback, int minimum, int maximum) throws IOException { return (int)whole(value, key, fallback == null ? null : fallback.longValue(), minimum, maximum); }
    private static <T extends Enum<T>> T enumeration(Map<?, ?> value, String key, Class<T> type) throws IOException {
        try { return Enum.valueOf(type, text(value, key)); } catch (IllegalArgumentException invalid) { throw new IOException("Unsupported Eagle policy: " + key, invalid); }
    }
}
