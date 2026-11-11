/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.MemeDiscoverySites;

/** Complete unchanged raw input capture; prepared canonical records are derived with a visible weight ledger. */
final class MemeDiscoveryInputs {
    private MemeDiscoveryInputs() { }
    static final class Unsupported extends IOException {
        private static final long serialVersionUID = 1L;
        Unsupported(String text) { super(text); }
    }
    record Population(List<MemeDiscoverySites.Input> records, double[] background, long bases, List<String> warnings) {
        Population { records = List.copyOf(records); background = background.clone(); warnings = List.copyOf(warnings); }
        @Override public double[] background() { return background.clone(); }
    }
    record Captured(Path root, MemeDiscoveryRequest.Request request, Population population, Map<String, Object> inventory,
                    NextflowFiles.Pin originalPrimary, NextflowFiles.Pin originalBackground) {
        Captured { inventory = Map.copyOf(inventory); }
        Path primary() { return root.resolve("primary.fa"); }
        Path backgroundFile() { return root.resolve("background.bg"); }
        void verify(boolean originals, Runnable check) throws IOException {
            request.verify(check); require(inventory.equals(MemeRuntimeFiles.tree(root, treeLimits(request), check)), "Complete captured MEME inputs or ledger changed");
            if (originals) require(originalPrimary.equals(NextflowFiles.pin(Path.of(request.primary()), request.limit("input_bytes"), check))
                && originalBackground.equals(NextflowFiles.pin(Path.of(request.background()), request.limit("input_bytes"), check)), "Original MEME input changed after capture");
        }
        MemeDiscoverySites.Expected expected() { return new MemeDiscoverySites.Expected(request.version(), request.mode(), request.bothStrands(), request.motifs(),
            request.minimumWidth(), request.maximumWidth(), request.seed() == null ? 0 : request.seed(), request.beta().doubleValue(), population.background(), population.records()); }
    }
    static Captured capture(Path newRoot, MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
        require(!Files.exists(newRoot, java.nio.file.LinkOption.NOFOLLOW_LINKS), "Require a NEW MEME input capture directory");
        var primary = NextflowFiles.pin(Path.of(request.primary()), request.limit("input_bytes"), check);
        var background = NextflowFiles.pin(Path.of(request.background()), request.limit("input_bytes"), check);
        Files.createDirectory(newRoot); copy(Path.of(request.primary()), newRoot.resolve("primary.fa"), request.limit("input_bytes"), primary, check);
        copy(Path.of(request.background()), newRoot.resolve("background.bg"), request.limit("input_bytes"), background, check);
        Population population = read(newRoot.resolve("primary.fa"), newRoot.resolve("background.bg"), request, check);
        MemeRuntimeFiles.write(newRoot.resolve("INPUTS.json"), document(request, population, primary, background));
        var captured = new Captured(newRoot, request, population, MemeRuntimeFiles.tree(newRoot, treeLimits(request), check), primary, background);
        captured.verify(true, check); return captured;
    }
    static Captured open(Path root, MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
        Population population = read(root.resolve("primary.fa"), root.resolve("background.bg"), request, check);
        var value = NextflowFiles.object(NextflowFiles.json(root.resolve("INPUTS.json"), 16777216));
        var primary = NextflowFiles.fingerprint(value.get("primary"), request.limit("input_bytes"));
        var background = NextflowFiles.fingerprint(value.get("background"), request.limit("input_bytes"));
        require(primary.equals(NextflowFiles.pin(root.resolve("primary.fa"), request.limit("input_bytes"), check))
            && background.equals(NextflowFiles.pin(root.resolve("background.bg"), request.limit("input_bytes"), check)), "Captured original MEME raw input identity differs");
        require(json(document(request, population, primary, background)).equals(json(value)), "Captured MEME preparation/settings/weights ledger differs from complete raw input replay");
        return new Captured(root, request, population, MemeRuntimeFiles.tree(root, treeLimits(request), check), primary, background);
    }
    static Population read(Path fasta, Path background, MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
        var fa = NextflowFiles.pin(fasta, request.limit("input_bytes"), check); var bg = NextflowFiles.pin(background, request.limit("input_bytes"), check);
        String text = NextflowFiles.utf8(NextflowFiles.bytes(fasta, Math.toIntExact(request.limit("input_bytes"))));
        require(text.length() <= request.limit("characters"), "Complete MEME FASTA character population exceeds capacity");
        if (text.startsWith("\ufeff") || text.indexOf('\u0000') >= 0) throw new Unsupported("BOM, binary/gzip or NUL input needs its own explicit original-input preparation; preserve raw bytes and skip this analysis");
        var records = new ArrayList<MemeDiscoverySites.Input>(); var weights = new ArrayList<Double>();
        var names = new HashSet<String>(); String name = null; var bases = new StringBuilder(); long total = 0; boolean weightHeader = false;
        int cursor = 0;
        while (cursor <= text.length()) {
            int end = text.indexOf('\n', cursor); String raw = text.substring(cursor, end < 0 ? text.length() : end);
            cursor = end < 0 ? text.length() + 1 : end + 1;
            check.run(); require(raw.length() <= request.limit("line_characters"), "Complete MEME FASTA line exceeds capacity");
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            require(line.indexOf('\r') < 0 && line.chars().noneMatch(c -> c < 32 && c != '\t'), "Invalid MEME FASTA control character");
            if (line.isEmpty()) continue;
            if (line.startsWith(">WEIGHTS") && (line.length() == 8 || line.charAt(8) == ' ' || line.charAt(8) == '\t')) {
                require(name == null && records.isEmpty() && !weightHeader, "MEME WEIGHTS must be one initial control header"); weightHeader = true;
                String rest = line.substring(8).strip(); require(!rest.isEmpty(), "MEME WEIGHTS control header has no weights");
                for (String token : rest.split("[ \t]+")) {
                    require(weights.size() < request.limit("sequences"), "Complete MEME weight population exceeds capacity");
                    BigDecimal value = decimal(token); require(value.signum() > 0 && value.compareTo(BigDecimal.ONE) <= 0 && value.doubleValue() > 0, "Original MEME weights must be in (0,1]; no clipping/default replacement"); weights.add(value.doubleValue());
                }
            } else if (line.startsWith(">")) {
                if (name != null) records.add(record(name, bases.toString(), records.size(), weights, weightHeader, request));
                require(records.size() < request.limit("sequences"), "Complete MEME sequence population exceeds capacity");
                String header = line.substring(1); require(!header.isEmpty() && !Character.isWhitespace(header.charAt(0)) && header.length() <= 8192, "Require a complete bounded original MEME FASTA header");
                name = header.split("[ \t]", 2)[0];
                if (name.getBytes(StandardCharsets.UTF_8).length > 100) throw new Unsupported("MEME names above the scoped 100-byte source boundary need a separate name/disposition audit; preserve identifiers and skip");
                require(name.codePoints().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)) && names.add(name), "Duplicate/invalid original MEME sequence name; no silent source filtering or renaming");
                bases = new StringBuilder();
            } else {
                require(name != null, "MEME residues precede the first original sequence header");
                for (int i = 0; i < line.length(); i++) if ("ACGT".indexOf(line.charAt(i)) < 0)
                    throw new Unsupported("MEME lowercase, aliases, ambiguity symbols, gaps or sequence whitespace need a matching original-input audit; preserve input and skip this analysis instead of silently rewriting residues");
                total = Math.addExact(total, line.length()); require(total <= request.limit("bases") && (long) bases.length() + line.length() <= request.limit("sequence_length"), "Complete MEME residue population exceeds capacity"); bases.append(line);
            }
        }
        if (name != null) records.add(record(name, bases.toString(), records.size(), weights, weightHeader, request));
        require(records.size() >= 2 && (!weightHeader || weights.size() == records.size()), "Require at least two complete MEME records and exactly one declared weight per record");
        if (request.mode() != MemeDiscoverySites.Mode.ANR) {
            require(request.minimumSites() == null || request.minimumSites() <= records.size(), "Requested MEME minimum sites exceeds the original sequence population");
            require(request.maximumSites() == null || request.maximumSites() <= records.size(), "Requested MEME maximum sites exceeds the original sequence population");
        }
        double[] frequencies = background(background, request, check);
        if (request.bothStrands()) frequencies = new double[]{(frequencies[0] + frequencies[3]) / 2, (frequencies[1] + frequencies[2]) / 2, (frequencies[1] + frequencies[2]) / 2, (frequencies[0] + frequencies[3]) / 2};
        require(fa.equals(NextflowFiles.pin(fasta, request.limit("input_bytes"), check)) && bg.equals(NextflowFiles.pin(background, request.limit("input_bytes"), check)), "Complete MEME raw input changed during preparation");
        return new Population(records, frequencies, total, List.of("Prepared records preserve original input order, names and canonical DNA; all raw FASTA/header bytes remain unchanged. Initial WEIGHTS is parsed as control metadata, not a sequence.",
            "Background is an explicit positive normalized order-zero file; complement averaging follows the requested original strand choice. No inferred empirical/higher-order background or source filtering is substituted."));
    }
    private static MemeDiscoverySites.Input record(String name, String bases, int index, List<Double> weights, boolean weighted, MemeDiscoveryRequest.Request request) throws IOException {
        require(bases.length() >= request.maximumWidth(), "Empty/short MEME input requires a separate complete source filtering ledger; no record was silently removed");
        require(!weighted || index < weights.size(), "Initial MEME WEIGHTS has fewer entries than original records");
        return new MemeDiscoverySites.Input(name, bases, weighted ? weights.get(index) : 1);
    }
    private static double[] background(Path path, MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
        String value = NextflowFiles.utf8(NextflowFiles.bytes(path, Math.toIntExact(request.limit("input_bytes"))));
        var fields = new ArrayList<String>();
        for (String line : value.split("\n", -1)) { check.run(); String part = line.split("#", 2)[0].strip(); if (!part.isEmpty()) fields.addAll(List.of(part.split("\\s+"))); }
        if (fields.size() != 8) throw new Unsupported("This MEME profile requires a complete four-base order-zero background file; empirical or higher-order background needs a separate prepared-input audit");
        var seen = new HashSet<String>(); double[] result = new double[4]; double sum = 0;
        for (int i = 0; i < fields.size(); i += 2) {
            String base = fields.get(i); require(Set.of("A", "C", "G", "T").contains(base) && seen.add(base), "Invalid or duplicate MEME order-zero background base");
            BigDecimal p = decimal(fields.get(i + 1)); require(p.compareTo(new BigDecimal("1e-250")) >= 0 && p.compareTo(BigDecimal.ONE) <= 0, "MEME background must be supported positive finite probabilities; no epsilon substitution");
            double d = p.doubleValue(); result["ACGT".indexOf(base)] = d; sum += d;
        }
        require(Math.abs(sum - 1) <= 1e-12, "MEME background is not normalized; no silent normalization"); return result;
    }
    private static BigDecimal decimal(String token) throws IOException {
        require(token.length() <= 128 && token.matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,4})?"), "Invalid bounded original MEME numeric input token");
        try { BigDecimal value = new BigDecimal(token); require(Math.abs((long) value.scale()) <= 4096, "MEME numeric input scale exceeds capacity"); return value; }
        catch (NumberFormatException failure) { throw new IOException("Invalid original MEME numeric input", failure); }
    }
    private static Map<String, Object> document(MemeDiscoveryRequest.Request request, Population population, NextflowFiles.Pin primary, NextflowFiles.Pin background) {
        return Map.of("primary", MemeRuntimeFiles.pin(primary), "background", MemeRuntimeFiles.pin(background), "original_primary_path", request.primary(),
            "original_background_path", request.background(), "records", population.records().stream().map(r -> Map.of("name", r.name(), "bases", r.bases(), "weight", r.weight())).toList(),
            "bases", population.bases(), "resolved_background", java.util.Arrays.stream(population.background()).boxed().toList(),
            "warnings", population.warnings(), "scope", "Unchanged raw captured inputs and complete prepared canonical records; no discovery execution admission or authentication");
    }
    private static void copy(Path from, Path to, long maximum, NextflowFiles.Pin expected, Runnable check) throws IOException {
        long count = 0;
        try (var input = Files.newInputStream(from, java.nio.file.LinkOption.NOFOLLOW_LINKS);
             var output = Files.newOutputStream(to, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) >= 0;) {
                check.run(); count = Math.addExact(count, n); require(count <= maximum && count <= expected.bytes(), "Original MEME input grew during bounded capture"); output.write(buffer, 0, n);
            }
        }
        require(count == expected.bytes() && expected.equals(NextflowFiles.pin(to, maximum, check)) && expected.equals(NextflowFiles.pin(from, maximum, check)), "Original MEME input changed while copying its complete raw bytes");
    }
    static MemeRuntimeFiles.Limits treeLimits(MemeDiscoveryRequest.Request r) { return new MemeRuntimeFiles.Limits(r.limit("file_bytes"), r.limit("workspace_bytes"), Math.toIntExact(r.limit("files"))); }
    private static String json(Object value) throws IOException { return NextflowJson.json(NextflowParameters.parse(RsemJson.json(value), 33554432, 1000000)); }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
