/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;
import org.jlinalg.genetics.Pops;
import org.jlinalg.genetics.PopsChunked;
import org.jlinalg.genetics.PopsCovariance;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Literal identifier joins and complete prepared PoPS input bindings. */
final class PopsWorkflowInputs {
    record Gene(String chromosome, long tss) { }
    record Plan(int file, int firstLocal, int width, int firstGlobal) { }
    record Data(List<String> predictions, List<String> targets, double[] target, int[] targetRows,
                double[][] covariates, PopsCovariance covariance, boolean[][] masks,
                Pops.Selection selection, Features features, Map<String, String> diagnostics) { }

    static final class Features implements PopsChunked.Features {
        final List<String> ids, names;
        final List<PopsWorkflowNpy> files;
        final List<Plan> plans;
        final Consumer<String> progress;
        int reads;
        Features(List<String> ids, List<String> names, List<PopsWorkflowNpy> files, List<Plan> plans, Consumer<String> progress) {
            this.ids = List.copyOf(ids); this.names = List.copyOf(names); this.files = List.copyOf(files); this.plans = List.copyOf(plans); this.progress = progress;
        }
        public int rows() { return ids.size(); }
        public int columns() { return names.size(); }
        public int batches() { return plans.size(); }
        public PopsChunked.Batch read(int ordinal) throws IOException {
            Plan plan = plans.get(ordinal); reads++;
            if (ordinal == 0 || ordinal == plans.size() - 1 || reads % 100 == 0) progress.accept("Reading feature batch " + (ordinal + 1) + "/" + plans.size() + ", read " + reads);
            return new PopsChunked.Batch(plan.firstGlobal, files.get(plan.file).columns(plan.firstLocal, plan.width));
        }
    }

    private PopsWorkflowInputs() { }

