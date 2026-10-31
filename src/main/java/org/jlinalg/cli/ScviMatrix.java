/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.util.SortingCollection;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit raw-count or signed-expression admission; no normalization, HVG selection or cell filtering. */
final class ScviMatrix {
    private ScviMatrix() { }
    enum Kind { RAW_COUNTS, SIGNED_EXPRESSION }
    enum Precision { EXACT_FLOAT32, REVIEWED_FLOAT32_ROUNDING }
    enum Duplicates { REJECT, SUM_SOURCE_ORDER }
    record Limits(long inputBytes, long decodedBytes, long workspaceBytes, long retainedBytes,
                  int files, int genes, int cells, long entries, int sortRecords, int lineCharacters) {
        Limits {
            if (inputBytes < 1 || decodedBytes < 1 || workspaceBytes < 1 || retainedBytes < 1 || files < 12
                    || genes < 1 || cells < 1 || entries < 0 || sortRecords < 1 || lineCharacters < 32 || lineCharacters > 1_048_576)
                throw new IllegalArgumentException("Invalid scVI matrix capacities");
        }
    }
    record Request(ScviText.Source matrix, ScviText.Source genes, ScviText.Source cells,
                   Kind kind, Precision precision, Duplicates duplicates, Limits limits) {
        Request {
            Objects.requireNonNull(matrix); Objects.requireNonNull(genes); Objects.requireNonNull(cells);
            Objects.requireNonNull(kind); Objects.requireNonNull(precision); Objects.requireNonNull(duplicates); Objects.requireNonNull(limits);
            if (kind == Kind.RAW_COUNTS && precision != Precision.EXACT_FLOAT32)
                throw new IllegalArgumentException("Raw counts require exact float32 canonical values; no count rounding is inferred");
        }
    }
    record Summary(long inputEntries, long canonicalEntries, long duplicateEntries, long zeroInputEntries,
                   long roundedCoordinates, long underflowCoordinates, double maximumAbsoluteRoundingError,
                   long decodedBytes, long reservedWorkspaceBytes, long reservedRetainedBytes) { }
    record Prepared(Path directory, Request request, List<String> genes, List<String> cells,
                    List<ScviText.Snapshot> sources, Map<String,String> fileHashes, Summary summary) {
        Prepared { genes = List.copyOf(genes); cells = List.copyOf(cells); sources = List.copyOf(sources); fileHashes = Map.copyOf(fileHashes); }
        void verify(Runnable check) throws IOException {
            for (var source : sources) source.verify(request.limits().inputBytes(), check);
            var actual = inventory(directory, request.limits(), check);
            if (!actual.equals(fileHashes)) throw new IOException("Prepared scVI input bytes or complete file population changed");
        }
    }
    private record Entry(int cell, int gene, long ordinal, long bits) { }
    private static final long MAXIMUM_INTEGER = 9_007_199_254_740_991L;
    private static final BigDecimal MAXIMUM_DECIMAL = BigDecimal.valueOf(MAXIMUM_INTEGER);

