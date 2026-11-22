/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded, read-only access to selected variants in a variant-major PLINK BED set.
 *
 * <p>Sample identity is the exact case-sensitive FID/IID pair. All FAM/BIM rows
 * are structurally checked, but only requested variant metadata is retained and
 * only selected BED rows are decoded. Selected IDs must be unique and present.
 * Genotypes count BIM A1: 2,1,0, or NaN for missing. No ploidy, allele complementation,
 * reference assembly, relatedness or phenotype model is inferred. Callers must
 * preserve input files during use and apply their analysis-specific contracts.</p>
 */
public final class PlinkBedDataset implements AutoCloseable {
    /** Resolved effects by default; QC may inspect raw calls with missing allele labels. */
    public enum AllelePolicy { REQUIRE_RESOLVED, QC_ALLOW_MISSING }
    /** Metadata and allocation/work admission bounds; retained bytes are an estimate. */
    public record Limits(int maximumSamples, long maximumVariants, int maximumSelectedVariants,
            long maximumMetadataBytes, long maximumEstimatedRetainedBytes, int maximumLineBytes) {
        /** Constructs positive metadata limits. */
        public Limits {
            if (maximumSamples < 1 || maximumVariants < 1 || maximumSelectedVariants < 1
                    || maximumMetadataBytes < 1 || maximumEstimatedRetainedBytes < 1 || maximumLineBytes < 16)
                throw new IllegalArgumentException("BED metadata limits must be positive; line limit must be at least 16 bytes");
        }
        /** Defaults to bounded large-cohort metadata; selected rows are streamed separately. */
        public static Limits defaults() {
            return new Limits(1_000_000, 50_000_000, 1_000_000,
                8L * 1_073_741_824, 256L * 1_048_576, 16_384);
        }
    }

    /** Exact sample identifiers and the recorded FAM founder/sex fields. */
    public record Sample(String familyId, String individualId, boolean founder, int sex) { }
    /** Selected BIM metadata and zero-based BED variant-row ordinal. */
    public record Variant(String chromosome, String id, long position, String allele1,
            String allele2, long ordinal) {
        /** Whether both allele labels resolve to distinct nonmissing codes. */
        public boolean resolvedAlleles() { return !Set.of("0",".").contains(allele1) && !Set.of("0",".").contains(allele2) && !allele1.equalsIgnoreCase(allele2); }
    }
    private record SampleId(String family, String individual) { }
    private final Path bedPath;
    private final List<Sample> samples;
    private final Map<String,Variant> selected;
    private final long variants;
    private final int bytesPerVariant;
    private final FileChannel channel;

