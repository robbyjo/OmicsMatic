/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Complete, streaming, lossless views of the original Sniffles 1 and 2 VCF text.
 * Does not call, merge, phase, normalize, repair, or discard structural variants.
 * Source header declarations (including legacy duplicate/inaccurate declarations),
 * symbolic/breakend alleles, missing values and original sample order are retained.
 */
public final class SnifflesVcf {
    private SnifflesVcf() { }

    public enum Edition {
        V1_0_7("1.0.7", "RE"), V2_0_2("2.0.2", "SUPPORT"),
        V2_0_7("2.0.7", "SUPPORT"), V2_3_2("2.3.2", "SUPPORT"), V2_4("2.4", "SUPPORT");
        private final String version;
        private final String support;
        Edition(String version, String support) { this.version = version; this.support = support; }
        public String version() { return version; }
        /** Supporting reads, not a substitute for an unspecified paper's read depth. */
        public String readSupportInfoField() { return support; }
    }

    public record Limits(long maximumBytes, long maximumRecords, int maximumLineCharacters,
                         int maximumHeaderLines, int maximumSamples) {
        public Limits {
            if (maximumBytes < 1 || maximumRecords < 1 || maximumLineCharacters < 128
                    || maximumHeaderLines < 1 || maximumSamples < 1)
                throw new IllegalArgumentException("Positive complete-VCF capacities are required");
        }
        public static Limits bounded() { return new Limits(268435456L, 1000000L, 16777216, 100000, 10000); }
    }

    /** A null value represents an INFO flag; the literal dot remains a string. */
    public record Info(String key, String value) {
        public Info { Objects.requireNonNull(key); }
    }

    public record Header(List<String> metadataLines, String columnLine, List<String> samples) {
        public Header { metadataLines = List.copyOf(metadataLines); samples = List.copyOf(samples); }
    }

    public record Variant(long row, String rawLine, List<String> columns, List<Info> info,
                          List<String> format, List<List<String>> samples) {
        public Variant {
            columns = List.copyOf(columns); info = List.copyOf(info); format = List.copyOf(format);
            samples = samples.stream().map(List::copyOf).toList();
        }
        public String chromosome() { return columns.get(0); }
        public long position() { return Long.parseLong(columns.get(1)); }
        public String id() { return columns.get(2); }
        public String reference() { return columns.get(3); }
        public String alternate() { return columns.get(4); }
        public String filter() { return columns.get(6); }
        public boolean passes() { return filter().equals("PASS"); }
        public String infoValue(String key) throws IOException {
            String found = null; boolean seen = false;
            for (Info field : info) if (field.key().equals(key)) {
                require(!seen, "Ambiguous repeated INFO field " + key + " at row " + row);
                found = field.value(); seen = true;
                require(found != null, "INFO " + key + " is a flag, not a scalar value at row " + row);
            }
            return found;
        }
        /** Missing or absent remains empty; zero is not imputed. */
        public OptionalLong infoInteger(String key) throws IOException { return integer(infoValue(key), "INFO " + key); }
        public OptionalLong readSupport(Edition edition) throws IOException {
            OptionalLong value = infoInteger(edition.readSupportInfoField());
            require(value.isEmpty() || value.getAsLong() >= 0, "Negative read support at row " + row);
            return value;
        }
        /** Returns null for an absent key or omitted trailing value; retains literal dots. */
        public String sampleValue(int sample, String key) {
            int index = format.indexOf(key);
            return index < 0 || index >= samples.get(sample).size() ? null : samples.get(sample).get(index);
        }
        public OptionalLong sampleInteger(int sample, String key) throws IOException {
            return integer(sampleValue(sample, key), "FORMAT " + key);
        }
    }

    public record Summary(Header header, long records, Map<String, Long> variantTypes,
                          long passRecords, long supportPresent, long supportMissing,
                          long phasedGenotypes, long phaseInfoRecords) {
        public Summary { variantTypes = Collections.unmodifiableMap(new LinkedHashMap<>(variantTypes)); }
    }

    @FunctionalInterface public interface Sink<T> { void accept(T value) throws IOException; }

