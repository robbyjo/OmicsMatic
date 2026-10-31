/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.util.BlockCompressedOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.EagleGeneticMap;
import org.jlinalg.genetics.EagleGenotypes;
import org.jlinalg.genetics.VcfCsiIndex;

/** Complete protected cohort intake and GT-preserving BGZF/CSI preparation. */
final class EagleCohortInputs {
    private EagleCohortInputs() { }
    enum PartialMissing { REFUSE, ALLOW_ORIGINAL_WHOLE_CALL_MISSING }
    record Spec(String contig, int chromosome, int chromosomeX, int start, int end,
            EagleGenotypes.Options genotypes, PartialMissing partialMissing,
            EagleGeneticMap.Outside outsideMap, EagleGeneticMap.Limits mapLimits,
            EagleVcf.Limits vcfLimits, long retainedBytes, long ledgerBytes, long workspaceBytes) {
        Spec {
            Objects.requireNonNull(genotypes, "genotypes"); Objects.requireNonNull(partialMissing, "partialMissing");
            Objects.requireNonNull(outsideMap, "outsideMap"); Objects.requireNonNull(mapLimits, "mapLimits");
            Objects.requireNonNull(vcfLimits, "vcfLimits");
            if (chromosome < 1 || chromosomeX < 1 || start < 1 || end < start
                    || genotypes.mode() != EagleGenotypes.Mode.COHORT || genotypes.sexChromosome() != (chromosome == chromosomeX)
                    || retainedBytes < 1 || ledgerBytes < 1 || workspaceBytes < 1)
                throw new IllegalArgumentException("Invalid explicit Eagle cohort chromosome, region, mode or capacities");
            Set<String> names = chromosome == chromosomeX
                    ? Set.of(Integer.toString(chromosome), "chr" + chromosome, "X", "chrX")
                    : Set.of(Integer.toString(chromosome), "chr" + chromosome);
            if (!names.contains(contig)) throw new IllegalArgumentException("Literal target contig must agree with the explicit original Eagle chromosome; no alias rewrite");
            long estimate = Math.addExact(Math.multiplyExact(16L, vcfLimits.text().lineCharacters()),
                    Math.addExact(Math.multiplyExact(6L, vcfLimits.headerCharacters()),
                            Math.addExact(Math.multiplyExact(256L, vcfLimits.text().samples()),
                                    Math.multiplyExact(32L, mapLimits.maximumSelectedRows()))));
            if (estimate > retainedBytes) throw new IllegalArgumentException("Estimated bounded Eagle header/row/map storage exceeds declared retained capacity; reduce the explicit capacities or supply reviewed larger limits");
        }
        boolean selected(EagleVcf.Row row) { return row.contig().equals(contig) && row.position() >= start && row.position() <= end; }
        String exclusion(EagleVcf.Row row) { return row.contig().equals(contig) ? "outside-explicit-inclusive-region" : "outside-explicit-literal-contig"; }
    }
    record Snapshot(Path original, Path copy, long bytes, String sha256) { }
    record Prepared(Spec spec, Path work, Path target, Path index, Path map,
            List<String> samples, EagleVcf.Report input, EagleVcf.Report stage,
            EagleGeneticMap.Summary mapSummary, long selectedRows, long excludedRows,
            long fullyMissingCalls, long partiallyMissingCalls, long observedAllelesIgnoredByInference, long extrapolatedRows,
            List<Snapshot> snapshots, Map<Path,String> ownedSha256, List<String> warnings) {
        Prepared {
            samples = List.copyOf(samples); snapshots = List.copyOf(snapshots);
            ownedSha256 = Map.copyOf(ownedSha256); warnings = List.copyOf(warnings);
        }
        void verify(Runnable check) throws IOException {
            for (Snapshot snapshot : snapshots) {
                if (!digest(snapshot.original(), spec.vcfLimits().text().inputBytes(), check).equals(snapshot.sha256())
                        || !digest(snapshot.copy(), spec.vcfLimits().text().inputBytes(), check).equals(snapshot.sha256()))
                    throw new IOException("Eagle immutable original/snapshot changed: " + snapshot.original());
            }
            for (var entry : ownedSha256.entrySet()) if (!digest(entry.getKey(), spec.workspaceBytes(), check).equals(entry.getValue()))
                throw new IOException("Eagle admitted preparation changed: " + entry.getKey());
            workspace(work, spec.workspaceBytes()); check.run();
        }
    }

