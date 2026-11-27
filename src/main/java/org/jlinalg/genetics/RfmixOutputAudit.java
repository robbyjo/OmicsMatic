/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Read-only audit of original RFMix v2.03-r0 (09599c19) output tables.
 * This does not infer ancestry or establish a supplied contract's provenance.
 * The upstream SIS header/writer defects remain explicit diagnostic limitations.
 */
public final class RfmixOutputAudit {
    private RfmixOutputAudit() { }

    /** A retained, filtered VCF locus with the unrounded interpolated map position in cM.
     * The audit explicitly applies the original source's binary32 SNP-map storage.
     */
    public record Locus(long position, double geneticCm) { }
    /** A reference sample and its literal declared population. */
    public record Reference(String sample, String population) { }
    /** Full source populations and explicit model settings; never derive these from the output being checked. */
    public record Contract(String chromosome, List<String> querySamples, List<Reference> references,
            List<Locus> retainedLoci, double crfSpacing, int requestedEmIterations,
            boolean reanalyzeReference, boolean automaticCrfWeight) {
        public Contract {
            querySamples = List.copyOf(querySamples);
            references = List.copyOf(references);
            retainedLoci = List.copyOf(retainedLoci);
            if (chromosome == null || chromosome.isEmpty() || querySamples.isEmpty() || references.isEmpty()
                    || retainedLoci.isEmpty() || !Double.isFinite(crfSpacing) || crfSpacing <= 0
                    || crfSpacing > Integer.MAX_VALUE || requestedEmIterations < 0)
                throw new IllegalArgumentException("Invalid explicit RFMix source contract");
            Set<String> ids = new HashSet<>();
            for (String id : querySamples) if (!identifier(id) || !ids.add(id)) throw new IllegalArgumentException("Invalid/duplicate query identity");
            for (Reference reference : references)
                if (!identifier(reference.sample) || !populationLabel(reference.population) || !ids.add(reference.sample))
                    throw new IllegalArgumentException("Invalid/duplicate/overlapping reference identity");
            long previous = 0;
            double mapPrevious = Double.NEGATIVE_INFINITY;
            for (Locus locus : retainedLoci) {
                if (locus.position <= previous || !Double.isFinite(locus.geneticCm) || !Float.isFinite((float) locus.geneticCm)
                        || locus.geneticCm < mapPrevious)
                    throw new IllegalArgumentException("Retained loci must have increasing positive coordinates and finite nondecreasing cM");
                previous = locus.position;
                mapPrevious = locus.geneticCm;
            }
        }
    }
    /** Explicit per-file and per-table complete-population capacities; exceeding one fails rather than truncates. */
    public record Limits(long fileBytes, int lineCharacters, int loci, int samples, int populations, long numericCells) {
        public Limits {
            if (fileBytes < 1 || lineCharacters < 1 || loci < 1 || samples < 1 || populations < 1 || numericCells < 1)
                throw new IllegalArgumentException("Positive RFMix audit capacities required");
        }
    }
    /** Exact immutable file identity. */
    public record Fingerprint(long bytes, String sha256) { }
    /** All SIS numeric rows are checked, but its source header and final-iteration meaning are not repaired/certified. */
    public record SisDiagnostic(int rows, int headerColumns, int dataColumns,
            boolean sourceHeaderDataMismatch, boolean finalIterationCertified) { }
    /** Core table integrity and cross-table checks, with original output identities and explicit SIS limitations. */
    public record Report(List<String> populations, int retainedLoci, int markers, int segments,
            int querySamples, int qRows, long posteriorCells, long qCells,
            Map<String, List<Double>> queryFractionsFromMspMarkers,
            SisDiagnostic sis, Map<String, Fingerprint> files, List<String> warnings) {
        public Report {
            populations = List.copyOf(populations);
            queryFractionsFromMspMarkers = Collections.unmodifiableMap(new TreeMap<>(queryFractionsFromMspMarkers));
            files = Collections.unmodifiableMap(new TreeMap<>(files));
            warnings = List.copyOf(warnings);
        }
    }
    private record Segment(int start, int end, int[] states) { }

