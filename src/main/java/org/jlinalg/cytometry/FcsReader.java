/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Streaming stored-channel reader for one complete list-mode FCS 2.0, 3.0 or 3.1
 * data set. Supports unsigned byte-aligned integers up to 32 bits and IEEE F/D.
 * Integer unused bits are masked as required by FCS; floating values are never
 * clipped to PnR. No gain, antilog, time, display or compensation transform is
 * inferred or applied. Metadata values and parameter names retain their spelling.
 * This profile is not a claim of full FCS-standard or vendor-workspace support.
 */
public final class FcsReader implements AutoCloseable {
    /** Explicit vendor TEXT-space-padding support; the original file/segment offsets remain unchanged. */
    public enum TextPolicy { EXACT, ALLOW_TRAILING_SPACE_PADDING }
    /** Explicit numeric-token formatting interpretation; names and original metadata remain unchanged. */
    public enum NumericPolicy { EXACT, ALLOW_ASCII_SPACE_PADDING }
    /** A capability outside this reader profile; callers should skip the dependent analysis. */
    public static final class UnsupportedFormatException extends IOException {
        private static final long serialVersionUID = 1L;
        public UnsupportedFormatException(String message) { super(message); }
    }
    /** Limits refuse the whole input; they never select a prefix of events. */
    public record Limits(long fileBytes, long events, int parameters, int textBytes) {
        public Limits {
            if (fileBytes < 58 || events < 0 || parameters < 1 || parameters > 65536 || textBytes < 2)
                throw new IllegalArgumentException("Invalid complete FCS capacities");
        }
        public static Limits defaults() { return new Limits(68_719_476_736L, 100_000_000L, 4096, 16_777_216); }
    }
    public record Parameter(int index, String name, String label, int bits, String range,
                            String amplification, String gain) { }
    /** Original keyword spelling plus exact decoded value. Lookup is separately case insensitive. */
    public record Keyword(String name, String value) { }
    public record Info(String version, String dataType, ByteOrder byteOrder, long events,
                       long dataStart, long dataEnd, long analysisStart, long analysisEnd,
                       List<Parameter> parameters, List<Keyword> keywords, List<String> warnings) {
        public Info {
            parameters = List.copyOf(parameters); keywords = List.copyOf(keywords); warnings = List.copyOf(warnings);
        }
    }
    public record Summary(long eventsRead, long integerValuesWithUnusedBits, long nonfiniteValues) { }
    private final FileChannel file;
    private final long originalSize;
    private final Info info;
    private final ByteBuffer row;
    private final long[] masks;
    private final Map<String, String> values;
    private final NumericPolicy numericPolicy;
    private long eventsRead, maskedValues, nonfiniteValues;
    private boolean closed;