    static Prepared prepare(Path source, Path mapSource, Path newWork, Spec spec, Runnable check) throws IOException {
        Objects.requireNonNull(spec, "spec"); Objects.requireNonNull(check, "check"); check.run();
        Path work = newWork.toAbsolutePath().normalize();
        if (work.getParent() == null || !work.getParent().toRealPath().equals(work.getParent()))
            throw new IOException("Eagle preparation requires a canonical existing parent and a NEW owned work directory");
        Files.createDirectory(work); Path snapshots = Files.createDirectory(work.resolve("input-snapshots"));
        var budget = new Budget(spec.workspaceBytes());
        Snapshot targetSource = snapshot(source, snapshots.resolve("target.vcf-source"), spec.vcfLimits().text().inputBytes(), budget, check);
        Path originalMap = mapSource.toAbsolutePath().normalize();
        MinimacVcf.regular(originalMap, spec.vcfLimits().text().inputBytes());
        boolean gzipMap = gzip(originalMap);
        Snapshot mapInput = snapshot(originalMap, snapshots.resolve(gzipMap ? "genetic-map-source.txt.gz" : "genetic-map-source.txt"), spec.vcfLimits().text().inputBytes(), budget, check);
        if (gzip(mapInput.copy()) != gzipMap) throw new IOException("Eagle map encoding changed while protecting its source snapshot");
        EagleGeneticMap map;
        try (Reader reader = decoded(mapInput.copy())) {
            map = EagleGeneticMap.read(reader, spec.chromosome(), spec.outsideMap(), spec.mapLimits(), check);
        }
        Path target = work.resolve("target.vcf.gz"), ledger = work.resolve("target-selection.tsv");
        long selected = 0, excluded = 0, fullyMissing = 0, partial = 0, ignored = 0, extrapolated = 0;
        EagleVcf.Report original; List<String> samples;
        try (var input = EagleVcf.open(targetSource.copy(), spec.vcfLimits(), check);
                var stage = writer(target, spec.vcfLimits().text().stagedBytes(), true, budget);
                var rows = writer(ledger, spec.ledgerBytes(), false, budget)) {
            samples = input.samples();
            if (samples.size() < 2) throw new IOException("Within-cohort Eagle preparation requires at least two explicitly supplied samples");
            for (String header : input.header()) { stage.write(header); stage.write('\n'); }
            rows.write("source_row\tcontig\tposition\tid\tref\talt\taction\treason\tstorage_ploidy\tfully_missing\tpartially_missing\tobserved_alleles_ignored\tmorgans_hex\tmap_region\n");
            EagleVcf.Row row;
            while ((row = input.next()) != null) {
                check.run(); String prefix = row.row() + "\t" + row.identity() + "\t";
                if (!spec.selected(row)) {
                    excluded++; rows.write(prefix + "EXCLUDED\t" + spec.exclusion(row) + "\t.\t.\t.\t.\t.\t.\n"); continue;
                }
                if (row.alternateCount() != 1) throw new IOException("Selected Eagle cohort row must be biallelic with exactly one explicit alternate allele; input row " + row.row());
                final EagleGenotypes.Encoding encoding; final EagleGeneticMap.Estimate estimate;
                try { encoding = EagleGenotypes.encodeTargets(row.calls(), spec.genotypes()); estimate = map.estimate(row.position()); }
                catch (IllegalArgumentException invalid) { throw new IOException("Eagle selected input row " + row.row() + ": " + invalid.getMessage(), invalid); }
                if (encoding.partiallyMissingCalls() != 0 && spec.partialMissing() == PartialMissing.REFUSE)
                    throw new IOException("Partially missing selected Eagle GT requires explicit ALLOW_ORIGINAL_WHOLE_CALL_MISSING; its supplied allele is ignored during original inference");
                selected++; fullyMissing += encoding.fullyMissingCalls(); partial += encoding.partiallyMissingCalls(); ignored += encoding.observedAllelesIgnoredByInference();
                if (estimate.region() == EagleGeneticMap.Region.BEFORE_FIRST_ROW || estimate.region() == EagleGeneticMap.Region.AFTER_LAST_ROW) extrapolated++;
                stage.write(text(row)); stage.write('\n');
                rows.write(prefix + "PHASED\texplicit-cohort-region\t" + encoding.framePloidy() + "\t"
                        + encoding.fullyMissingCalls() + "\t" + encoding.partiallyMissingCalls() + "\t"
                        + encoding.observedAllelesIgnoredByInference() + "\t" + Double.toHexString(estimate.morgans())
                        + "\t" + estimate.region() + "\n");
            }
            original = input.report();
            if (!original.complete() || selected == 0) throw new IOException("Eagle preparation requires complete input EOF and a nonempty explicitly selected cohort region");
        }
        Path index = work.resolve("target.vcf.gz.csi"); VcfCsiIndex.create(target, index); budget.add(Files.size(index));
        EagleVcf.Report staged = verifyStage(targetSource.copy(), target, spec, selected, check);
        var warnings = new ArrayList<String>();
        if (partial != 0) warnings.add("PARTIALLY MISSING GT: " + partial + " selected calls contain " + ignored
                + " supplied alleles that original Eagle ignores during inference. Required action: review target-selection.tsv and the explicit whole-call-missing policy before interpreting phase or imputation.");
        if (fullyMissing != 0 || partial != 0) warnings.add(spec.genotypes().missing() == EagleGenotypes.Missing.KEEP
                ? "MISSING GT RETAINED: cohort --noImpMissing keeps original missing/partial GT strings, although partially supplied alleles remain ignored during inference. Required action: retain this distinction in downstream missingness and phase QC."
                : "MISSING GT IMPUTATION: original Eagle may replace an entire partially missing call, including its supplied allele. Required action: review complete original/output genotype ledgers and validate downstream suitability; this is model inference, not an observed genotype.");
        if (excluded != 0) warnings.add("EXPLICIT REGION EXCLUSIONS: " + excluded + " original rows were excluded from this run. Required action: review every row in target-selection.tsv; excluded data was retained in input-snapshots/.");
        if (extrapolated != 0) warnings.add("ORIGINAL MAP BOUNDARIES: " + extrapolated + " selected positions lie outside the supplied chromosome's map rows and use the explicitly permitted original source boundary arithmetic. Required action: review the genetic positions and map_region fields in target-selection.tsv; use a suitable map or require bracketing if extrapolation is unsuitable.");
        warnings.add("OPAQUE METADATA: original GT separators, sample identities and non-GT fields are retained in the prepared VCF. Required action: review any INFO/FORMAT fields, assembly and genetic-map provenance separately; stale phase-set, likelihood, dosage or INFO annotations are not recalculated by preparation.");
        var hashes = new LinkedHashMap<Path,String>();
        for (Path path : List.of(target, index, ledger)) hashes.put(path, digest(path, spec.workspaceBytes(), check));
        var prepared = new Prepared(spec, work, target, index, mapInput.copy(), samples, original, staged,
                map.summary(), selected, excluded, fullyMissing, partial, ignored, extrapolated, List.of(targetSource, mapInput), hashes, warnings);
        prepared.verify(check); return prepared;
    }