    /**
     * Reads plain UTF-8 VCF. The owner must decompress externally with its own
     * encoded/decoded bounds. Sinks stay provisional until this method returns;
     * a workflow must additionally admit its whole original-process batch.
     * This validates textual structure and named support values, not SV biology
     * or every legacy metadata declaration's compliance with the VCF standard.
     */
    public static Summary read(InputStream input, Edition edition, Limits limits,
                               Sink<Header> headerSink, Sink<Variant> sink) throws IOException {
        Objects.requireNonNull(edition); Objects.requireNonNull(limits);
        Objects.requireNonNull(headerSink); Objects.requireNonNull(sink);
        long count = 0, pass = 0, support = 0, missing = 0, phased = 0, phaseInfo = 0;
        Map<String, Long> types = new LinkedHashMap<>();
        Header header = null;
        List<String> metadata = new ArrayList<>();
        try (Lines lines = new Lines(input, limits)) {
            for (String line; (line = lines.next()) != null;) {
                require(!line.isEmpty(), "Empty VCF line");
                if (header == null) {
                    if (line.startsWith("##")) {
                        require(metadata.size() < limits.maximumHeaderLines(), "VCF header-line capacity exceeded");
                        metadata.add(line); continue;
                    }
                    require(!metadata.isEmpty() && metadata.get(0).equals("##fileformat=VCFv4.2"), "Require original VCFv4.2 header");
                    String[] fields = line.split("\t", -1);
                    String[] fixed = {"#CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO", "FORMAT"};
                    require(fields.length >= 10 && fields.length - 9 <= limits.maximumSamples(), "Require explicit bounded genotype/sample columns");
                    for (int i = 0; i < fixed.length; i++) require(fields[i].equals(fixed[i]), "Unexpected VCF column " + i);
                    List<String> samples = new ArrayList<>();
                    for (int i = 9; i < fields.length; i++) {
                        require(!fields[i].isEmpty(), "Empty VCF sample identifier"); samples.add(fields[i]);
                    }
                    // Retain duplicate sample labels positionally; no identifier repair.
                    header = new Header(metadata, line, samples); headerSink.accept(header); continue;
                }
                require(!line.startsWith("#") && ++count <= limits.maximumRecords(), "Unexpected header or VCF record capacity exceeded");
                String[] fields = line.split("\t", -1);
                require(fields.length == header.samples().size() + 9, "VCF sample-column count changed at row " + count);
                for (String field : fields) require(!field.isEmpty(), "Empty VCF cell at row " + count);
                require(fields[1].matches("[0-9]+"), "Noninteger VCF position at row " + count);
                try { Long.parseLong(fields[1]); } catch (NumberFormatException failure) { throw new IOException("VCF position exceeds capacity", failure); }
                List<Info> info = new ArrayList<>();
                if (!fields[7].equals(".")) for (String token : fields[7].split(";", -1)) {
                    int separator = token.indexOf('=');
                    String key = separator < 0 ? token : token.substring(0, separator);
                    require(!key.isEmpty(), "Empty VCF INFO key at row " + count);
                    info.add(new Info(key, separator < 0 ? null : token.substring(separator + 1)));
                }
                List<String> format = List.of(fields[8].split(":", -1));
                require(!format.contains("") && new HashSet<>(format).size() == format.size(), "Empty or duplicate FORMAT key at row " + count);
                List<List<String>> sampleValues = new ArrayList<>();
                for (int i = 9; i < fields.length; i++) {
                    List<String> values = List.of(fields[i].split(":", -1));
                    // Original v2 phased output can omit a trailing PS value.
                    // VCF permits trailing omissions; preserve them without adding dots.
                    require(values.size() <= format.size() && !values.contains(""), "Too many or empty FORMAT values at row " + count);
                    sampleValues.add(values);
                }
                Variant variant = new Variant(count, line, List.of(fields), info, format, sampleValues);
                String type = variant.infoValue("SVTYPE");
                require(type != null && !type.isEmpty() && !type.equals("."), "Missing SVTYPE at row " + count);
                types.merge(type, 1L, Long::sum);
                if (variant.passes()) pass++;
                if (variant.readSupport(edition).isPresent()) support++; else missing++;
                if (variant.infoValue("PHASE") != null) phaseInfo++;
                for (int sample = 0; sample < sampleValues.size(); sample++) {
                    String gt = variant.sampleValue(sample, "GT");
                    if (gt != null && gt.contains("|")) phased++;
                }
                sink.accept(variant);
            }
        }
        require(header != null, "VCF ended before complete header");
        return new Summary(header, count, types, pass, support, missing, phased, phaseInfo);
    }

    private static OptionalLong integer(String value, String name) throws IOException {
        if (value == null || value.equals(".")) return OptionalLong.empty();
        require(value.matches("-?[0-9]+"), "Expected scalar integer " + name + "; retained value: " + value);
        try { return OptionalLong.of(Long.parseLong(value)); }
        catch (NumberFormatException failure) { throw new IOException("Integer capacity exceeded for " + name, failure); }
    }
    private static void require(boolean condition, String detail) throws IOException { if (!condition) throw new IOException(detail); }

    private static final class Bounded extends FilterInputStream {
        private final long maximum; private long count;
        Bounded(InputStream input, long maximum) { super(Objects.requireNonNull(input)); this.maximum = maximum; }
        private void add(int read) throws IOException { if (read > 0) { count = Math.addExact(count, read); require(count <= maximum, "Complete VCF byte capacity exceeded"); } }
        @Override public int read() throws IOException { int value = in.read(); if (value >= 0) add(1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { int read = in.read(bytes, offset, length); add(read); return read; }
    }
    private static final class Lines implements AutoCloseable {
        private final BufferedReader reader; private final Limits limits;
        Lines(InputStream input, Limits limits) {
            this.limits = limits;
            reader = new BufferedReader(new InputStreamReader(new Bounded(input, limits.maximumBytes()),
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)), 65536);
        }
        String next() throws IOException {
            StringBuilder text = new StringBuilder();
            for (;;) {
                int ch = reader.read();
                if (ch < 0) { require(text.length() == 0, "Unterminated final VCF line"); return null; }
                if (ch == '\n') {
                    if (text.length() > 0 && text.charAt(text.length() - 1) == '\r') text.setLength(text.length() - 1);
                    return text.toString();
                }
                require(ch != 0 && text.length() < limits.maximumLineCharacters(), "NUL or VCF line capacity exceeded");
                text.append((char) ch);
            }
        }
        @Override public void close() throws IOException { reader.close(); }
    }
}
