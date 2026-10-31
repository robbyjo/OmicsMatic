/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jlinalg.genetics.EagleGenotypes;

/** Complete bounded GT-preserving VCF admission for the Eagle workflow. */
final class EagleVcf {
    private EagleVcf() { }
    record Limits(MinimacVcf.Limits text, int headerLines, long headerCharacters,
            int formatFields, int alternateAlleles, long positionKeyCharacters) {
        Limits {
            Objects.requireNonNull(text, "text");
            if (headerLines < 1 || headerCharacters < 1 || formatFields < 1 || formatFields > 65536
                    || alternateAlleles < 1 || alternateAlleles > 65536 || positionKeyCharacters < 1)
                throw new IllegalArgumentException("Invalid complete Eagle VCF header/field capacities");
        }
        static Limits defaults() {
            return new Limits(new MinimacVcf.Limits(100000, 1000000, 100000000, 1073741824L,
                    8589934592L, 16777216, 4294967296L, 1000000), 10000, 33554432, 1024, 1024, 4194304);
        }
    }
    record Row(long row, String contig, int position, String positionText, String id, String reference, String alternate,
            String quality, String filter, String info, List<String> formats,
            List<String> originalSampleFields, List<EagleGenotypes.Call> calls) {
        Row { formats = List.copyOf(formats); originalSampleFields = List.copyOf(originalSampleFields); calls = List.copyOf(calls); }
        String identity() { return contig + "\t" + position + "\t" + id + "\t" + reference + "\t" + alternate; }
        String alleleKey() { return contig + "\t" + position + "\t" + reference + "\t" + alternate; }
        int alternateCount() { return alternate.equals(".") ? 0 : alternate.split(",", -1).length; }
    }
    record Report(long variants, long calls, long encodedAlleles, long observedAlleles,
            long fullyMissingCalls, long partiallyMissingCalls, long phasedDiploidCalls,
            long unphasedDiploidCalls, long haploidCalls, long decodedCharacters, boolean gzip,
            boolean complete, Set<String> undeclaredNonGtFormats) {
        Report { undeclaredNonGtFormats = Set.copyOf(undeclaredNonGtFormats); }
    }

    static Reader open(Path source, Limits limits, Runnable check) throws IOException { return new Reader(source, limits, check, false); }
    static Reader openSites(Path source, Limits limits, Runnable check) throws IOException { return new Reader(source, limits, check, true); }

    /** Uses only the shared bounded text decoder; never invokes Minimac's phased-GT parser. */
    static final class Reader implements AutoCloseable {
        private final Path source;
        private final Limits limits;
        private final Runnable check;
        private final MinimacVcf.Lines lines;
        private final BasicFileAttributes initial;
        private final List<String> header, samples;
        private final Set<String> declaredFormats;
        private final Set<String> seenContigs = new HashSet<>(), positionAlleles = new HashSet<>(), undeclared = new HashSet<>();
        private String currentContig;
        private int lastPosition;
        private long variants, calls, alleles, observed, fullMissing, partialMissing, phased, unphased, haploid, positionCharacters;
        private boolean complete, closed, failed;

