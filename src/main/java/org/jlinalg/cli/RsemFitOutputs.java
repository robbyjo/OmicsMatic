/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.RsemCounts;
import org.jlinalg.sequence.RsemEvidence;
import org.jlinalg.sequence.RsemModel;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemResults;
import org.jlinalg.sequence.RsemSamAlignments;

/** Complete parser/model/results and output-population audit after original fitting. */
final class RsemFitOutputs {
    private RsemFitOutputs() { }
    record Limits(long readGroups, long hits, int histogramBins, long countBytes, int countLineCharacters,
                  RsemModel.Limits model, RsemResults.Limits results, RepeatBundle.Limits bundle) {
        Limits {
            java.util.Objects.requireNonNull(model); java.util.Objects.requireNonNull(results); java.util.Objects.requireNonNull(bundle);
            if (readGroups < 1 || hits < 1 || histogramBins < 1 || countBytes < 1 || countLineCharacters < 1)
                throw new IllegalArgumentException("Require explicit complete count/model/result/output capacities");
        }
        void heap() throws IOException {
            try {
                long estimate = Math.addExact(Math.multiplyExact(model.maxScalars(), 192), Math.multiplyExact((long)model.maxTranscripts(), 1024));
                estimate = Math.addExact(estimate, Math.multiplyExact((long)results.maxGroupTranscripts(), 512));
                estimate = Math.addExact(estimate, Math.multiplyExact((long)Math.max(model.maxLineChars(), results.maxLineChars()), 8));
                if (estimate > Runtime.getRuntime().maxMemory() / 4 * 3) throw new IOException("Declared complete fitted-model/result retained capacities exceed75%of this Java heap; use a reviewed larger -Xmx or smaller complete limits without dropping rows or model parameters");
            } catch (ArithmeticException invalid) { throw new IOException("Declared fitted-model/result capacities overflow the supported heap estimate", invalid); }
        }
    }
    record Audit(RsemCounts.Counts counts, RsemModel.FitState model, RsemResults.Audit results,
                 Map<String,String> scientificFiles, int originalParserFilesCompared) {
        Audit { scientificFiles = Map.copyOf(scientificFiles); }
    }
    private static final Set<String> PROCESS = Set.of("process.stdout", "process.stderr", "process.json", "launch.json", "WARNINGS.md");
    private static final Set<String> STAT = Set.of("sample.cnt", "sample.model", "sample.theta");
    private static final Set<String> TEMP = Set.of("sample.dat", "sample.gene_res", "sample.iso_res", "sample.mparams", "sample.omit");
    private static final Set<String> POSTERIOR = Set.of("sample.ofg", "sample.tmp", "sample.genes.results.bak1", "sample.genes.results.bak2", "sample.isoforms.results.bak1", "sample.isoforms.results.bak2");

