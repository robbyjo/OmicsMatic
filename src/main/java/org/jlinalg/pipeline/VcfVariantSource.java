/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.pipeline;

import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCFVersion;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFIterator;
import htsjdk.variant.vcf.VCFIteratorBuilder;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Streaming VCF, BGZF VCF, or BCF additive dosage source backed by HTSJDK.
 *
 * <p>Header-declared {@code FORMAT/DS} is preferred. Missing DS falls back to
 * called {@code GT}. Multiallelic records produce one alternate-allele dosage
 * row per ALT allele.</p>
 */
public final class VcfVariantSource implements VariantSource {
    private final Path path;
    private final DataFormat format;
    private final VariantSourceMetadata metadata;

    public VcfVariantSource(Path path, DataFormat format) throws IOException {
        if (format != DataFormat.VCF && format != DataFormat.VCF_GZ
                && format != DataFormat.BCF) {
            throw new IllegalArgumentException(
                "VCF source requires VCF, VCF_GZ, or BCF format");
        }
        this.path = path.toAbsolutePath().normalize();
        this.format = format;
        try (Cursor cursor = cursor()) {
            metadata = new VariantSourceMetadata(this.path, -1,
                cursor.header().getGenotypeSamples(), format);
        }
    }

    public static VcfVariantSource open(Path path) throws IOException {
        return new VcfVariantSource(path, DataFormat.infer(path));
    }

    @Override public VariantSourceMetadata metadata() { return metadata; }

    @Override
    public VariantBlockReader open(int[] requestedOrder) throws IOException {
        int[] order = DelimitedVariantSource.normalizeOrder(requestedOrder,
            metadata.sampleIds().size());
        return new Reader(cursor(), order);
    }

    private Cursor cursor() throws IOException {
        if (format == DataFormat.BCF) return new BcfCursor(path);
        return new TextCursor(path);
    }

    private final class Reader implements VariantBlockReader {
        private final Cursor cursor;
        private final int[] order;
        private final ArrayDeque<VariantRecord> pending = new ArrayDeque<>();
        private long sourceIndex;
        private boolean closed;

        private Reader(Cursor cursor, int[] order) {
            this.cursor = cursor;
            this.order = order;
        }

        @Override
        public VariantBlock read(int maximumVariants) throws IOException {
            if (maximumVariants < 1)
                throw new IllegalArgumentException("maximum variants must be positive");
            if (closed) throw new IOException("variant reader is closed");
            List<VariantRecord> result = new ArrayList<>(maximumVariants);
            long first = sourceIndex;
            while (result.size() < maximumVariants) {
                if (!pending.isEmpty()) {
                    result.add(pending.removeFirst());
                    sourceIndex++;
                    continue;
                }
                if (!cursor.hasNext()) break;
                expand(cursor.next(), order, pending);
            }
            return result.isEmpty() ? null : new VariantBlock(first, result);
        }

        @Override public void close() throws IOException {
            if (!closed) {
                closed = true;
                cursor.close();
            }
        }
    }

    private static void expand(
            VariantContext variant, int[] order,
            ArrayDeque<VariantRecord> output) {
        List<Allele> alternates = variant.getAlternateAlleles();
        String baseId = variant.hasID() ? variant.getID()
            : variant.getContig() + ":" + variant.getStart()
                + ":" + variant.getReference().getDisplayString();
        for (int alternateIndex = 0;
                alternateIndex < alternates.size(); alternateIndex++) {
            Allele alternate = alternates.get(alternateIndex);
            double[] dosages = new double[order.length];
            byte[] ploidies = new byte[order.length];
            boolean fromDosage=false,fromCalls=false;
            for (int destination = 0; destination < order.length; destination++) {
                Genotype genotype = variant.getGenotype(order[destination]);
                Double ds=genotype==null?null:dosageAttribute(genotype.getAnyAttribute("DS"),alternateIndex,alternates.size());
                dosages[destination] = ds!=null?ds:dosage(genotype, alternate, alternateIndex,alternates.size());
                if(ds!=null)fromDosage=true;else if(genotype!=null)fromCalls=true;
                int ploidy=genotype==null?0:genotype.getPloidy();
                // A lone missing GT '.' does not establish haploidy.
                if(ploidy==1&&genotype.getAlleles().stream().allMatch(Allele::isNoCall))ploidy=0;
                if(ploidy>63)throw new IllegalArgumentException("VCF ploidy exceeds 63");
                ploidies[destination]=(byte)ploidy;
            }
            String id = alternates.size() == 1 ? baseId
                : baseId + ":" + alternate.getDisplayString();
            output.add(new VariantRecord(id, variant.getContig(),
                variant.getStart(), variant.getReference().getDisplayString(),
                alternate.getDisplayString(), dosages, quality(variant,alternateIndex,alternates.size()),
                new VariantGenotypeMetadata(fromDosage?(fromCalls?VariantGenotypeMetadata.Origin.MIXED:VariantGenotypeMetadata.Origin.DOSAGE):VariantGenotypeMetadata.Origin.HARD_CALL,alternates.size()!=1,ploidies)));
        }
    }