        private Reader(Path path, Limits limits, Runnable check, boolean allowSitesOnly) throws IOException {
            this.source = path.toAbsolutePath().normalize(); this.limits = Objects.requireNonNull(limits, "limits"); this.check = Objects.requireNonNull(check, "check");
            check.run(); MinimacVcf.regular(source, limits.text().inputBytes());
            initial = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            lines = new MinimacVcf.Lines(source, limits.text());
            try {
                var headers = new ArrayList<String>(); var formats = new HashSet<String>(); Map<String,String> gt = null;
                String line = nextLine(); long characters = 0;
                if (line == null || !line.matches("##fileformat=VCFv4[.][0-9]+")) throw error("Require original VCFv4 fileformat first; no BOM or repair");
                List<String> found = null;
                for (;;) {
                    if (headers.size() == limits.headerLines() || line.length() > limits.headerCharacters() - characters)
                        throw error("Complete VCF header capacity exceeded");
                    headers.add(line); characters += line.length();
                    if (line.startsWith("##FORMAT=")) {
                        var def = definition(line.substring(9)); String id = def.get("ID");
                        if (!token(id) || !formats.add(id)) throw error("Duplicate or invalid FORMAT definition");
                        if (id.equals("GT")) gt = def;
                    } else if (!line.startsWith("##")) {
                        String[] fields = line.split("\t", limits.text().samples() + 10);
                        String[] fixed = {"#CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO", "FORMAT"};
                        if (allowSitesOnly && fields.length == 8) {
                            for (int i = 0; i < 8; i++) if (!fields[i].equals(fixed[i])) throw error("Invalid fixed sample-free exclusion VCF header");
                            found = List.of(); break;
                        }
                        if (fields.length < 10 || fields.length - 9 > limits.text().samples()) throw error("Require bounded nonempty genotype sample population");
                        for (int i = 0; i < fixed.length; i++) if (!fields[i].equals(fixed[i])) throw error("Invalid fixed VCF column header");
                        found = List.of(java.util.Arrays.copyOfRange(fields, 9, fields.length));
                        if (found.stream().anyMatch(s -> !token(s)) || new HashSet<>(found).size() != found.size()) throw error("Sample IDs must be exact distinct tokens");
                        if (gt == null || !"1".equals(gt.get("Number")) || !"String".equals(gt.get("Type"))) throw error("Require declared GT Number=1 Type=String; no dosage/likelihood inference");
                        break;
                    }
                    line = nextLine(); if (line == null) throw error("Missing complete VCF column header");
                }
                header = List.copyOf(headers); samples = List.copyOf(found); declaredFormats = Set.copyOf(formats);
            } catch (IOException | RuntimeException failure) {
                try { lines.close(); } catch (IOException closing) { failure.addSuppressed(closing); }
                throw failure;
            }
        }
        List<String> header() { return header; }
        List<String> samples() { return samples; }
        Report report() { return new Report(variants, calls, alleles, observed, fullMissing, partialMissing,
                phased, unphased, haploid, lines.characters, lines.gzip, complete, undeclared); }

