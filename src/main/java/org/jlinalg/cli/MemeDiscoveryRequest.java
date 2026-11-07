/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.MemeDiscoveryScoring;
import org.jlinalg.sequence.MemeDiscoverySites;
import org.jlinalg.sequence.MemeXml;

/** Closed classic-DNA discovery request; original defaults remain explicit and editions never alias. */
final class MemeDiscoveryRequest {
    private MemeDiscoveryRequest() { }
    static final List<String> PROFILES = List.of("MEME_4_9_1_BASE", "MEME_4_9_1_PATCH2", "MEME_5_5_4", "MEME_5_5_9");
    static final Map<String, Long> DEFAULTS = Map.ofEntries(
        Map.entry("input_bytes", 16777216L), Map.entry("characters", 16777216L), Map.entry("sequences", 10000L),
        Map.entry("bases", 4000000L), Map.entry("sequence_length", 1000000L), Map.entry("line_characters", 1048576L),
        Map.entry("xml_bytes", 67108864L), Map.entry("xml_characters", 67108864L), Map.entry("xml_elements", 2000000L),
        Map.entry("xml_depth", 64L), Map.entry("xml_attributes", 128L), Map.entry("motifs", 1000L), Map.entry("width", 512L),
        Map.entry("sites", 1000000L), Map.entry("native_workspace_bytes", 134217728L), Map.entry("native_operations", 1000000000L),
        Map.entry("selected_hits", 100000L), Map.entry("wall_seconds", 3600L), Map.entry("cpu_seconds", 3600L),
        Map.entry("address_space_bytes", 4294967296L), Map.entry("file_bytes", 268435456L), Map.entry("open_files", 1024L),
        Map.entry("workspace_bytes", 2147483648L), Map.entry("files", 10000L), Map.entry("processes", 32L), Map.entry("threads", 256L),
        Map.entry("sampled_rss_bytes", 2147483648L), Map.entry("poll_millis", 50L));

    record Request(Path source, NextflowFiles.Pin sourcePin, String profile, String primary, String background,
                   MemeDiscoverySites.Mode mode, boolean bothStrands, int motifs, int minimumWidth, int maximumWidth,
                   Integer seed, Integer searchSize, BigDecimal beta, int iterations, BigDecimal distance,
                   BigDecimal maximumEvalue, Integer minimumSites, Integer maximumSites, Map<String, Long> limits) {
        Request { limits = Map.copyOf(limits); }
        boolean legacy() { return profile.startsWith("MEME_4_9_1_"); }
        String version() { return legacy() ? "4.9.1" : profile.equals("MEME_5_5_4") ? "5.5.4" : "5.5.9"; }
        long limit(String name) { return limits.get(name); }
        void verify(Runnable check) throws IOException { require(sourcePin.equals(NextflowFiles.pin(source, 2097152, check)), "Original MEME request changed"); }
        List<String> arguments(Path home, Path fasta, Path backgroundFile, Path newResult, int sequences) throws IOException {
            for (Path p : List.of(home, fasta, backgroundFile, newResult)) nativePath(p);
            return argumentsRecorded(home.toString(), fasta.toString(), backgroundFile.toString(), newResult.toString(), sequences);
        }
        List<String> argumentsRecorded(String home, String fasta, String backgroundFile, String newResult, int sequences) throws IOException {
            for (String path : List.of(home, fasta, backgroundFile, newResult)) TomtomRecipe.nativePath(path);
            var args = new ArrayList<>(List.of(home + "/bin/meme", fasta, "-dna", "-mod", mode.name().toLowerCase(java.util.Locale.ROOT),
                "-nmotifs", Integer.toString(motifs), "-minw", Integer.toString(minimumWidth), "-maxw", Integer.toString(maximumWidth),
                "-prior", "dirichlet", "-b", beta.toString(), "-maxiter", Integer.toString(iterations), "-distance", distance.toString(),
                "-bfile", backgroundFile, "-maxsize", Long.toString(limit("bases")), "-nostatus", "-o", newResult));
            if (bothStrands) args.add("-revcomp");
            if (!legacy()) args.addAll(List.of("-seed", seed.toString(), "-searchsize", searchSize.toString(), "-brief", Integer.toString(sequences)));
            if (maximumEvalue != null) args.addAll(List.of("-evt", maximumEvalue.toString()));
            if (minimumSites != null) args.addAll(List.of("-minsites", minimumSites.toString()));
            if (maximumSites != null) args.addAll(List.of("-maxsites", maximumSites.toString()));
            return List.copyOf(args);
        }
        MemeDiscoverySites.Limits outputLimits() {
            return new MemeDiscoverySites.Limits(new MemeXml.Limits(Math.toIntExact(limit("xml_bytes")), Math.toIntExact(limit("xml_characters")),
                Math.toIntExact(limit("xml_elements")), Math.toIntExact(limit("xml_depth")), Math.toIntExact(limit("xml_attributes"))),
                new MemeDiscoveryScoring.Limits(limit("native_workspace_bytes"), limit("native_operations"), Math.toIntExact(limit("width")),
                    Math.toIntExact(limit("motifs")), Math.toIntExact(limit("sequence_length")), Math.toIntExact(limit("selected_hits"))),
                Math.toIntExact(limit("sequences")), limit("bases"), limit("sites"), limit("native_operations"));
        }
        RsemProcess.Limits processLimits() { return new RsemProcess.Limits(limit("wall_seconds") * 1000, limit("address_space_bytes"), limit("cpu_seconds"),
            limit("file_bytes"), Math.toIntExact(limit("open_files")), limit("workspace_bytes"), Math.toIntExact(limit("files")),
            Math.toIntExact(limit("processes")), Math.toIntExact(limit("threads")), limit("sampled_rss_bytes"), Math.toIntExact(limit("poll_millis"))); }
    }