    static Audit audit(Path home, RsemReference.Reference reference, RsemSamAlignments.Summary input,
            RsemSamAlignments.Options options, Path expectedParserFiles, RsemFitRequest.Settings settings, Limits limits, Runnable check) throws IOException {
        limits.heap(); check.run();
        var before = RepeatBundle.inventory(home, limits.bundle(), true, check);
        shape(home, before.hashes(), options, settings, check);
        for (String name : List.of("sample.isoforms.results", "sample.genes.results", "sample.stat/sample.cnt", "sample.stat/sample.model", "sample.stat/sample.theta",
                "sample.temp/sample.dat", "sample.temp/sample.omit", "sample.temp/sample.mparams", "sample.temp/sample.gene_res", "sample.temp/sample.iso_res"))
            if (!before.hashes().containsKey(name)) throw new IOException("Original fitting lacks a complete required output: " + name);
        int compared = compareParser(expectedParserFiles, home, options, limits, check);
        var counts = RsemCounts.read(home.resolve("sample.stat/sample.cnt"), limits.readGroups(), limits.hits(), limits.histogramBins(), limits.countBytes(), limits.countLineCharacters());
        input.accounting().requireMatch(counts);
        var model = RsemModel.readFitState(home.resolve("sample.stat/sample.model"), home.resolve("sample.stat/sample.theta"), counts, reference.transcripts().size(), options.release(), limits.model());
        var posterior = switch (settings.posterior()) { case NONE -> RsemResults.Posterior.NONE; case MEAN -> RsemResults.Posterior.MEAN; case INTERVAL -> RsemResults.Posterior.INTERVAL; };
        var results = RsemResults.audit(home.resolve("sample.isoforms.results"), home.resolve("sample.genes.results"), reference, counts, model, options.release(), posterior, settings.appendNames(), limits.results());
        if (results.transcripts() != reference.transcripts().size() || results.genes() != reference.groups().size()) throw new IOException("Complete fitted transcript/gene populations disagree with protected reference");
        if (counts.alignableGroups() > 0) {
            String base = "sample.temp/sample_alignable", suffix = options.qualities() ? ".fq" : ".fa";
            for (int mate = 0; mate < (options.paired() ? 2 : 1); mate++) {
                String name = base + (options.paired() ? "_" + (mate + 1) : "") + suffix + ".ridx";
                if (!before.hashes().containsKey(name) || Files.size(home.resolve(name)) == 0) throw new IOException("Original fitting lacks its complete nonempty alignable-read index: " + name);
            }
        }
        var consumed = new ArrayList<RsemEvidence>(); consumed.add(counts.evidence()); consumed.addAll(model.evidence()); consumed.addAll(results.resultEvidence());
        for (var evidence : consumed) {
            Path path = Path.of(evidence.path());
            if (!path.startsWith(home) || !evidence.sha256().equals(before.hashes().get(home.relativize(path).toString().replace('\\', '/'))))
                throw new IOException("Fitted output bytes actually consumed differ from the protected complete output census: " + path);
        }
        check.run(); var after = RepeatBundle.inventory(home, limits.bundle(), true, check);
        if (!before.equals(after)) throw new IOException("Original fitted output bytes or complete population changed during independent auditing");
        var scientific = new java.util.TreeMap<String,String>(); before.hashes().forEach((name, hash) -> { if (name.startsWith("sample")) scientific.put(name, hash); });
        return new Audit(counts, model, results, scientific, compared);
    }
    private static int compareParser(Path expected, Path actual, RsemSamAlignments.Options options, Limits limits, Runnable check) throws IOException {
        var expectedFiles = RepeatBundle.inventory(expected, limits.bundle(), true, check).hashes(); int compared = 0;
        for (var entry : expectedFiles.entrySet()) {
            String name = entry.getKey(); if (!name.startsWith("expected")) continue;
            String actualName = "sample" + name.substring("expected".length());
            Path path = actual.resolve(name.equals("expected.cnt") ? "sample.stat/" + actualName : "sample.temp/" + actualName);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !entry.getValue().equals(RepeatInputs.hash(path, limits.bundle().bytes(), check)))
                throw new IOException("Original fitted parser output differs from the complete native read/hit/count projection: " + path);
            compared++;
        }
        String suffix = options.qualities() ? ".fq" : ".fa";
        for (String category : List.of("un", "alignable", "max")) for (int mate = 0; mate < (options.paired() ? 2 : 1); mate++) {
            String tail = "_" + category + (options.paired() ? "_" + (mate + 1) : "") + suffix;
            Path path = actual.resolve("sample.temp/sample" + tail);
            if (!expectedFiles.containsKey("expected" + tail) && Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) != 0)
                throw new IOException("Unexpected nonempty original read population: " + path);
        }
        if (!expectedFiles.keySet().containsAll(Set.of("expected.cnt", "expected.dat", "expected.omit"))) throw new IOException("Native parser comparison lacks its complete count/data/omission populations");
        return compared;
    }
    private static void shape(Path home, Map<String,String> files, RsemSamAlignments.Options options, RsemFitRequest.Settings settings, Runnable check) throws IOException {
        try (var paths = Files.walk(home)) { var iterator = paths.iterator(); while (iterator.hasNext()) {
            check.run(); Path path = iterator.next(); if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                String name = home.relativize(path).toString().replace('\\', '/');
                if (!Set.of("", "sample.stat", "sample.temp").contains(name)) throw new IOException("Unexpected original fitting directory: " + name);
            }
        } }
        for (String name : files.keySet()) {
            if (PROCESS.contains(name) || name.equals("sample.isoforms.results") || name.equals("sample.genes.results")) continue;
            if (name.startsWith("sample.stat/") && STAT.contains(name.substring("sample.stat/".length()))) continue;
            if (name.startsWith("sample.temp/")) {
                String file = name.substring("sample.temp/".length());
                if (TEMP.contains(file)) continue;
                if (settings.posterior() != RsemFitRequest.Posterior.NONE && POSTERIOR.contains(file)) continue;
                if (settings.posterior() != RsemFitRequest.Posterior.NONE && file.matches("sample\\.countvectors[0-9]+")) {
                    try { int index = Integer.parseInt(file.substring("sample.countvectors".length())); if (index >= 0 && index < settings.workerThreads()) continue; }
                    catch (NumberFormatException invalid) { throw new IOException("Invalid original posterior worker population", invalid); }
                }
                String suffix = options.qualities() ? "\\.fq" : "\\.fa";
                String mate = options.paired() ? "_[12]" : "";
                if (file.matches("sample_(un|alignable|max)" + mate + suffix) || file.matches("sample_alignable" + mate + suffix + "\\.ridx")) continue;
            }
            throw new IOException("Unexpected, undeclared-mode or forbidden original fitting output: " + name);
        }
    }
}