        Row next() throws IOException {
            if (closed || failed) throw error("Closed/failed VCF scans cannot continue");
            if (complete) return null;
            try {
                String line = nextLine();
                if (line == null) { verify(); check.run(); complete = true; return null; }
                if (line.isEmpty() || line.startsWith("#")) throw error("Unexpected blank/header line in genotype population");
                if (variants == limits.text().variants() || calls > limits.text().genotypeCells() - samples.size()) throw error("Complete VCF row/genotype capacity exceeded");
                String[] fields = line.split("\t", samples.size() + 10);
                if (fields.length != (samples.isEmpty() ? 8 : samples.size() + 9)) throw error("Exact row width differs from complete sample header");
                for (int i : new int[]{0,2,3,4,5,6,7}) if (!token(fields[i])) throw error("Invalid empty/control/whitespace fixed VCF token");
                int position = position(fields[1]);
                String[] alternate = fields[4].equals(".") ? new String[0] : fields[4].split(",", limits.alternateAlleles() + 1);
                if (fields[3].equals(".") || fields[3].indexOf(',') >= 0 || alternate.length > limits.alternateAlleles()) throw error("Invalid REF/ALT declarations or allele capacity");
                var distinct = new HashSet<String>();
                for (String alt : alternate) if (!token(alt) || alt.equals(".") || alt.equals(fields[3]) || !distinct.add(alt)) throw error("Invalid or duplicate REF/ALT allele token");
                if (!fields[0].equals(currentContig)) {
                    if (!seenContigs.add(fields[0]) || seenContigs.size() > 10000) throw error("VCF contig reappears or exceeds complete contig capacity");
                    currentContig = fields[0]; lastPosition = 0; positionAlleles.clear(); positionCharacters = 0;
                }
                if (position < lastPosition) throw error("VCF positions decrease within contig");
                if (position != lastPosition) { positionAlleles.clear(); positionCharacters = 0; }
                String positionKey = fields[3] + "\0" + fields[4];
                if (positionKey.length() > limits.positionKeyCharacters() - positionCharacters) throw error("Complete same-position allele-key character capacity exceeded");
                if (!positionAlleles.add(positionKey)) throw error("Duplicate exact variant allele key; no silent deduplication");
                positionCharacters += positionKey.length();
                lastPosition = position;
                String[] format = samples.isEmpty() ? new String[0] : fields[8].split(":", limits.formatFields() + 1);
                if (format.length > limits.formatFields()) throw error("FORMAT capacity exceeded");
                var used = new HashSet<String>(); int gt = -1;
                for (int i = 0; i < format.length; i++) {
                    if (!token(format[i]) || !used.add(format[i])) throw error("Invalid or duplicate FORMAT field");
                    if (format[i].equals("GT")) gt = i;
                    else if (!declaredFormats.contains(format[i]) && !undeclared.contains(format[i])) {
                        if (undeclared.size() == limits.formatFields()) throw error("Complete undeclared non-GT FORMAT inventory capacity exceeded");
                        undeclared.add(format[i]);
                    }
                }
                if (gt < 0 && !samples.isEmpty()) throw error("Every genotype row requires explicit GT; no DS/GP/PL reconstruction");
                var result = new ArrayList<EagleGenotypes.Call>(samples.size()); var rawSamples = new ArrayList<String>(samples.size());
                long rowAlleles = 0, rowObserved = 0, rowFull = 0, rowPartial = 0, rowPhased = 0, rowUnphased = 0, rowHaploid = 0;
                for (int s = 0; s < samples.size(); s++) {
                    check.run(); String raw = fields[9 + s]; String[] values = raw.split(":", format.length + 1);
                    if (values.length > format.length || gt >= values.length) throw error("GT absent or sample FORMAT width exceeds its declaration at sample " + (s + 1));
                    EagleGenotypes.Call call;
                    try { call = EagleGenotypes.parse(values[gt], alternate.length); }
                    catch (IllegalArgumentException bad) { throw error("Invalid GT at sample " + (s + 1) + ": " + bad.getMessage()); }
                    result.add(call); rawSamples.add(raw); rowAlleles += call.ploidy(); rowObserved += call.observedAlleles();
                    if (call.partiallyMissing()) rowPartial++; else if (call.missing()) rowFull++;
                    if (call.ploidy() == 1) rowHaploid++; else if (call.phased()) rowPhased++; else rowUnphased++;
                }
                variants++; calls += samples.size(); alleles = Math.addExact(alleles, rowAlleles); observed = Math.addExact(observed, rowObserved);
                fullMissing += rowFull; partialMissing += rowPartial; phased += rowPhased; unphased += rowUnphased; haploid += rowHaploid;
                return new Row(variants, fields[0], position, fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], fields[7], List.of(format), rawSamples, result);
            } catch (IOException | RuntimeException failure) { failed = true; throw failure; }
        }
        private String nextLine() throws IOException { check.run(); return lines.next(); }
        private void verify() throws IOException {
            MinimacVcf.regular(source, limits.text().inputBytes()); var now = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (now.size() != initial.size() || !now.lastModifiedTime().equals(initial.lastModifiedTime()) || !Objects.equals(now.fileKey(), initial.fileKey()))
                throw error("VCF source identity/size/modification time changed during admission; caller must also verify protected file hashes");
        }
        private IOException error(String cause) { return new IOException("Eagle VCF " + source + ", line " + lines.line + ": " + cause); }
        @Override public void close() throws IOException { if (!closed) { closed = true; lines.close(); } }
        private int position(String value) throws IOException {
            if (!value.matches("[0-9]+")) throw error("Require positive decimal VCF position");
            try { int n = Integer.parseInt(value); if (n > 0) return n; }
            catch (NumberFormatException ignored) { /* classified below */ }
            throw error("VCF position exceeds positive signed32 capacity");
        }
        private Map<String,String> definition(String text) throws IOException {
            if (!text.startsWith("<") || !text.endsWith(">")) throw error("Invalid structured FORMAT definition");
            var out = new LinkedHashMap<String,String>(); var field = new StringBuilder(); boolean quoted = false, escaped = false;
            for (int i = 1; i < text.length(); i++) {
                char c = text.charAt(i); boolean end = i == text.length() - 1;
                if (end || c == ',' && !quoted) {
                    if (quoted || escaped) throw error("Unclosed FORMAT quote");
                    String item = field.toString().trim(); int eq = item.indexOf('=');
                    if (out.size() == 64 || eq < 1 || out.putIfAbsent(item.substring(0, eq).trim(), item.substring(eq + 1).trim()) != null) throw error("Invalid duplicate or over-capacity FORMAT attributes");
                    field.setLength(0); continue;
                }
                field.append(c); if (escaped) { escaped = false; continue; }
                if (quoted && c == '\\') { escaped = true; continue; } if (c == '"') quoted = !quoted;
            }
            return out;
        }
    }
    private static boolean token(String value) { return value != null && !value.isEmpty() && value.codePoints().noneMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)); }
}