    /** Reproduce the inspected source's CRF point selection, including its ceiling/end-collapse behavior.
     * Values below one specify cM; values at least one increment an integer SNP index,
     * truncating fractional increments as in the source. The unreachable nearest-SNP
     * adjustment is deliberately not replaced with a nearest-neighbor rule.
     */
    public static List<Integer> markerIndices(List<Locus> loci, double spacing) {
        if (loci.isEmpty() || !Double.isFinite(spacing) || spacing <= 0 || spacing > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid CRF grid source domain");
        double previous = Double.NEGATIVE_INFINITY;
        for (Locus locus : loci) {
            if (!Double.isFinite(locus.geneticCm) || !Float.isFinite((float) locus.geneticCm) || locus.geneticCm < previous)
                throw new IllegalArgumentException("CRF grid requires finite nondecreasing genetic positions");
            previous = locus.geneticCm;
        }
        List<Integer> result = new ArrayList<>();
        if (spacing >= 1) {
            long step = (int) spacing;
            for (long index = 0; index < loci.size(); index += step) result.add((int) index);
        } else {
            int index = 0;
            result.add(index);
            double last = sourceCm(loci.get(loci.size() - 1));
            while (index < loci.size() - 1) {
                double target = sourceCm(loci.get(index)) + spacing;
                if (last - target < spacing / 2) target = last;
                int next = index + 1;
                while (next < loci.size() - 1 && sourceCm(loci.get(next)) < target) next++;
                result.add(next);
                index = next;
            }
        }
        return List.copyOf(result);
    }

    /** Audit all four original files. MSP/FB/Q have complete independent contract checks.
     * SIS remains an auxiliary diagnostic, with every row checked but its known header
     * discrepancy and unclosed iteration streams explicitly unqualified.
     * @throws IOException on changed, malformed, incomplete, nonfinite or capacity-exceeding output
     */
    public static Report audit(Path msp, Path fb, Path q, Path sis, Contract contract, Limits limits) throws IOException {
        require(contract.retainedLoci.size() <= limits.loci && contract.querySamples.size() + contract.references.size() <= limits.samples,
                "RFMix complete source population exceeds capacity");
        Map<String, Path> paths = Map.of("msp", msp, "fb", fb, "Q", q, "sis", sis);
        Map<String, Fingerprint> before = new LinkedHashMap<>();
        for (var entry : paths.entrySet()) before.put(entry.getKey(), fingerprint(entry.getValue(), limits.fileBytes));
        List<Integer> markers = markerIndices(contract.retainedLoci, contract.crfSpacing);
        List<String> populations;
        List<Segment> segments = new ArrayList<>();
        try (Table table = new Table(msp, limits, before.get("msp"))) {
            String prefix = "#Subpopulation order/codes: ";
            String first = table.line();
            require(first != null && first.startsWith(prefix), "Missing original MSP population header");
            populations = new ArrayList<>();
            for (String field : first.substring(prefix.length()).split("\t", -1)) {
                int split = field.lastIndexOf('=');
                require(split > 0 && integer(field.substring(split + 1)) == populations.size(), "Nonconsecutive MSP population code");
                populations.add(field.substring(0, split));
            }
            Set<String> expected = new HashSet<>();
            for (Reference reference : contract.references) expected.add(reference.population);
            require(populations.size() <= limits.populations && new HashSet<>(populations).size() == populations.size()
                    && new HashSet<>(populations).equals(expected), "MSP population identity/set differs from reference contract");
            List<String> header = new ArrayList<>(List.of("#chm", "spos", "epos", "sgpos", "egpos", "n snps"));
            header.addAll(haplotypes(contract.querySamples));
            table.header(header);
            int count = 0;
            String[] fields;
            while ((fields = table.row(header.size())) != null) {
                require(segments.size() < markers.size(), "Excess MSP segments");
                require(fields[0].equals(contract.chromosome), "MSP chromosome differs");
                long size = integer(fields[5]);
                require(size > 0 && size <= contract.retainedLoci.size() - count, "Invalid MSP retained-SNP count");
                int end = count + (int) size;
                Locus startLocus = contract.retainedLoci.get(count);
                Locus endLocus = contract.retainedLoci.get(end == contract.retainedLoci.size() ? end - 1 : end);
                require(integer(fields[1]) == startLocus.position && integer(fields[2]) == endLocus.position,
                        "MSP source interval/count boundaries differ; do not reinterpret as BED/inclusive intervals");
                rounded(number(fields[3]), sourceCm(startLocus), .005000000001, "MSP start cM");
                rounded(number(fields[4]), sourceCm(endLocus), .005000000001, "MSP end cM");
                int[] states = new int[2 * contract.querySamples.size()];
                for (int i = 0; i < states.length; i++) {
                    long state = integer(fields[6 + i]);
                    require(state >= 0 && state < populations.size(), "Invalid MSP ancestry state");
                    states[i] = (int) state;
                }
                if (!segments.isEmpty()) require(!Arrays.equals(segments.get(segments.size() - 1).states, states), "Unmerged identical adjacent MSP state vectors");
                segments.add(new Segment(count, end, states));
                count = end;
            }
            require(count == contract.retainedLoci.size() && !segments.isEmpty(), "MSP omits retained input loci");
        }
        long posteriorCells = product(markers.size(), 2L * contract.querySamples.size(), populations.size());
        require(posteriorCells <= limits.numericCells, "Posterior cell population exceeds capacity");
        long[][] counts = new long[contract.querySamples.size()][populations.size()];
        List<String> fbHeader = new ArrayList<>(List.of("chromosome", "physical_position", "genetic_position", "genetic_marker_index"));
        for (String sample : contract.querySamples) for (int h = 1; h <= 2; h++)
            for (String population : populations) fbHeader.add(sample + ":::hap" + h + ":::" + population);
        try (Table table = new Table(fb, limits, before.get("fb"))) {
            table.header(populationHeader("#reference_panel_population:", populations));
            table.header(fbHeader);
            int row = 0, segment = 0;
            String[] fields;
            while ((fields = table.row(fbHeader.size())) != null) {
                require(row < markers.size(), "Excess posterior marker row");
                int marker = markers.get(row);
                coordinate(fields, marker, contract, "FB");
                while (marker >= segments.get(segment).end) segment++;
                require(marker >= segments.get(segment).start, "FB marker lacks an MSP interval");
                int offset = 4;
                for (int sample = 0; sample < contract.querySamples.size(); sample++) for (int h = 0; h < 2; h++) {
                    double sum = 0;
                    for (int p = 0; p < populations.size(); p++) sum += probability(fields[offset++]);
                    // Original EF16/DF16 encode log odds to integer steps of 1/1024.
                    // Logistic derivative <=1/4; printed five-place rounding adds 0.000005.
                    require(Math.abs(sum - 1) <= populations.size() * (1.0 / 4096 + .000005) + 1e-12,
                            "Posterior family exceeds the source log-odds quantization/printing bound");
                    counts[sample][segments.get(segment).states[2 * sample + h]]++;
                }
                row++;
            }
            require(row == markers.size(), "Missing posterior CRF markers");
        }
        List<String> qSamples = new ArrayList<>(contract.querySamples);
        if (contract.requestedEmIterations > 0) for (Reference reference : contract.references) qSamples.add(reference.sample);
        long qCells = product(qSamples.size(), populations.size());
        require(qCells <= limits.numericCells, "Q cell population exceeds capacity");
        try (Table table = new Table(q, limits, before.get("Q"))) {
            require("#rfmix diploid global ancestry .Q format output".equals(table.line()), "Missing original Q heading");
            table.header(populationHeader("#sample", populations));
            for (int i = 0; i < qSamples.size(); i++) {
                String[] fields = table.row(populations.size() + 1);
                require(fields != null && fields[0].equals(qSamples.get(i)), "Q sample population/order differs");
                double sum = 0;
                for (int p = 0; p < populations.size(); p++) {
                    double actual = probability(fields[p + 1]);
                    sum += actual;
                    if (i < contract.querySamples.size()) rounded(actual, counts[i][p] / (2.0 * markers.size()), .000005000001,
                            "Q differs from equal-CRF-marker-weight MSP states");
                    else if (!contract.reanalyzeReference) {
                        Reference reference = contract.references.get(i - contract.querySamples.size());
                        rounded(actual, reference.population.equals(populations.get(p)) ? 1 : 0, .000005000001, "Q fixed reference ancestry differs");
                    }
                }
                require(Math.abs(sum - 1) <= populations.size() * .000005000001, "Q fractions do not sum within original printing precision");
            }
            require(table.line() == null, "Excess Q samples/rows");
        }
        SisDiagnostic diagnostic;
        try (Table table = new Table(sis, limits, before.get("sis"))) {
            String first = table.line();
            require(first != null, "Missing SIS header");
            String[] fields = first.split("\t", -1);
            List<String> prefix = new ArrayList<>(List.of("#chm", "pos", "gpos", "snp idx"));
            List<String> all = new ArrayList<>(contract.querySamples);
            for (Reference reference : contract.references) all.add(reference.sample);
            prefix.addAll(haplotypes(all));
            require(fields.length >= prefix.size() && (fields.length - prefix.size()) % 2 == 0, "Unexpected SIS header population");
            for (int i = 0; i < prefix.size(); i++) require(fields[i].equals(prefix.get(i)), "SIS original query/reference header order differs");
            if (!contract.automaticCrfWeight) require(fields.length == prefix.size(), "Unexpected internally simulated SIS samples");
            for (int i = prefix.size(); i < fields.length; i += 2)
                require(fields[i].endsWith(".0") && fields[i + 1].equals(fields[i].substring(0, fields[i].length() - 2) + ".1"), "Invalid internal-simulation SIS haplotype pair");
            int columns = 4 + 2 * contract.querySamples.size(), row = 0;
            String[] data;
            while ((data = table.row(columns)) != null) {
                require(row < markers.size() - 1, "Excess SIS rows");
                coordinate(data, markers.get(row), contract, "SIS");
                for (int i = 4; i < data.length; i++) probability(data[i]);
                row++;
            }
            require(row == markers.size() - 1, "Missing SIS diagnostic marker rows");
            diagnostic = new SisDiagnostic(row, fields.length, columns, fields.length != columns, false);
        }
        for (var entry : paths.entrySet()) require(fingerprint(entry.getValue(), limits.fileBytes).equals(before.get(entry.getKey())), "Original table changed during audit");
        Map<String, List<Double>> fractions = new LinkedHashMap<>();
        for (int i = 0; i < contract.querySamples.size(); i++) {
            List<Double> values = new ArrayList<>();
            for (long count : counts[i]) values.add(count / (2.0 * markers.size()));
            fractions.put(contract.querySamples.get(i), List.copyOf(values));
        }
        List<String> warnings = new ArrayList<>(List.of(
                "SIS original header includes reference/internal-simulation samples absent from its numeric rows; original bytes are retained, not repaired.",
                "Original SIS writer leaves iteration streams open. Final-iteration SIS probabilities are not certified; do not use this auxiliary file for a dependent SIS analysis.",
                "Original SNP map positions use binary32 storage before CRF point selection; this audit explicitly reproduces that storage without changing the supplied map values.",
                "Q uses equal weights per CRF marker, not SNP, physical-length or genetic-length weights. MSP boundary coordinates are original source conventions, not an inferred BED conversion.",
                "A core table audit does not infer ancestry, validate input-contract provenance or qualify a complete tool/paper workflow."));
        if (contract.reanalyzeReference) warnings.add("Reanalyzed-reference Q probabilities have finite/population/printing checks only; query-only MSP/FB cannot independently reconstruct reference paths.");
        return new Report(populations, contract.retainedLoci.size(), markers.size(), segments.size(), contract.querySamples.size(),
                qSamples.size(), posteriorCells, qCells, fractions, diagnostic, before, warnings);
    }

    private static boolean identifier(String value) {
        return value != null && !value.isEmpty() && value.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c));
    }
    private static boolean populationLabel(String value) {
        return value != null && !value.isEmpty() && value.chars().noneMatch(Character::isISOControl);
    }
    private static List<String> haplotypes(List<String> samples) {
        List<String> result = new ArrayList<>();
        for (String sample : samples) { result.add(sample + ".0"); result.add(sample + ".1"); }
        return result;
    }
    private static List<String> populationHeader(String first, List<String> populations) {
        List<String> result = new ArrayList<>(); result.add(first); result.addAll(populations); return result;
    }
    private static void coordinate(String[] fields, int marker, Contract contract, String kind) throws IOException {
        Locus locus = contract.retainedLoci.get(marker);
        require(fields[0].equals(contract.chromosome) && integer(fields[1]) == locus.position && integer(fields[3]) == marker,
                kind + " source marker identity/order differs");
        rounded(number(fields[2]), sourceCm(locus), .000005000001, kind + " genetic map position");
    }
    private static double sourceCm(Locus locus) { return (double) (float) locus.geneticCm; }
    private static long product(long... values) throws IOException {
        long result = 1;
        try { for (long value : values) result = Math.multiplyExact(result, value); }
        catch (ArithmeticException overflow) { throw new IOException("RFMix cell population overflows capacity", overflow); }
        return result;
    }
    private static long integer(String value) throws IOException {
        try { require(value.matches("0|[1-9][0-9]*"), "Noncanonical source integer"); return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid source integer", invalid); }
    }
    private static double number(String value) throws IOException {
        try {
            require(value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"), "Invalid/nonfinite decimal source token");
            double number = Double.parseDouble(value); require(Double.isFinite(number), "Nonfinite source number"); return number;
        }
        catch (NumberFormatException invalid) { throw new IOException("Invalid source number", invalid); }
    }
    private static double probability(String value) throws IOException {
        double number = number(value); require(number >= 0 && number <= 1, "Source probability outside [0,1]"); return number;
    }
    private static void rounded(double actual, double expected, double bound, String label) throws IOException {
        require(Math.abs(actual - expected) <= bound, label + " differs beyond source printing precision");
    }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
    private static Fingerprint fingerprint(Path path, long maximum) throws IOException {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Missing/nonregular original RFMix output: " + path);
        long expected = Files.size(path);
        require(expected > 0 && expected <= maximum, "Original output byte capacity exceeded/empty");
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        long count = 0;
        try (var stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = stream.read(buffer)) >= 0;) { interrupted(); count += n; require(count <= maximum, "Growing output exceeds byte capacity"); digest.update(buffer, 0, n); }
        }
        require(count == expected && count == Files.size(path), "Output length changed during fingerprint");
        return new Fingerprint(count, HexFormat.of().formatHex(digest.digest()));
    }
    private static final class Table implements AutoCloseable {
        private final BufferedReader reader;
        private final Counting input;
        private final MessageDigest digest;
        private final Fingerprint expected;
        private final int maximum;
        private boolean eof;
        Table(Path path, Limits limits, Fingerprint expected) throws IOException {
            this.expected = expected;
            try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
            input = new Counting(Files.newInputStream(path), limits.fileBytes);
            reader = new BufferedReader(new InputStreamReader(new DigestInputStream(input, digest),
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
            maximum = limits.lineCharacters;
        }
        String line() throws IOException {
            interrupted();
            StringBuilder line = new StringBuilder();
            for (int c; (c = reader.read()) != -1;) {
                if (c == '\n') {
                    if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
                    return line.toString();
                }
                require(line.length() < maximum, "Original table line exceeds character capacity"); line.append((char) c);
            }
            eof = true;
            return line.isEmpty() ? null : line.toString();
        }
        void header(List<String> expected) throws IOException { require(String.join("\t", expected).equals(line()), "Original table header identity/order differs"); }
        String[] row(int width) throws IOException {
            String line = line(); if (line == null) return null;
            String[] fields = line.split("\t", -1); require(fields.length == width, "Original table numeric row width differs"); return fields;
        }
        @Override public void close() throws IOException {
            reader.close();
            require(eof && expected.bytes == input.count && expected.sha256.equals(HexFormat.of().formatHex(digest.digest())),
                    "Actual parsed table byte stream differs or was not consumed completely");
        }
    }
    private static void interrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("RFMix output audit cancelled");
    }
    private static final class Counting extends FilterInputStream {
        private final long maximum;
        private long count;
        Counting(InputStream input, long maximum) { super(input); this.maximum = maximum; }
        @Override public int read() throws IOException {
            int value = in.read();
            if (value != -1) { count++; require(count <= maximum, "Parsed output stream exceeds byte capacity"); }
            return value;
        }
        @Override public int read(byte[] target, int offset, int length) throws IOException {
            int size = in.read(target, offset, length);
            if (size > 0) { count += size; require(count <= maximum, "Parsed output stream exceeds byte capacity"); }
            return size;
        }
    }
}
