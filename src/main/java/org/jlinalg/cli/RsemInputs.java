/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemSamAlignments;

/** Exact scientific input snapshots. Copying and reference auditing do not admit a fit. */
final class RsemInputs {
    private RsemInputs() { }
    private static final List<String> REFERENCE = List.of(".ti", ".seq", ".grp", ".chrlist", ".idx.fa", ".n2g.idx.fa", ".transcripts.fa");
    private static final List<String> ALLELE = List.of(".gt", ".ta");
    private static final long RESERVE = 8192;

    record Spec(Path referencePrefix, Path alignment, Path cramFasta, RsemSamAlignments.Options options) {
        Spec { Objects.requireNonNull(referencePrefix); Objects.requireNonNull(alignment); Objects.requireNonNull(options); }
    }
    record Limits(long fileBytes, long totalBytes, int members, long metadataBytes, RsemReference.Limits reference) {
        Limits {
            Objects.requireNonNull(reference);
            if (fileBytes < RESERVE || fileBytes > 1099511627776L || totalBytes < 2 * RESERVE || totalBytes > 1099511627776L
                    || members < 16 || members > 10000 || metadataBytes < RESERVE || metadataBytes > 16777216)
                throw new IllegalArgumentException("Require explicit complete scientific-input capacities with diagnostic reserve");
        }
    }
    record Pin(long bytes, String sha256) { }
    record Member(boolean directory, long bytes, String sha256) { }
    record Prepared(Spec spec, Path home, Path referencePrefix, Path alignment, Path cramFasta,
                    RsemReference.Reference reference, Limits limits, Map<Path,Pin> sourcePins,
                    List<Path> absentSources, Map<String,Member> owned) {
        Prepared {
            sourcePins = Collections.unmodifiableMap(new TreeMap<>(sourcePins));
            absentSources = List.copyOf(absentSources);
            owned = Collections.unmodifiableMap(new TreeMap<>(owned));
        }
        void verify(Runnable check) throws IOException {
            for (var entry : sourcePins.entrySet()) {
                Pin actual = pin(entry.getKey(), limits.fileBytes(), check);
                if (!actual.equals(entry.getValue())) throw new IOException("RSEM scientific source changed: " + entry.getKey());
            }
            for (Path absent : absentSources) {
                check.run();
                canonicalParent(absent);
                if (Files.exists(absent, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Previously absent RSEM reference companion appeared: " + absent);
            }
            if (!owned.equals(inventory(home, limits, check))) throw new IOException("Protected RSEM scientific input bytes or complete membership changed");
        }
    }

    static Prepared prepare(Spec spec, Path home, Limits limits, Runnable check) throws IOException {
        Objects.requireNonNull(spec); Objects.requireNonNull(limits); Objects.requireNonNull(check);
        canonicalParent(home); canonicalParent(spec.referencePrefix()); check.run();
        if (Files.exists(home, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Scientific input snapshot requires a NEW directory");
        for (String suffix : ALLELE) if (Files.exists(companion(spec.referencePrefix(), suffix), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Allele-specific reference companions require their own complete fitting contract: " + suffix);
        Files.createDirectory(home);
        try {
            Path refDirectory = Files.createDirectory(home.resolve("reference"));
            Path alignmentDirectory = Files.createDirectory(home.resolve("alignment"));
            Path stagedPrefix = refDirectory.resolve("reference");
            var sources = new TreeMap<Path,Pin>(); var absent = new ArrayList<Path>(); long[] copied = {0};
            for (String suffix : REFERENCE) {
                Path original = companion(spec.referencePrefix(), suffix);
                if (Files.exists(original, LinkOption.NOFOLLOW_LINKS))
                    copy(original, companion(stagedPrefix, suffix), limits, copied, sources, check);
                else {
                    absent.add(original);
                    if (List.of(".ti", ".seq", ".grp").contains(suffix)) throw new IOException("Missing required RSEM reference member: " + original);
                }
            }
            for (String suffix : ALLELE) absent.add(companion(spec.referencePrefix(), suffix));
            Path alignment = alignmentDirectory.resolve("reads.input");
            copy(spec.alignment(), alignment, limits, copied, sources, check);
            boolean cram;
            try (var input = Files.newInputStream(alignment, LinkOption.NOFOLLOW_LINKS)) {
                cram = java.util.Arrays.equals(input.readNBytes(4), new byte[]{'C','R','A','M'});
            }
            Path fasta = null;
            if (spec.cramFasta() != null) {
                Path directory = Files.createDirectory(home.resolve("cram-reference")); fasta = directory.resolve("reference.fa");
                copy(spec.cramFasta(), fasta, limits, copied, sources, check);
                copy(companion(spec.cramFasta(), ".fai"), companion(fasta, ".fai"), limits, copied, sources, check);
                try (var input = Files.newInputStream(fasta, LinkOption.NOFOLLOW_LINKS)) {
                    if (input.read() != '>') throw new IOException("CRAM reference closure requires an uncompressed local FASTA and its exact .fai");
                }
            } else if (cram) throw new RsemProcess.UnavailableDependency("CRAM input requires the exact permitted local FASTA and .fai. Supply them explicitly; skip dependent decoding/fitting while unavailable. No reference download or cache fallback is admitted.");
            check.run();
            var reference = RsemReference.read(stagedPrefix, limits.reference());
            bindReferenceEvidence(reference, spec.referencePrefix(), stagedPrefix, sources);
            check.run();
            var record = new LinkedHashMap<String,Object>();
            record.put("schema_version", 1); record.put("status", "SNAPSHOTTED_BYTES_AND_COMPLETE_ORDINARY_REFERENCE_ONLY");
            record.put("source_reference_prefix", spec.referencePrefix()); record.put("source_alignment", spec.alignment());
            record.put("source_cram_fasta", spec.cramFasta()); record.put("reference_prefix", stagedPrefix);
            record.put("alignment", alignment); record.put("cram_fasta", fasta); record.put("cram_magic", cram);
            record.put("parser_options", Map.of("release", spec.options().release(), "read_type", spec.options().readType(),
                    "read_type_tag", spec.options().readTypeTag(), "seed_length", spec.options().seedLength()));
            var pins = new TreeMap<String,Object>(); sources.forEach((path, value) -> pins.put(path.toString(), value));
            record.put("source_pins", pins); record.put("absent_source_companions", absent); record.put("limits", limitsDescription(limits));
            record.put("transcripts", reference.transcripts().size()); record.put("genes", reference.groups().size());
            record.put("scientific_alignment_admitted", false); record.put("public_fitting_workflow_available", false);
            record.put("scope", "All supplied alignment bytes remain unchanged, including BAM/CRAM. Complete ordinary reference metadata, sequence and grouping are audited. Decoder/reference lookup closure, alignment semantics and fitting/output completion are separate required gates. Not a signature or OS sandbox.");
            byte[] raw = RsemJson.bytes(record);
            if (raw.length > limits.metadataBytes() || raw.length > limits.fileBytes() || copied[0] + raw.length + RESERVE > limits.totalBytes())
                throw new IOException("Complete scientific-input metadata and diagnostic reserve exceed capacity");
            Files.write(home.resolve("inputs.json"), raw, StandardOpenOption.CREATE_NEW);
            var prepared = new Prepared(spec, home, stagedPrefix, alignment, fasta, reference, limits, sources, absent, inventory(home, limits, check));
            prepared.verify(check); return prepared;
        } catch (IOException | RuntimeException failure) {
            try {
                String message = failure.toString(); if (message.length() > 1024) message = message.substring(0, 1024);
                Files.writeString(home.resolve("INPUTS_FAILED.txt"), "RSEM INPUTS FAILED OR SKIPPED: no scientific alignment or fit admitted.\n" + message
                        + "\nRequired action: preserve this directory. Supply the named complete local files, correct integrity/format/capacity errors, and retry to NEW paths. If required installation or reference access is forbidden, skip dependent fitting.\n", StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { failure.addSuppressed(diagnostic); }
            throw failure;
        }
    }
    /** Bind the bytes actually consumed by native parsing, even if a changed file was restored later. */
    static void bindReferenceEvidence(RsemReference.Reference reference, Path originalPrefix, Path stagedPrefix, Map<Path,Pin> sources) throws IOException {
        if (reference.type() != 0 && reference.type() != 1) throw new IOException("Require an ordinary reference for scientific input binding");
        var expected = new TreeMap<Path,Pin>();
        for (String suffix : reference.type() == 0 ? List.of(".ti", ".seq", ".grp", ".chrlist") : List.of(".ti", ".seq", ".grp")) {
            Pin pin = sources.get(companion(originalPrefix, suffix));
            if (pin == null) throw new IOException("Native reference source lacks its exact copy pin: " + suffix);
            expected.put(companion(stagedPrefix, suffix), pin);
        }
        var seen = new HashSet<Path>();
        for (var evidence : reference.evidence()) {
            Path path = Path.of(evidence.path());
            if (!seen.add(path) || !new Pin(evidence.bytes(), evidence.sha256()).equals(expected.get(path)))
                throw new IOException("Native reference consumed bytes differ from the exact protected copy: " + path);
        }
        if (!seen.equals(expected.keySet())) throw new IOException("Incomplete native reference byte-evidence population");
    }
    private static Map<String,Object> limitsDescription(Limits limits) {
        var r = limits.reference();
        return Map.of("file_bytes", limits.fileBytes(), "total_bytes", limits.totalBytes(), "members", limits.members(), "metadata_bytes", limits.metadataBytes(),
                "reference", Map.of("transcripts", r.maxTranscripts(), "chromosomes", r.maxChromosomes(), "bases", r.maxBases(), "exons", r.maxExons(), "file_bytes", r.maxBytes(), "line_characters", r.maxLineChars()));
    }
    private static Path companion(Path prefix, String suffix) { return prefix.resolveSibling(prefix.getFileName() + suffix); }
    private static void canonicalParent(Path path) throws IOException {
        if (!path.isAbsolute() || !path.normalize().equals(path) || path.getParent() == null || !path.getParent().toRealPath().equals(path.getParent()))
            throw new IOException("Scientific inputs require canonical absolute paths and existing unlinked parents: " + path);
    }
    private static Pin pin(Path path, long maximum, Runnable check) throws IOException {
        canonicalParent(path); check.run();
        var before = attributes(path);
        if (!before.isRegularFile() || !path.toRealPath().equals(path) || before.size() > maximum)
            throw new IOException("RSEM scientific source is linked, nonregular or over capacity: " + path);
        String digest = RepeatInputs.hash(path, maximum, check);
        var after = attributes(path);
        if (!after.isRegularFile() || !path.toRealPath().equals(path) || before.size() != after.size()
                || !Objects.equals(before.fileKey(), after.fileKey()) || !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IOException("RSEM scientific source changed during complete hashing: " + path);
        return new Pin(after.size(), digest);
    }
    private static BasicFileAttributes attributes(Path path) throws IOException { return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
    private static void copy(Path source, Path target, Limits limits, long[] total, Map<Path,Pin> sources, Runnable check) throws IOException {
        Pin before = pin(source, limits.fileBytes(), check);
        if (before.bytes() > limits.totalBytes() - RESERVE - total[0]) throw new IOException("Complete scientific input copies exceed total capacity");
        long count = 0;
        try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
             var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = input.read(buffer)) != -1) {
                check.run(); count += n;
                if (count > before.bytes()) throw new IOException("Scientific input grew during protected copying: " + source);
                output.write(buffer, 0, n);
            }
        }
        if (count != before.bytes() || !before.equals(pin(source, limits.fileBytes(), check)) || !before.equals(pin(target, limits.fileBytes(), check)))
            throw new IOException("Scientific input changed during exact-byte copying: " + source);
        sources.put(source, before); total[0] += count;
    }
    private static Map<String,Member> inventory(Path home, Limits limits, Runnable check) throws IOException {
        canonicalParent(home);
        if (!Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS) || !home.toRealPath().equals(home)) throw new IOException("Linked or missing protected scientific input root");
        var result = new TreeMap<String,Member>(); long total = 0;
        try (var paths = Files.walk(home)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                check.run(); Path path = iterator.next(); var attributes = attributes(path);
                if (result.size() >= limits.members() || attributes.isSymbolicLink() || !path.toRealPath().equals(path)) throw new IOException("Protected scientific input membership/link capacity exceeded");
                String name = home.relativize(path).toString().replace('\\', '/');
                if (attributes.isDirectory()) result.put(name, new Member(true, 0, ""));
                else {
                    Pin item = pin(path, limits.fileBytes(), check); total = Math.addExact(total, item.bytes());
                    if (total + RESERVE > limits.totalBytes()) throw new IOException("Complete protected scientific input inventory exceeds total capacity");
                    result.put(name, new Member(false, item.bytes(), item.sha256()));
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