    /**
     * Opens an exact single data set. FCS3.1 uses strict UTF-8; the explicitly
     * selected charset applies to legacy FCS2.0/3.0 values only. No encoding fallback.
     * Unsupported storage, packed bits or multiple data sets cause an explicit
     * capability refusal before event delivery. Changed/invalid offsets are errors.
     */
    public static FcsReader open(Path path, Limits limits, Charset legacyCharset) throws IOException {
        return open(path, limits, legacyCharset, TextPolicy.EXACT);
    }
    public static FcsReader open(Path path, Limits limits, Charset legacyCharset, TextPolicy textPolicy) throws IOException {
        return open(path, limits, legacyCharset, textPolicy, NumericPolicy.EXACT);
    }
    public static FcsReader open(Path path, Limits limits, Charset legacyCharset, TextPolicy textPolicy, NumericPolicy numericPolicy) throws IOException {
        Objects.requireNonNull(path); Objects.requireNonNull(limits); Objects.requireNonNull(legacyCharset);
        Objects.requireNonNull(textPolicy); Objects.requireNonNull(numericPolicy);
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try { return new FcsReader(channel, limits, legacyCharset, textPolicy, numericPolicy); }
        catch (IOException | RuntimeException | Error failure) {
            try { channel.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private FcsReader(FileChannel channel, Limits limits, Charset legacyCharset, TextPolicy textPolicy, NumericPolicy numericPolicy) throws IOException {
        this.numericPolicy = numericPolicy;
        file = channel; originalSize = file.size();
        if (originalSize > limits.fileBytes()) throw new IOException("Complete FCS file exceeds declared capacity");
        byte[] header = read(0, 57, 58);
        String signature = new String(header, 0, 6, StandardCharsets.US_ASCII);
        if (!List.of("FCS2.0", "FCS3.0", "FCS3.1").contains(signature)) throw new UnsupportedFormatException("Unsupported FCS edition: " + signature + "; dependent analysis skipped");
        for (int i = 6; i < 10; i++) if (header[i] != 32) throw new IOException("Invalid FCS HEADER reserved bytes");
        long ts = offset(header, 10), te = offset(header, 18), hs = offset(header, 26), he = offset(header, 34);
        long ha = offset(header, 42), hz = offset(header, 50);
        if (ts < 58) throw new IOException("FCS primary TEXT overlaps the HEADER");
        byte[] text = read(ts, te, limits.textBytes());
        Charset charset = signature.equals("FCS3.1") ? StandardCharsets.UTF_8 : legacyCharset;
        var keywords = new ArrayList<Keyword>(); var lookup = new LinkedHashMap<String, String>();
        var warnings = new ArrayList<String>();
        if (numericPolicy == NumericPolicy.ALLOW_ASCII_SPACE_PADDING) warnings.add("Explicit numeric policy permits outer ASCII spaces in recognized numeric metadata tokens. Original keyword values and parameter names remain unchanged; tabs, other whitespace and malformed values are not repaired.");
        parseText(text, charset, keywords, lookup, textPolicy, warnings, signature);
        if (lookup.containsKey("$UNICODE")) throw new UnsupportedFormatException("Legacy $UNICODE requires its declared encoding semantics; this reader does not guess; dependent analysis skipped");
        long ss = optionalNumber(lookup, "$BEGINSTEXT", 0), se = optionalNumber(lookup, "$ENDSTEXT", 0);
        if (ss == ts && se == te) warnings.add("Supplemental TEXT points to the primary TEXT itself; the original pointer is retained and that same segment is decoded once.");
        else if (ss != 0 || se != 0) {
            if (ss < 58) throw new IOException("Supplemental TEXT overlaps HEADER");
            nonoverlap(ts, te, ss, se, "primary/supplemental TEXT");
            byte[] supplemental = read(ss, se, limits.textBytes());
            if (supplemental[0] != text[0]) throw new IOException("Supplemental TEXT delimiter differs");
            parseText(supplemental, charset, keywords, lookup, textPolicy, warnings, signature);
        }
        long count = number(lookup, "$TOT"), next = number(lookup, "$NEXTDATA");
        if (next != 0) throw new UnsupportedFormatException("Multiple FCS data sets require a complete multi-dataset reader; no first-data-set-only result; dependent analysis skipped");
        if (!required(lookup, "$MODE").equals("L")) throw new UnsupportedFormatException("FCS reader requires list mode L; dependent analysis skipped");
        if (count > limits.events()) throw new IOException("Complete FCS event population exceeds declared capacity");
        long n = number(lookup, "$PAR");
        if (n < 1 || n > limits.parameters()) throw new IOException("Complete FCS parameter population is invalid or exceeds declared capacity");
        String type = required(lookup, "$DATATYPE");
        if (!List.of("I", "F", "D").contains(type)) throw new UnsupportedFormatException("Unsupported FCS DATA type " + type + "; dependent analysis skipped");
        String order = required(lookup, "$BYTEORD");
        ByteOrder endian;
        if (order.equals("1,2,3,4") || !signature.equals("FCS3.1") && order.equals("1,2")) endian = ByteOrder.LITTLE_ENDIAN;
        else if (order.equals("4,3,2,1") || !signature.equals("FCS3.1") && order.equals("2,1")) endian = ByteOrder.BIG_ENDIAN;
        else throw new UnsupportedFormatException("Unsupported FCS byte order " + order + "; no host-order substitution; dependent analysis skipped");
        var parameters = new ArrayList<Parameter>(); masks = new long[(int)n];
        int width = 0;
        for (int i = 1; i <= n; i++) {
            String prefix = "$P" + i; long bits = number(lookup, prefix+"B");
            if (type.equals("I") && (bits < 8 || bits > 32 || bits%8 != 0)) throw new UnsupportedFormatException("Unsupported packed/integer width " + bits + " for parameter " + i + "; dependent analysis skipped");
            if (type.equals("F") && bits != 32 || type.equals("D") && bits != 64) throw new IOException("FCS floating DATA type/parameter width disagree");
            String range = required(lookup, prefix+"R");
            if (type.equals("I")) {
                long r = unsigned(numericMetadataText(range), prefix+"R");
                if (r < 1 || r > (1L << (int)bits)) throw new IOException("FCS integer range does not fit its declared storage");
                masks[i-1] = r == 1 ? 0 : (1L << (64-Long.numberOfLeadingZeros(r-1)))-1;
            } else {
                double r;
                String parsedRange = numericMetadataText(range);
                if (!parsedRange.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw new IOException("FCS floating parameter range must use decimal syntax");
                try { r = Double.parseDouble(parsedRange); } catch (NumberFormatException error) { throw new IOException("Invalid FCS floating parameter range", error); }
                if (!Double.isFinite(r) || r <= 0) throw new IOException("Invalid FCS floating parameter range");
            }
            String name = required(lookup, prefix+"N"), amplification = required(lookup, prefix+"E");
            parameters.add(new Parameter(i, name, lookup.get(prefix+"S"), (int)bits, range, amplification, lookup.get(prefix+"G")));
            width = Math.addExact(width, (int)bits/8);
        }
        long ds = signature.equals("FCS2.0") ? hs : number(lookup, "$BEGINDATA");
        long de = signature.equals("FCS2.0") ? he : number(lookup, "$ENDDATA");
        if (!signature.equals("FCS2.0") && !(hs == 0 && he == 0 && de > 99_999_999L) && (hs != ds || he != de)) throw new IOException("FCS HEADER/TEXT DATA offsets disagree; no guessed offset correction");
        long expected;
        try { expected = Math.multiplyExact(count, (long)width); } catch (ArithmeticException error) { throw new IOException("FCS complete DATA size overflows", error); }
        if (count == 0 && ds == 0 && de == 0) { /* Explicit empty DATA segment. */ }
        else {
            range(ds, de);
            if (ds < 58 || de-ds+1 != expected) throw new IOException("FCS DATA length disagrees with complete TOT/PAR/width population");
            nonoverlap(ts, te, ds, de, "TEXT/DATA");
            if (ss != 0 || se != 0) nonoverlap(ss, se, ds, de, "supplemental TEXT/DATA");
        }
        long as = optionalNumber(lookup, "$BEGINANALYSIS", ha), ae = optionalNumber(lookup, "$ENDANALYSIS", hz);
        if (!(ha == 0 && hz == 0 && ae > 99_999_999L) && (ha != as || hz != ae)) throw new IOException("FCS HEADER/TEXT ANALYSIS offsets disagree");
        if (as != 0 || ae != 0) {
            range(as, ae); if (as < 58) throw new IOException("ANALYSIS overlaps HEADER");
            nonoverlap(as, ae, ts, te, "ANALYSIS/TEXT");
            if (count != 0) nonoverlap(as, ae, ds, de, "ANALYSIS/DATA");
            if (ss != 0 || se != 0) nonoverlap(as, ae, ss, se, "ANALYSIS/supplemental TEXT");
            warnings.add("ANALYSIS segment is retained in the original file; its model/gating contents are not interpreted by stored-channel extraction.");
        }
        warnings.add("Stored channels only: no gain, antilog, time, compensation, calibration, display transform or gate is inferred. Floating negative/over-range/nonfinite values remain visible. This profile does not validate a CRC or claim complete FCS conformance.");
        values = Collections.unmodifiableMap(lookup);
        info = new Info(signature.substring(3), type, endian, count, ds, de, as, ae, parameters, keywords, warnings);
        row = ByteBuffer.allocate(width).order(endian); file.position(ds);
    }
    public Info info() { return info; }
    /** Case-insensitive keyword lookup; returns null for absent values. Values are not normalized. */
    public String keyword(String name) { return values.get(Objects.requireNonNull(name).toUpperCase(Locale.ROOT)); }
    public NumericPolicy numericPolicy() { return numericPolicy; }
    /** Interpret a numeric token under the explicitly selected policy; never use this on names or identifiers. */
    public String numericMetadataText(String value) {
        Objects.requireNonNull(value);
        if (numericPolicy == NumericPolicy.EXACT) return value;
        int start = 0, end = value.length();
        while (start < end && value.charAt(start) == ' ') start++;
        while (end > start && value.charAt(end-1) == ' ') end--;
        return value.substring(start,end);
    }
    /**
     * Reads one complete event into an exactly-sized caller array. Unsigned integers
     * up to 32 bits are exactly representable in double. Returns false only after
     * all declared events. A consumer must reach {@link #summary()} before treating
     * a stream as complete; stopping early supplies no complete-result claim.
     */
    public boolean readEvent(double[] destination) throws IOException {
        if (closed) throw new IOException("FCS reader is closed");
        if (destination.length != info.parameters().size()) throw new IllegalArgumentException("Require exactly one complete FCS event buffer");
        if (eventsRead == info.events()) { checkSize(); return false; }
        row.clear();
        while (row.hasRemaining()) if (file.read(row) < 0) throw new EOFException("Complete FCS event data ended early");
        row.flip();
        for (int i = 0; i < destination.length; i++) {
            if (info.dataType().equals("I")) {
                int bytes = info.parameters().get(i).bits()/8; long raw = 0;
                for (int j = 0; j < bytes; j++) raw |= (long)(row.get() & 255) << (8*(info.byteOrder() == ByteOrder.LITTLE_ENDIAN ? j : bytes-1-j));
                long masked = raw & masks[i]; if (raw != masked) maskedValues++;
                destination[i] = masked;
            } else {
                destination[i] = info.dataType().equals("F") ? (double)row.getFloat() : row.getDouble();
                if (!Double.isFinite(destination[i])) nonfiniteValues++;
            }
        }
        eventsRead++; return true;
    }
    /** Requires the complete declared population and an unchanged file length; no implicit draining. */
    public Summary summary() throws IOException {
        if (closed || eventsRead != info.events()) throw new IOException("No complete FCS stream: consume every declared event before requesting its summary");
        checkSize(); return new Summary(eventsRead, maskedValues, nonfiniteValues);
    }
    private void checkSize() throws IOException { if (file.size() != originalSize) throw new IOException("FCS file length changed during reading"); }
    private static void parseText(byte[] raw, Charset charset, List<Keyword> keywords, Map<String, String> lookup,
                                  TextPolicy textPolicy, List<String> warnings, String signature) throws IOException {
        int delimiter = raw[0] & 255;
        if (textPolicy == TextPolicy.ALLOW_TRAILING_SPACE_PADDING && delimiter != 32) {
            int length = raw.length;
            while (length > 1 && raw[length-1] == 32) length--;
            if (length != raw.length) {
                warnings.add("Explicit TEXT policy decoded a segment excluding " + (raw.length-length) + " trailing ASCII space padding bytes. The original bytes and segment offsets remain unchanged.");
                raw = Arrays.copyOf(raw, length);
            }
        }
        if (delimiter < 1 || delimiter > 126 || raw[raw.length-1] != raw[0]) throw new IOException("FCS TEXT requires its exact closing ASCII delimiter");
        var parts = new ArrayList<byte[]>(); var part = new ByteArrayOutputStream();
        for (int i = 1; i < raw.length; i++) {
            if ((raw[i] & 255) != delimiter) part.write(raw[i]);
            else if (i+1 < raw.length && raw[i+1] == raw[i]) { part.write(delimiter); i++; }
            else { if (part.size() == 0) throw new IOException("Ambiguous empty FCS TEXT token; no delimiter/empty-value guessing"); parts.add(part.toByteArray()); part.reset(); }
        }
        if (part.size() != 0 || parts.size()%2 != 0) {
            if (signature.equals("FCS2.0")) throw new UnsupportedFormatException("Legacy FCS2.0 TEXT has an ambiguous/unterminated trailing token; this profile does not discard it or guess an empty value; dependent analysis skipped");
            throw new IOException("Incomplete FCS TEXT keyword/value population");
        }
        for (int i = 0; i < parts.size(); i += 2) {
            for (byte c : parts.get(i)) if (c < 32 || c > 126) throw new IOException("FCS keyword contains nonprintable/non-ASCII bytes");
            String key = new String(parts.get(i), StandardCharsets.US_ASCII), value;
            try { value = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(parts.get(i+1))).toString(); }
            catch (CharacterCodingException error) { throw new IOException("FCS value does not match its explicitly selected text encoding", error); }
            if (lookup.putIfAbsent(key.toUpperCase(Locale.ROOT), value) != null) throw new IOException("Duplicate case-insensitive FCS keyword: " + key);
            keywords.add(new Keyword(key, value));
        }
    }
    private static long offset(byte[] header, int start) throws IOException {
        String value = new String(header, start, 8, StandardCharsets.US_ASCII).strip();
        return value.isEmpty() ? 0 : unsigned(value, "HEADER offset");
    }
    private static long unsigned(String value, String label) throws IOException {
        if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) throw new IOException("Invalid unsigned FCS " + label);
        try { return Long.parseLong(value); } catch (NumberFormatException error) { throw new IOException("FCS " + label + " exceeds supported complete-file addressing", error); }
    }
    private static String required(Map<String, String> values, String key) throws IOException {
        String value = values.get(key); if (value == null) throw new IOException("Required FCS keyword is absent: " + key); return value;
    }
    private long number(Map<String, String> values, String key) throws IOException { return unsigned(numericMetadataText(required(values, key)), key); }
    private long optionalNumber(Map<String, String> values, String key, long absent) throws IOException { return values.containsKey(key) ? number(values, key) : absent; }
    private void range(long start, long end) throws IOException {
        if (start < 0 || end < start || end >= originalSize) throw new IOException("FCS declared segment lies outside complete file");
    }
    private static void nonoverlap(long a, long z, long b, long y, String label) throws IOException {
        if (a <= y && b <= z) throw new IOException("FCS segments overlap: " + label);
    }
    private byte[] read(long start, long end, int maximum) throws IOException {
        range(start, end); long length = end-start+1;
        if (length > maximum) throw new IOException("Complete FCS metadata exceeds declared capacity");
        ByteBuffer buffer = ByteBuffer.allocate((int)length); file.position(start);
        while (buffer.hasRemaining()) if (file.read(buffer) < 0) throw new EOFException("FCS segment ended early");
        return buffer.array();
    }
    @Override public void close() throws IOException { if (!closed) { closed = true; file.close(); } }
}
