/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only hard-call input for the LDAK kinship profile. FAM fields beyond
 * FID/IID are literal metadata: in particular, the official LDAK test data use
 * NA for sex. Kinship construction does not interpret or normalize those fields.
 * Only selected variant-major BED rows are decoded. BIM/FAM tokens are retained
 * unchanged; no pedigree, reference allele, ancestry or ploidy is inferred.
 */
public final class LdakBedDataset implements AutoCloseable {
    /** Metadata bounds are separate from LdakKinship's principal numeric buffers. */
    public record Limits(int maximumSamples, int maximumVariants, long maximumMetadataBytes,
            int maximumLineBytes) {
        public Limits {
            if (maximumSamples < 1 || maximumVariants < 1 || maximumMetadataBytes < 1 || maximumLineBytes < 16)
                throw new IllegalArgumentException("Positive BED metadata limits required; line limit at least 16");
        }
        public static Limits defaults() { return new Limits(100_000, 1_000_000, 512L * 1024 * 1024, 16384); }
    }
    /** All six original FAM fields, without conversion of NA or other tokens. */
    public record Sample(String familyId, String individualId, String fatherId,
            String motherId, String sex, String phenotype) { }
    /** Original BIM tokens and the zero-based BED row ordinal. */
    public record Variant(String chromosome, String id, String geneticDistance, String position,
            String allele1, String allele2, long ordinal) { }
    private final List<Sample> samples;
    private final List<Variant> variants;
    private final Set<Variant> admitted;
    private final FileChannel channel;
    private final int stride;
    private LdakBedDataset(List<Sample> samples, List<Variant> variants, FileChannel channel) {
        this.samples = List.copyOf(samples);
        this.variants = List.copyOf(variants);
        this.admitted = Set.copyOf(variants);
        this.channel = channel;
        this.stride = Math.toIntExact((samples.size() + 3L) / 4);
    }
    public List<Sample> samples() { return samples; }
    public List<Variant> selectedVariants() { return variants; }

