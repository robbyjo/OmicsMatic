/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed, local SAM/BAM and VCF operations using pinned upstream HTS applications. */
final class HtsToolsCli {
    private HtsToolsCli() { }
    private static final String VERSION = "1.24";
    private static final Set<String> COMMON = Set.of("method", "input", "samtools", "bcftools", "out", "threads", "timeout");
    private static final Map<String, Set<String>> SPECIFIC = Map.ofEntries(
        Map.entry("sam-view", Set.of("min-mapq", "require-flags", "exclude-flags", "region")),
        Map.entry("sam-sort", Set.of("sort-order", "memory-mb", "create-index")), Map.entry("sam-index", Set.of()), Map.entry("sam-flagstat", Set.of()),
        Map.entry("sam-markdup", Set.of("remove-duplicates", "memory-mb", "optical-distance")),
        Map.entry("sam-mpileup", Set.of("reference", "region", "min-mapq", "min-baseq", "max-depth", "baq", "exclude-flags")),
        Map.entry("bcf-call", Set.of("reference", "region", "ploidy", "min-mapq", "min-baseq", "max-depth")),
        Map.entry("bcf-norm", Set.of("reference", "split-multiallelic")), Map.entry("bcf-filter", Set.of("min-qual", "filter-name")),
        Map.entry("bcf-select", Set.of("samples-list")), Map.entry("bcf-stats", Set.of()));

    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (Arrays.asList(arguments).contains("--help")) { output.println(help()); return 0; }
        try {
            var allowed = new HashSet<>(COMMON); SPECIFIC.values().forEach(allowed::addAll);
            Map<String, String> o = options(arguments, allowed); String method = required(o, "method");
            if (method.equals("fasta-index")) return FastaIndexCli.run(o, output, error);
            if (!SPECIFIC.containsKey(method)) throw new IllegalArgumentException("Unknown HTS method: " + method);
            boolean sam = method.startsWith("sam-"); String tool = sam ? "samtools" : "bcftools";
            for (String key : o.keySet()) if ((!COMMON.contains(key) && !SPECIFIC.get(method).contains(key))
                    || key.equals(sam ? "bcftools" : "samtools")) throw new IllegalArgumentException("--" + key + " does not apply to " + method);
            required(o, "input"); required(o, tool);
            if (o.containsKey("threads") && Set.of("sam-mpileup", "bcf-stats").contains(method)) throw new IllegalArgumentException("--threads does not apply to " + method);
            int threads = integer(o, "threads", 0, 0, 16), timeout = integer(o, "timeout", 3600, 1, 86400);
            int[] exit = {0};
            externalTransaction(o, "hts-tools-" + method, (directory, manifest) -> {
                var session = new LinkedHashMap<String, Object>(); session.put("method", method);
                session.put("scope", "Pinned SAMtools/BCFtools/HTSlib 1.24, typed local inputs only; no CRAM, remote reference/input, arbitrary expressions or plugins");
                try {
                    Path input = file(o.get("input")); String beforeHash = hash(input);
                    String executable = executable(o.get(tool));
                    Path binary = Path.of(executable); if (Files.isRegularFile(binary)) manifest.put("executable_sha256", hash(binary));
                    // Same-directory DLLs are relevant to private Windows packages; record exact bytes.
                    if (Files.isRegularFile(binary) && binary.toString().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                        var dlls = new TreeMap<String, String>();
                        try (var files = Files.list(binary.toAbsolutePath().getParent())) {
                            for (Path dll : files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".dll")).toList()) dlls.put(dll.getFileName().toString(), hash(dll));
                        }
                        session.put("runtime_dll_sha256", dlls);
                    }
                    try {
                        int code = execute(List.of(executable, "--version"), directory, "version", null, 30, session);
                        String version = Files.readString(directory.resolve("version.log")).replace("\r\n", "\n");
                        if (code != 0 || !version.startsWith(tool + " " + VERSION + "\n") || !version.contains("Using htslib " + VERSION + "\n"))
                            throw new IOException("Require " + tool + " " + VERSION + " and HTSlib " + VERSION + "; inspect version.log");
                    } catch (IOException failure) { throw new DependencyUnavailable(failure.getMessage()); }
                    var metrics = new LinkedHashMap<String, String>();
                    if (sam) alignment(method, executable, input, o, directory, threads, timeout, session, metrics, manifest);
                    else variants(method, executable, input, o, directory, threads, timeout, session, metrics, manifest);
                    if (!hash(input).equals(beforeHash)) throw new IOException("Input changed while analysis was running");
                    if (o.containsKey("reference") && (!hash(Path.of(o.get("reference"))).equals(manifest.get("reference_sha256"))
                            || !hash(Path.of(o.get("reference") + ".fai")).equals(manifest.get("reference_fai_sha256")))) throw new IOException("Reference or FASTA index changed during analysis");
                    String state = "completed";
                    List<String> warnings = new ArrayList<>();
                    try (var paths = Files.list(directory)) {
                        for (Path log : paths.filter(p -> p.getFileName().toString().endsWith(".log")).toList())
                            try (var lines = Files.lines(log)) { lines.filter(line -> line.matches(".*(?:\\[W::|\\bWARN(?:ING)?\\b).*"))
                                .limit(100).forEach(line -> warnings.add(log.getFileName() + ": " + line)); }
                    }
                    if (metrics.getOrDefault("output_records", "1").equals("0")) warnings.add("The requested operation retained no records; check the selection and input coverage.");
                    if (!warnings.isEmpty()) {
                        state = "completed-with-warnings"; session.put("upstream_warnings_first_100_per_log", warnings);
                        Files.writeString(directory.resolve("WARNINGS.md"), "# HTS ANALYSIS WARNINGS\n\n" + String.join("\n", warnings)
                            + "\n\nRequired action: inspect the source logs, counts and selection settings before downstream use. Empty variant output may reflect no supported alternate alleles; it is not evidence of complete callable coverage.\n");
                    }
                    manifest.put("analysis_status", state); session.put("metrics", metrics);
                    table(directory.resolve("results.tsv"), List.of("metric", "value"), metrics.entrySet().stream().map(e -> new String[]{e.getKey(), e.getValue()}).toList());
                } catch (Exception failure) {
                    exit[0] = failure instanceof DependencyUnavailable ? 3 : 2;
                    manifest.put("analysis_status", exit[0] == 3 ? "skipped-dependency-unavailable" : "failed"); manifest.put("error", String.valueOf(failure.getMessage()));
                    quarantine(directory);
                    Files.writeString(directory.resolve("WARNINGS.md"), "# " + (exit[0] == 3 ? "ANALYSIS SKIPPED - HTS DEPENDENCY UNAVAILABLE" : "ANALYSIS FAILED")
                        + "\n\n" + failure.getMessage() + "\n\nRequired action: " + (exit[0] == 3
                            ? "On Windows x86_64, where allowed run python tools/literature/install_hts_dependencies.py --allow-download and supply its ucrt64/bin executable. On other hosts obtain SAMtools/BCFtools 1.24 with HTSlib 1.24 through an allowed package/build process. If installation is forbidden, skip this analysis until an administrator supplies the runtime."
                            : "Inspect the retained logs and input contracts, correct the named error and rerun to a new output directory.")
                        + " Partial outputs are quarantined under partial/. No successful results.tsv is published.\n");
                }
                Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + manifest.get("analysis_status") + "\"}\n");
                Files.writeString(directory.resolve("session.yaml"), new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if (exit[0] != 0) error.println((exit[0] == 3 ? "SKIPPED ANALYSIS" : "ANALYSIS FAILED") + ": see " + Path.of(o.get("out"), "WARNINGS.md") + " for the cause and required action.");
            else { output.println("HTS operation completed: " + o.get("out")); if (Files.exists(Path.of(o.get("out"), "WARNINGS.md"))) error.println("HTS WARNINGS: see " + Path.of(o.get("out"), "WARNINGS.md")); }
            return exit[0];
        } catch (Exception failure) { error.println("jlinalg: " + failure.getMessage()); return 2; }
    }

    private static void alignment(String method, String exe, Path input, Map<String, String> o, Path dir, int threads, int timeout,
            Map<String, Object> session, Map<String, String> metrics, Map<String, Object> manifest) throws Exception {
        String name = input.toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".sam") && !name.endsWith(".bam")) throw new IOException("SAMtools adapter requires local .sam/.bam input; CRAM is not exposed");
        Region region = o.containsKey("region") ? Region.parse(o.get("region")) : null;
        int mapq = integer(o, "min-mapq", 0, 0, 255), require = integer(o, "require-flags", 0, 0, 4095), exclude = integer(o, "exclude-flags", 0, 0, 4095);
        Predicate<SAMRecord> predicate = r -> r.getMappingQuality() >= mapq && (r.getFlags() & require) == require && (r.getFlags() & exclude) == 0
            && (region == null || (!r.getReadUnmappedFlag() && r.getReferenceName().equals(region.contig) && r.getAlignmentStart() <= region.end && r.getAlignmentEnd() >= region.start));
        SamCounts before = scan(input, r -> true, null); metrics.put("input_records", Long.toString(before.count));
        var command = new ArrayList<String>(List.of(exe)); Path bam = dir.resolve("alignment.bam");
        switch (method) {
            case "sam-view" -> {
                command.addAll(List.of("view", "-@", Integer.toString(threads), "-b", "-q", Integer.toString(mapq), "-f", Integer.toString(require), "-F", Integer.toString(exclude), "-o", bam.toString(), input.toString()));
                if (region != null) { manifest.put("alignment_index_sha256", hash(indexedBam(input, region))); command.add(region.text()); }
                checked(command, dir, "external", null, timeout, session);
                SamCounts expected = scan(input, predicate, null), after = scan(bam, r -> true, null);
                if (expected.count != after.count || !expected.fingerprint.equals(after.fingerprint)) throw new IOException("SAMtools selection changed record identities or retained the wrong records");
                metrics.put("output_records", Long.toString(after.count));
            }
            case "sam-sort" -> {
                String order = o.getOrDefault("sort-order", "coordinate");
                if (!Set.of("coordinate", "queryname").contains(order)) throw new IllegalArgumentException("sort-order must be coordinate or queryname");
                int memory = integer(o, "memory-mb", 256, 16, 4096); boolean index = bool(o, "create-index", false);
                if (index && !order.equals("coordinate")) throw new IllegalArgumentException("BAM indexing requires coordinate order");
                command.addAll(List.of("sort", "-@", Integer.toString(threads), "-m", memory + "M", "-T", dir.resolve("sort-tmp").toString(), "-o", bam.toString()));
                if (order.equals("queryname")) command.add("-N"); command.add(input.toString());
                checked(command, dir, "external", null, timeout, session);
                SamCounts after = scan(bam, r -> true, order);
                if (before.count != after.count || !before.fingerprint.equals(after.fingerprint)) throw new IOException("SAMtools sort changed record content/counts");
                metrics.put("output_records", Long.toString(after.count));
                if (index) index(exe, bam, dir.resolve("alignment.bai"), dir, threads, timeout, session);
            }
            case "sam-index" -> {
                scan(input, r -> true, "coordinate");
                if (!name.endsWith(".bam")) throw new IOException("Index requires BAM input");
                index(exe, input, dir.resolve("alignment.bai"), dir, threads, timeout, session);
                metrics.put("output_index", "alignment.bai");
            }
            case "sam-flagstat" -> {
                checked(List.of(exe, "flagstat", "-@", Integer.toString(threads), "-O", "json", input.toString()), dir, "external", dir.resolve("flagstat.json"), timeout, session);
                Object parsed = SimpleJson.parse(Files.readString(dir.resolve("flagstat.json")));
                if (!(parsed instanceof Map<?, ?> groups)) throw new IOException("Invalid flagstat JSON");
                for (String group : List.of("QC-passed reads", "QC-failed reads")) {
                    if (!(groups.get(group) instanceof Map<?, ?> values)) throw new IOException("Missing flagstat quality group");
                    SamCounts expected = scan(input, r -> r.getReadFailsVendorQualityCheckFlag() == group.equals("QC-failed reads"), null);
                    for (var e : Map.of("total", expected.count, "mapped", expected.mapped, "primary", expected.primary, "duplicates", expected.duplicates).entrySet())
                        if (!(values.get(e.getKey()) instanceof Number n) || n.longValue() != e.getValue()) throw new IOException("Flagstat count differs from independent SAM scan: " + group + " " + e.getKey());
                    for (var e : values.entrySet()) metrics.put(group + ": " + e.getKey(), e.getValue() == null ? "NA" : e.getValue().toString());
                }
                metrics.put("output_records", Long.toString(before.count));
            }
            case "sam-markdup" -> {
                int memory = integer(o, "memory-mb", 256, 16, 4096), optical = integer(o, "optical-distance", 0, 0, 1000000);
                boolean remove = bool(o, "remove-duplicates", false);
                Path byName = dir.resolve("work-name.bam"), fixed = dir.resolve("work-fixmate.bam"), byPosition = dir.resolve("work-position.bam");
                checked(List.of(exe, "sort", "-N", "-@", Integer.toString(threads), "-m", memory + "M", "-T", dir.resolve("sort-tmp-name").toString(), "-o", byName.toString(), input.toString()), dir, "namesort", null, timeout, session);
                checked(List.of(exe, "fixmate", "-m", "-@", Integer.toString(threads), byName.toString(), fixed.toString()), dir, "fixmate", null, timeout, session);
                checked(List.of(exe, "sort", "-@", Integer.toString(threads), "-m", memory + "M", "-T", dir.resolve("sort-tmp-position").toString(), "-o", byPosition.toString(), fixed.toString()), dir, "positionsort", null, timeout, session);
                var mark = new ArrayList<>(List.of(exe, "markdup", "-s", "-d", Integer.toString(optical), "-@", Integer.toString(threads)));
                if (remove) mark.add("-r"); mark.add(byPosition.toString()); mark.add(bam.toString()); checked(mark, dir, "external", null, timeout, session);
                SamCounts after = scan(bam, r -> true, "coordinate");
                if ((!remove && before.count != after.count) || after.count > before.count || (remove && after.duplicates != 0)) throw new IOException("SAMtools duplicate operation changed counts outside the requested removal");
                metrics.put("output_records", Long.toString(after.count)); metrics.put("output_duplicate_flags", Long.toString(after.duplicates));
                var intermediate = new LinkedHashMap<String, String>();
                for (Path path : List.of(byName, fixed, byPosition)) { intermediate.put(path.getFileName().toString(), hash(path)); Files.delete(path); }
                session.put("validated_stage_sha256", intermediate); session.put("intermediate_policy", "Stage BAM hashes and commands retained; successful intermediate BAMs removed to limit storage; partial failed stages retained");
            }
            case "sam-mpileup" -> {
                Path reference = reference(o, manifest); scan(input, r -> true, "coordinate");
                if (region != null) manifest.put("alignment_index_sha256", hash(indexedBam(input, region)));
                var pileup = new ArrayList<>(List.of(exe, "mpileup", "-f", reference.toString(), "-q", Integer.toString(mapq),
                    "-Q", Integer.toString(integer(o, "min-baseq", 13, 0, 93)), "-d", Integer.toString(integer(o, "max-depth", 8000, 1, 100000)),
                    "--ff", Integer.toString(integer(o, "exclude-flags", 1796, 0, 4095))));
                if (!bool(o, "baq", true)) pileup.add("-B"); if (region != null) pileup.addAll(List.of("-r", region.text())); pileup.add(input.toString());
                checked(pileup, dir, "external", dir.resolve("pileup.tsv"), timeout, session);
                long positions = 0, depth = 0;
                try (var lines = Files.newBufferedReader(dir.resolve("pileup.tsv"))) {
                    String line; while ((line = lines.readLine()) != null) {
                        String[] row = line.split("\t", -1); if (row.length != 6) throw new IOException("Expected single-file six-column textual pileup");
                        int position = Integer.parseInt(row[1]), n = Integer.parseInt(row[3]);
                        if (position < 1 || n < 0 || (region != null && (!region.contig.equals(row[0]) || position < region.start || position > region.end))) throw new IOException("Invalid pileup coordinate/depth");
                        positions++; depth = Math.addExact(depth, n);
                    }
                }
                metrics.put("output_records", Long.toString(positions)); metrics.put("pileup_depth_sum", Long.toString(depth));
                session.put("pileup_scope", "Raw SAMtools textual pileup, one-based positions; no pseudo-haploid genotype sampling. BAQ, base/map quality and flag exclusions are explicit.");
            }
            default -> throw new AssertionError(method);
        }
    }

    private static void variants(String method, String exe, Path input, Map<String, String> o, Path dir, int threads, int timeout,
            Map<String, Object> session, Map<String, String> metrics, Map<String, Object> manifest) throws Exception {
        Path vcf = dir.resolve("variants.vcf"); List<String> samples;
        var command = new ArrayList<String>(List.of(exe));
        if (method.equals("bcf-call")) {
            int ploidy = integer(o, "ploidy", -1, 1, 2); // Always explicit; no implicit sex/chromosome model.
            Region region = o.containsKey("region") ? Region.parse(o.get("region")) : null;
            manifest.put("alignment_index_sha256", hash(indexedBam(input, region))); scan(input, r -> true, "coordinate");
            Path reference = reference(o, manifest);
            var pileup = new ArrayList<>(List.of(exe, "mpileup", "--threads", Integer.toString(threads), "-f", reference.toString(),
                "-q", Integer.toString(integer(o, "min-mapq", 0, 0, 255)), "-Q", Integer.toString(integer(o, "min-baseq", 13, 0, 93)),
                "-d", Integer.toString(integer(o, "max-depth", 250, 1, 100000)), "-a", "FORMAT/DP,FORMAT/AD", "-Ob", "-o", dir.resolve("pileup.bcf").toString()));
            if (region != null) pileup.addAll(List.of("-r", region.text())); pileup.add(input.toString());
            checked(pileup, dir, "pileup", null, timeout, session);
            command.addAll(List.of("call", "--threads", Integer.toString(threads), "--multiallelic-caller", "--ploidy", Integer.toString(ploidy), "--variants-only", "-Ov", "-o", vcf.toString(), dir.resolve("pileup.bcf").toString()));
            try (var reader = samFactory().open(input)) {
                samples = reader.getFileHeader().getReadGroups().stream().map(SAMReadGroupRecord::getSample).filter(Objects::nonNull).distinct().sorted().toList();
                if (samples.isEmpty()) throw new IOException("Calling requires explicit read groups with sample names");
            }
            session.put("calling_model", "Multiallelic caller, explicit uniform ploidy, variants only; no inferred pooled/mosaic/sex-specific model. Bounded depth is recorded; absent calls do not establish callability.");
        } else {
            String name = input.toString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".vcf") && !name.endsWith(".vcf.gz")) throw new IOException("Variant input must be local VCF/VCF.gz; direct BCF input is not exposed");
            try (var reader = new VCFFileReader(input, false); var records = reader.iterator()) {
                samples = reader.getHeader().getGenotypeSamples(); long count = 0;
                while (records.hasNext()) { records.next(); count++; } metrics.put("input_records", Long.toString(count));
                switch (method) {
                    case "bcf-norm" -> {
                        Path reference = reference(o, manifest);
                        command.addAll(List.of("norm", "--threads", Integer.toString(threads), "--check-ref", "e", "-f", reference.toString(), "-Ov", "-o", vcf.toString()));
                        if (bool(o, "split-multiallelic", true)) command.addAll(List.of("-m", "-any"));
                    }
                    case "bcf-filter" -> {
                        double quality = number(o, "min-qual", Double.NaN); if (!Double.isFinite(quality) || quality < 0) throw new IllegalArgumentException("--min-qual must be a nonnegative finite threshold");
                        String label = o.getOrDefault("filter-name", "JL_LOW_QUAL");
                        if (!label.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || reader.getHeader().getFilterHeaderLine(label) != null) throw new IllegalArgumentException("Filter label must be new, unique, and use letters/digits/underscore");
                        // Single-quoted literals survive MinGW's Windows argv parsing without nested double quotes.
                        command.addAll(List.of("filter", "--threads", Integer.toString(threads), "-m", "+", "-s", label, "-e", "QUAL='.' || QUAL<" + quality, "-Ov", "-o", vcf.toString()));
                    }
                    case "bcf-select" -> {
                        Path list = file(required(o, "samples-list")); manifest.put("samples_list_sha256", hash(list));
                        if (Files.size(list) > 1024 * 1024) throw new IOException("Sample list must be <=1 MiB");
                        List<String> requested = Files.readAllLines(list); var unique = new LinkedHashSet<>(requested);
                        if (requested.isEmpty() || requested.size() > 10000 || unique.size() != requested.size() || requested.stream().anyMatch(s -> s.isBlank() || s.chars().anyMatch(Character::isWhitespace)) || !samples.containsAll(requested))
                            throw new IOException("Require 1..10000 unique nonblank known sample IDs, one per line without a header/whitespace");
                        Files.write(dir.resolve("samples.txt"), requested); samples = requested;
                        command.addAll(List.of("view", "--threads", Integer.toString(threads), "-S", dir.resolve("samples.txt").toString(), "-Ov", "-o", vcf.toString()));
                    }
                    case "bcf-stats" -> command.addAll(List.of("stats", "-s", "-"));
                    default -> throw new AssertionError(method);
                }
            }
            command.add(input.toString());
        }
        checked(command, dir, "external", method.equals("bcf-stats") ? dir.resolve("variant-stats.txt") : null, timeout, session);
        if (method.equals("bcf-stats")) {
            boolean found = false;
            try (var lines = Files.newBufferedReader(dir.resolve("variant-stats.txt"))) {
                String line; while ((line = lines.readLine()) != null) if (line.startsWith("SN\t0\tnumber of records:\t")) {
                    if (!line.substring(line.lastIndexOf('\t') + 1).equals(metrics.get("input_records"))) throw new IOException("BCFtools stats record count mismatch"); found = true;
                }
            }
            if (!found) throw new IOException("BCFtools stats omitted the expected record summary");
        } else {
            long count = 0;
            // Decode through one explicitly closed stream; no feature/index reader owns a second handle.
            var codec = new VCFCodec();
            try (var records = new LineIteratorImpl(new SynchronousLineReader(Files.newInputStream(vcf)))) {
                List<String> actual = ((VCFHeader) codec.readActualHeader(records)).getGenotypeSamples();
                if (!actual.equals(samples) && !(method.equals("bcf-call") && new HashSet<>(actual).equals(new HashSet<>(samples)))) throw new IOException("Variant output changed sample IDs/order");
                while (records.hasNext()) {
                    VariantContext variant = codec.decode(records.next());
                    if (variant.getStart() < 1 || variant.getReference().length() < 1 || variant.getNSamples() != samples.size()) throw new IOException("Invalid variant output"); count++;
                }
            }
            metrics.put("output_records", Long.toString(count)); metrics.put("output_samples", Integer.toString(samples.size()));
            if ((method.equals("bcf-select") || method.equals("bcf-filter")) && !metrics.get("input_records").equals(Long.toString(count))) throw new IOException("Site-preserving variant operation changed record count");
        }
    }

    private record Region(String contig, int start, int end) {
        static Region parse(String value) {
            var matcher = java.util.regex.Pattern.compile("([^:\\s]+):([0-9]+)-([0-9]+)").matcher(value);
            if (!matcher.matches()) throw new IllegalArgumentException("region must be exact CHR:START-END, one-based inclusive");
            int start = Integer.parseInt(matcher.group(2)), end = Integer.parseInt(matcher.group(3));
            if (start < 1 || end < start) throw new IllegalArgumentException("Invalid one-based inclusive region");
            return new Region(matcher.group(1), start, end);
        }
        String text() { return contig + ":" + start + "-" + end; }
    }
    private record SamCounts(long count, long mapped, long primary, long duplicates, BigInteger fingerprint) { }
    private static SamCounts scan(Path input, Predicate<SAMRecord> keep, String order) throws Exception {
        long count = 0, mapped = 0, primary = 0, duplicates = 0; BigInteger fingerprint = BigInteger.ZERO;
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); SAMRecord previous = null; var coordinate = new SAMRecordCoordinateComparator();
        try (var reader = samFactory().open(input); var records = reader.iterator()) {
            if (order != null && !reader.getFileHeader().getSortOrder().name().equals(order)) throw new IOException("Required " + order + " SAM/BAM header omitted");
            while (records.hasNext()) {
                SAMRecord record = records.next();
                if (previous != null && (("coordinate".equals(order) && coordinate.fileOrderCompare(previous, record) > 0)
                    || ("queryname".equals(order) && previous.getReadName().compareTo(record.getReadName()) > 0))) throw new IOException("SAM/BAM records are not actually in requested order");
                previous = record; if (!keep.test(record)) continue;
                count++; if (!record.getReadUnmappedFlag()) mapped++; if (!record.isSecondaryOrSupplementary()) primary++; if (record.getDuplicateReadFlag()) duplicates++;
                fingerprint = fingerprint.add(new BigInteger(1, digest.digest(record.getSAMString().getBytes(StandardCharsets.UTF_8))));
            }
        }
        return new SamCounts(count, mapped, primary, duplicates, fingerprint);
    }
    private static Path file(String value) throws IOException {
        Path file = Path.of(value).toAbsolutePath().normalize(); if (!Files.isRegularFile(file)) throw new IOException("Local input does not exist: " + file); return file;
    }
    private static Path reference(Map<String, String> o, Map<String, Object> manifest) throws IOException {
        Path path = file(required(o, "reference")), fai = file(path + ".fai");
        org.jlinalg.sequence.FastaIndexes.validate(path, fai);
        manifest.put("reference_sha256", hash(path)); manifest.put("reference_fai_sha256", hash(fai)); return path;
    }
    private static Path indexedBam(Path bam, Region region) throws IOException {
        if (!bam.toString().toLowerCase(Locale.ROOT).endsWith(".bam")) throw new IOException("Indexed operations require local BAM input");
        try (var reader = samFactory().open(bam)) {
            if (!reader.hasIndex()) throw new IOException("BAM requires its .bai/.csi index beside the source file");
            if (region != null) {
                var sequence = reader.getFileHeader().getSequenceDictionary().getSequence(region.contig);
                if (sequence == null || region.end > sequence.getSequenceLength()) throw new IOException("Region is absent from/outside the BAM reference dictionary");
            }
        }
        Path index = SamFiles.findIndex(bam); if (index == null) throw new IOException("Cannot resolve the required BAM index path"); return index;
    }
    private static void index(String exe, Path bam, Path index, Path dir, int threads, int timeout, Map<String, Object> session) throws Exception {
        checked(List.of(exe, "index", "-@", Integer.toString(threads), "-b", bam.toString(), index.toString()), dir, "index", null, timeout, session);
        if (!Files.isRegularFile(index)) throw new IOException("SAMtools omitted BAM index");
        try (var reader = samFactory().open(SamInputResource.of(bam).index(index))) {
            if (!reader.hasIndex()) throw new IOException("Independent BAM index reader rejected output");
            if (!reader.getFileHeader().getSequenceDictionary().isEmpty()) reader.indexing().getIndex().getSpanOverlapping(0, 1, 1);
        }
    }
    private static SamReaderFactory samFactory() {
        // Index memory mapping can keep newly produced .bai files locked on Windows after close.
        return SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).enable(SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX);
    }
    private static void checked(List<String> command, Path dir, String log, Path stdout, int timeout, Map<String, Object> session) throws Exception {
        int code = execute(command, dir, log, stdout, timeout, session); if (code != 0) throw new IOException("Upstream HTS process failed with status " + code + "; inspect " + log + ".log");
    }
    private static int execute(List<String> command, Path dir, String log, Path stdout, int timeout, Map<String, Object> session) throws Exception {
        session.put(log + "_command", List.copyOf(command));
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).redirectError(dir.resolve(log + ".log").toFile());
        if (stdout == null) builder.redirectErrorStream(true).redirectOutput(dir.resolve(log + ".log").toFile()); else builder.redirectOutput(stdout.toFile());
        builder.environment().remove("BCFTOOLS_PLUGINS"); builder.environment().remove("HTS_PATH"); builder.environment().put("LC_ALL", "C");
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); throw new IOException("Upstream HTS process exceeded timeout; inspect " + log + ".log"); }
            session.put(log + "_exit_code", process.exitValue()); return process.exitValue();
        } catch (InterruptedException failure) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); Thread.currentThread().interrupt(); throw failure; }
    }
    private static void quarantine(Path dir) throws IOException {
        try (var paths = Files.list(dir)) {
            for (Path p : paths.toList()) if (Set.of("results.tsv", "alignment.bam", "alignment.bai", "variants.vcf", "pileup.bcf", "pileup.tsv", "flagstat.json", "variant-stats.txt").contains(p.getFileName().toString()) || p.getFileName().toString().startsWith("sort-tmp") || p.getFileName().toString().startsWith("work-")) {
                Files.createDirectories(dir.resolve("partial")); Files.move(p, dir.resolve("partial").resolve(p.getFileName()));
            }
        }
    }
    private static final class DependencyUnavailable extends IOException { private static final long serialVersionUID=1L; DependencyUnavailable(String message) { super(message); } }
    static String help() { return """
        hts-tools --method METHOD --input LOCAL_FILE --out NEW_DIR [--threads 0 --timeout 3600]
        fasta-index: native Java, no external executable; uncompressed FASTA -> ORIGINAL_NAME.fai.
          Indexes actual LF/CRLF byte widths. Existing source/index files are preserved.
        SAMtools methods require --samtools EXECUTABLE (version 1.24 with HTSlib 1.24):
          sam-view: SAM/BAM -> alignment.bam; --min-mapq 0 --require-flags 0 --exclude-flags 0
            Optional --region CHR:START-END requires indexed BAM; coordinates one-based inclusive.
          sam-sort: SAM/BAM -> alignment.bam; --sort-order coordinate|queryname --memory-mb 256
            --create-index false. Queryname uses explicit lexicographical -N (not natural -n).
          sam-index: coordinate BAM -> alignment.bai in output bundle; does not alter source BAM.
          sam-flagstat: full flag counts -> flagstat.json; QC-passed/failed counts independently checked.
          sam-markdup: name sort -> fixmate -m -> coordinate sort -> markdup, alignment.bam;
            --remove-duplicates false --optical-distance 0 --memory-mb 256. Successful intermediate
            BAMs are removed after hashing; failed stages are quarantined. Inspect markdup statistics/logs.
          sam-mpileup: raw textual pileup.tsv (not genotype calls); --reference LOCAL.fa with .fai;
            --min-mapq 0 --min-baseq 13 --max-depth 8000 --baq true --exclude-flags 1796.
            Optional --region CHR:START-END needs indexed BAM. Set --baq false for explicit -B.
        BCFtools methods require --bcftools EXECUTABLE (version 1.24 with HTSlib 1.24):
          bcf-call: indexed coordinate BAM -> pileup.bcf and variants.vcf;
            --reference LOCAL.fa (matching .fai required) --ploidy 1|2 (explicit required)
            [--region CHR:START-END --min-mapq 0 --min-baseq 13 --max-depth 250].
            Multiallelic variants-only caller; uniform ploidy, no inferred sex, pool or mosaic model.
          bcf-norm: VCF/VCF.gz -> variants.vcf; --reference LOCAL.fa --split-multiallelic true;
            strict reference match, reference alignment/left-normalization and optional allele splitting.
          bcf-filter: VCF/VCF.gz -> variants.vcf; --min-qual NUMBER [--filter-name JL_LOW_QUAL];
            soft labels missing QUAL or QUAL below threshold, retains sites and existing filters.
          bcf-select: VCF/VCF.gz -> variants.vcf; --samples-list FILE, one ID/line, no header;
            1..10000 unique known samples, <=1 MiB; requested order and all sites retained.
          bcf-stats: VCF/VCF.gz -> variant-stats.txt; upstream summary plus independent record count.
        Local SAM/BAM/VCF and uncompressed FASTA only; no CRAM, plugins or remote data.
        Reference .fai lengths/offsets/wrapping must match the exact FASTA; checked before processing.
        threads is the upstream additional/compression thread count, not total caller CPU threads.
        BAM/VCF records are streamed. Full input hashes/counts and process/session/DLL versions retained.
        Windows setup where permitted: python tools/literature/install_hts_dependencies.py --allow-download
          then executables under build/omics-hts/ucrt64/bin/. MSYS2-built binaries with pinned packages.
        Missing/incompatible runtime: skip 3; other failure: 2, prominent WARNINGS.md and required action.
        Failures quarantine partial outputs and publish no results.tsv. Empty results retain a warning.
        """; }
}
