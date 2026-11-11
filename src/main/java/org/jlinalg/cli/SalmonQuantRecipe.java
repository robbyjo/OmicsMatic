/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit bulk mapping/alignment quantification; no opaque flags or inferred paper settings. */
final class SalmonQuantRecipe {
    private SalmonQuantRecipe() { }
    enum Mode { SINGLE_FASTQ, PAIRED_FASTQ, TRANSCRIPTOME_ALIGNMENTS }
    enum Optimizer { EM, VARIATIONAL_BAYES }
    enum Population { REQUIRE_DECLARED_NONDECOYS, ORIGINAL_RUNTIME_WITH_EXPLICIT_LEDGER }
    enum Format { SAM, BAM }
    enum Layout { SINGLE, PAIRED }
    record Reads(String first, String second, SalmonReads.Alphabet alphabet, SalmonReads.Pairing pairing) { }
    record Alignments(String path, Format format, Layout layout) { }
    record Reference(String path, RepeatFasta.Encoding encoding, SalmonReferences.Headers headers) { }
    record FragmentPrior(BigDecimal mean, BigDecimal sd) { }
    record Request(Path source, NextflowFiles.Pin sourcePin, String profile, String version, Mode mode, String indexBundle,
                   Reads reads, Alignments alignments, Reference reference, String libraryType, Optimizer optimizer,
                   boolean sequenceBias, boolean gcBias, boolean positionBias, int bootstraps, long minimumAssignedFragments,
                   FragmentPrior prior, int threads, Population population, Map<String, Long> capacities) {
        Request { capacities = Map.copyOf(capacities); }
        boolean paired() { return mode == Mode.PAIRED_FASTQ || alignments != null && alignments.layout() == Layout.PAIRED; }
        void verify(Runnable check) throws IOException { require(sourcePin.equals(NextflowFiles.pin(source, 2097152, check)), "Original Salmon quantification recipe changed"); }
        List<String> arguments(String executable, String index, String first, String second, String alignment, String fasta, String output) throws IOException {
            SalmonIndexRecipe.nativePath(executable); SalmonIndexRecipe.nativePath(output);
            var command = new ArrayList<>(List.of(executable, "--no-version-check", "quant"));
            if (mode == Mode.TRANSCRIPTOME_ALIGNMENTS) {
                require(index == null && first == null && second == null && alignment != null && fasta != null, "Prepared Salmon alignment/mapping roles conflict");
                SalmonIndexRecipe.nativePath(alignment); SalmonIndexRecipe.nativePath(fasta); command.addAll(List.of("-t", fasta, "-l", libraryType, "-a", alignment));
                if (reference.headers() == SalmonReferences.Headers.GENCODE_FIRST_PIPE) command.add("--gencode");
            } else {
                require(index != null && first != null && alignment == null && fasta == null && (second != null) == paired(), "Prepared Salmon mapping/alignment roles conflict");
                SalmonIndexRecipe.nativePath(index); SalmonIndexRecipe.nativePath(first); command.addAll(List.of("-i", index, "-l", libraryType));
                if (paired()) { SalmonIndexRecipe.nativePath(second); command.addAll(List.of("-1", first, "-2", second)); } else command.addAll(List.of("-r", first));
                // Original1.x mapping is selective alignment. This historical
                // flag is accepted but deprecated/no-op in later editions.
                command.add("--validateMappings");
            }
            command.add(optimizer == Optimizer.EM ? "--useEM" : "--useVBOpt");
            if (sequenceBias) command.add("--seqBias"); if (gcBias) command.add("--gcBias"); if (positionBias) command.add("--posBias");
            if (bootstraps > 0) command.addAll(List.of("--numBootstraps", Integer.toString(bootstraps)));
            if (prior != null) command.addAll(List.of("--fldMean", prior.mean().toPlainString(), "--fldSD", prior.sd().toPlainString()));
            command.addAll(List.of("--minAssignedFrags", Long.toString(minimumAssignedFragments), "-p", Integer.toString(threads), "-o", output)); return List.copyOf(command);
        }
        long capacity(String name) { Long value = capacities.get(name); if (value == null) throw new IllegalArgumentException("Undeclared Salmon capacity: " + name); return value; }
    }
    static Request read(Path source, Runnable check) throws IOException { return read(source, check, false); }
    static Request readCaptured(Path source, Runnable check) throws IOException { return read(source, check, true); }
    private static Request read(Path source, Runnable check, boolean captured) throws IOException {
        NextflowFiles.Pin pin = NextflowFiles.pin(source, 2097152, check);
        Map<?, ?> raw = NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(source, 2097152)), 2097152, 100000));
        NextflowFiles.keys(raw, "schema_version", "profile", "mode", "index_bundle", "reads", "alignments", "reference", "library_type", "optimizer", "sequence_bias", "gc_bias", "position_bias", "bootstraps", "minimum_assigned_fragments", "fragment_prior", "threads", "population", "limits");
        NextflowFiles.integer(raw.get("schema_version"), 1, 1); String profile = NextflowFiles.text(raw.get("profile"));
        if (!SalmonIndexRecipe.profiles().contains(profile)) throw new SalmonRuntime.Unavailable("Requested exact Salmon quantification edition is unsupported; no automatic replacement");
        Mode mode = choice(Mode.class, raw.get("mode")); String index = raw.get("index_bundle") == null ? null : path(raw.get("index_bundle"), captured);
        Reads reads = null; Alignments alignments = null; Reference reference = null;
        if (raw.get("reads") != null) {
            Map<?, ?> r = NextflowFiles.object(raw.get("reads")); NextflowFiles.keys(r, "first", "second", "alphabet", "pairing");
            reads = new Reads(path(r.get("first"), captured), r.get("second") == null ? null : path(r.get("second"), captured), choice(SalmonReads.Alphabet.class, r.get("alphabet")), choice(SalmonReads.Pairing.class, r.get("pairing")));
        }
        if (raw.get("alignments") != null) {
            Map<?, ?> a = NextflowFiles.object(raw.get("alignments")); NextflowFiles.keys(a, "path", "format", "layout"); alignments = new Alignments(path(a.get("path"), captured), choice(Format.class, a.get("format")), choice(Layout.class, a.get("layout")));
        }
        if (raw.get("reference") != null) {
            Map<?, ?> r = NextflowFiles.object(raw.get("reference")); NextflowFiles.keys(r, "path", "encoding", "headers"); reference = new Reference(path(r.get("path"), captured), choice(RepeatFasta.Encoding.class, r.get("encoding")), choice(SalmonReferences.Headers.class, r.get("headers")));
        }
        require(mode == Mode.TRANSCRIPTOME_ALIGNMENTS ? alignments != null && reference != null && reads == null && index == null : reads != null && index != null && alignments == null && reference == null && (reads.second() != null) == (mode == Mode.PAIRED_FASTQ), "Declare exactly the reference/index and complete read/alignment roles for this Salmon mode");
        boolean paired = mode == Mode.PAIRED_FASTQ || alignments != null && alignments.layout() == Layout.PAIRED;
        String library = NextflowFiles.text(raw.get("library_type"));
        require((paired ? Set.of("A", "IU", "ISF", "ISR", "OU", "OSF", "OSR", "MU", "MSF", "MSR") : Set.of("A", "U", "SF", "SR")).contains(library), "Declare an exact original library type compatible with the read layout; an orientation phrase is not a library code");
        Optimizer optimizer = choice(Optimizer.class, raw.get("optimizer")); Population population = choice(Population.class, raw.get("population"));
        require(mode != Mode.TRANSCRIPTOME_ALIGNMENTS || population == Population.REQUIRE_DECLARED_NONDECOYS, "Alignment mode has no mapping-index source-decoy replacement policy");
        FragmentPrior prior = null;
        if (raw.get("fragment_prior") != null) {
            Map<?, ?> p = NextflowFiles.object(raw.get("fragment_prior")); NextflowFiles.keys(p, "mean", "sd"); prior = new FragmentPrior(positive(p.get("mean")), positive(p.get("sd")));
        }
        require(paired || prior != null, "Single-end Salmon quantification requires an explicit fragment mean and SD; read length is not fragment length");
        var capacities = capacities(NextflowFiles.object(raw.get("limits")));
        Request result = new Request(source, pin, profile, profile.substring("linux-x86_64-salmon-".length()), mode, index, reads, alignments, reference, library, optimizer,
            bool(raw.get("sequence_bias")), bool(raw.get("gc_bias")), bool(raw.get("position_bias")), (int) NextflowFiles.integer(raw.get("bootstraps"), 0, 1000),
            NextflowFiles.integer(raw.get("minimum_assigned_fragments"), 1, 9007199254740991L), prior, (int) NextflowFiles.integer(raw.get("threads"), 1, 1024), population, capacities);
        result.verify(check); return result;
    }
    private static Map<String, Long> capacities(Map<?, ?> raw) throws IOException {
        // The complete policy is saved, including defaults. A later workflow
        // cannot silently replace a small requested capacity with a larger one.
        Map<String, Long> defaults = Map.ofEntries(Map.entry("input_bytes", 68719476736L), Map.entry("decoded_characters", 274877906944L), Map.entry("alignment_decoded_bytes", 274877906944L), Map.entry("read_records", 1000000000L), Map.entry("read_bases", 1000000000000L),
            Map.entry("read_length", 1000000L), Map.entry("header_characters", 65536L), Map.entry("references", 2000000L), Map.entry("reference_residues", 68719476736L), Map.entry("retained_bytes", 4294967296L),
            Map.entry("alignment_records", 2000000000L), Map.entry("alignment_groups", 1000000000L), Map.entry("hits_per_group", 1000000L), Map.entry("line_characters", 1048576L), Map.entry("name_bytes", 1073741824L),
            Map.entry("metadata_bytes", 16777216L), Map.entry("table_characters", 1073741824L), Map.entry("bootstrap_values", 2000000000L), Map.entry("wall_seconds", 86400L), Map.entry("cpu_seconds", 86400L),
            Map.entry("address_space_bytes", 34359738368L), Map.entry("file_bytes", 68719476736L), Map.entry("open_files", 4096L), Map.entry("workspace_bytes", 549755813888L), Map.entry("files", 100000L),
            Map.entry("processes", 64L), Map.entry("threads", 4096L), Map.entry("sampled_rss_bytes", 17179869184L), Map.entry("poll_millis", 100L));
        require(defaults.keySet().containsAll(raw.keySet()), "Unknown Salmon quantification capacity"); var result = new java.util.TreeMap<>(defaults);
        for (var entry : raw.entrySet()) { String key = NextflowFiles.text(entry.getKey()); result.put(key, NextflowFiles.integer(entry.getValue(), 1, 1000000000000L)); }
        require(result.get("metadata_bytes") >= 1024 && result.get("metadata_bytes") <= 67108864 && result.get("read_length") <= 10000000 && result.get("header_characters") <= 1000000
            && result.get("references") <= 10000000 && result.get("line_characters") <= 10000000 && result.get("hits_per_group") <= 10000000 && result.get("files") <= 1000000
            && result.get("read_records") <= 1000000000L && result.get("retained_bytes") >= 262144, "Salmon parser/metadata capacity is outside the supported complete domain");
        // RsemProcess validates the full operating-system capacity domain now,
        // before a captured request can launch any original process.
        process(result); return Map.copyOf(result);
    }
    static RsemProcess.Limits process(Map<String, Long> v) throws IOException {
        try { return new RsemProcess.Limits(Math.multiplyExact(v.get("wall_seconds"), 1000), v.get("address_space_bytes"), v.get("cpu_seconds"), v.get("file_bytes"), Math.toIntExact(v.get("open_files")),
            v.get("workspace_bytes"), Math.toIntExact(v.get("files")), Math.toIntExact(v.get("processes")), Math.toIntExact(v.get("threads")), v.get("sampled_rss_bytes"), Math.toIntExact(v.get("poll_millis"))); }
        catch (IllegalArgumentException | ArithmeticException invalid) { throw new IOException("Invalid explicit complete Salmon process capacities", invalid); }
    }
    private static BigDecimal positive(Object raw) throws IOException { BigDecimal value = SalmonMetadata.decimal(raw); require(value.signum() > 0 && value.compareTo(BigDecimal.valueOf(1000000000)) <= 0 && value.scale() >= -100 && value.scale() <= 100, "Declare a finite positive bounded Salmon fragment prior"); return value; }
    private static String path(Object raw, boolean captured) throws IOException {
        String value = NextflowFiles.text(raw);
        if (captured) { require(value.startsWith("/") && value.indexOf(0) < 0, "Recorded Salmon producer paths must remain absolute Linux text"); for (String part : value.substring(1).split("/", -1)) require(!part.isEmpty() && !part.equals(".") && !part.equals(".."), "Recorded Salmon producer path contains an empty/relative segment"); }
        else { try { Path p = Path.of(value); require(p.isAbsolute() && p.normalize().equals(p), "Declare absolute normalized original Salmon input paths"); } catch (java.nio.file.InvalidPathException invalid) { throw new IOException("Invalid original Salmon input path", invalid); } }
        return value;
    }
    private static <E extends Enum<E>> E choice(Class<E> type, Object raw) throws IOException { try { return Enum.valueOf(type, NextflowFiles.text(raw)); } catch (IllegalArgumentException invalid) { throw new IOException("Unsupported explicit Salmon " + type.getSimpleName(), invalid); } }
    private static boolean bool(Object raw) throws IOException { if (!(raw instanceof Boolean value)) throw new IOException("Declare an explicit Salmon boolean"); return value; }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
