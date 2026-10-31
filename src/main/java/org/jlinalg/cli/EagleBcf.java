/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.util.BlockCompressedInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded BCF2.2 to VCF decoding for Eagle, preserving encoded haploid/diploid GT. */
final class EagleBcf {
    private EagleBcf() { }
    record Report(String version, boolean bgzf, long bgzfBlocks, int samples, long variants,
            long genotypeCells, long decodedBytes, long outputBytes, long gtFirstReorderedRows,
            long noncanonicalNanPayloads, long legacyFirstPhaseBits, String sourceSha256, String outputSha256,
            String genotypeSha256, boolean complete, List<String> warnings) {
        Report { warnings = List.copyOf(warnings); }
    }
    private record Declaration(String id, String type, String number) { }
    private record Header(List<String> lines, List<String> samples, Map<Integer,String> contigs,
            Map<Integer,String> names, Map<String,Declaration> info, Map<String,Declaration> formats, Set<String> filters) { }
    private record Shape(int type, int length, int width) { }
    private static final byte[] EOF_BLOCK = HexFormat.of().parseHex("1f8b08040000000000ff0600424302001b0003000000000000000000");

    /** Independently parse the entire converted VCF and compare its GT wire digest. */
    static void auditConverted(Path vcf,Report report,EagleVcf.Limits limits,Runnable check) throws IOException {
        if (!report.complete() || !EagleCohortInputs.digest(vcf,limits.text().inputBytes(),check).equals(report.outputSha256())) throw new IOException("BCF conversion is incomplete or its output fingerprint differs");
        MessageDigest digest=hasher();frame(digest,"Eagle-BCF2.2-GT-v1");
        try (var input=report.samples()==0?EagleVcf.openSites(vcf,limits,check):EagleVcf.open(vcf,limits,check)) {
            for(String sample:input.samples())frame(digest,sample);
            for(EagleVcf.Row row;(row=input.next())!=null;) {
                for(String value:List.of(row.contig(),Integer.toString(row.position()),row.id(),row.reference(),row.alternate()))frame(digest,value);
                for(var call:row.calls())frame(digest,call.raw());
            }
            if (!input.report().complete() || input.samples().size()!=report.samples() || input.report().variants()!=report.variants()
                    || input.report().calls()!=report.genotypeCells() || !HexFormat.of().formatHex(digest.digest()).equals(report.genotypeSha256()))
                throw new IOException("Complete converted VCF GT/sample/identity population differs from BCF wire decoding");
        }
        if (!EagleCohortInputs.digest(vcf,limits.text().inputBytes(),check).equals(report.outputSha256())) throw new IOException("Converted VCF changed during independent audit");
    }