    private PlinkBedDataset(Path bedPath, List<Sample> samples, Map<String,Variant> selected,
            long variants, FileChannel channel) {
        this.bedPath = bedPath; this.samples = List.copyOf(samples);
        this.selected = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(selected));
        this.variants = variants; this.bytesPerVariant = (int) ((samples.size() + 3L) / 4); this.channel = channel;
    }

    /** Opens selected variants with default metadata bounds. */
    public static PlinkBedDataset open(Path prefix, Set<String> requested) throws IOException {
        return open(prefix, requested, Limits.defaults());
    }

    /** Opens a BED/BIM/FAM prefix with explicit limits and without modifying any input. */
    public static PlinkBedDataset open(Path prefix, Set<String> requested, Limits limits) throws IOException {
        return open(prefix,requested,limits,AllelePolicy.REQUIRE_RESOLVED);
    }

    /**
     * Opens explicit selected metadata under an allele admission policy. QC_ALLOW_MISSING
     * admits 0/dot allele labels for raw call inspection only; dosage interpretation still
     * requires resolved alleles. Identical nonmissing alleles remain invalid. The ordinary
     * scoring/association overloads preserve their resolved-allele requirement.
     */
    public static PlinkBedDataset open(Path prefix, Set<String> requested, Limits limits, AllelePolicy policy) throws IOException {
        Objects.requireNonNull(prefix, "BED prefix"); Objects.requireNonNull(requested, "Requested variants");
        Objects.requireNonNull(limits, "BED limits");Objects.requireNonNull(policy,"Allele admission policy");
        if (requested.size() > limits.maximumSelectedVariants()) throw new IOException("Selected-variant limit exceeded");
        for (String id : requested) token(id, "requested variant ID");
        Set<String> ids = Set.copyOf(requested);
        Path bed = Path.of(prefix + ".bed"), bim = Path.of(prefix + ".bim"), fam = Path.of(prefix + ".fam");
        for (Path path : List.of(bed,bim,fam)) if (!Files.isRegularFile(path)) throw new IOException("Missing BED-set input: " + path);
        long metadataBytes;
        try { metadataBytes = Math.addExact(Files.size(bim),Files.size(fam)); }
        catch (ArithmeticException overflow) { throw new IOException("BED metadata byte count overflow",overflow); }
        if (metadataBytes > limits.maximumMetadataBytes()) throw new IOException("BED metadata input-byte limit exceeded");
        var samples = new ArrayList<Sample>(); var unique = new HashSet<SampleId>(); long retained = 0;
        try (var lines = new Lines(fam,limits.maximumLineBytes())) {
            for (String line; (line = lines.next()) != null;) {
                if (line.isBlank()) continue;
                String[] fields = fields(line,"FAM");
                if (samples.size() >= limits.maximumSamples()) throw new IOException("BED sample limit exceeded");
                SampleId id = new SampleId(fields[0],fields[1]);
                if (!unique.add(id)) throw new IOException("Duplicate FID/IID in FAM: " + id);
                if (fields[1].equals("0")) throw new IOException("FAM individual ID must not be 0");
                int sex;
                try { sex = Integer.parseInt(fields[4]); }
                catch (NumberFormatException error) { throw new IOException("Invalid FAM sex field",error); }
                if (sex < 0 || sex > 2) throw new IOException("FAM sex field must be 0,1 or 2; ploidy is not inferred");
                retained = retained(retained,line,256,limits);
                samples.add(new Sample(fields[0],fields[1],fields[2].equals("0") && fields[3].equals("0"),sex));
            }
        }
        if (samples.isEmpty()) throw new IOException("FAM contains no samples");
        var selected = new LinkedHashMap<String,Variant>(); long count = 0;
        try (var lines = new Lines(bim,limits.maximumLineBytes())) {
            for (String line; (line = lines.next()) != null;) {
                if (line.isBlank()) continue;
                String[] fields = fields(line,"BIM");
                if (count >= limits.maximumVariants()) throw new IOException("BIM variant limit exceeded");
                long position;
                try {
                    if (!Double.isFinite(Double.parseDouble(fields[2]))) throw new NumberFormatException("nonfinite genetic distance");
                    position = Long.parseLong(fields[3]);
                } catch (NumberFormatException error) { throw new IOException("Invalid BIM genetic/bp position for " + fields[1],error); }
                if (ids.contains(fields[1])) {
                    if (position < 0) throw new IOException("Selected BIM variant has negative bp position: " + fields[1]);
                    if (selected.containsKey(fields[1])) throw new IOException("Selected variant ID is duplicated in BIM: " + fields[1]);
                    boolean missingAllele=Set.of("0",".").contains(fields[4]) || Set.of("0",".").contains(fields[5]);
                    if (missingAllele && policy==AllelePolicy.REQUIRE_RESOLVED || !missingAllele && fields[4].equalsIgnoreCase(fields[5]))
                        throw new IOException("Selected variant requires two distinct resolved BIM alleles: " + fields[1]);
                    retained = retained(retained,line,384,limits);
                    selected.put(fields[1],new Variant(fields[0],fields[1],position,fields[4],fields[5],count));
                }
                count++;
            }
        }
        if (count == 0) throw new IOException("BIM contains no variants");
        if (selected.size() != ids.size()) throw new IOException("Requested variants absent from BIM: "
            + ids.stream().filter(id -> !selected.containsKey(id)).sorted().limit(10).toList());
        long expected;
        try { expected = Math.addExact(3,Math.multiplyExact(count,(samples.size() + 3L) / 4)); }
        catch (ArithmeticException overflow) { throw new IOException("BED dimension overflow",overflow); }
        if (Files.size(bed) != expected) throw new IOException("BED size does not match BIM/FAM dimensions; expected " + expected + " bytes");
        FileChannel channel = FileChannel.open(bed,StandardOpenOption.READ);
        try {
            ByteBuffer header = ByteBuffer.allocate(3); read(channel,header,0);
            if (!Arrays.equals(header.array(),new byte[] {(byte)0x6c,(byte)0x1b,1}))
                throw new IOException("Require variant-major PLINK BED header 6c 1b 01");
            return new PlinkBedDataset(bed,samples,selected,count,channel);
        } catch (IOException | RuntimeException error) { channel.close(); throw error; }
    }

    private static long retained(long previous, String line, int overhead, Limits limits) throws IOException {
        long next;
        try { next = Math.addExact(previous,Math.addExact(overhead,2L * line.getBytes(StandardCharsets.UTF_8).length)); }
        catch (ArithmeticException overflow) { throw new IOException("Estimated retained metadata overflow",overflow); }
        if (next > limits.maximumEstimatedRetainedBytes()) throw new IOException("Estimated retained BED metadata limit exceeded");
        return next;
    }

    private static String[] fields(String line, String kind) throws IOException {
        String[] result = line.strip().split("[ \\t]+");
        if (result.length != 6) throw new IOException(kind + " requires exactly six whitespace-delimited fields");
        for (String value : result) token(value,kind + " field");
        return result;
    }

    private static void token(String value, String label) throws IOException {
        if (value == null || value.isEmpty()) throw new IOException("Empty " + label);
        for (int index = 0; index < value.length(); index++)
            if (Character.isISOControl(value.charAt(index)) || Character.isWhitespace(value.charAt(index)) || Character.isSpaceChar(value.charAt(index)))
                throw new IOException("Control or whitespace inside " + label);
    }

    /** Ordered, immutable FAM sample records. */
    public List<Sample> samples() { return samples; }
    /** Total BIM/BED variant rows, including unselected rows. */
    public long variantCount() { return variants; }
    /** Selected metadata in BIM order, with exact original allele labels. */
    public List<Variant> selectedVariants() { return List.copyOf(selected.values()); }
    /** Native genotype input path; reading never changes it. */
    public Path bedPath() { return bedPath; }

    /**
     * Decodes one admitted selected variant. Genotypes count BIM A1; missing is
     * NaN. Unused high padding bits must be zero. The caller owns the returned array.
     */
    public double[] readAllele1Dosages(Variant variant) throws IOException {
        byte[] packed=readPackedRow(variant);
        if (!variant.resolvedAlleles()) throw new IOException("Cannot interpret allele-1 dosage for unresolved QC metadata; inspect raw packed call codes: "+variant.id());
        double[] result = new double[samples.size()];
        for (int sample = 0; sample < result.length; sample++) {
            int code = (packed[sample / 4] >>> (2 * (sample % 4))) & 3;
            result[sample] = switch (code) { case 0 -> 2; case 2 -> 1; case 3 -> 0; default -> Double.NaN; };
        }
        return result;
    }

    /**
     * Reads one selected variant's original packed bytes, validating unused padding.
     * The caller owns the array. Codes 00/10/11 are calls and 01 is missing; an
     * unresolved allele label does not establish a biological dosage interpretation.
     */
    public byte[] readPackedRow(Variant variant) throws IOException {
        Objects.requireNonNull(variant,"Selected variant");
        if (!variant.equals(selected.get(variant.id()))) throw new IOException("Variant is not in the admitted selected metadata");
        ByteBuffer packed = ByteBuffer.allocate(bytesPerVariant);
        read(channel,packed,Math.addExact(3,Math.multiplyExact(variant.ordinal(),bytesPerVariant)));
        int remainder = samples.size() % 4;
        if (remainder != 0 && ((packed.array()[bytesPerVariant - 1] & 0xff) >>> (2 * remainder)) != 0)
            throw new IOException("Nonzero unused BED padding bits at selected variant " + variant.id());
        return packed.array();
    }

    private static void read(FileChannel channel, ByteBuffer destination, long offset) throws IOException {
        while (destination.hasRemaining()) {
            int count = channel.read(destination,offset + destination.position());
            if (count < 0) throw new EOFException("Truncated selected BED data");
        }
    }

    /** Closes this read-only BED handle. */
    @Override public void close() throws IOException { channel.close(); }

    private static final class Lines implements AutoCloseable {
        private final InputStream input;
        private final int maximum;
        Lines(Path path, int maximum) throws IOException { input = new BufferedInputStream(Files.newInputStream(path)); this.maximum = maximum; }
        String next() throws IOException {
            var bytes = new ByteArrayOutputStream(); int value;
            while ((value = input.read()) != -1 && value != '\n') {
                if (bytes.size() == maximum) throw new IOException("BED metadata line-byte limit exceeded");
                bytes.write(value);
            }
            if (value == -1 && bytes.size() == 0) return null;
            byte[] raw = bytes.toByteArray(); int length = raw.length;
            if (length > 0 && raw[length - 1] == '\r') length--;
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,length)).toString();
        }
        @Override public void close() throws IOException { input.close(); }
    }
}