    static Request read(Path path, boolean portable, Runnable check) throws IOException {
        var identity = NextflowFiles.pin(path, 2097152, check);
        var raw = NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(path, 2097152)), 2097152, 10000));
        NextflowFiles.keys(raw, "schema_version", "profile", "primary", "background", "occurrence", "both_strands", "motifs", "minimum_width", "maximum_width",
            "seed", "search_size", "dirichlet_beta", "maximum_iterations", "convergence_distance", "maximum_motif_evalue", "minimum_sites", "maximum_sites", "limits");
        NextflowFiles.integer(raw.get("schema_version"), 1, 1);
        String profile = NextflowFiles.text(raw.get("profile")); require(PROFILES.contains(profile), "Unknown exact original MEME runtime profile");
        String primary = path(raw.get("primary"), portable), background = path(raw.get("background"), portable);
        MemeDiscoverySites.Mode mode;
        try { mode = MemeDiscoverySites.Mode.valueOf(NextflowFiles.text(raw.get("occurrence"))); }
        catch (IllegalArgumentException failure) { throw new IOException("Unknown MEME occurrence model", failure); }
        boolean legacy = profile.startsWith("MEME_4_9_1_");
        Integer seed = nullableInteger(raw.get("seed"), 0, Integer.MAX_VALUE), search = nullableInteger(raw.get("search_size"), 0, Integer.MAX_VALUE);
        require(legacy ? seed == null && search == null : seed != null && search != null,
            "4.9.1 requires null seed/search_size because its original CLI has neither option; newer editions require both explicitly");
        var limits = new LinkedHashMap<>(DEFAULTS); var supplied = NextflowFiles.object(raw.get("limits"));
        require(DEFAULTS.keySet().containsAll(supplied.keySet()), "Unknown MEME complete-population/process capacity");
        for (var e : supplied.entrySet()) limits.put(NextflowFiles.text(e.getKey()), NextflowFiles.integer(e.getValue(), 1, 1099511627776L));
        require(limits.get("input_bytes") <= 268435456 && limits.get("characters") <= 268435456 && limits.get("line_characters") <= 268435456,
            "Complete MEME input/line byte capacity exceeds supported reader domain");
        Integer minSites = nullableInteger(raw.get("minimum_sites"), 2, 1000000), maxSites = nullableInteger(raw.get("maximum_sites"), 2, 1000000);
        require(mode != MemeDiscoverySites.Mode.OOPS || minSites == null && maxSites == null, "OOPS fixes one occurrence per sequence; do not supply ignored site-count options");
        require(minSites == null || maxSites == null || minSites <= maxSites, "MEME minimum sites exceeds maximum sites");
        var request = new Request(path, identity, profile, primary, background, mode, bool(raw.get("both_strands")),
            (int) NextflowFiles.integer(raw.get("motifs"), 1, 1000), (int) NextflowFiles.integer(raw.get("minimum_width"), 2, 512),
            (int) NextflowFiles.integer(raw.get("maximum_width"), 2, 512), seed, search, decimal(raw.get("dirichlet_beta"), "1e-12", "1000000"),
            (int) NextflowFiles.integer(raw.get("maximum_iterations"), 1, 100000), decimal(raw.get("convergence_distance"), "1e-15", "1"),
            raw.get("maximum_motif_evalue") == null ? null : decimal(raw.get("maximum_motif_evalue"), "1e-300", "1e300"), minSites, maxSites, limits);
        require(request.minimumWidth() <= request.maximumWidth() && request.maximumWidth() <= request.limit("width") && request.motifs() <= request.limit("motifs"),
            "Requested MEME motif/width population exceeds declared capacities");
        require(request.legacy() || request.maximumEvalue() == null || request.motifs() == 1000,
            "Original MEME 5.5.x -evt overrides -nmotifs to 1000. Explicitly set motifs and its capacity to 1000 when supplying maximum_motif_evalue, or choose a null cutoff; no request is silently changed");
        try { request.outputLimits(); request.processLimits(); }
        catch (ArithmeticException | IllegalArgumentException failure) { throw new IOException("Invalid explicit MEME input/output/process capacities", failure); }
        request.verify(check); return request;
    }
    static Map<String, Object> template(String profile, String primary, String background) {
        var result = new LinkedHashMap<String, Object>(); result.put("schema_version", 1); result.put("profile", profile);
        result.put("primary", primary); result.put("background", background); result.put("occurrence", "ZOOPS"); result.put("both_strands", true);
        result.put("motifs", 2); result.put("minimum_width", 6); result.put("maximum_width", 18);
        boolean legacy = profile.startsWith("MEME_4_9_1_"); result.put("seed", legacy ? null : 17); result.put("search_size", legacy ? null : 0);
        result.put("dirichlet_beta", new BigDecimal("0.01")); result.put("maximum_iterations", 50); result.put("convergence_distance", new BigDecimal("0.00001"));
        result.put("maximum_motif_evalue", null); result.put("minimum_sites", null); result.put("maximum_sites", null); result.put("limits", Map.of()); return result;
    }
    static void nativePath(Path path) throws IOException { TomtomRecipe.nativePath(path.toString()); require(path.isAbsolute() && path.normalize().equals(path), "Require canonical absolute MEME command carriers"); }
    private static String path(Object raw, boolean portable) throws IOException {
        String value = NextflowFiles.text(raw); require(!value.isEmpty() && value.length() <= 32768 && value.chars().noneMatch(c -> c < 32 || c == 127), "Invalid original MEME path text");
        if (portable) { require(value.startsWith("/") || value.matches("[A-Za-z]:[\\\\/].*"), "Captured MEME producer path must be absolute inert text"); return value; }
        try { Path path = Path.of(value); require(path.isAbsolute() && path.normalize().equals(path), "Declare normalized absolute original MEME input paths"); return value; }
        catch (java.nio.file.InvalidPathException failure) { throw new IOException("Invalid original MEME input path", failure); }
    }
    private static Integer nullableInteger(Object value, int minimum, int maximum) throws IOException { return value == null ? null : (int) NextflowFiles.integer(value, minimum, maximum); }
    private static boolean bool(Object raw) throws IOException { if (raw instanceof Boolean value) return value; throw new IOException("Require an explicit MEME boolean"); }
    private static BigDecimal decimal(Object raw, String minimum, String maximum) throws IOException {
        require(raw instanceof NextflowParameters.NumberToken && ((NextflowParameters.NumberToken) raw).text().length() <= 128, "Require exact bounded MEME decimal tokens");
        try {
            BigDecimal value = new BigDecimal(((NextflowParameters.NumberToken) raw).text());
            require(Math.abs((long) value.scale()) <= 4096 && value.compareTo(new BigDecimal(minimum)) >= 0 && value.compareTo(new BigDecimal(maximum)) <= 0
                && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0, "MEME decimal is outside the supported positive finite domain"); return value;
        } catch (NumberFormatException failure) { throw new IOException("Invalid original MEME decimal", failure); }
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