    static Report convert(Path source, Path newVcf, EagleVcf.Limits limits, long retainedBytes,
            long outputByteLimit, Runnable check) throws IOException {
        String originalHash = EagleCohortInputs.digest(source, limits.text().inputBytes(), check);
        Path destination = newVcf.toAbsolutePath().normalize();
        if (destination.getParent() == null || !destination.getParent().toRealPath().equals(destination.getParent())) throw new IOException("BCF conversion requires canonical parent and new output");
        long blocks = bgzfBlocks(source, limits.text().inputBytes(), check); boolean compressed = blocks != 0;
        var warnings = new ArrayList<String>();
        warnings.add("BCF CANONICAL TEXT: the complete binary source must remain protected. IDX header annotations are removed, finite Float32 values use round-trip decimal text, vector ends become variable-length VCF fields, and missing vector elements remain dots. This is semantic decoding, not recovery of an earlier VCF's lexical formatting.");
        long rows = 0, cells = 0, reordered = 0; long[] nanPayloads = {0}, firstPhaseBits = {0}; MessageDigest genotypeHash = hasher();
        try (var physical = new BufferedInputStream(Files.newInputStream(source), 65536);
             var decoded = compressed ? new BlockCompressedInputStream(physical) : physical;
             var input = new Bounded(decoded, limits.text().decodedCharacters(), check);
             var output = new Sink(destination, outputByteLimit)) {
            if (decoded instanceof BlockCompressedInputStream bgzf) bgzf.setCheckCrcs(true);
            byte[] magic = exact(input, 5);
            if (magic[0] != 'B' || magic[1] != 'C' || magic[2] != 'F' || magic[3] != 2) throw new IOException("Require BCF2 magic");
            if (magic[4] != 2) throw new EagleRecipe.UnavailableCapability("Native Eagle conversion currently admits BCF2.2; other editions require a separately verified conversion");
            long headerLength = u32(input);
            if (headerLength < 1 || headerLength > Integer.MAX_VALUE || headerLength > Math.multiplyExact(4L, limits.headerCharacters())
                    || Math.multiplyExact(headerLength, 16) > retainedBytes) throw new IOException("BCF header exceeds character/retained capacity");
            byte[] rawHeader = exact(input, (int)headerLength);
            if (rawHeader[rawHeader.length - 1] != 0) throw new IOException("BCF header must end in exactly one NUL");
            for (int i = 0; i < rawHeader.length - 1; i++) if (rawHeader[i] == 0) throw new IOException("Interior NUL in BCF header");
            Header header = header(utf8(rawHeader, 0, rawHeader.length - 1), limits);
            for (String line : header.lines) output.line(line);
            frame(genotypeHash, "Eagle-BCF2.2-GT-v1"); for (String sample : header.samples) frame(genotypeHash, sample);
            for (int first; (first = input.read()) >= 0;) {
                check.run(); long sharedSize = u32(input, first), individualSize = u32(input), recordSize = Math.addExact(sharedSize, individualSize);
                long estimate = Math.addExact(Math.multiplyExact(16, recordSize), Math.addExact(Math.multiplyExact(16, headerLength),
                        Math.addExact(Math.multiplyExact(256L, header.samples.size()), Math.multiplyExact(16L, limits.text().lineCharacters()))));
                if (sharedSize < 24 || sharedSize > Integer.MAX_VALUE || individualSize > Integer.MAX_VALUE || estimate > retainedBytes)
                    throw new IOException("BCF record framing or retained capacity exceeded");
                if (++rows > limits.text().variants()) throw new IOException("BCF complete variant capacity exceeded");
                cells = Math.addExact(cells, header.samples.size()); if (cells > limits.text().genotypeCells()) throw new IOException("BCF complete genotype-cell capacity exceeded");
                ByteBuffer shared = ByteBuffer.wrap(exact(input, (int)sharedSize)).order(ByteOrder.LITTLE_ENDIAN);
                ByteBuffer individual = ByteBuffer.wrap(exact(input, (int)individualSize)).order(ByteOrder.LITTLE_ENDIAN);
                int contigIndex = shared.getInt(), zeroPosition = shared.getInt(), referenceLength = shared.getInt(), qual = shared.getInt();
                int infoCount = Short.toUnsignedInt(shared.getShort()), alleleCount = Short.toUnsignedInt(shared.getShort()), sampleAndFormats = shared.getInt();
                int sampleCount = sampleAndFormats & 0xffffff, formatCount = sampleAndFormats >>> 24;
                String contig = header.contigs.get(contigIndex);
                if (contig == null || zeroPosition < 0 || zeroPosition == Integer.MAX_VALUE || referenceLength < 1
                        || alleleCount < 1 || alleleCount - 1 > limits.alternateAlleles() || sampleCount != header.samples.size()
                        || formatCount > limits.formatFields() || sampleCount == 0 && (formatCount != 0 || individualSize != 0)) throw new IOException("BCF fixed dimensions/contig/position are invalid");
                String id = string(shared, shape(shared), true), ref = string(shared, shape(shared), false); var alternatives = new ArrayList<String>();
                for (int i = 1; i < alleleCount; i++) alternatives.add(string(shared, shape(shared), false));
                String alt = alternatives.isEmpty() ? "." : String.join(",", alternatives);
                Shape filterShape = shape(shared); List<String> filterValues = integers(shared, filterShape); var filters = new ArrayList<String>();
                for (String value : filterValues) {
                    if (value.equals(".")) { if (filterValues.size() != 1) throw new IOException("Missing FILTER mixed with filter IDs"); filters.add("."); }
                    else { String name = header.names.get(Integer.parseInt(value)); if (name == null || !header.filters.contains(name)) throw new IOException("Unknown BCF FILTER dictionary entry"); filters.add(name); }
                }
                if (filters.size() > 1 && filters.contains("PASS")) throw new IOException("PASS mixed with failing filters");
                var info = new ArrayList<String>(); var infoKeys = new HashSet<String>();
                for (int i = 0; i < infoCount; i++) {
                    String key = name(shared, header.names); Declaration declaration = header.info.get(key);
                    if (declaration == null || !infoKeys.add(key)) throw new IOException("Undeclared/duplicate BCF INFO key");
                    Shape shape = shape(shared);
                    if (declaration.type.equals("Flag")) { flag(shared, shape); info.add(key); }
                    else info.add(key + "=" + value(shared, shape, declaration, nanPayloads, true));
                }
                if (shared.hasRemaining()) throw new IOException("Unconsumed BCF shared bytes");
                String[] rawGt = new String[sampleCount]; StringBuilder[] sampleFields = new StringBuilder[sampleCount];
                for (int i = 0; i < sampleCount; i++) sampleFields[i] = new StringBuilder();
                var formatKeys = new ArrayList<String>(); var formatSet = new HashSet<String>(); long textCharacters = 0; int gtOffset = -1;
                for (int f = 0; f < formatCount; f++) {
                    String key = name(individual, header.names); Declaration declaration = header.formats.get(key);
                    if (declaration == null || !formatSet.add(key)) throw new IOException("Undeclared/duplicate BCF FORMAT key");
                    formatKeys.add(key); Shape shape = shape(individual);
                    if (key.equals("GT")) gtOffset = f;
                    long expectedBytes = Math.multiplyExact(Math.multiplyExact((long)shape.length, shape.width), sampleCount);
                    if (expectedBytes > individual.remaining()) throw new IOException("BCF FORMAT payload exceeds individual block");
                    for (int sample = 0; sample < sampleCount; sample++) {
                        String cell = key.equals("GT") ? genotype(individual, shape, alleleCount, firstPhaseBits) : value(individual, shape, declaration, nanPayloads, false);
                        if (key.equals("GT")) rawGt[sample] = cell;
                        textCharacters = Math.addExact(textCharacters, cell.length() + 1L);
                        if (textCharacters > limits.text().lineCharacters()) throw new IOException("Decoded BCF sample fields exceed VCF line capacity");
                        if (f != 0) sampleFields[sample].append(':'); sampleFields[sample].append(cell);
                    }
                }
                if (individual.hasRemaining()) throw new IOException("Unconsumed BCF individual bytes");
                // VCF requires GT first. Preserve all other keys and their cells.
                if (gtOffset > 0) {
                    reordered++; formatKeys.remove(gtOffset); formatKeys.add(0, "GT");
                    for (int sample = 0; sample < sampleCount; sample++) {
                        String[] fields = sampleFields[sample].toString().split(":", -1); var normalized = new ArrayList<String>(); normalized.add(fields[gtOffset]);
                        for (int f = 0; f < fields.length; f++) if (f != gtOffset) normalized.add(fields[f]); sampleFields[sample] = new StringBuilder(String.join(":", normalized));
                    }
                }
                var row = new StringBuilder();
                for (String field : List.of(contig, Integer.toString(zeroPosition + 1), id, ref, alt, floating(qual, nanPayloads),
                        filters.isEmpty() ? "." : String.join(";", filters), info.isEmpty() ? "." : String.join(";", info))) { if (row.length() != 0) row.append('\t'); row.append(field); }
                if (sampleCount != 0) {
                    row.append('\t').append(formatKeys.isEmpty() ? "." : String.join(":", formatKeys));
                    for (var sample : sampleFields) row.append('\t').append(sample.length() == 0 ? "." : sample);
                }
                if (row.length() > limits.text().lineCharacters()) throw new IOException("Decoded BCF complete VCF row exceeds capacity");
                for (String field : List.of(contig, Integer.toString(zeroPosition + 1), id, ref, alt)) frame(genotypeHash, field);
                for (String gt : rawGt) frame(genotypeHash, gt == null ? "GT-ABSENT" : gt);
                output.line(row.toString());
            }
            if (!EagleCohortInputs.digest(source, limits.text().inputBytes(), check).equals(originalHash)) throw new IOException("BCF source changed during conversion");
            if (reordered != 0) warnings.add("BCF FORMAT ORDER: GT was moved to the first FORMAT position in " + reordered + " rows for VCF; per-key values were preserved.");
            if (nanPayloads[0] != 0) warnings.add("BCF NaN PAYLOADS: " + nanPayloads[0] + " noncanonical NaN payloads are represented as NaN in text; original payload bits remain only in the protected binary source.");
            if (firstPhaseBits[0] != 0) warnings.add("LEGACY FIRST PHASE BIT: " + firstPhaseBits[0] + " first-allele bits have no leading-phase text representation in VCF4.0-4.3 and are ignored as by the original reader; subsequent separators and ploidy are preserved. Newer VCF syntax requires separate admission.");
            return new Report("BCF2.2", compressed, blocks, header.samples.size(), rows, cells, input.count, output.count, reordered, nanPayloads[0], firstPhaseBits[0],
                    originalHash, output.digest(), HexFormat.of().formatHex(genotypeHash.digest()), true, warnings);
        } catch (java.nio.BufferUnderflowException | ArithmeticException | htsjdk.samtools.SAMException invalid) { throw new IOException("Malformed, CRC-invalid or excessive BCF framing", invalid); }
    }