    static EagleVcf.Limits stagedLimits(Spec spec) {
        var l = spec.vcfLimits(); var t = l.text();
        return new EagleVcf.Limits(new MinimacVcf.Limits(t.samples(), t.variants(), t.genotypeCells(), t.stagedBytes(),
                t.decodedCharacters(), t.lineCharacters(), t.stagedBytes(), t.identityRows()),
                l.headerLines(), l.headerCharacters(), l.formatFields(), l.alternateAlleles(), l.positionKeyCharacters());
    }
    private static EagleVcf.Report verifyStage(Path snapshot, Path target, Spec spec, long expected, Runnable check) throws IOException {
        try (var before = EagleVcf.open(snapshot, spec.vcfLimits(), check); var after = EagleVcf.open(target, stagedLimits(spec), check)) {
            if (!before.header().equals(after.header()) || !before.samples().equals(after.samples())) throw new IOException("Eagle preparation changed the complete source header or sample identities");
            EagleVcf.Row row; long count = 0;
            while ((row = before.next()) != null) if (spec.selected(row)) {
                var staged = after.next();
                if (staged == null || !text(row).equals(text(staged))) throw new IOException("Eagle preparation changed original GT, variant identity or opaque non-GT fields");
                count++;
            }
            if (after.next() != null || count != expected || !before.report().complete() || !after.report().complete())
                throw new IOException("Eagle preparation did not preserve the complete explicit row selection");
            return after.report();
        }
    }
    static String text(EagleVcf.Row row) {
        String fixed = row.contig() + "\t" + row.positionText() + "\t" + row.id() + "\t" + row.reference() + "\t" + row.alternate()
                + "\t" + row.quality() + "\t" + row.filter() + "\t" + row.info();
        return row.formats().isEmpty() ? fixed : fixed + "\t" + String.join(":", row.formats()) + "\t" + String.join("\t", row.originalSampleFields());
    }
    static Snapshot snapshot(Path source, Path copy, long bound, Budget budget, Runnable check) throws IOException {
        Path original = source.toAbsolutePath().normalize();
        String before = digest(original, bound, check);
        try (var input = Files.newInputStream(original); var output = bounded(copy, bound, budget)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = input.read(buffer)) != -1) { check.run(); output.write(buffer, 0, n); }
        }
        if (!digest(original, bound, check).equals(before) || !digest(copy, bound, check).equals(before))
            throw new IOException("Eagle original source changed while protecting its complete snapshot: " + original);
        return new Snapshot(original, copy, Files.size(copy), before);
    }
    static Reader decoded(Path path) throws IOException {
        var raw = new BufferedInputStream(Files.newInputStream(path));
        try {
            raw.mark(2); int first = raw.read(), second = raw.read(); raw.reset();
            return new InputStreamReader(first == 31 && second == 139 ? new GZIPInputStream(raw) : raw,
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
        } catch (IOException | RuntimeException failure) { try { raw.close(); } catch (IOException close) { failure.addSuppressed(close); } throw failure; }
    }
    static boolean gzip(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) { return input.read() == 31 && input.read() == 139; }
    }
    static String digest(Path path, long bound, Runnable check) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path) || Files.size(path) > bound)
            throw new IOException("Missing, linked, noncanonical or oversized immutable Eagle file: " + path);
        var initial = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        final MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
        long bytes = 0;
        try (var stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = stream.read(buffer)) != -1) {
                check.run(); bytes = Math.addExact(bytes, n);
                if (bytes > bound) throw new IOException("Immutable Eagle file grew past its complete byte capacity");
                digest.update(buffer, 0, n);
            }
        }
        var now = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (initial.size() != bytes || now.size() != bytes || !initial.lastModifiedTime().equals(now.lastModifiedTime()) || !Objects.equals(initial.fileKey(), now.fileKey()))
            throw new IOException("Immutable Eagle file changed during complete hashing: " + path);
        return HexFormat.of().formatHex(digest.digest());
    }
    static final class Budget {
        private final long maximum; private long bytes;
        Budget(long maximum) { this.maximum = maximum; }
        void add(long n) throws IOException { bytes = Math.addExact(bytes, n); if (bytes > maximum) throw new IOException("Eagle complete preparation workspace byte capacity exceeded; no provisional result is admitted"); }
    }
    private static OutputStream bounded(Path path, long maximum, Budget budget) throws IOException {
        OutputStream raw = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW);
        return new FilterOutputStream(raw) {
            private long bytes;
            private void count(int n) throws IOException { bytes = Math.addExact(bytes, n); if (bytes > maximum) throw new IOException("Eagle prepared file byte capacity exceeded"); budget.add(n); }
            @Override public void write(int value) throws IOException { count(1); out.write(value); }
            @Override public void write(byte[] data, int offset, int length) throws IOException { count(length); out.write(data, offset, length); }
        };
    }
    static BufferedWriter writer(Path path, long maximum, boolean bgzf, Budget budget) throws IOException {
        OutputStream raw = bounded(path, maximum, budget);
        try {
            var encoded = new OutputStreamWriter(bgzf ? new BlockCompressedOutputStream(raw, (Path)null) : raw, StandardCharsets.UTF_8);
            return new BufferedWriter(encoded) {
                @Override public void close() throws IOException {
                    Throwable failure = null;
                    try { super.close(); } catch (IOException | RuntimeException invalid) { failure = invalid; throw invalid; }
                    finally { try { raw.close(); } catch (IOException closing) { if (failure != null) failure.addSuppressed(closing); else throw closing; } }
                }
            };
        } catch (RuntimeException failure) { try { raw.close(); } catch (IOException closing) { failure.addSuppressed(closing); } throw failure; }
    }
    static void workspace(Path root, long maximum) throws IOException {
        long bytes = 0; int count = 0;
        try (var walk = Files.walk(root)) {
            var paths = walk.iterator();
            while (paths.hasNext()) {
                Path path = paths.next(); if (++count > 64 || Files.isSymbolicLink(path)) throw new IOException("Eagle preparation member/link capacity exceeded");
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) bytes = Math.addExact(bytes, Files.size(path));
                else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Nonregular Eagle preparation member");
                if (bytes > maximum) throw new IOException("Eagle complete preparation workspace byte capacity exceeded");
            }
        }
    }
}
