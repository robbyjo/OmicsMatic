/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.SalmonIndexReferences;

/** Closed original-index request: explicit preprocessing choices and complete capacities. */
final class SalmonIndexRecipe {
    private SalmonIndexRecipe() { }
    enum PolyA { SOURCE_DEFAULT, DISABLE_CLIPPING }
    record Limits(SalmonReferences.Limits references, SalmonIndexReferences.Limits tables,
                  RsemProcess.Limits process, int metadataBytes, int metadataNodes) { }
    record Request(Path source, NextflowFiles.Pin sourcePin, String profile, String version, String fastaSource,
                   RepeatFasta.Encoding encoding, SalmonReferences.Headers headers, String decoySource, int k, int threads,
                   boolean keepDuplicates, boolean keepFixedFasta, PolyA polyA, Limits limits) {
        Path fasta() { return Path.of(fastaSource); }
        Path decoys() { return decoySource == null ? null : Path.of(decoySource); }
        void verify(Runnable check) throws IOException {
            require(sourcePin.equals(NextflowFiles.pin(source, 2097152, check)), "Original Salmon index recipe changed");
        }
        List<String> arguments(String executable, String preparedFasta, String preparedDecoys, String index) throws IOException {
            for (String path : preparedDecoys == null ? List.of(executable, preparedFasta, index) : List.of(executable, preparedFasta, preparedDecoys, index)) nativePath(path);
            require((decoySource == null) == (preparedDecoys == null), "Salmon original/prepared decoy roles differ");
            var command = new ArrayList<>(List.of(executable, "--no-version-check", "index", "-t", preparedFasta, "-i", index, "-k", Integer.toString(k), "-p", Integer.toString(threads)));
            if (preparedDecoys != null) command.addAll(List.of("-d", preparedDecoys));
            if (headers == SalmonReferences.Headers.GENCODE_FIRST_PIPE) command.add("--gencode");
            if (keepDuplicates) command.add("--keepDuplicates");
            if (keepFixedFasta) command.add("--keepFixedFasta");
            if (polyA == PolyA.DISABLE_CLIPPING) command.add("--no-clip");
            return List.copyOf(command);
        }
        String capturedFastaName() { return encoding == RepeatFasta.Encoding.GZIP_UTF8 ? "reference.fa.gz" : "reference.fa"; }
    }
    static Request read(Path source, Runnable check) throws IOException {
        return read(source, check, false);
    }
    static Request readCaptured(Path source, Runnable check) throws IOException {
        return read(source, check, true);
    }
    private static Request read(Path source, Runnable check, boolean producerPathsOnly) throws IOException {
        NextflowFiles.Pin pin = NextflowFiles.pin(source, 2097152, check);
        Map<?, ?> raw = NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(source, 2097152)), 2097152, 100000));
        NextflowFiles.keys(raw, "schema_version", "profile", "fasta", "encoding", "headers", "decoys", "k", "threads", "keep_duplicates", "keep_fixed_fasta", "poly_a", "limits");
        NextflowFiles.integer(raw.get("schema_version"), 1, 1);
        String profile = NextflowFiles.text(raw.get("profile"));
        if (!profiles().contains(profile)) throw new SalmonRuntime.Unavailable("Exact Salmon index edition is unavailable; declare one complete supported profile without a fallback");
        String version = profile.substring("linux-x86_64-salmon-".length());
        String fasta = absolute(raw.get("fasta"), producerPathsOnly), decoys = raw.get("decoys") == null ? null : absolute(raw.get("decoys"), producerPathsOnly);
        var encoding = choice(RepeatFasta.Encoding.class, raw.get("encoding"));
        var headers = choice(SalmonReferences.Headers.class, raw.get("headers"));
        int k = (int) NextflowFiles.integer(raw.get("k"), 1, 31), threads = (int) NextflowFiles.integer(raw.get("threads"), 1, 1024);
        require((k & 1) == 1, "Declare an odd original Salmon index k in 1..31");
        boolean keepDuplicates = bool(raw.get("keep_duplicates")), keepFixed = bool(raw.get("keep_fixed_fasta"));
        PolyA polyA = choice(PolyA.class, raw.get("poly_a"));
        if (version.equals("1.0.0") && keepFixed) throw new SalmonRuntime.Unavailable("Salmon1.0.0 has no --keepFixedFasta option; skip this requested capability or explicitly choose a supported edition in a NEW recipe");
        if (polyA == PolyA.DISABLE_CLIPPING && !Set.of("1.8.0", "1.9.0").contains(version)) throw new SalmonRuntime.Unavailable("Requested --no-clip is unavailable in this exact Salmon edition; do not silently enable clipping");
        Limits limits = limits(NextflowFiles.object(raw.get("limits")));
        Request result = new Request(source, pin, profile, version, fasta, encoding, headers, decoys, k, threads, keepDuplicates, keepFixed, polyA, limits);
        result.verify(check); return result;
    }
    static List<String> profiles() { return List.of("linux-x86_64-salmon-1.0.0", "linux-x86_64-salmon-1.1.0", "linux-x86_64-salmon-1.5.2", "linux-x86_64-salmon-1.8.0", "linux-x86_64-salmon-1.9.0"); }
    private static Limits limits(Map<?, ?> raw) throws IOException {
        Set<String> supported = Set.of("input_bytes", "decoded_characters", "residues", "per_sequence", "references", "line_characters", "header_characters", "retained_bytes", "decoy_bytes", "decoy_name_characters", "positions", "name_bytes", "total_name_bytes", "table_bytes", "metadata_bytes", "metadata_nodes", "wall_seconds", "address_space_bytes", "cpu_seconds", "file_bytes", "open_files", "workspace_bytes", "files", "processes", "threads", "sampled_rss_bytes", "poll_millis");
        require(supported.containsAll(raw.keySet()), "Unknown Salmon index capacity; no setting is discarded");
        int refs = (int) value(raw, "references", 2000000, 1, 10000000), header = (int) value(raw, "header_characters", 65536, 1, 1000000);
        long retained = value(raw, "retained_bytes", 4294967296L, 262144, 1099511627776L);
        var fasta = new RepeatFasta.Limits(value(raw, "input_bytes", 34359738368L, 1, 1000000000000L), value(raw, "decoded_characters", 137438953472L, 1, 4000000000000L),
            value(raw, "residues", 68719476736L, 1, 1000000000000L), value(raw, "per_sequence", 2147483647, 1, 2147483647), refs,
            (int) value(raw, "line_characters", 1048576, header, 10000000), header, retained);
        var references = new SalmonReferences.Limits(fasta, (int) value(raw, "decoy_bytes", 67108864, 1, 1073741824), refs, (int) value(raw, "decoy_name_characters", 65536, 1, 1000000), retained);
        var tables = new SalmonIndexReferences.Limits(refs, value(raw, "positions", 100000000000L, 1, 1000000000000L), (int) value(raw, "name_bytes", 262144, 1, 1000000),
            value(raw, "total_name_bytes", 1073741824, 1, 1000000000000L), value(raw, "table_bytes", 274877906944L, 1, 1000000000000L), retained);
        var process = new RsemProcess.Limits(value(raw, "wall_seconds", 86400, 1, 604800) * 1000, value(raw, "address_space_bytes", 34359738368L, 16777216, 1099511627776L),
            value(raw, "cpu_seconds", 86400, 1, 604800), value(raw, "file_bytes", 68719476736L, 1, 1000000000000L), (int) value(raw, "open_files", 4096, 8, 1048576),
            value(raw, "workspace_bytes", 549755813888L, 1, 1000000000000L), (int) value(raw, "files", 100000, 4, 1000000), (int) value(raw, "processes", 64, 1, 4096),
            (int) value(raw, "threads", 4096, 1, 1048576), value(raw, "sampled_rss_bytes", 17179869184L, 1, 1099511627776L), (int) value(raw, "poll_millis", 100, 1, 1000));
        return new Limits(references, tables, process, (int) value(raw, "metadata_bytes", 16777216, 1024, 67108864), (int) value(raw, "metadata_nodes", 1000000, 100, 10000000));
    }
    static void nativePath(String path) throws IOException {
        require(path.matches("/(?:[A-Za-z0-9._+-]+/)*[A-Za-z0-9._+-]+"), "Prepared Salmon paths require absolute canonical ASCII names without whitespace");
        for (String part : path.substring(1).split("/")) require(!part.equals(".") && !part.equals(".."), "Prepared Salmon path contains a relative segment");
    }
    private static String absolute(Object raw, boolean producerPathsOnly) throws IOException {
        String text = NextflowFiles.text(raw);
        if (producerPathsOnly) {
            require(text.startsWith("/") && text.indexOf(0) < 0, "Recorded original Salmon producer path must be absolute Linux text");
            for (String part : text.substring(1).split("/", -1)) require(!part.isEmpty() && !part.equals(".") && !part.equals(".."), "Recorded original Salmon producer path contains an empty/relative segment");
            return text;
        }
        try { Path result = Path.of(text); require(result.isAbsolute() && result.normalize().equals(result), "Declare absolute normalized original Salmon paths"); return text; }
        catch (java.nio.file.InvalidPathException invalid) { throw new IOException("Invalid original Salmon path", invalid); }
    }
    private static <E extends Enum<E>> E choice(Class<E> type, Object raw) throws IOException {
        try { return Enum.valueOf(type, NextflowFiles.text(raw)); } catch (IllegalArgumentException invalid) { throw new IOException("Unsupported explicit Salmon " + type.getSimpleName() + " choice", invalid); }
    }
    private static boolean bool(Object raw) throws IOException { if (!(raw instanceof Boolean value)) throw new IOException("Declare an explicit Salmon boolean"); return value; }
    private static long value(Map<?, ?> raw, String name, long fallback, long min, long max) throws IOException { return NextflowFiles.integer(raw.containsKey(name) ? raw.get(name) : fallback, min, max); }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