    private static Header header(String text, EagleVcf.Limits limits) throws IOException {
        var lines = new ArrayList<String>(); var contigs = new HashMap<Integer,String>(); var names = new HashMap<Integer,String>(); names.put(0,"PASS");
        var nameIndices = new HashMap<String,Integer>(); nameIndices.put("PASS",0); var info = new HashMap<String,Declaration>(); var formats = new HashMap<String,Declaration>(); var filters = new HashSet<String>(); filters.add("PASS");
        List<String> samples = null; int implicitName = 1, implicitContig = 0; Boolean tagIndexed = null, contigIndexed = null; var declaredFilters = new HashSet<String>();
        if (text.length() > limits.headerCharacters()) throw new IOException("BCF decoded header character capacity exceeded");
        String[] physicalLines = text.split("\n", -1);
        for (int ordinal = 0; ordinal < physicalLines.length; ordinal++) {
            String raw = physicalLines[ordinal];
            if (raw.isEmpty() && ordinal == physicalLines.length-1 && text.endsWith("\n") && samples != null) continue;
            String line = raw.endsWith("\r") ? raw.substring(0,raw.length()-1) : raw;
            if (line.length() > limits.text().lineCharacters() || lines.size() >= limits.headerLines() || samples != null) throw new IOException("Invalid/excessive BCF header lines");
            if (lines.isEmpty() && !line.matches("##fileformat=VCFv4[.][0-9]+")) throw new IOException("Require VCF4 fileformat in BCF header");
            if (lines.isEmpty() && !line.matches("##fileformat=VCFv4[.][0-3]"))
                throw new EagleRecipe.UnavailableCapability("Native Eagle BCF conversion currently admits VCF4.0-4.3 header semantics; newer leading-phase syntax requires separate admission");
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
                if (f.length != 8 && f.length < 10 || f.length > limits.text().samples()+9) throw new IOException("BCF sample header width/capacity invalid");
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
    private static String genotype(ByteBuffer data,Shape shape,int alleles,long[] firstPhaseBits) throws IOException {
        if (shape.type < 1 || shape.type > 3 || shape.length < 1) throw new IOException("GT requires an explicit integer vector");
        int end = missing(shape.type)+1, count = 0; boolean ended = false; var gt = new StringBuilder();
        for (int i = 0; i < shape.length; i++) {
            int value = scalarInt(data,shape.type); if (value == end) { ended = true; continue; }
            if (value < 0 || ended || ++count > 2) throw new IOException("Eagle BCF GT requires explicit haploid/diploid calls without data after vector end");
            if (count == 1 && (value & 1) != 0) firstPhaseBits[0]++;
            int allele = (value >>> 1)-1; if (allele >= alleles) throw new IOException("BCF GT exceeds declared alleles");
            if (count > 1) gt.append((value & 1) == 1 ? '|' : '/'); gt.append(allele < 0 ? "." : Integer.toString(allele));
        }
        if (count == 0) throw new IOException("BCF GT has no declared allele slots"); return gt.toString();
    }
    private static String value(ByteBuffer data,Shape shape,Declaration declaration,long[] nan,boolean info) throws IOException {
        if (shape.length == 0) return ".";
        if (declaration.type.equals("String") || declaration.type.equals("Character")) {
            if (shape.type != 7) throw new IOException("BCF string field has non-character type"); String result = string(data,shape,true);
            if (result.indexOf('\t') >= 0 || result.indexOf('\n') >= 0 || result.indexOf('\r') >= 0 || result.indexOf(';') >= 0 && info || result.indexOf(':') >= 0 && !info)
                throw new IOException("BCF string cannot be represented in the admitted VCF field without escaping"); return result;
        }
        if (declaration.type.equals("Integer")) { List<String> values = integers(data,shape); return values.isEmpty() ? "." : String.join(",",values); }
        if (!declaration.type.equals("Float") || shape.type != 5) throw new IOException("BCF declared/encoded primitive types differ");
        var values = new ArrayList<String>(); boolean ended = false;
        for (int i = 0; i < shape.length; i++) {
            int bits = data.getInt(); if (bits == 0x7f800002) { ended = true; continue; }
            if (ended) throw new IOException("Float data follows BCF vector end"); values.add(floating(bits,nan));
        }
        return values.isEmpty() ? "." : String.join(",",values);
    }
    private static void flag(ByteBuffer data,Shape shape) throws IOException {
        if (shape.type == 0 && shape.length == 0) return;
        if (shape.length != 1 || shape.type < 1 || shape.type > 3) throw new IOException("Unsupported BCF flag representation"); int value = scalarInt(data,shape.type);
        if (value >= missing(shape.type) && value <= missing(shape.type)+7) throw new IOException("Reserved/missing BCF INFO flag");
    }
    private static String floating(int bits,long[] nan) throws IOException {
        if (bits == 0x7f800001) return "."; if (bits >= 0x7f800002 && bits <= 0x7f800007) throw new IOException("Reserved float or vector-end used as a value");
        float value = Float.intBitsToFloat(bits);
        if (Float.isNaN(value)) { if (bits != 0x7fc00000) nan[0]++; return "NaN"; }
        if (Float.isInfinite(value)) return value < 0 ? "-Inf" : "Inf"; return Float.toString(value);
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
    private static final class Sink implements AutoCloseable {
        private final BufferedOutputStream output; private final long maximum; private final MessageDigest digest=hasher(); private long count;
        Sink(Path path,long maximum)throws IOException{output=new BufferedOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW));this.maximum=maximum;}
        void line(String line)throws IOException{byte[] raw=(line+"\n").getBytes(StandardCharsets.UTF_8);if(raw.length>maximum-count)throw new IOException("BCF converted output byte capacity exceeded");output.write(raw);digest.update(raw);count+=raw.length;}
        String digest()throws IOException{output.flush();return HexFormat.of().formatHex(digest.digest());}
        @Override public void close()throws IOException{output.close();}
    }
}