    /** Null selects all BIM rows; a nonnull set must be nonempty and fully present. */
    public static LdakBedDataset open(Path prefix, Set<String> requested) throws IOException {
        return open(prefix, requested, Limits.defaults());
    }
    /** Structural checks are strict; input bytes and metadata are never rewritten. */
    public static LdakBedDataset open(Path prefix, Set<String> requested, Limits limits) throws IOException {
        Objects.requireNonNull(prefix, "BED prefix");
        Objects.requireNonNull(limits, "Metadata limits");
        Set<String> selection = requested == null ? null : Set.copyOf(requested);
        if (selection != null && (selection.isEmpty() || selection.size() > limits.maximumVariants()))
            throw new IllegalArgumentException("Admit a nonempty bounded predictor selection, or null for all");
        Path fam = Path.of(prefix + ".fam"), bim = Path.of(prefix + ".bim"), bed = Path.of(prefix + ".bed");
        for (Path path : List.of(fam, bim, bed)) if (!Files.isRegularFile(path)) throw new IOException("Missing input: " + path);
        if (Math.addExact(Files.size(fam), Files.size(bim)) > limits.maximumMetadataBytes())
            throw new IOException("FAM/BIM metadata-byte limit exceeded");
        var samples = new ArrayList<Sample>();
        var sampleIds = new HashSet<String>();
        try (var rows = new Lines(fam, limits.maximumLineBytes())) {
            for (String line; (line = rows.next()) != null;) {
                if (line.isBlank()) continue;
                String[] fields = tokens(line, "FAM");
                if (samples.size() == limits.maximumSamples()) throw new IOException("Sample metadata limit exceeded");
                if (!sampleIds.add(fields[0] + '\0' + fields[1])) throw new IOException("Duplicate exact FID/IID pair");
                samples.add(new Sample(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]));
            }
        }
        if (samples.isEmpty()) throw new IOException("No FAM samples");
        var variants = new ArrayList<Variant>();
        var variantIds = new HashSet<String>();
        long total = 0;
        try (var rows = new Lines(bim, limits.maximumLineBytes())) {
            for (String line; (line = rows.next()) != null;) {
                if (line.isBlank()) continue;
                String[] fields = tokens(line, "BIM");
                if (total == limits.maximumVariants()) throw new IOException("Variant metadata limit exceeded");
                if (!variantIds.add(fields[1])) throw new IOException("Duplicate BIM predictor ID: " + fields[1]);
                if (selection == null || selection.contains(fields[1]))
                    variants.add(new Variant(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], total));
                total++;
            }
        }
        if (variants.isEmpty()) throw new IOException("No selected BIM predictors");
        if (selection != null && !variantIds.containsAll(selection)) throw new IOException("Requested predictor IDs absent from BIM");
        long stride = (samples.size() + 3L) / 4;
        if (Files.size(bed) != Math.addExact(3L, Math.multiplyExact(stride, total)))
            throw new IOException("BED size differs from complete FAM/BIM dimensions");
        FileChannel channel = FileChannel.open(bed, StandardOpenOption.READ);
        try {
            ByteBuffer header = ByteBuffer.allocate(3);
            read(channel, header, 0);
            if (header.get(0) != 0x6c || header.get(1) != 0x1b || header.get(2) != 1)
                throw new IOException("Require variant-major PLINK BED magic and mode");
            return new LdakBedDataset(samples, variants, channel);
        } catch (IOException | RuntimeException error) {
            channel.close();
            throw error;
        }
    }
    /** BIM A1 counts 2/1/0; the PLINK missing code becomes NaN, never a call. */
    public double[] readAllele1Dosages(Variant variant) throws IOException {
        if (!admitted.contains(variant)) throw new IOException("Variant is not in the admitted selection");
        ByteBuffer bytes = ByteBuffer.allocate(stride);
        read(channel, bytes, Math.addExact(3L, Math.multiplyExact((long) stride, variant.ordinal())));
        int remainder = samples.size() % 4;
        if (remainder != 0 && (Byte.toUnsignedInt(bytes.get(stride - 1)) >>> (2 * remainder)) != 0)
            throw new IOException("Nonzero unused BED padding; input is not repaired");
        double[] dosage = new double[samples.size()];
        for (int i = 0; i < dosage.length; i++) {
            int code = (bytes.get(i / 4) >>> (2 * (i % 4))) & 3;
            dosage[i] = switch (code) { case 0 -> 2; case 2 -> 1; case 3 -> 0; default -> Double.NaN; };
        }
        return dosage;
    }
    @Override public void close() throws IOException { channel.close(); }
    private static void read(FileChannel channel, ByteBuffer target, long position) throws IOException {
        while (target.hasRemaining()) {
            int count = channel.read(target, position + target.position());
            if (count <= 0) throw new EOFException("BED ended while reading an admitted row");
        }
    }
    private static String[] tokens(String line, String kind) throws IOException {
        for (int i = 0; i < line.length(); i++)
            if (line.charAt(i) == '\0' || line.charAt(i) == '\ufeff' || (Character.isISOControl(line.charAt(i)) && line.charAt(i) != '\t'))
                throw new IOException("Invalid control/BOM character in " + kind);
        String[] result = line.trim().split("\\s+");
        if (result.length != 6) throw new IOException("Require exactly six " + kind + " fields");
        return result;
    }
    private static final class Lines implements AutoCloseable {
        private final BufferedInputStream input;
        private final int limit;
        Lines(Path path, int limit) throws IOException { input = new BufferedInputStream(Files.newInputStream(path)); this.limit = limit; }
        String next() throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int value;
            while ((value = input.read()) != -1 && value != '\n') {
                if (output.size() == limit) throw new IOException("FAM/BIM line-byte limit exceeded");
                output.write(value);
            }
            if (value == -1 && output.size() == 0) return null;
            byte[] raw = output.toByteArray();
            int length = raw.length != 0 && raw[raw.length - 1] == '\r' ? raw.length - 1 : raw.length;
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw, 0, length)).toString();
        }
        @Override public void close() throws IOException { input.close(); }
    }
}