    static Config capture(Path requestDirectory, Config original, Path root) throws IOException {
        Map<String, String> effective = new TreeMap<>(original.values());
        Map<Path, Path> captures = new LinkedHashMap<>();
        add(captures, requestDirectory, original.get("gene_annotation"), root.resolve("inputs/gene-annotation.tsv")); effective.put("gene_annotation", "inputs/gene-annotation.tsv");
        Path prefix = resolve(requestDirectory, original.get("feature_prefix"));
        add(captures, requestDirectory, prefix + ".rows.txt", root.resolve("inputs/features.rows.txt"));
        for (int i = 0; i < original.integer("feature_chunks"); i++) {
            add(captures, requestDirectory, prefix + ".cols." + i + ".txt", root.resolve("inputs/features.cols." + i + ".txt"));
            add(captures, requestDirectory, prefix + ".mat." + i + ".npy", root.resolve("inputs/features.mat." + i + ".npy"));
        }
        effective.put("feature_prefix", "inputs/features");
        if (original.magma()) {
            Path magma = resolve(requestDirectory, original.get("magma_prefix"));
            add(captures, requestDirectory, magma + ".genes.out", root.resolve("inputs/magma.genes.out"));
            add(captures, requestDirectory, magma + ".genes.raw", root.resolve("inputs/magma.genes.raw")); effective.put("magma_prefix", "inputs/magma");
        } else {
            add(captures, requestDirectory, original.get("scores"), root.resolve("inputs/scores.tsv")); effective.put("scores", "inputs/scores.tsv");
            for (String key : List.of("score_covariates", "error_covariance")) if (original.get(key) != null) {
                add(captures, requestDirectory, original.get(key), root.resolve("inputs/" + key)); effective.put(key, "inputs/" + key);
            }
        }
        for (String key : List.of("subset_features", "control_features")) if (original.get(key) != null) {
            add(captures, requestDirectory, original.get(key), root.resolve("inputs/" + key + ".txt")); effective.put(key, "inputs/" + key + ".txt");
        }
        if (captures.size() + 30L > original.integer("max_files")) throw new IOException("Capture and workflow records exceed max_files; increase capacity without dropping inputs");
        long bytes = 0; StringBuilder records = new StringBuilder("source\tcaptured\tbytes\tsha256\n");
        for (var entry : captures.entrySet()) {
            bytes = Math.addExact(bytes, Files.size(entry.getKey())); if (bytes > original.number("max_input_bytes")) throw new IOException("Complete captured inputs exceed max_input_bytes");
            copy(entry.getKey(), entry.getValue(), original.number("max_input_bytes"));
            records.append(entry.getKey()).append('\t').append(root.relativize(entry.getValue()).toString().replace('\\', '/')).append('\t').append(Files.size(entry.getValue())).append('\t').append(sha(entry.getValue())).append('\n');
        }
        // A coherent captured snapshot is established before reading any model data.
        for (var entry : captures.entrySet()) if (!sha(entry.getKey()).equals(sha(entry.getValue()))) throw new IOException("Original input changed during multi-file capture: " + entry.getKey());
        write(root.resolve("CAPTURE.tsv"), records.toString()); return config(asProperties(effective));
    }
    private static void add(Map<Path, Path> files, Path base, String source, Path target) throws IOException {
        Path path = resolve(base, source); if (files.putIfAbsent(path, target) != null) throw new IOException("The same file was supplied for different input roles: " + path);
    }
    static Path input(Path root, String name) throws IOException {
        Path path = resolve(root, name), inputs = root.resolve("inputs").toAbsolutePath().normalize();
        if (!path.startsWith(inputs)) throw new IOException("Worker inputs must use the captured inputs directory");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) throw new IOException("Captured input unavailable: " + path);
        return path;
    }

    static Data prepare(Path root, Config c, Consumer<String> progress) throws IOException {
        return prepare(root, c, progress, true);
    }
    static Data layout(Path root, Config c) throws IOException { return prepare(root, c, message -> { }, false); }
    private static Data prepare(Path root, Config c, Consumer<String> progress, boolean numerical) throws IOException {
        Features features = features(root, c, progress); Map<String, Gene> annotation = annotation(input(root, c.get("gene_annotation")), c);
        Path scoreFile = c.magma() ? input(root, c.get("magma_prefix") + ".genes.out") : input(root, c.get("scores"));
        List<String> ids = new ArrayList<>(); List<Double> scores = new ArrayList<>(); Set<String> distinct = new HashSet<>();
        try (var reader = Files.newBufferedReader(scoreFile)) {
            String[] header = header(reader.readLine(), c.magma()); int gene = column(header, c.magma() ? "GENE" : "ENSGID"), score = column(header, c.magma() ? "ZSTAT" : "Score");
            for (String line; (line = reader.readLine()) != null;) {
                if (ids.size() >= c.integer("max_target_genes")) throw new IOException("Target gene count exceeds capacity");
                String[] row = row(line, header.length, c.magma()); String id = identifier(row[gene], c.integer("max_identifier_chars"));
                if (!distinct.add(id)) throw new IOException("Duplicate target gene: " + id);
                ids.add(id); scores.add(finite(row[score]));
            }
        }
        if (ids.size() < 2) throw new IOException("At least two finite target genes required");
        double[] target = scores.stream().mapToDouble(Double::doubleValue).toArray();
        Map<String, Integer> predictionRows = new HashMap<>(); for (int i = 0; i < features.ids.size(); i++) predictionRows.put(features.ids.get(i), i);
        int[] mapping = new int[ids.size()]; String[] chromosomes = new String[ids.size()]; long[] tss = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            Integer position = predictionRows.get(ids.get(i)); Gene gene = annotation.get(ids.get(i));
            if (position == null || gene == null) throw new IOException("Target gene lacks its exact feature-row or annotation identifier: " + ids.get(i) + "; no identifier repair is performed");
            mapping[i] = position; chromosomes[i] = gene.chromosome; tss[i] = gene.tss;
        }
        Map<String, String> diagnostics = new TreeMap<>(); double[][] covariates; PopsCovariance covariance;
        if (!numerical) { covariates = null; covariance = null; }
        else if (c.magma()) {
            Magma parsed = magma(input(root, c.get("magma_prefix") + ".genes.raw"), ids, chromosomes, c, diagnostics);
            covariates = c.bool("use_magma_covariates") ? Pops.magmaCovariates(parsed.nsnps, parsed.nparam, parsed.mac) : null;
            covariance = c.bool("use_magma_error_covariance") ? parsed.covariance : null;
        } else {
            covariates = c.get("score_covariates") == null ? null : scoreCovariates(input(root, c.get("score_covariates")), ids, c);
            covariance = c.get("error_covariance") == null ? null : customCovariance(input(root, c.get("error_covariance")), root, chromosomes, c, diagnostics);
        }
        Set<String> allChromosomes = new HashSet<>(); for (Gene gene : annotation.values()) allChromosomes.add(gene.chromosome);
        boolean[][] masks = new boolean[3][]; String[] roles = {"projection", "selection", "training"};
        for (int k = 0; k < roles.length; k++) {
            String requested = c.get(roles[k] + "_chromosomes"); Set<String> use = requested.equals("all") ? allChromosomes : new LinkedHashSet<>(Arrays.asList(requested.split(",", -1)));
            if (!allChromosomes.containsAll(use) || use.contains("")) throw new IOException("Unknown/empty chromosome in " + roles[k] + " mask");
            masks[k] = Pops.geneMask(chromosomes, tss, use, c.bool(roles[k] + "_remove_hla"));
        }
        Map<String, Integer> featureOrdinals = new HashMap<>(); for (int i = 0; i < features.names.size(); i++) featureOrdinals.put(features.names.get(i), i);
        int[] subset = null; boolean[] controls = null;
        boolean forward = !c.get("forward_steps").equals("none");
        if (c.get("subset_features") != null && !forward) {
            List<String> names = tokens(input(root, c.get("subset_features")), c.integer("max_features"), c.integer("max_identifier_chars")); subset = new int[names.size()];
            for (int i = 0; i < names.size(); i++) { Integer ordinal = featureOrdinals.get(names.get(i)); if (ordinal == null) throw new IOException("Requested subset feature is absent: " + names.get(i)); subset[i] = ordinal; }
        }
        if (c.get("control_features") != null && !forward) {
            controls = new boolean[features.names.size()]; int absent = 0;
            for (String name : tokens(input(root, c.get("control_features")), c.integer("max_features"), c.integer("max_identifier_chars"))) {
                Integer ordinal = featureOrdinals.get(name); if (ordinal == null) absent++; else controls[ordinal] = true;
            }
            diagnostics.put("control_names_absent_from_matrix", Integer.toString(absent));
        }
        if (forward) diagnostics.put("feature_selection_files_ignored_by_forward_selection", Integer.toString((c.get("subset_features") == null ? 0 : 1) + (c.get("control_features") == null ? 0 : 1)));
        Pops.Selection selection = new Pops.Selection(c.get("p_cutoff").equals("none") ? null : finite(c.get("p_cutoff")),
            c.get("max_selected_by_p").equals("none") ? null : Integer.valueOf(c.get("max_selected_by_p")), subset, controls,
            c.get("forward_steps").equals("none") ? null : Integer.valueOf(c.get("forward_steps")));
        return new Data(features.ids, List.copyOf(ids), target, mapping, covariates, covariance, masks, selection, features, diagnostics);
    }

    private static Features features(Path root, Config c, Consumer<String> progress) throws IOException {
        String prefix = c.get("feature_prefix"); List<String> ids = tokens(input(root, prefix + ".rows.txt"), c.integer("max_prediction_genes"), c.integer("max_identifier_chars"));
        if (ids.isEmpty()) throw new IOException("Feature gene rows are empty");
        List<String> names = new ArrayList<>(); Set<String> unique = new HashSet<>(); List<PopsWorkflowNpy> files = new ArrayList<>(); List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < c.integer("feature_chunks"); i++) {
            List<String> columns = tokens(input(root, prefix + ".cols." + i + ".txt"), c.integer("max_features"), c.integer("max_identifier_chars"));
            var matrix = PopsWorkflowNpy.inspect(input(root, prefix + ".mat." + i + ".npy"), c.integer("max_header_bytes"), c.number("max_input_bytes"));
            if (matrix.dimensions() != 2 || matrix.dimension(0) != ids.size() || matrix.dimension(1) != columns.size() || columns.isEmpty()) throw new IOException("Feature NPY dimensions differ from the exact gene/column lists at chunk " + i);
            if ((long) names.size() + columns.size() > c.integer("max_features")) throw new IOException("Feature count exceeds capacity");
            for (String name : columns) if (!unique.add(name)) throw new IOException("Duplicate feature identifier across chunks: " + name);
            int global = names.size(); names.addAll(columns); files.add(matrix);
            for (int first = 0; first < columns.size();) { int width = Math.min(c.integer("batch_columns"), columns.size() - first); plans.add(new Plan(i, first, width, global + first)); first += width; }
        }
        return new Features(ids, names, files, plans, progress);
    }

    private static Map<String, Gene> annotation(Path file, Config c) throws IOException {
        Map<String, Gene> result = new LinkedHashMap<>();
        try (var reader = Files.newBufferedReader(file)) {
            String[] header = header(reader.readLine(), true); int gene = column(header, "ENSGID"), chromosome = column(header, "CHR"), tss = column(header, "TSS");
            for (String line; (line = reader.readLine()) != null;) {
                if (result.size() >= c.integer("max_prediction_genes")) throw new IOException("Annotation gene count exceeds capacity");
                String[] row = row(line, header.length, true); String id = identifier(row[gene], c.integer("max_identifier_chars"));
                long position; try { position = Long.parseLong(row[tss]); } catch (NumberFormatException e) { throw new IOException("Integer annotation TSS required for " + id, e); }
                if (position < 0 || result.putIfAbsent(id, new Gene(identifier(row[chromosome], c.integer("max_identifier_chars")), position)) != null) throw new IOException("Negative TSS or duplicate annotation gene: " + id);
            }
        }
        return result;
    }

    private record Magma(double[] nsnps, double[] nparam, double[] mac, PopsCovariance covariance) { }
    private static Magma magma(Path file, List<String> ids, String[] annotationChromosomes, Config c, Map<String, String> diagnostics) throws IOException {
        int n = ids.size(); double[] nsnps = new double[n], nparam = new double[n], mac = new double[n]; List<Integer> breaks = new ArrayList<>(); breaks.add(0);
        Set<String> closed = new HashSet<>(); String previous = null; int count = 0, within = 0;
        Map<String, String> rawToAnnotation = new HashMap<>(), annotationToRaw = new HashMap<>();
        try (var reader = Files.newBufferedReader(file)) {
            String version = reader.readLine(), covar = reader.readLine();
            if (version == null || covar == null || version.length() > 1048576 || covar.length() > 1048576) throw new IOException("Two bounded MAGMA raw header lines required; the source skips their contents");
            diagnostics.put("magma_raw_header", version); diagnostics.put("magma_raw_covariates", covar);
            for (String line; (line = reader.readLine()) != null;) {
                if (count >= n) throw new IOException("Extra MAGMA raw gene rows"); String[] row = rawRow(line);
                if (!ids.get(count).equals(row[0])) throw new IOException("MAGMA raw and target gene order differ");
                if (!row[1].equals(previous)) {
                    if (!closed.add(row[1])) throw new IOException("MAGMA chromosomes are not sequentially ordered");
                    if (count != 0) breaks.add(count); within = 0; previous = row[1];
                }
                if (row.length - 9 > within) throw new IOException("MAGMA correlation row extends before chromosome start");
                String label = annotationChromosomes[count]; String old = rawToAnnotation.putIfAbsent(row[1], label), reverse = annotationToRaw.putIfAbsent(label, row[1]);
                if ((old != null && !old.equals(label)) || (reverse != null && !reverse.equals(row[1]))) throw new IOException("MAGMA and annotation chromosome partitions differ; no chromosome repair is performed");
                nsnps[count] = ieee(row[4]); nparam[count] = ieee(row[5]); mac[count] = ieee(row[7]);
                for (int j = 9; j < row.length; j++) ieee(row[j]); count++; within++;
            }
        }
        if (count != n) throw new IOException("Missing MAGMA raw gene rows"); breaks.add(n);
        if (!c.bool("use_magma_error_covariance")) return new Magma(nsnps, nparam, mac, null);
        long entries = 0; for (int k = 0; k + 1 < breaks.size(); k++) { long size = breaks.get(k + 1) - breaks.get(k); entries = Math.addExact(entries, Math.multiplyExact(size, size)); }
        if (entries > c.number("max_covariance_entries")) throw new IOException("Complete MAGMA covariance blocks exceed capacity; do not split dependencies");
        List<PopsCovariance.Block> blocks = new ArrayList<>();
        try (var reader = Files.newBufferedReader(file)) {
            reader.readLine(); reader.readLine();
            for (int k = 0; k + 1 < breaks.size(); k++) {
                int first = breaks.get(k), size = breaks.get(k + 1) - first; double[][] values = new double[size][size]; int[] indices = new int[size];
                for (int i = 0; i < size; i++) {
                    String[] row = rawRow(Objects.requireNonNull(reader.readLine())); if (!row[0].equals(ids.get(first + i))) throw new IOException("MAGMA input changed between reads");
                    indices[i] = first + i; values[i][i] = 1; int correlations = row.length - 9;
                    for (int j = 0; j < correlations; j++) values[i][i - correlations + j] = values[i - correlations + j][i] = ieee(row[9 + j]);
                }
                blocks.add(new PopsCovariance.Block(indices, values));
            }
            if (reader.readLine() != null) throw new IOException("MAGMA input gained gene rows");
        }
        diagnostics.put("covariance_block_entries", Long.toString(entries)); return new Magma(nsnps, nparam, mac, new PopsCovariance(n, blocks));
    }

    private static double[][] scoreCovariates(Path file, List<String> ids, Config c) throws IOException {
        Map<String, double[]> rows = new HashMap<>(); int columns;
        try (var reader = Files.newBufferedReader(file)) {
            String[] header = header(reader.readLine(), false); int idColumn = column(header, "ENSGID"); columns = header.length - 1;
            if (columns > c.integer("max_features")) throw new IOException("Target covariate count exceeds capacity");
            for (String line; (line = reader.readLine()) != null;) {
                if (rows.size() >= c.integer("max_prediction_genes")) throw new IOException("Covariate row count exceeds capacity");
                String[] row = row(line, header.length, false); String id = identifier(row[idColumn], c.integer("max_identifier_chars")); double[] values = new double[columns];
                for (int j = 0, k = 0; j < row.length; j++) if (j != idColumn) values[k++] = ieee(row[j]);
                if (rows.putIfAbsent(id, values) != null) throw new IOException("Duplicate covariate gene: " + id);
            }
        }
        double[][] result = new double[ids.size()][columns];
        for (int i = 0; i < ids.size(); i++) { double[] values = rows.get(ids.get(i)); if (values == null) throw new IOException("Covariates lack target gene: " + ids.get(i)); result[i] = values; }
        return result;
    }

    private static PopsCovariance customCovariance(Path file, Path root, String[] chromosomes, Config c, Map<String, String> diagnostics) throws IOException {
        Map<String, List<Integer>> groups = new LinkedHashMap<>(); for (int i = 0; i < chromosomes.length; i++) groups.computeIfAbsent(chromosomes[i], key -> new ArrayList<>()).add(i);
        List<int[]> indices = new ArrayList<>(); List<double[][]> blocks = new ArrayList<>(); int[] groupOf = new int[chromosomes.length], local = new int[chromosomes.length]; long entries = 0;
        for (var group : groups.values()) {
            int[] rows = group.stream().mapToInt(Integer::intValue).toArray(); entries = Math.addExact(entries, (long) rows.length * rows.length);
            if (entries > c.number("max_covariance_entries")) throw new IOException("Complete custom covariance blocks exceed capacity");
            for (int j = 0; j < rows.length; j++) { groupOf[rows[j]] = indices.size(); local[rows[j]] = j; }
            indices.add(rows); blocks.add(new double[rows.length][rows.length]);
        }
        long[] ignored = new long[2]; byte[] magic; try (var input = Files.newInputStream(file)) { magic = input.readNBytes(2); }
        if (Arrays.equals(magic, new byte[] {'P', 'K'})) sparse(file, root, c, groupOf, local, blocks, ignored);
        else {
            var array = PopsWorkflowNpy.inspect(file, c.integer("max_header_bytes"), c.number("max_input_bytes"));
            if (array.dimensions() != 2 || array.dimension(0) != chromosomes.length || array.dimension(1) != chromosomes.length) throw new IOException("Custom covariance shape differs from target row count");
            for (int k = 0; k < indices.size(); k++) blocks.set(k, array.square(indices.get(k)));
            for (int first = 0; first < chromosomes.length;) {
                int width = Math.min(c.integer("batch_columns"), chromosomes.length - first); double[][] values = array.columns(first, width);
                for (int i = 0; i < chromosomes.length; i++) for (int j = 0; j < width; j++) if (groupOf[i] != groupOf[first + j]) ignored(values[i][j], ignored);
                first += width;
            }
        }
        List<PopsCovariance.Block> result = new ArrayList<>(); for (int k = 0; k < indices.size(); k++) result.add(new PopsCovariance.Block(indices.get(k), blocks.get(k)));
        diagnostics.put("custom_covariance_cross_chromosome_nonzero_ignored", Long.toString(ignored[0]));
        diagnostics.put("custom_covariance_cross_chromosome_nonfinite_ignored", Long.toString(ignored[1]));
        diagnostics.put("covariance_block_entries", Long.toString(entries)); return new PopsCovariance(chromosomes.length, result);
    }
    private static void ignored(double value, long[] count) { if (value != 0) count[0]++; if (!Double.isFinite(value)) count[1]++; }
    private static void sparse(Path file, Path root, Config c, int[] groupOf, int[] local, List<double[][]> blocks, long[] ignored) throws IOException {
        Path directory = root.resolve("working/covariance-npz"); Files.createDirectory(directory); Set<String> seen = new HashSet<>();
        Set<String> allowed = Set.of("format.npy", "shape.npy", "data.npy", "indices.npy", "indptr.npy", "row.npy", "col.npy"); long unpacked = 0;
        try (var zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName(); if (entry.isDirectory() || !allowed.contains(name) || !seen.add(name)) throw new IOException("Unqualified/duplicate SciPy NPZ member: " + name);
                try (var output = Files.newOutputStream(directory.resolve(name), StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[65536]; for (int n; (n = zip.read(buffer)) >= 0;) { unpacked = Math.addExact(unpacked, n); if (unpacked > c.number("max_work_bytes")) throw new IOException("Unpacked covariance exceeds work capacity"); if (n > 0) output.write(buffer, 0, n); }
                }
                zip.closeEntry();
            }
        }
        java.util.function.Function<String, Path> path = directory::resolve;
        String format = PopsWorkflowNpy.inspect(path.apply("format.npy"), c.integer("max_header_bytes"), c.number("max_work_bytes")).textScalar();
        Set<String> expected = Set.of("csr", "csc").contains(format) ? Set.of("format.npy", "shape.npy", "data.npy", "indices.npy", "indptr.npy")
            : format.equals("coo") ? Set.of("format.npy", "shape.npy", "data.npy", "row.npy", "col.npy") : Set.of();
        if (expected.isEmpty() || !seen.equals(expected)) throw new IOException("CSR, CSC or COO SciPy NPZ covariance required with its complete member family");
        double[] shape = vector(path.apply("shape.npy"), c), data = vector(path.apply("data.npy"), c);
        if (shape.length != 2 || shape[0] != groupOf.length || shape[1] != groupOf.length || data.length > c.number("max_covariance_entries")) throw new IOException("Sparse covariance shape or nonzero capacity differs");
        if (format.equals("coo")) {
            double[] rows = vector(path.apply("row.npy"), c), columns = vector(path.apply("col.npy"), c);
            if (rows.length != data.length || columns.length != data.length) throw new IOException("COO member lengths differ");
            for (int k = 0; k < data.length; k++) put(index(rows[k], groupOf.length), index(columns[k], groupOf.length), data[k], groupOf, local, blocks, ignored);
        } else {
            double[] pointers = vector(path.apply("indptr.npy"), c), coordinates = vector(path.apply("indices.npy"), c);
            if (pointers.length != groupOf.length + 1 || coordinates.length != data.length || pointers[0] != 0 || pointers[pointers.length - 1] != data.length) throw new IOException("Sparse covariance pointer/coordinate lengths differ");
            for (int i = 0; i < groupOf.length; i++) {
                int first = index(pointers[i], (long) data.length + 1), last = index(pointers[i + 1], (long) data.length + 1); if (first > last) throw new IOException("Sparse pointers are not monotone");
                for (int k = first; k < last; k++) { int j = index(coordinates[k], groupOf.length); put(format.equals("csr") ? i : j, format.equals("csr") ? j : i, data[k], groupOf, local, blocks, ignored); }
            }
        }
    }
    private static double[] vector(Path path, Config c) throws IOException {
        var array = PopsWorkflowNpy.inspect(path, c.integer("max_header_bytes"), c.number("max_work_bytes"));
        if (array.elements() > 0 && array.elements() - 1 > c.number("max_covariance_entries")) throw new IOException("Sparse member exceeds declared entry capacity"); return array.vector();
    }
    private static int index(double value, long limit) throws IOException { if (!Double.isFinite(value) || value != Math.rint(value) || value < 0 || value >= limit || value > Integer.MAX_VALUE) throw new IOException("Sparse index/pointer out of range"); return (int) value; }
    private static void put(int row, int column, double value, int[] groups, int[] local, List<double[][]> blocks, long[] ignored) {
        if (groups[row] == groups[column]) blocks.get(groups[row])[local[row]][local[column]] += value; else ignored(value, ignored);
    }

    static List<String> tokens(Path file, int maximum, int maximumLength) throws IOException {
        List<String> result = new ArrayList<>(); Set<String> unique = new HashSet<>();
        try (var reader = Files.newBufferedReader(file)) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.length() > 1048576) throw new IOException("Identifier-list line exceeds 1 MiB");
                int comment = line.indexOf('#'); if (comment >= 0) line = line.substring(0, comment); if (line.isBlank()) continue;
                for (String token : line.trim().split("\\s+")) {
                    identifier(token, maximumLength); if (result.size() >= maximum || !unique.add(token)) throw new IOException("Identifier list exceeds capacity or contains a duplicate: " + token); result.add(token);
                }
            }
        }
        return List.copyOf(result);
    }
    private static String[] header(String text, boolean whitespace) throws IOException {
        if (text == null) throw new IOException("Missing table header"); if (text.startsWith("\ufeff")) text = text.substring(1);
        String[] values = whitespace ? text.trim().split("\\s+", -1) : text.split("\t", -1);
        if (new HashSet<>(Arrays.asList(values)).size() != values.length || Arrays.stream(values).anyMatch(String::isEmpty)) throw new IOException("Unique nonempty table headers required"); return values;
    }
    private static int column(String[] header, String name) throws IOException { int index = Arrays.asList(header).indexOf(name); if (index < 0) throw new IOException("Required table column: " + name); return index; }
    private static String[] row(String text, int columns, boolean whitespace) throws IOException {
        if (text.length() > 1048576) throw new IOException("Table line exceeds 1 MiB"); String[] values = whitespace ? text.trim().split("\\s+", -1) : text.split("\t", -1);
        if (values.length != columns) throw new IOException("Table row has a different field count"); return values;
    }
    private static String[] rawRow(String text) throws IOException { if (text.length() > 1048576) throw new IOException("MAGMA row exceeds 1 MiB"); String[] row = text.split(" ", -1); if (row.length < 9 || Arrays.stream(row).anyMatch(String::isEmpty)) throw new IOException("Exact single-space MAGMA raw fields required"); return row; }
    private static double ieee(String text) throws IOException { if (Set.of("", "NA", "NaN", "nan", "null").contains(text)) return Double.NaN; if (text.equals("inf")) return Double.POSITIVE_INFINITY; if (text.equals("-inf")) return Double.NEGATIVE_INFINITY; try { return Double.parseDouble(text); } catch (NumberFormatException e) { throw new IOException("Numeric prepared input required: " + text, e); } }
}