    private static double dosage(
            Genotype genotype, Allele alternate, int alternateIndex,
            int alternateCount) {
        if (genotype == null) return Double.NaN;
        Object raw = genotype.getAnyAttribute("DS");
        Double dosage = dosageAttribute(raw, alternateIndex, alternateCount);
        if (dosage != null) return dosage;
        if (!genotype.isAvailable() || !genotype.isCalled()) return Double.NaN;
        int count = 0;
        for (Allele allele : genotype.getAlleles()) {
            if (allele.isNoCall()) return Double.NaN;
            if (allele.equals(alternate, true)) count++;
        }
        return count;
    }

    private static Double dosageAttribute(
            Object raw, int alternateIndex, int alternateCount) {
        if (raw == null) return null;
        if (raw instanceof Number number)
            return alternateIndex == 0 ? finite(number.doubleValue()) : null;
        if (raw instanceof List<?> list) {
            if (alternateIndex >= list.size()) return null;
            return parseDosage(list.get(alternateIndex).toString());
        }
        String text = raw.toString().trim();
        if (text.isEmpty() || text.equals(".")) return null;
        String[] values = text.split(",", -1);
        if (alternateIndex >= values.length) {
            return alternateCount == 1 ? parseDosage(text) : null;
        }
        return parseDosage(values[alternateIndex]);
    }

    private static Double parseDosage(String value) {
        try {
            if (value == null || value.isBlank() || value.equals(".")) return null;
            return finite(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Double finite(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private static double quality(VariantContext variant,int alternateIndex,int alternateCount) {
        for (String key : List.of("R2", "INFO", "DR2")) {
            if (!variant.hasAttribute(key)) continue;
            Object raw=variant.getAttribute(key);
            if(raw==null)continue;
            List<?> values=raw instanceof List<?> list?list:List.of(raw.toString().split(",",-1));
            if(values.size()!=1&&values.size()!=alternateCount)continue;
            Double value=parseDosage(values.get(values.size()==1?0:alternateIndex).toString());
            if (value!=null && value >= 0 && value <= 1) return value;
        }
        return Double.NaN;
    }

    private interface Cursor extends AutoCloseable {
        VCFHeader header();
        boolean hasNext();
        VariantContext next();
        @Override void close() throws IOException;
    }

    private static final class TextCursor implements Cursor {
        private final VCFIterator iterator;
        private TextCursor(Path path) throws IOException {
            iterator = new VCFIteratorBuilder().open(path);
        }
        @Override public VCFHeader header() { return iterator.getHeader(); }
        @Override public boolean hasNext() { return iterator.hasNext(); }
        @Override public VariantContext next() { return iterator.next(); }
        @Override public void close() { iterator.close(); }
    }

    private static final class BcfCursor implements Cursor {
        private final Bcf21Or22Codec codec=new Bcf21Or22Codec();
        private final PositionalBufferedStream source;
        private final VCFHeader header;

        private BcfCursor(Path path) throws IOException {
            var raw=new BufferedInputStream(Files.newInputStream(path),65536);
            PositionalBufferedStream opened=null;
            try{
                raw.mark(2);int a=raw.read(),b=raw.read();raw.reset();
                // Standard BCF is BGZF-compressed despite using the plain .bcf suffix.
                // Decode compression by magic; suffix-based FeatureReader selection
                // otherwise passes the gzip header directly to the BCF codec.
                opened=codec.makeSourceFromStream(a==31&&b==139?new GZIPInputStream(raw,65536):raw);source=opened;
                Object value=codec.readHeader(source).getHeaderValue();
                if(!(value instanceof VCFHeader parsed))throw new IOException("BCF header is missing or invalid: "+path);
                header=parsed;
            }catch(IOException|RuntimeException invalid){if(opened!=null)codec.close(opened);else try{raw.close();}catch(IOException close){invalid.addSuppressed(close);}throw invalid;}
        }
        @Override public VCFHeader header() { return header; }
        @Override public boolean hasNext() { return !codec.isDone(source); }
        @Override public VariantContext next() { return codec.decode(source); }
        @Override public void close() throws IOException {
            codec.close(source);
        }
    }

    /** HTSJDK decodes the BCF2 record layout used by both BCF 2.1 and 2.2. */
    private static final class Bcf21Or22Codec extends BCF2Codec {
        @Override
        protected void validateVersionCompatibility(
                BCFVersion supported, BCFVersion actual) {
            if (actual.getMajorVersion() != 2
                    || (actual.getMinorVersion() != 1
                        && actual.getMinorVersion() != 2)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "BCF 2.1 or 2.2 is required; found %d.%d",
                    actual.getMajorVersion(), actual.getMinorVersion()));
            }
        }
    }
}
