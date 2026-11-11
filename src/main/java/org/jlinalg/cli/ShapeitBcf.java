/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.util.BlockCompressedInputStream;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Complete raw BCF2.2 reader: never reconstructs GT through VCF text or modifies its source. */
final class ShapeitBcf {
    private ShapeitBcf() { }
    static final int MISSING = Integer.MIN_VALUE, VECTOR_END = Integer.MIN_VALUE + 1;
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message) { super(message); }
    }
    record Limits(int samples, long records, long genotypeCells, long inputBytes, long decodedBytes,
            long retainedBytes, int headerLines, int headerCharacters, int lineCharacters,
            int alternateAlleles, int formatFields, int genotypeWidth, long valuesPerRecord) {
        Limits {
            if (samples < 1 || records < 1 || genotypeCells < 1 || inputBytes < 1 || decodedBytes < 1 || retainedBytes < 1
                    || headerLines < 1 || headerCharacters < 1 || lineCharacters < 1 || alternateAlleles < 1
                    || formatFields < 1 || genotypeWidth < 1 || valuesPerRecord < 1)
                throw new IllegalArgumentException("Require positive complete BCF capacities");
        }
        static Limits defaults() {
            return new Limits(100000, 1000000, 100000000, 1073741824L, 8589934592L, 1073741824L,
                    10000, 33554432, 16777216, 1024, 255, 64, 100000000);
        }
    }
    record HeaderView(String rawText, List<String> samples, Map<Integer,String> contigs, Set<String> infoTags) {
        HeaderView { samples = List.copyOf(samples); contigs = Collections.unmodifiableMap(new LinkedHashMap<>(contigs)); infoTags = Set.copyOf(infoTags); }
    }
    /** Exact physical payload; integer sentinels are normalized only by integers(). */
    record Field(String name, int type, int width, int cells, byte[] payload) {
        Field {
            int primitiveBytes = switch (type) { case 0 -> 0; case 1,7 -> 1; case 2 -> 2; case 3,5 -> 4; default -> -1; };
            if (name == null || name.isEmpty() || primitiveBytes < 0 || width < 0 || cells < 1 || type == 0 && width != 0
                    || (long)width * cells * primitiveBytes != payload.length)
                throw new IllegalArgumentException("Invalid complete BCF field dimensions");
            payload = payload.clone();
        }
        @Override public byte[] payload() { return payload.clone(); }
        int payloadBytes() { return payload.length; }
        int[] integers() throws IOException {
            if (width == 0) return new int[0];
            if (type < 1 || type > 3) throw new IOException("BCF field is not integer encoded: " + name);
            var data = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            int[] values = new int[Math.multiplyExact(width, cells)]; int missing = missing(type);
            for (int i = 0; i < values.length; i++) {
                int value = scalarInt(data, type);
                if (value >= missing + 2 && value <= missing + 7) throw new IOException("Reserved BCF integer encoding");
                values[i] = value == missing ? MISSING : value == missing + 1 ? VECTOR_END : value;
            }
            return values;
        }
        int[] floatBits() throws IOException {
            if (width == 0) return new int[0];
            if (type != 5) throw new IOException("BCF field is not Float32 encoded: " + name);
            var data = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            int[] values = new int[Math.multiplyExact(width, cells)];
            for (int i = 0; i < values.length; i++) values[i] = data.getInt();
            return values;
        }
        int[] cellIntegers(int cell) throws IOException {
            if (cell < 0 || cell >= cells) throw new IllegalArgumentException("BCF cell index is outside the complete field");
            if (width == 0) return new int[0];
            if (type < 1 || type > 3) throw new IOException("BCF field is not integer encoded: " + name);
            int primitiveBytes = type == 1 ? 1 : type == 2 ? 2 : 4;
            var data = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN); data.position(Math.multiplyExact(cell * width, primitiveBytes));
            int[] values = new int[width]; int count = 0, missing = missing(type);
            for (int i = 0; i < width; i++) {
                int value = scalarInt(data, type);
                if (value == missing + 1) break;
                if (value >= missing + 2 && value <= missing + 7) throw new IOException("Reserved BCF integer encoding");
                values[count++] = value == missing ? MISSING : value;
            }
            return count == width ? values : Arrays.copyOf(values, count);
        }
        int[] cellFloatBits(int cell) throws IOException {
            if (cell < 0 || cell >= cells) throw new IllegalArgumentException("BCF cell index is outside the complete field");
            if (width == 0) return new int[0];
            if (type != 5) throw new IOException("BCF field is not Float32 encoded: " + name);
            var data = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN); data.position(Math.multiplyExact(cell * width, 4));
            int[] values = new int[width]; int count = 0;
            for (int i = 0; i < width; i++) {
                int value = data.getInt(); if (value == 0x7f800002) break; values[count++] = value;
            }
            return count == width ? values : Arrays.copyOf(values, count);
        }
    }
    record Row(long ordinal, String contig, int position, int referenceLength, String id, String reference, List<String> alternates,
            int qualityBits, List<String> filters, Map<String,Field> info, List<Field> formats, String rawWireSha256) {
        Row {
            alternates = List.copyOf(alternates); filters = List.copyOf(filters);
            info = Collections.unmodifiableMap(new LinkedHashMap<>(info)); formats = List.copyOf(formats);
        }
        Field format(String name) { return formats.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null); }
    }
    @FunctionalInterface interface Visitor {
        default void header(HeaderView header) throws IOException { }
        void row(Row row) throws IOException;
    }
    record Report(boolean bgzf, long bgzfBlocks, int samples, long records, long genotypeCells,
            long genotypeValues, long firstAllelePhaseBits, long vectorEnds, long typedMissingGtValues,
            long rowsWithoutGt, long decodedBytes, String sourceSha256, String gtWireSha256, boolean complete) { }
    private record Declaration(String id, String type, String number) { }
    private record Header(List<String> lines, List<String> samples, Map<Integer,String> contigs,
            Map<Integer,String> names, Map<String,Declaration> info, Map<String,Declaration> formats, Set<String> filters) { }
    private record Shape(int type, int length, int width) { }
    private static final byte[] EOF_BLOCK = HexFormat.of().parseHex("1f8b08040000000000ff0600424302001b0003000000000000000000");

    static Report read(Path supplied, Limits limits, Visitor visitor, Runnable check) throws IOException {
        Objects.requireNonNull(limits); Objects.requireNonNull(visitor); Objects.requireNonNull(check);
        Path source = supplied.toAbsolutePath().normalize();
        String original = fingerprint(source, limits.inputBytes(), check);
        long blocks = bgzfBlocks(source, limits.inputBytes(), check);
        long records = 0, cells = 0, values = 0, firstBits = 0, ends = 0, typedMissing = 0, absent = 0;
        MessageDigest wire = hasher(); frame(wire, "SHAPEIT-BCF2.2-raw-GT-v1");
        try (var physical = new BufferedInputStream(Files.newInputStream(source), 65536);
             var decoded = blocks == 0 ? physical : new BlockCompressedInputStream(physical);
             var input = new Bounded(decoded, limits.decodedBytes(), check)) {
            if (decoded instanceof BlockCompressedInputStream bgzf) bgzf.setCheckCrcs(true);
            byte[] magic = exact(input, 5);
            if (magic[0] != 'B' || magic[1] != 'C' || magic[2] != 'F' || magic[3] != 2) throw new IOException("Require BCF2 magic");
            if (magic[4] != 2) throw new Unavailable("Raw SHAPEIT intake requires verified BCF2.2; preserve the source and supply a separately validated reader for another edition");
            long headerBytes = u32(input);
            if (headerBytes < 1 || headerBytes > Integer.MAX_VALUE || headerBytes > Math.multiplyExact(4L, limits.headerCharacters()))
                throw new IOException("BCF header exceeds its complete capacity");
            memory(Math.multiplyExact(16L, headerBytes), limits);
            byte[] bytes = exact(input, (int)headerBytes);
            if (bytes[bytes.length - 1] != 0) throw new IOException("BCF header is not NUL terminated");
            for (int i = 0; i < bytes.length - 1; i++) if (bytes[i] == 0) throw new IOException("Interior NUL in BCF header");
            String rawHeader = utf8(bytes, 0, bytes.length - 1);
            Header header = header(rawHeader, limits);
            number(wire, header.samples().size()); for (String sample : header.samples()) frame(wire, sample);
            visitor.header(new HeaderView(rawHeader, header.samples(), header.contigs(), header.info().keySet()));
            MessageDigest recordWire = hasher();
            for (int first; (first = input.read()) >= 0;) {
                check.run();
                long sharedSize = u32(input, first), individualSize = u32(input), total = Math.addExact(sharedSize, individualSize);
                if (sharedSize < 24 || sharedSize > Integer.MAX_VALUE || individualSize > Integer.MAX_VALUE)
                    throw new IOException("Invalid or excessive BCF record framing");
                long base = Math.addExact(Math.multiplyExact(64L, total), Math.addExact(Math.multiplyExact(16L, headerBytes), Math.multiplyExact(256L, header.samples().size())));
                memory(base, limits);
                if (++records > limits.records()) throw new IOException("Complete BCF record capacity exceeded; no rows were truncated");
                var shared = ByteBuffer.wrap(exact(input, (int)sharedSize)).order(ByteOrder.LITTLE_ENDIAN);
                var individual = ByteBuffer.wrap(exact(input, (int)individualSize)).order(ByteOrder.LITTLE_ENDIAN);
                recordWire.reset();
                recordWire.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt((int)sharedSize).putInt((int)individualSize).array());
                recordWire.update(shared.array()); recordWire.update(individual.array());
                String rawWireSha256 = HexFormat.of().formatHex(recordWire.digest());
                int rid = shared.getInt(), zeroPosition = shared.getInt(), referenceLength = shared.getInt(), quality = shared.getInt();
                int infoCount = Short.toUnsignedInt(shared.getShort()), alleleCount = Short.toUnsignedInt(shared.getShort()), dimensions = shared.getInt();
                int sampleCount = dimensions & 0xffffff, formatCount = dimensions >>> 24;
                String contig = header.contigs().get(rid);
                if (alleleCount - 1 > limits.alternateAlleles()) throw new IOException("Complete BCF ALT capacity exceeded; increase alternateAlleles");
                if (formatCount > limits.formatFields()) throw new IOException("Complete BCF FORMAT capacity exceeded; increase formatFields");
                if (contig == null || zeroPosition < 0 || zeroPosition == Integer.MAX_VALUE || referenceLength < 1 || alleleCount < 1
                        || sampleCount != header.samples().size() || sampleCount == 0 && (formatCount != 0 || individualSize != 0))
                    throw new IOException("BCF fixed dimensions, contig or position are invalid");
                memory(Math.addExact(base, Math.multiplyExact(128L, infoCount + formatCount)), limits);
                String id = string(shared, shape(shared), true), ref = string(shared, shape(shared), false);
                var alternatives = new ArrayList<String>();
                for (int i = 1; i < alleleCount; i++) alternatives.add(string(shared, shape(shared), false));
                var filterValues = integers(shared, shape(shared)); var filters = new ArrayList<String>();
                for (String value : filterValues) {
                    if (value.equals(".")) { if (filterValues.size() != 1) throw new IOException("Missing FILTER mixed with IDs"); filters.add("."); }
                    else { String name = header.names().get(Integer.parseInt(value)); if (name == null || !header.filters().contains(name)) throw new IOException("Unknown BCF FILTER ID"); filters.add(name); }
                }
                if (filters.size() > 1 && filters.contains("PASS")) throw new IOException("PASS mixed with failing FILTER IDs");
                var info = new LinkedHashMap<String,Field>(); long recordValues = 0;
                for (int i = 0; i < infoCount; i++) {
                    String name = name(shared, header.names()); Declaration declaration = header.info().get(name);
                    if (declaration == null || info.containsKey(name)) throw new IOException("Undeclared or duplicate BCF INFO field");
                    Shape shape = shape(shared); recordValues = Math.addExact(recordValues, shape.length()); capacity(recordValues, limits);
                    Field field = field(name, shared, shape, 1); validate(field, declaration, false, alleleCount);
                    info.put(name, field);
                }
                if (shared.hasRemaining()) throw new IOException("Unconsumed bytes in BCF shared record");
                var formats = new ArrayList<Field>(); var seen = new HashSet<String>(); Field gt = null;
                for (int i = 0; i < formatCount; i++) {
                    String name = name(individual, header.names()); Declaration declaration = header.formats().get(name);
                    if (declaration == null || !seen.add(name)) throw new IOException("Undeclared or duplicate BCF FORMAT field");
                    Shape shape = shape(individual); long count = Math.multiplyExact((long)shape.length(), sampleCount);
                    recordValues = Math.addExact(recordValues, count); capacity(recordValues, limits);
                    if (name.equals("GT") && shape.length() > limits.genotypeWidth()) throw new IOException("BCF GT storage width exceeds capacity; increase the declared limit");
                    Field field = field(name, individual, shape, sampleCount); validate(field, declaration, name.equals("GT"), alleleCount);
                    formats.add(field); if (name.equals("GT")) gt = field;
                }
                if (individual.hasRemaining()) throw new IOException("Unconsumed bytes in BCF individual record");
                for (String value : List.of(contig, Integer.toString(zeroPosition + 1), id, ref, String.join(",", alternatives))) frame(wire, value);
                if (gt == null) { absent++; number(wire, 0); }
                else {
                    cells = Math.addExact(cells, sampleCount); values = Math.addExact(values, Math.multiplyExact((long)gt.width(), sampleCount));
                    if (cells > limits.genotypeCells()) throw new IOException("Complete BCF genotype-cell capacity exceeded; no cells were truncated");
                    number(wire, gt.width()); int[] codes = gt.integers();
                    for (int i = 0; i < codes.length; i++) {
                        int code = codes[i]; number(wire, code);
                        if (code == VECTOR_END) ends++;
                        else if (code == MISSING) typedMissing++;
                        else if (i % gt.width() == 0 && (code & 1) != 0) firstBits++;
                    }
                }
                visitor.row(new Row(records, contig, zeroPosition + 1, referenceLength, id, ref, alternatives, quality, filters, info, formats, rawWireSha256));
            }
            if (!fingerprint(source, limits.inputBytes(), check).equals(original)) throw new IOException("BCF source changed during complete raw decoding");
            return new Report(blocks != 0, blocks, header.samples().size(), records, cells, values, firstBits, ends, typedMissing, absent,
                    input.count, original, HexFormat.of().formatHex(wire.digest()), true);
        } catch (java.nio.BufferUnderflowException | IndexOutOfBoundsException | ArithmeticException invalid) {
            throw new IOException("Malformed or excessive complete BCF payload", invalid);
        } catch (htsjdk.samtools.SAMFormatException | htsjdk.samtools.util.RuntimeIOException invalid) {
            throw new IOException("Malformed complete BGZF payload: " + invalid.getMessage(), invalid);
        }
    }
    private static void capacity(long values, Limits limits) throws IOException {
        if (values > limits.valuesPerRecord()) throw new IOException("Complete BCF typed-value capacity exceeded; no values were truncated");
    }
    private static void memory(long estimate, Limits limits) throws IOException {
        if (estimate > limits.retainedBytes() || estimate > Runtime.getRuntime().maxMemory() / 2)
            throw new IOException("Complete BCF record/header needs more retained capacity or Java heap; increase the declared limits and -Xmx");
    }
    private static Field field(String name, ByteBuffer data, Shape shape, int cells) throws IOException {
        long size = Math.multiplyExact(Math.multiplyExact((long)shape.length(), cells), shape.width());
        if (size > data.remaining() || size > Integer.MAX_VALUE) throw new IOException("BCF field exceeds its complete payload block");
        byte[] raw = new byte[(int)size]; data.get(raw); return new Field(name, shape.type(), shape.length(), cells, raw);
    }
    private static void validate(Field field, Declaration declared, boolean gt, int alleles) throws IOException {
        if (gt) {
            if (field.type() < 1 || field.type() > 3 || field.width() < 1) throw new IOException("BCF GT requires an explicit integer storage frame");
        } else if (field.width() == 0) return;
        else if (declared.type().equals("Flag")) {
            if (field.width() != 1 || field.cells() != 1 || field.type() < 1 || field.type() > 3) throw new IOException("Invalid BCF flag representation");
        } else {
            int expected = switch (declared.type()) { case "Integer" -> 1; case "Float" -> 5; case "String", "Character" -> 7; default -> -1; };
            if (expected == 1 ? field.type() < 1 || field.type() > 3 : field.type() != expected) throw new IOException("BCF declared and encoded primitive types differ");
        }
        if (field.type() >= 1 && field.type() <= 3) {
            int[] codes = field.integers();
            for (int cell = 0; cell < field.cells(); cell++) {
                boolean ended = false;
                for (int i = 0; i < field.width(); i++) {
                    int code = codes[cell * field.width() + i];
                    if (code == VECTOR_END) { ended = true; continue; }
                    if (ended || code >= MISSING + 2 && code <= MISSING + 7) throw new IOException("Reserved integer or data after BCF vector end");
                    if (gt && code != MISSING && (code < 0 || (code >>> 1) - 1 >= alleles)) throw new IOException("BCF GT exceeds declared alleles");
                    if (declared.type().equals("Flag") && code == MISSING) throw new IOException("Missing BCF INFO flag");
                }
            }
        } else if (field.type() == 5) {
            int[] codes = field.floatBits();
            for (int cell = 0; cell < field.cells(); cell++) {
                boolean ended = false;
                for (int i = 0; i < field.width(); i++) {
                    int code = codes[cell * field.width() + i];
                    if (code == 0x7f800002) { ended = true; continue; }
                    if (ended || code >= 0x7f800003 && code <= 0x7f800007) throw new IOException("Reserved Float32 value or data after BCF vector end");
                }
            }
        } else if (field.type() == 7) {
            for (int cell = 0; cell < field.cells(); cell++) {
                int start = cell * field.width(), end = start;
                while (end < start + field.width() && field.payload[end] != 0) end++;
                for (int i = end; i < start + field.width(); i++) if (field.payload[i] != 0) throw new IOException("Nonzero BCF string padding");
                if (!(end == start + 1 && field.payload[start] == 7)) utf8(field.payload, start, end - start);
            }
        }
    }

    private static String fingerprint(Path path, long maximum, Runnable check) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path) || Files.size(path) > maximum)
            throw new IOException("Require a canonical, regular SHAPEIT BCF within the declared source capacity");
        var before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        MessageDigest digest = hasher(); long bytes = 0;
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = input.read(buffer)) != -1) {
                check.run(); bytes = Math.addExact(bytes, n);
                if (bytes > maximum) throw new IOException("SHAPEIT BCF exceeded its complete source capacity");
                digest.update(buffer, 0, n);
            }
        }
        var after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (bytes != before.size() || before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey()) || !after.isRegularFile())
            throw new IOException("SHAPEIT BCF changed while fingerprinting");
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void number(MessageDigest digest, int value) {
        digest.update((byte)(value >>> 24)); digest.update((byte)(value >>> 16));
        digest.update((byte)(value >>> 8)); digest.update((byte)value);
    }

    // Header, typed-descriptor and BGZF framing helpers follow the existing,
    // separately validated Eagle reader. Its scientific/output code is unchanged.
    // The helpers below do not convert the original SHAPEIT input to VCF.

    private static Header header(String text, Limits limits) throws IOException {
        var lines = new ArrayList<String>(); var contigs = new HashMap<Integer,String>(); var names = new HashMap<Integer,String>(); names.put(0,"PASS");
        var nameIndices = new HashMap<String,Integer>(); nameIndices.put("PASS",0); var info = new HashMap<String,Declaration>(); var formats = new HashMap<String,Declaration>(); var filters = new HashSet<String>(); filters.add("PASS");
        List<String> samples = null; int implicitName = 1, implicitContig = 0; Boolean tagIndexed = null, contigIndexed = null; var declaredFilters = new HashSet<String>();
        if (text.length() > limits.headerCharacters()) throw new IOException("BCF decoded header character capacity exceeded");
        String[] physicalLines = text.split("\n", -1);
        for (int ordinal = 0; ordinal < physicalLines.length; ordinal++) {
            String raw = physicalLines[ordinal];
            if (raw.isEmpty() && ordinal == physicalLines.length-1 && text.endsWith("\n") && samples != null) continue;
            String line = raw.endsWith("\r") ? raw.substring(0,raw.length()-1) : raw;
            if (line.length() > limits.lineCharacters() || lines.size() >= limits.headerLines() || samples != null) throw new IOException("Invalid/excessive BCF header lines");
            if (lines.isEmpty() && !line.matches("##fileformat=VCFv4[.][0-9]+")) throw new IOException("Require VCF4 fileformat in BCF header");
            String role = null;
            for (String value : List.of("contig", "FILTER", "INFO", "FORMAT")) if (line.startsWith("##" + value + "=<")) role = value;
            if (role != null) {
                if (!line.endsWith(">")) throw new IOException("Malformed BCF structured header");
                List<String> fields = structured(line.substring(line.indexOf('<')+1,line.length()-1)); var values = new LinkedHashMap<String,String>(); var retained = new ArrayList<String>();
                for (String field : fields) {
                    int equals = field.indexOf('='); if (equals < 1) throw new IOException("Malformed BCF header attribute");
                    String key = field.substring(0,equals).trim(), value = field.substring(equals+1).trim();
                    if (values.putIfAbsent(key, value) != null) throw new IOException("Duplicate BCF header attribute");
                    if (!key.equals("IDX")) retained.add(field);
                }
                String id = values.get("ID"); if (id == null || !id.matches("[^\\s,<>\"=]+")) throw new IOException("Invalid BCF dictionary ID");
                boolean indexed = values.containsKey("IDX"); int index;
                try { index = indexed ? Integer.parseInt(values.get("IDX")) : role.equals("contig") ? implicitContig : nameIndices.getOrDefault(id,implicitName); }
                catch (NumberFormatException invalid) { throw new IOException("Invalid BCF IDX",invalid); }
                if (index < 0) throw new IOException("Negative BCF dictionary IDX");
                if (role.equals("contig")) {
                    if (contigIndexed != null && contigIndexed != indexed) throw new IOException("Mixed implicit/explicit BCF contig IDX"); contigIndexed = indexed;
                    if (contigs.putIfAbsent(index,id) != null || new HashSet<>(contigs.values()).size() != contigs.size()) throw new IOException("Duplicate BCF contig IDX/name"); implicitContig++;
                } else {
                    if (tagIndexed != null && tagIndexed != indexed) throw new IOException("Mixed implicit/explicit BCF tag IDX"); tagIndexed = indexed;
                    String previous = names.putIfAbsent(index,id); Integer previousIndex = nameIndices.putIfAbsent(id,index);
                    if (previous != null && !previous.equals(id) || previousIndex != null && previousIndex != index) throw new IOException("Conflicting BCF dictionary IDX/name");
                    if (previousIndex == null) implicitName++;
                    if (role.equals("FILTER")) { if (!declaredFilters.add(id)) throw new IOException("Duplicate BCF FILTER definition"); filters.add(id); }
                    else {
                        String type = values.get("Type"), number = values.get("Number");
                        if (number == null || type == null || !Set.of("Integer","Float","Flag","Character","String").contains(type)
                                || type.equals("Flag") && (!role.equals("INFO") || !number.equals("0"))) throw new IOException("Require typed BCF INFO/FORMAT definitions");
                        if (role.equals("FORMAT") && id.equals("GT") && (!type.equals("String") || !number.equals("1"))) throw new IOException("Require standard GT String/Number1 definition");
                        var map = role.equals("INFO") ? info : formats;
                        if (map.putIfAbsent(id,new Declaration(id,type,number)) != null) throw new IOException("Duplicate BCF role definition");
                    }
                }
                line = "##" + role + "=<" + String.join(",",retained) + ">";
            } else if (line.startsWith("#CHROM\t")) {
                String[] f = line.split("\t",-1); String[] fixed = {"#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO","FORMAT"};
                if (f.length != 8 && f.length < 10 || f.length > (long)limits.samples()+9) throw new IOException("BCF sample header width/capacity invalid");
                for (int i = 0; i < Math.min(9,f.length); i++) if (!f[i].equals(fixed[i])) throw new IOException("Invalid BCF fixed sample header");
                samples = new ArrayList<>(); for (int i = 9; i < f.length; i++) { if (f[i].isBlank() || f[i].codePoints().anyMatch(Character::isWhitespace)) throw new IOException("Invalid BCF sample identity"); samples.add(f[i]); }
                if (new HashSet<>(samples).size() != samples.size()) throw new IOException("Duplicate BCF sample identity");
            } else if (!line.startsWith("##")) throw new IOException("BCF header lacks a valid sample header");
            lines.add(line);
        }
        if (samples == null || contigs.isEmpty()) throw new IOException("Incomplete BCF header/dictionary");
        return new Header(List.copyOf(lines),List.copyOf(samples),Map.copyOf(contigs),Map.copyOf(names),Map.copyOf(info),Map.copyOf(formats),Set.copyOf(filters));
    }
    private static List<String> structured(String value) throws IOException {
        var fields = new ArrayList<String>(); boolean quote = false, escaped = false; int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) { escaped = false; continue; } if (c == '\\' && quote) { escaped = true; continue; }
            if (c == '"') quote = !quote; else if (c == ',' && !quote) { fields.add(value.substring(start,i)); start = i+1; }
        }
        if (quote || escaped) throw new IOException("Unterminated BCF header quote"); fields.add(value.substring(start)); return fields;
    }
    private static Shape shape(ByteBuffer data) throws IOException {
        int descriptor = Byte.toUnsignedInt(data.get()), type = descriptor & 15, length = descriptor >>> 4;
        int width = switch (type) { case 0 -> 0; case 1,7 -> 1; case 2 -> 2; case 3,5 -> 4; default -> throw new IOException("Reserved BCF primitive type"); };
        if (length == 15) {
            int nested = Byte.toUnsignedInt(data.get()); if (nested >>> 4 != 1 || (nested & 15) < 1 || (nested & 15) > 3) throw new IOException("Invalid BCF extended vector length");
            length = scalarInt(data,nested & 15); if (length < 0) throw new IOException("Negative/reserved BCF vector length");
        }
        if (type == 0 && length != 0 || (long)length * width > data.remaining()) throw new IOException("BCF typed vector exceeds its block"); return new Shape(type,length,width);
    }
    private static String name(ByteBuffer data, Map<Integer,String> dictionary) throws IOException {
        Shape s = shape(data); if (s.length != 1 || s.type < 1 || s.type > 3) throw new IOException("BCF key must be one typed integer");
        String result = dictionary.get(scalarInt(data,s.type)); if (result == null) throw new IOException("BCF key absent from dictionary"); return result;
    }
    private static int scalarInt(ByteBuffer data,int type) throws IOException { return switch(type) { case 1 -> data.get(); case 2 -> data.getShort(); case 3 -> data.getInt(); default -> throw new IOException("Require BCF integer type"); }; }
    private static int missing(int type) throws IOException { return switch(type) { case 1 -> Byte.MIN_VALUE; case 2 -> Short.MIN_VALUE; case 3 -> Integer.MIN_VALUE; default -> throw new IOException("Require integer vector"); }; }
    private static List<String> integers(ByteBuffer data,Shape shape) throws IOException {
        if (shape.length == 0) return List.of(); int missing = missing(shape.type); var values = new ArrayList<String>(); boolean ended = false;
        for (int i = 0; i < shape.length; i++) {
            int value = scalarInt(data,shape.type); if (value == missing+1) { ended = true; continue; }
            if (ended || value >= missing+2 && value <= missing+7) throw new IOException("Reserved value or data after BCF vector end"); values.add(value == missing ? "." : Integer.toString(value));
        }
        return values;
    }
    private static String string(ByteBuffer data,Shape shape,boolean missingAllowed) throws IOException {
        if (shape.type != 7) throw new IOException("BCF string requires character type"); byte[] raw = new byte[shape.length]; data.get(raw); int length = 0;
        while (length < raw.length && raw[length] != 0) length++;
        for (int i = length; i < raw.length; i++) if (raw[i] != 0) throw new IOException("Nonzero BCF string padding");
        if (length == 0 || length == 1 && raw[0] == 7) { if (!missingAllowed) throw new IOException("Missing BCF allele string"); return "."; }
        String value = utf8(raw,0,length); if (value.codePoints().anyMatch(Character::isISOControl)) throw new IOException("Control character in BCF string"); return value;
    }
    private static long bgzfBlocks(Path source,long maximum,Runnable check) throws IOException {
        try (var input = new RandomAccessFile(source.toFile(),"r")) {
            if (input.length() > maximum) throw new IOException("BCF source byte capacity exceeded"); int a = input.read(), b = input.read(); if (a != 31 || b != 139) return 0;
            long position = 0, count = 0; boolean terminal = false;
            while (position < input.length()) {
                check.run(); input.seek(position); byte[] fixed = new byte[12]; input.readFully(fixed);
                if (fixed[0] != 31 || fixed[1] != (byte)139 || fixed[2] != 8 || fixed[3] != 4) throw new IOException("Require ordinary complete BGZF blocks for compressed BCF");
                int extraLength = (fixed[10]&255) | (fixed[11]&255)<<8; byte[] extra = new byte[extraLength]; input.readFully(extra); int size = -1;
                for (int i = 0; i < extra.length;) {
                    if (i+4 > extra.length) throw new IOException("Truncated BGZF extra field"); int len = (extra[i+2]&255) | (extra[i+3]&255)<<8;
                    if (i+4+len > extra.length) throw new IOException("BGZF extra length exceeds header");
                    if (extra[i] == 'B' && extra[i+1] == 'C') { if (len != 2 || size >= 0) throw new IOException("Duplicate/invalid BGZF BC subfield"); size = ((extra[i+4]&255) | (extra[i+5]&255)<<8)+1; } i += 4+len;
                }
                if (size < 12+extraLength+8 || size > 65536 || position+size > input.length()) throw new IOException("Invalid/truncated BGZF block size");
                input.seek(position+size-4); long expanded = readLe32(input); if (expanded > 65536) throw new IOException("BGZF expanded block exceeds65536");
                if (expanded == 0) { byte[] eof = new byte[size]; input.seek(position); input.readFully(eof); if (!java.util.Arrays.equals(eof,EOF_BLOCK) || position+size != input.length()) throw new IOException("Require one final canonical BGZF EOF block"); terminal = true; }
                position += size; count++;
            }
            if (!terminal) throw new IOException("Compressed BCF is missing its final BGZF EOF block"); return count;
        }
    }
    private static long readLe32(RandomAccessFile input) throws IOException { return Integer.toUnsignedLong(Integer.reverseBytes(input.readInt())); }
    private static long u32(InputStream input) throws IOException { int first = input.read(); if (first < 0) throw new EOFException("Truncated BCF length"); return u32(input,first); }
    private static long u32(InputStream input,int first) throws IOException { byte[] rest = exact(input,3); return (long)first | (long)(rest[0]&255)<<8 | (long)(rest[1]&255)<<16 | (long)(rest[2]&255)<<24; }
    private static byte[] exact(InputStream input,int count) throws IOException { byte[] bytes = input.readNBytes(count); if (bytes.length != count) throw new EOFException("Truncated complete BCF field"); return bytes; }
    private static String utf8(byte[] bytes,int offset,int length) throws IOException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,offset,length)).toString(); }
    private static MessageDigest hasher() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); } }
    private static void frame(MessageDigest digest,String value) { byte[] raw = value.getBytes(StandardCharsets.UTF_8); digest.update(ByteBuffer.allocate(4).putInt(raw.length).array()); digest.update(raw); }
    private static final class Bounded extends InputStream {
        private final InputStream input; private final long maximum; private final Runnable check; private long count;
        Bounded(InputStream input,long maximum,Runnable check) { this.input=input;this.maximum=maximum;this.check=check; }
        @Override public int read() throws IOException { check.run(); int value=input.read();if(value>=0&&++count>maximum)throw new IOException("BCF decoded byte capacity exceeded");return value; }
        @Override public int read(byte[] data,int off,int len)throws IOException {check.run();int n=input.read(data,off,(int)Math.min(len,Math.max(1,maximum-count+1)));if(n>0&&(count+=n)>maximum)throw new IOException("BCF decoded byte capacity exceeded");return n;}
        @Override public void close()throws IOException{input.close();}
    }

}