    static Prepared prepare(Request request, Path output, Runnable check) throws IOException {
        Objects.requireNonNull(check); Limits limits = request.limits(); Path root = output.toAbsolutePath().normalize();
        if (root.getParent() == null || !Files.isDirectory(root.getParent(), LinkOption.NOFOLLOW_LINKS) || !root.getParent().toRealPath().equals(root.getParent()))
            throw new IOException("scVI preparation requires a canonical existing parent and a NEW output directory");
        Files.createDirectory(root); Path encoded = Files.createDirectory(root.resolve("sources"));
        var snapshots = new ArrayList<ScviText.Snapshot>(); long inputBytes = 0;
        for (var pair : List.of(Map.entry("matrix", request.matrix()), Map.entry("genes", request.genes()), Map.entry("cells", request.cells()))) {
            var snapshot = ScviText.copy(pair.getValue(), encoded.resolve(pair.getKey()+".input"), limits.inputBytes()-inputBytes, check);
            snapshots.add(snapshot); inputBytes = Math.addExact(inputBytes, snapshot.bytes());
            capacity(root, limits, check);
        }
        var decoded = new ScviText.DecodedBudget(limits.decodedBytes()); long[] retained = {2_097_152L + 8L * limits.lineCharacters()};
        List<String> genes = identifiers(source(snapshots.get(1)), limits.genes(), limits, retained, decoded, check);
        List<String> cells = identifiers(source(snapshots.get(2)), limits.cells(), limits, retained, decoded, check);
        retained[0] = Math.addExact(retained[0], Math.multiplyExact(32L, cells.size()));
        long idBytes = 0; for (String id : genes) idBytes += id.getBytes(StandardCharsets.UTF_8).length + 1L;
        for (String id : cells) idBytes += id.getBytes(StandardCharsets.UTF_8).length + 1L;
        Path scratch = Files.createDirectory(root.resolve("sort")); SortingCollection<Entry> sorter = null;
        try (var input = lines(source(snapshots.get(0)), decoded, limits, check)) {
            String header = input.next();
            if (header == null || !header.matches("%%MatrixMarket matrix coordinate (integer|real) general")) throw new IOException("Require genes-by-cells Matrix Market coordinate integer|real general");
            String dimension = nextData(input); if (dimension == null) throw new IOException("Missing scVI matrix dimensions");
            String[] fields = dimension.trim().split("\\s+"); if (fields.length != 3) throw new IOException("Require three scVI matrix dimensions");
            int rows = positive(fields[0]), columns = positive(fields[1]); long declared = nonnegative(fields[2]);
            if (rows != genes.size() || columns != cells.size() || declared > limits.entries()) throw new IOException("scVI dimensions differ from complete ID populations or entries exceed capacity");
            long runs = declared == 0 ? 0 : Math.floorDiv(declared-1, limits.sortRecords())+1;
            long reservedWork = Math.addExact(inputBytes, Math.addExact(Math.multiplyExact(384L, declared),
                    Math.addExact(4*idBytes+128L*cells.size()+2_097_152L, Math.multiplyExact(65536L, runs))));
            long reservedMemory = Math.addExact(retained[0], Math.addExact(96L*Math.min(declared, limits.sortRecords()), Math.multiplyExact(131072L, runs)));
            if (reservedWork > limits.workspaceBytes() || reservedMemory > limits.retainedBytes() || runs+12 > limits.files())
                throw new IOException("scVI complete sort/output reservation exceeds workspace, retained-memory or member capacity; review limits/sort_records without reducing data");
            sorter = SortingCollection.newInstance(Entry.class, new Codec(), Comparator.comparingInt(Entry::cell).thenComparingInt(Entry::gene).thenComparingLong(Entry::ordinal), limits.sortRecords(), scratch);
            long entries = 0, zeros = 0; String line;
            while ((line = nextData(input)) != null) {
                check.run(); if (++entries > declared) throw new IOException("More scVI matrix entries than declared");
                String[] f = line.trim().split("\\s+"); if (f.length != 3) throw new IOException("Require gene,cell,value on each scVI matrix row");
                int gene = positive(f[0])-1, cell = positive(f[1])-1;
                if (gene >= rows || cell >= columns) throw new IOException("scVI matrix coordinate outside complete ID populations");
                long bits;
                if (request.kind() == Kind.RAW_COUNTS) { long count = rawCount(f[2]); bits = count; if (count == 0) zeros++; }
                else { double value = expression(f[2]); bits = Double.doubleToRawLongBits(value); if (value == 0) zeros++; }
                // Zero entries remain in sorting so duplicates cannot be concealed by an explicit zero.
                sorter.add(new Entry(cell, gene, entries, bits));
                if ((entries & 65535) == 0) capacity(root, limits, check);
            }
            if (entries != declared) throw new IOException("Fewer scVI matrix entries than declared");
            sorter.doneAdding(); capacity(root, limits, check);
            long[] totals = request.kind() == Kind.RAW_COUNTS ? new long[cells.size()] : null;
            double[] signed = new double[cells.size()], absolute = new double[cells.size()]; int[] detected = new int[cells.size()];
            long[] statistics = new long[4]; double[] rounding = {0}; Path body = root.resolve("matrix-body.tmp");
            try (var iterator = sorter.iterator(); var writer = Files.newBufferedWriter(body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                 var audit = Files.newBufferedWriter(root.resolve("float32-conversion.tsv"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                audit.write("cell_index\tgene_index\tcanonical_source_sum\tmodel_float32\tchange\n"); Entry pending = null; long integer = 0; double real = 0;
                while (iterator.hasNext()) {
                    check.run(); Entry entry = iterator.next();
                    if (pending != null && (pending.cell() != entry.cell() || pending.gene() != entry.gene())) {
                        emit(writer, audit, pending, integer, real, request, totals, signed, absolute, detected, statistics, rounding); pending = null;
                    }
                    if (pending != null) { statistics[1]++; if (request.duplicates() == Duplicates.REJECT) throw new IOException("Duplicate scVI coordinate, including explicit zero: cell="+(entry.cell()+1)+",gene="+(entry.gene()+1)); }
                    else { pending = entry; integer = 0; real = 0; }
                    if (request.kind() == Kind.RAW_COUNTS) { integer = Math.addExact(integer, entry.bits()); if (integer > MAXIMUM_INTEGER) throw new IOException("Aggregated raw scVI count exceeds exact integer capacity"); }
                    else { real += Double.longBitsToDouble(entry.bits()); if (!Double.isFinite(real)) throw new IOException("Signed-expression sum in source order became nonfinite"); }
                }
                if (pending != null) emit(writer, audit, pending, integer, real, request, totals, signed, absolute, detected, statistics, rounding);
            }
            if (totals != null) for (int i = 0; i < totals.length; i++) if (totals[i] == 0) throw new IOException("Raw scVI count cell has zero library total; no cell was filtered: " + cells.get(i));
            try (var out = Files.newOutputStream(root.resolve("matrix.mtx"), StandardOpenOption.CREATE_NEW)) {
                out.write(("%%MatrixMarket matrix coordinate "+(request.kind() == Kind.RAW_COUNTS ? "integer" : "real")+" general\n% Explicit canonical float32 model input; gene by cell; no normalization/filtering\n"+rows+" "+columns+" "+statistics[0]+"\n").getBytes(StandardCharsets.US_ASCII)); Files.copy(body, out);
            }
            Files.delete(body); writeIds(root.resolve("genes.tsv"), genes); writeIds(root.resolve("cells.tsv"), cells);
            try (var out = Files.newBufferedWriter(root.resolve("cell-summary.tsv"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                out.write("cell_id\tmodel_value_sum\tabsolute_model_value_sum\tdetected_features\n");
                for (int i = 0; i < cells.size(); i++) out.write(cells.get(i)+"\t"+(totals == null ? Double.toString(signed[i]) : Long.toString(totals[i]))+"\t"+absolute[i]+"\t"+detected[i]+"\n");
            }
            Summary summary = new Summary(entries, statistics[0], statistics[1], zeros, statistics[2], statistics[3], rounding[0], decoded.bytes(), reservedWork, reservedMemory);
            var record = new LinkedHashMap<String,Object>(); record.put("component_status", "PREPARED_MATRIX_ONLY"); record.put("model_analysis_completed", false); record.put("kind", request.kind().name()); record.put("precision", request.precision().name()); record.put("duplicates", request.duplicates().name());
            record.put("genes", rows); record.put("cells", columns); record.put("input_entries", entries); record.put("canonical_entries", statistics[0]); record.put("duplicate_entries", statistics[1]); record.put("zero_input_entries", zeros); record.put("rounded_coordinates", statistics[2]); record.put("underflow_coordinates", statistics[3]); record.put("maximum_absolute_rounding_error", rounding[0]); record.put("decoded_bytes", decoded.bytes());
            record.put("sources", snapshots.stream().map(s -> Map.of("original", s.original().toString(), "copy", root.relativize(s.copy()).toString(), "encoding", s.encoding().name(), "bytes", s.bytes(), "sha256", s.sha256())).toList());
            Files.writeString(root.resolve("admission.json"), RegenieRuntime.json(record)+"\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            for (var snapshot : snapshots) snapshot.verify(limits.inputBytes(), check);
            sorter.cleanup(); sorter = null; Files.delete(scratch); capacity(root, limits, check);
            return new Prepared(root, request, genes, cells, snapshots, inventory(root, limits, check), summary);
        } catch (IllegalArgumentException | ArithmeticException failure) { throw new IOException("Invalid scVI matrix numeric value/capacity: "+failure.getMessage(), failure); }
        finally { if (sorter != null) sorter.cleanup(); if (Files.isDirectory(scratch)) try { Files.delete(scratch); } catch (DirectoryNotEmptyException remainingScratch) { /* preserve failed owned scratch */ } }
    }

    private static void emit(Writer out, Writer audit, Entry entry, long integer, double real, Request request,
                             long[] totals, double[] signed, double[] absolute, int[] detected, long[] statistics, double[] rounding) throws IOException {
        double value = request.kind() == Kind.RAW_COUNTS ? integer : real; float model = (float)value;
        if (!Float.isFinite(model)) throw new IOException("scVI model float32 conversion overflow");
        if ((double)model != value) {
            if (request.precision() == Precision.EXACT_FLOAT32) throw new IOException("scVI canonical coordinate is not exactly representable in float32: cell="+(entry.cell()+1)+",gene="+(entry.gene()+1));
            statistics[2]++; boolean underflow = model == 0 && value != 0; if (underflow) statistics[3]++;
            rounding[0] = Math.max(rounding[0], Math.abs((double)model-value));
            audit.write((entry.cell()+1)+"\t"+(entry.gene()+1)+"\t"+value+"\t"+(double)model+"\t"+(underflow?"UNDERFLOW_TO_ZERO":"ROUNDED_FLOAT32")+"\n");
        }
        if (model == 0) return;
        statistics[0]++; int cell = entry.cell(); detected[cell] = Math.addExact(detected[cell], 1); signed[cell] += model; absolute[cell] += Math.abs((double)model);
        if (totals != null) { totals[cell] = Math.addExact(totals[cell], integer); if (totals[cell] > MAXIMUM_INTEGER) throw new IOException("Raw scVI cell total exceeds exact integer capacity"); }
        out.write((entry.gene()+1)+" "+(cell+1)+" "+(totals == null ? Double.toString((double)model) : Long.toString(integer))+"\n");
    }
    private static ScviText.Source source(ScviText.Snapshot s) { return new ScviText.Source(s.copy(), s.encoding()); }
    private static ScviText.Lines lines(ScviText.Source source, ScviText.DecodedBudget budget, Limits limits, Runnable check) throws IOException { return new ScviText.Lines(source, budget, limits.lineCharacters(), check); }
    private static String nextData(ScviText.Lines input) throws IOException { String line; while ((line = input.next()) != null) if (!line.isBlank() && !line.startsWith("%")) return line; return null; }
    private static List<String> identifiers(ScviText.Source source, int maximum, Limits limits, long[] retained, ScviText.DecodedBudget budget, Runnable check) throws IOException {
        var result = new ArrayList<String>(); var seen = new HashSet<String>();
        try (var in = lines(source, budget, limits, check)) { String line; while ((line = in.next()) != null) {
            if (line.isEmpty() || line.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)) || !seen.add(line)) throw new IOException("scVI IDs require unique nonempty whitespace/control-free UTF-8 identifiers");
            if (result.size() >= maximum) throw new IOException("Complete scVI ID population exceeds declared capacity");
            retained[0] = Math.addExact(retained[0], 192L + 4L*line.length()); if (retained[0] > limits.retainedBytes()) throw new IOException("scVI ID retained capacity exceeded"); result.add(line);
        }}
        if (result.isEmpty()) throw new IOException("scVI ID population is empty"); return List.copyOf(result);
    }
    private static void writeIds(Path path, List<String> ids) throws IOException { try (var out = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) { for (String id : ids) out.write(id+"\n"); } }
    private static int positive(String s) throws IOException { long n = nonnegative(s); if (n < 1 || n > Integer.MAX_VALUE) throw new IOException("Positive signed-32-bit scVI dimension/index required"); return (int)n; }
    private static long nonnegative(String s) throws IOException { if (!s.matches("[0-9]{1,18}")) throw new IOException("Unsigned decimal scVI dimension/count required"); return Long.parseLong(s); }
    private static long rawCount(String s) throws IOException {
        if (s.length() > 128) throw new IOException("Excessive raw-count numeric token"); BigDecimal n = new BigDecimal(s);
        if (n.signum() < 0 || n.compareTo(MAXIMUM_DECIMAL) > 0 || n.stripTrailingZeros().scale() > 0) throw new IOException("Raw scVI input requires exact nonnegative integer counts <=2^53-1; transformed values are not inferred as counts"); return n.longValueExact();
    }
    private static double expression(String s) throws IOException { if (s.length() > 128) throw new IOException("Excessive expression numeric token"); BigDecimal n = new BigDecimal(s); double d = n.doubleValue(); if (!Double.isFinite(d) || d == 0 && n.signum() != 0) throw new IOException("Nonfinite or float64-underflowing signed scVI expression value"); return d; }
    static Map<String,String> inventory(Path root, Limits limits, Runnable check) throws IOException {
        capacity(root, limits, check); var result = new TreeMap<String,String>();
        int members = 0;
        try (var paths = Files.walk(root)) { for (var iterator = paths.iterator(); iterator.hasNext();) { Path p = iterator.next(); check.run();
            if (++members > limits.files() || Files.isSymbolicLink(p) || !p.toRealPath().equals(p.toAbsolutePath().normalize())) throw new IOException("Prepared scVI input member population or canonical path changed");
            String name = root.relativize(p).toString().replace('\\','/');
            if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) result.put(name, ScviText.hash(p, limits.workspaceBytes(), check));
            else if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) result.put(name, "DIRECTORY");
            else throw new IOException("Nonordinary member in prepared scVI inputs");
        }}
        return Map.copyOf(result);
    }
    private static void capacity(Path root, Limits limits, Runnable check) throws IOException {
        long bytes = 0; int members = 0;
        try (var paths = Files.walk(root)) { for (var iterator = paths.iterator(); iterator.hasNext();) { Path p = iterator.next(); check.run();
            if (++members > limits.files() || Files.isSymbolicLink(p) || !p.toRealPath().equals(p.toAbsolutePath().normalize())) throw new IOException("scVI workspace member capacity or canonical-path policy violated");
            if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) bytes = Math.addExact(bytes, Files.size(p));
            else if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Nonordinary member in scVI workspace");
            if (bytes > limits.workspaceBytes()) throw new IOException("Complete scVI preparation workspace capacity exceeded");
        }}
    }
    private static final class Codec implements SortingCollection.Codec<Entry> {
        private DataInputStream input; private DataOutputStream output;
        @Override public void setInputStream(InputStream stream) { input = new DataInputStream(stream); }
        @Override public void setOutputStream(OutputStream stream) { output = new DataOutputStream(stream); }
        @Override public void encode(Entry e) { try { output.writeInt(e.cell()); output.writeInt(e.gene()); output.writeLong(e.ordinal()); output.writeLong(e.bits()); } catch (IOException failure) { throw new UncheckedIOException(failure); } }
        @Override public Entry decode() { try { int first = input.read(); if (first < 0) return null; int cell = first<<24 | input.readUnsignedByte()<<16 | input.readUnsignedByte()<<8 | input.readUnsignedByte(); return new Entry(cell, input.readInt(), input.readLong(), input.readLong()); } catch (IOException failure) { throw new UncheckedIOException(failure); } }
        @Override public Codec clone() { return new Codec(); }
    }
}
