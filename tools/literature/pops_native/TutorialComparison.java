/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.genetics.Pops;
import org.jlinalg.genetics.PopsCovariance;

/** Complete official tutorial input binding and comparison, not a public file-format adapter. */
public final class TutorialComparison {
    private static final double TOLERANCE = 5e-8;

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("upstream original-output eigen-reference NEW-output CPU|OPENBLAS|... required");
        Path source = Path.of(args[0]), original = Path.of(args[1]), eigenReference = Path.of(args[2]), out = Path.of(args[3]);
        Files.createDirectory(out);
        Properties report = new Properties();
        try {
            run(source, original, eigenReference, out, BackendPolicy.valueOf(args[4]), report);
            report.setProperty("status", "PASS_COMPLETE_NATIVE_POPS_TUTORIAL_COMPARISON");
            try (var writer = Files.newBufferedWriter(out.resolve("COMPLETED.properties"))) { report.store(writer, "Software comparison; no biological validation"); }
            System.out.println("PASS_COMPLETE_NATIVE_POPS_TUTORIAL_COMPARISON " + report);
        } catch (Throwable error) {
            report.setProperty("status", "FAIL_NATIVE_POPS_TUTORIAL_COMPARISON"); report.setProperty("cause", error.toString());
            report.setProperty("required_action", "Retain original and native records; diagnose the discrepancy and retry a new output path; do not subset genes or features to obtain a pass.");
            try (var writer = Files.newBufferedWriter(out.resolve("FAILURE.properties"))) { report.store(writer, "No qualified comparison"); }
            error.printStackTrace(); System.exit(2);
        }
    }

    private static void run(Path source, Path original, Path eigenReference, Path out, BackendPolicy policy, Properties report) throws Exception {
        long start = System.nanoTime();
        Path data = source.resolve("example/data"), prefix = data.resolve("features_munged/pops_features");
        String[] rows = Files.readAllLines(Path.of(prefix + ".rows.txt")).toArray(String[]::new);
        List<String> columns = new ArrayList<>();
        for (int chunk = 0; chunk < 2; chunk++) columns.addAll(Files.readAllLines(Path.of(prefix + ".cols." + chunk + ".txt")));
        if (rows.length != 18383 || columns.size() != 508 || new HashSet<>(columns).size() != columns.size()) throw new IOException("Official tutorial feature dimensions differ");
        double[][] features = new double[rows.length][columns.size()];
        int offset = 0;
        for (int chunk = 0; chunk < 2; chunk++) offset += readNpy(Path.of(prefix + ".mat." + chunk + ".npy"), features, offset);
        if (offset != columns.size()) throw new IOException("NPY and column-name dimensions differ");
        Map<String, Integer> featureRows = new HashMap<>();
        for (int i = 0; i < rows.length; i++) if (featureRows.put(rows[i], i) != null) throw new IOException("Duplicate official feature gene");
        Map<String, String[]> annotation = new HashMap<>();
        try (var reader = Files.newBufferedReader(data.resolve("utils/gene_annot_jun10.txt"))) {
            String[] header = reader.readLine().split("\t");
            if (!Arrays.equals(header, new String[] {"ENSGID", "NAME", "CHR", "START", "END", "TSS"})) throw new IOException("Unexpected official annotation schema");
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.split("\t", -1); if (fields.length != 6 || annotation.put(fields[0], fields) != null) throw new IOException("Invalid annotation row");
            }
        }
        List<String[]> scores = table(data.resolve("magma_scores/PASS_Schizophrenia.genes.out"), "\\s+");
        String[] scoreHeader = scores.remove(0);
        int geneColumn = Arrays.asList(scoreHeader).indexOf("GENE"), zColumn = Arrays.asList(scoreHeader).indexOf("ZSTAT");
        int n = scores.size(); double[] y = new double[n], nsnps = new double[n], nparam = new double[n], mac = new double[n];
        String[] ids = new String[n], chromosomes = new String[n]; long[] tss = new long[n];
        double[][] x = new double[n][]; Set<String> allChromosomes = new HashSet<>();
        for (String[] fields : annotation.values()) allChromosomes.add(fields[2]);
        for (int i = 0; i < n; i++) {
            ids[i] = scores.get(i)[geneColumn]; y[i] = Double.parseDouble(scores.get(i)[zColumn]);
            Integer row = featureRows.get(ids[i]); if (row == null || !annotation.containsKey(ids[i])) throw new IOException("Official target gene lacks features/annotation");
            x[i] = features[row]; chromosomes[i] = annotation.get(ids[i])[2]; tss[i] = Long.parseLong(annotation.get(ids[i])[5]);
        }
        List<String[]> raw = new ArrayList<>();
        try (var reader = Files.newBufferedReader(data.resolve("magma_scores/PASS_Schizophrenia.genes.raw"))) {
            if (!reader.readLine().equals("# VERSION = 108") || !reader.readLine().equals("# COVAR = NSAMP MAC")) throw new IOException("Unexpected official MAGMA raw schema");
            for (String line; (line = reader.readLine()) != null;) raw.add(line.split(" ", -1));
        }
        if (raw.size() != n) throw new IOException("MAGMA target/raw row count differs");
        List<PopsCovariance.Block> blocks = new ArrayList<>(); Set<String> closedChromosomes = new HashSet<>();
        for (int first = 0; first < n;) {
            String chromosome = raw.get(first)[1]; int last = first + 1;
            while (last < n && raw.get(last)[1].equals(chromosome)) last++;
            if (!closedChromosomes.add(chromosome)) throw new IOException("Nonsequential MAGMA chromosomes");
            int[] indices = new int[last - first]; double[][] covariance = new double[indices.length][indices.length];
            for (int i = first; i < last; i++) {
                String[] fields = raw.get(i);
                if (!fields[0].equals(ids[i]) || !chromosomes[i].equals(chromosomes[first])) throw new IOException("Raw/target/annotation gene partitions differ");
                int local = i - first; indices[local] = i; covariance[local][local] = 1;
                nsnps[i] = Double.parseDouble(fields[4]); nparam[i] = Double.parseDouble(fields[5]); mac[i] = Double.parseDouble(fields[7]);
                int correlations = fields.length - 9;
                if (correlations > local) throw new IOException("MAGMA correlation extends before chromosome start");
                for (int j = 0; j < correlations; j++) covariance[local][local - correlations + j] = covariance[local - correlations + j][local] = Double.parseDouble(fields[9 + j]);
            }
            blocks.add(new PopsCovariance.Block(indices, covariance)); first = last;
        }
        boolean[] mask = Pops.geneMask(chromosomes, tss, allChromosomes, true);
        Set<String> controlNames = new HashSet<>(Files.readAllLines(data.resolve("utils/features_jul17_control.txt")));
        boolean[] controls = new boolean[columns.size()]; for (int j = 0; j < controls.length; j++) controls[j] = controlNames.contains(columns.get(j));
        var covariance = new PopsCovariance(n, blocks); PopsCovariance.Regularized adjusted;
        try (var context = BackendContext.select(policy)) { adjusted = covariance.regularizeMagma(y, context.backend()); }
        Properties expectedEigen = new Properties();
        try (var reader = Files.newBufferedReader(eigenReference.resolve("tutorial-eigen.properties"))) { expectedEigen.load(reader); }
        compare(number(expectedEigen.getProperty("minimum_eigenvalue")), adjusted.minimumEigenvalue(), "full tutorial minimum covariance eigenvalue");
        compare(number(expectedEigen.getProperty("target_variance")), adjusted.targetVariance(), "target population variance");
        compare(number(expectedEigen.getProperty("diagonal_shift")), adjusted.diagonalShift(), "full tutorial covariance adjustment");
        report.setProperty("covariance_shift", Double.toString(adjusted.diagonalShift()));
        report.setProperty("covariance_minimum_eigenvalue", Double.toString(adjusted.minimumEigenvalue()));
        report.setProperty("target_population_variance", Double.toString(adjusted.targetVariance()));
        System.out.println("Native covariance ready; targets=" + n + ", features=" + columns.size() + ", shift=" + adjusted.diagonalShift());
        var model = Pops.fit(y, x, Pops.magmaCovariates(nsnps, nparam, mac), adjusted.covariance(), mask, mask, mask,
            new Pops.Selection(.05, null, null, controls, null), Pops.Method.RIDGE, policy);
        double[] predictions = model.predict(features), beta = model.coefficients(); var marginals = model.marginals();
        boolean[] selected = new boolean[columns.size()]; for (int j : model.selectedFeatures()) selected[j] = true;
        try (var writer = Files.newBufferedWriter(out.resolve("native.preds"))) {
            writer.write("ENSGID\tPoPS_Score\n");
            for (int i = 0; i < rows.length; i++) writer.write(rows[i] + "\t" + predictions[i] + "\n");
        }
        try (var writer = Files.newBufferedWriter(out.resolve("native.marginals"))) {
            writer.write("feature\tbeta\tse\tpval\tr2\tselected\n");
            for (int j = 0; j < columns.size(); j++) writer.write(columns.get(j) + "\t" + marginals[j].beta() + "\t" + marginals[j].standardError() + "\t" + marginals[j].pValue() + "\t" + marginals[j].rSquared() + "\t" + selected[j] + "\n");
        }
        try (var writer = Files.newBufferedWriter(out.resolve("native.coefs"))) {
            writer.write("parameter\tbeta\nMETHOD\tRidgeCV\nSELECTED_CV_ALPHA\t" + model.selectedAlpha() + "\nBEST_CV_SCORE\t" + model.bestCvScore() + "\n");
            for (int j : model.selectedFeatures()) writer.write(columns.get(j) + "\t" + beta[j] + "\n");
        }
        try (var writer = Files.newBufferedWriter(out.resolve("native.cv.tsv"))) { for (double value : model.cvScores()) writer.write(value + "\n"); }
        List<String[]> expectedPredictions = table(original.resolve("original.preds"), "\t");
        List<String> predHeader = Arrays.asList(expectedPredictions.remove(0)); double maximumPredictionError = 0;
        if (expectedPredictions.size() != rows.length) throw new AssertionError("prediction row count");
        for (int i = 0; i < rows.length; i++) {
            String[] values = expectedPredictions.get(i); if (!values[0].equals(rows[i])) throw new AssertionError("prediction gene order");
            double expected = number(values[predHeader.indexOf("PoPS_Score")]); compare(expected, predictions[i], "prediction " + rows[i]);
            maximumPredictionError = Math.max(maximumPredictionError, Math.abs(expected - predictions[i]));
        }
        List<String[]> expectedMarginals = table(original.resolve("original.marginals"), "\t");
        List<String> marginalHeader = Arrays.asList(expectedMarginals.remove(0)); double maximumMarginalError = 0;
        if (expectedMarginals.size() != columns.size()) throw new AssertionError("marginal row count");
        for (int j = 0; j < columns.size(); j++) {
            String[] values = expectedMarginals.get(j); if (!values[0].equals(columns.get(j))) throw new AssertionError("marginal feature order");
            double[] actual = {marginals[j].beta(), marginals[j].standardError(), marginals[j].pValue(), marginals[j].rSquared()};
            for (int c = 0; c < 4; c++) {
                double expected = number(values[c + 1]); compare(expected, actual[c], "marginal " + j + "/" + c);
                if (Double.isFinite(expected)) maximumMarginalError = Math.max(maximumMarginalError, Math.abs(expected - actual[c]));
            }
            if (Boolean.parseBoolean(values[marginalHeader.indexOf("selected")]) != selected[j]) throw new AssertionError("selected feature " + j);
        }
        Map<String, String> expectedCoefficients = new HashMap<>();
        for (String[] values : table(original.resolve("original.coefs"), "\t")) expectedCoefficients.put(values[0], values[1]);
        double maximumCoefficientError = 0;
        for (int j = 0; j < columns.size(); j++) {
            if (selected[j] != expectedCoefficients.containsKey(columns.get(j))) throw new AssertionError("coefficient feature set");
            if (selected[j]) {
                double expected = number(expectedCoefficients.get(columns.get(j))); compare(expected, beta[j], "coefficient " + j);
                maximumCoefficientError = Math.max(maximumCoefficientError, Math.abs(expected - beta[j]));
            }
        }
        compare(number(expectedCoefficients.get("SELECTED_CV_ALPHA")), model.selectedAlpha(), "selected alpha");
        compare(number(expectedCoefficients.get("BEST_CV_SCORE")), model.bestCvScore(), "best CV score");
        report.setProperty("target_genes", Integer.toString(n)); report.setProperty("prediction_genes", Integer.toString(rows.length));
        report.setProperty("input_features", Integer.toString(columns.size())); report.setProperty("selected_features", Integer.toString(model.selectedFeatures().length));
        report.setProperty("selected_alpha", Double.toString(model.selectedAlpha())); report.setProperty("best_cv_score", Double.toString(model.bestCvScore()));
        report.setProperty("maximum_absolute_prediction_error", Double.toString(maximumPredictionError));
        report.setProperty("maximum_absolute_marginal_error", Double.toString(maximumMarginalError));
        report.setProperty("maximum_absolute_coefficient_error", Double.toString(maximumCoefficientError));
        report.setProperty("comparison_tolerance", "5e-8 * (1 + absolute expected value)"); report.setProperty("backend", model.backend());
        report.setProperty("seconds", Double.toString((System.nanoTime() - start) / 1e9));
        report.setProperty("full_official_data_no_subsampling", "true"); report.setProperty("whole_paper_reproduced", "false");
        report.setProperty("new_catalog_credit", "0");
    }

    private static List<String[]> table(Path file, String separator) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (var reader = Files.newBufferedReader(file)) {
            for (String line; (line = reader.readLine()) != null;) rows.add((separator.equals("\\s+") ? line.trim() : line).split(separator, -1));
        }
        return rows;
    }

    private static double number(String value) { return value.isEmpty() || value.equals("NaN") || value.equals("nan") ? Double.NaN : Double.parseDouble(value); }
    private static void compare(double expected, double actual, String context) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) return;
        if (!Double.isFinite(expected) || !Double.isFinite(actual) || Math.abs(expected - actual) > TOLERANCE * (1 + Math.abs(expected)))
            throw new AssertionError(context + ": expected=" + expected + ", actual=" + actual);
    }

    private static int readNpy(Path file, double[][] target, int offset) throws IOException {
        try (var stream = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1024 * 1024))) {
            if (!Arrays.equals(stream.readNBytes(6), new byte[] {(byte) 0x93, 'N', 'U', 'M', 'P', 'Y'}) || stream.readUnsignedByte() != 1 || stream.readUnsignedByte() != 0)
                throw new IOException("Only the official tutorial NPY v1 fixture is supported");
            int length = stream.readUnsignedByte() | (stream.readUnsignedByte() << 8);
            String header = new String(stream.readNBytes(length), StandardCharsets.US_ASCII);
            if (!header.contains("'descr': '<f8'") || !(header.contains("'fortran_order': False") || header.contains("'fortran_order': True")))
                throw new IOException("Unexpected official NPY dtype/order");
            boolean columnMajor = header.contains("'fortran_order': True");
            var shape = java.util.regex.Pattern.compile("'shape': \\((\\d+), (\\d+)\\)").matcher(header);
            if (!shape.find() || Integer.parseInt(shape.group(1)) != target.length) throw new IOException("NPY shape differs");
            int columns = Integer.parseInt(shape.group(2));
            if (offset + columns > target[0].length) throw new IOException("NPY feature count exceeds supplied columns");
            if (columnMajor) {
                for (int j = 0; j < columns; j++) for (double[] row : target) row[offset + j] = Double.longBitsToDouble(Long.reverseBytes(stream.readLong()));
            } else {
                for (double[] row : target) for (int j = 0; j < columns; j++) row[offset + j] = Double.longBitsToDouble(Long.reverseBytes(stream.readLong()));
            }
            if (stream.read() != -1) throw new IOException("Trailing NPY bytes");
            return columns;
        }
    }
}
