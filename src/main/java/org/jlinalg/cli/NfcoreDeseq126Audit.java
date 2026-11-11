/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdistlib.Normal;
import org.jlinalg.sequence.Deseq2IndependentFiltering;
import org.jlinalg.sequence.Deseq2SizeFactors;
import org.jlinalg.workflow.NfcoreChipseq121Consensus;

/** Complete original ChIP-seq1.2.1 differential-output consistency audit.
 * Original R performs the postfit contrast calculation; native Java checks the
 * complete identities, normalization, defined Wald probabilities, all filter
 * families and source exports. This does not refit or certify biological validity.
 */
final class NfcoreDeseq126Audit {
    private NfcoreDeseq126Audit() { }
    @FunctionalInterface interface Outputs { Path get(String name) throws IOException; }
    private static final List<String> PREFIX = List.of("Geneid", "Chr", "Start", "End", "Strand", "Length");
    private static final List<String> METRICS = List.of("baseMean", "log2FoldChange", "lfcSE", "stat", "pvalue", "padj");
    private static final long MAX_CHARACTERS = 268435456;
    private record Counts(List<List<String>> prefix, List<String> samples, int[][] values, double[] factors) { }
    private record Comparison(String id, String control, String treatment, String prefix, Table worker, Table original) { }

    static Map<String, Object> audit(Path featureCounts, List<NfcoreChipseq121Consensus.Row> intervals,
            List<String> libraries, String bamSuffix, Path worker, String publishedPrefix, String modelPrefix, Outputs outputs) throws IOException {
        if (!"COMPLETE_ORIGINAL_DESEQ126_POSTFIT_EXPORT_ONLY\n".equals(Files.readString(worker.resolve("WORKER_COMPLETED.txt"), StandardCharsets.UTF_8)))
            throw new IOException("Original postfit worker did not complete; no differential component admitted");
        Counts counts = counts(featureCounts, intervals, libraries, bamSuffix);
        int genes = counts.prefix().size(), samples = counts.samples().size();
        Map<String, String> metadata = keyValues(table(worker.resolve("model-metadata.tsv"), false));
        for (var pair : Map.of("schema_version", "1", "R_version", "3.6.2", "DESeq2_version", "1.26.0", "genefilter_version", "1.68.0", "design", "~condition", "test", "Wald", "beta_prior", "FALSE", "use_t", "FALSE").entrySet())
            if (!pair.getValue().equals(metadata.get(pair.getKey()))) throw new IOException("Original model setting differs: " + pair.getKey());
        if (integer(metadata.get("genes")) != genes || integer(metadata.get("samples")) != samples) throw new IOException("Original model population differs from complete featureCounts");
        var warnings = new ArrayList<String>();
        String fitType = metadata.get("fit_type");
        if (!Set.of("parametric", "local", "mean").contains(fitType)) throw new IOException("Original dispersion-fit type is absent or unsupported; no parametric setting inferred");
        if (!fitType.equals("parametric")) warnings.add("ORIGINAL_DISPERSION_FIT: " + fitType + "; retained from the fitted object, without refitting or substituting a parametric curve.");
        List<String> groups = counts.samples().stream().map(NfcoreDeseq126Audit::group).distinct().toList();
        if (groups.size() < 2) throw new IOException("No source differential branch with two original groups");
        int pairCount = Math.multiplyExact(groups.size(), groups.size() - 1) / 2;
        if (pairCount > 10000 || integer(metadata.get("comparisons")) != pairCount) throw new IOException("Complete source comparison population differs or exceeds capacity");
        Table sampleTable = table(worker.resolve("samples.tsv"), false); header(sampleTable, List.of("sample", "condition", "size_factor")); rows(sampleTable, samples);
        Table raw = matrix(worker.resolve("raw-counts.tsv"), counts), normalized = matrix(worker.resolve("normalized-counts.tsv"), counts), effective = matrix(worker.resolve("effective-fit-counts.tsv"), counts);
        Table replacement = table(worker.resolve("replacement.tsv"), false); header(replacement, List.of("gene", "replacement")); rows(replacement, genes);
        Table diagnostics = table(worker.resolve("gene-diagnostics.tsv"), false); header(diagnostics, List.of("gene", "all_zero", "beta_converged", "max_cooks", "dispersion")); rows(diagnostics, genes);
        Table transformed = matrix(worker.resolve("transform.tsv"), counts);
        var nonconverged = new ArrayList<String>(); var replaced = new ArrayList<String>(); var missingReplacement = new ArrayList<String>();
        double[] modelMeans = new double[genes];
        for (int s = 0; s < samples; s++) {
            List<String> row = sampleTable.data().get(s);
            if (!row.get(0).equals(counts.samples().get(s)) || !row.get(1).equals(group(row.get(0)))) throw new IOException("Original model sample/condition order differs; no identifier repair allowed");
            equal(counts.factors()[s], numeric(row.get(2)), "Original full-precision size factor " + row.get(0));
            Path factor = outputs.get(publishedPrefix + "/sizeFactors/" + row.get(0) + ".sizeFactor.txt");
            display(counts.factors()[s], Files.readString(factor, StandardCharsets.UTF_8).trim(), 7, "Source rounded size factor " + row.get(0));
        }
        for (int g = 0; g < genes; g++) {
            String id = counts.prefix().get(g).get(0);
            if (!replacement.data().get(g).get(0).equals(id) || !diagnostics.data().get(g).get(0).equals(id)) throw new IOException("Original gene diagnostic/replacement order differs");
            String replacementToken = replacement.data().get(g).get(1);
            boolean missingFlag = replacementToken.equals("NA"), replace = !missingFlag && flag(replacementToken); if (replace) replaced.add(id);
            boolean rawAllZero = true, fitAllZero = true; double sum = 0, correction = 0;
            for (int s = 0; s < samples; s++) {
                int observed = integer(raw.data().get(g).get(s + 1)); if (observed != counts.values()[g][s]) throw new IOException("Fitted original raw count differs at " + id);
                rawAllZero &= observed == 0;
                equal(observed / counts.factors()[s], numeric(normalized.data().get(g).get(s + 1)), "Original normalized count " + id);
                int fittingCount = integer(effective.data().get(g).get(s + 1));
                fitAllZero &= fittingCount == 0;
                if (!replace && fittingCount != observed) throw new IOException("Original non-replaced fitting count differs at " + id);
                double value = fittingCount / counts.factors()[s], next = sum + value;
                correction += Math.abs(sum) >= Math.abs(value) ? (sum - next) + value : (value - next) + sum; sum = next;
                if (!Double.isFinite(numeric(transformed.data().get(g).get(s + 1)))) throw new IOException("Original stored transformation has unassessable values at " + id);
            }
            modelMeans[g] = (sum + correction) / samples;
            if (missingFlag) { if (!rawAllZero) throw new IOException("Original NA replacement flag outside an all-zero raw gene: " + id); missingReplacement.add(id); }
            if (flag(diagnostics.data().get(g).get(1)) != fitAllZero) throw new IOException("Original effective-fit all-zero gene marker differs at " + id);
            String convergence = diagnostics.data().get(g).get(2);
            if (convergence.equals("FALSE")) nonconverged.add(id);
            else if (!convergence.equals("TRUE") && !(fitAllZero && convergence.equals("NA"))) throw new IOException("Unassessable original convergence marker at " + id);
            double cooks = numeric(diagnostics.data().get(g).get(3)), dispersion = numeric(diagnostics.data().get(g).get(4));
            if (!(Deseq2SizeFactors.isMissing(cooks) || Double.isFinite(cooks) && cooks >= 0)
                    || !(fitAllZero && Deseq2SizeFactors.isMissing(dispersion) || Double.isFinite(dispersion) && dispersion > 0)) throw new IOException("Unassessable original Cook's distance/dispersion at " + id);
        }
        if (!replaced.isEmpty()) warnings.add("ORIGINAL_OUTLIER_REPLACEMENT: " + replaced.size() + " genes. Original effective fitting counts are retained; native Java does not replace observed counts.");
        if (!nonconverged.isEmpty()) warnings.add("ORIGINAL_NONCONVERGED_GENES: " + nonconverged.size() + "; retain gene-diagnostics.tsv and review these genes before any scientific use. No fitted-model admission is granted.");
        design(worker, counts, groups);
        Table comparisons = table(worker.resolve("comparisons.tsv"), false); header(comparisons, List.of("id", "control", "treatment", "source_prefix")); rows(comparisons, pairCount);
        var details = new ArrayList<Map<String, Object>>(); var all = new ArrayList<Comparison>(); var names = new HashSet<String>(); int index = 0;
        for (int a = 0; a < groups.size(); a++) for (int b = a + 1; b < groups.size(); b++) {
            List<String> row = comparisons.data().get(index); String id = String.format(java.util.Locale.ROOT, "comparison-%06d", index + 1);
            String prefix = groups.get(a) + "vs" + groups.get(b);
            if (!row.equals(List.of(id, groups.get(a), groups.get(b), prefix)) || !names.add(prefix)) throw new IOException("Source comparison identities/order or prefixes collide");
            Table model = table(worker.resolve(id + ".results.tsv"), false); header(model, joined(List.of("gene"), METRICS)); rows(model, genes);
            String control = groups.get(a), treatment = groups.get(b);
            List<String> selectedSamples = counts.samples().stream().filter(s -> group(s).equals(control) || group(s).equals(treatment)).toList();
            String resultBase = publishedPrefix + "/" + prefix + "/" + prefix + ".deseq2";
            Table original = table(outputs.get(resultBase + ".results.txt"), false); header(original, resultHeader(selectedSamples)); rows(original, genes);
            double[] baseMeans = new double[genes], pValues = new double[genes]; int wald = 0, missingP = 0;
            for (int g = 0; g < genes; g++) {
                List<String> source = model.data().get(g), published = original.data().get(g); String gene = counts.prefix().get(g).get(0);
                if (!source.get(0).equals(gene) || !published.subList(0, 6).equals(counts.prefix().get(g))) throw new IOException("Original comparison gene/coordinate order differs at " + gene);
                for (int m = 0; m < METRICS.size(); m++) equal(numeric(source.get(m + 1)), numeric(published.get(m + 6)), "Original contrast metric " + METRICS.get(m) + " " + gene);
                baseMeans[g] = numeric(source.get(1)); pValues[g] = numeric(source.get(5));
                equal(modelMeans[g], baseMeans[g], "Original fitted base mean " + gene);
                double beta = numeric(source.get(2)), se = numeric(source.get(3)), stat = numeric(source.get(4));
                if (Double.isFinite(beta) && Double.isFinite(se) && se > 0 && Double.isFinite(stat)) {
                    equal(beta / se, stat, "Original Wald statistic " + gene);
                    if (Double.isFinite(pValues[g])) { equal(2 * Normal.cumulative(-Math.abs(stat), 0, 1, true, false), pValues[g], "Original two-sided normal Wald probability " + gene); wald++; }
                }
                if (Double.isNaN(pValues[g])) missingP++;
                for (int s = 0; s < selectedSamples.size(); s++) {
                    int position = counts.samples().indexOf(selectedSamples.get(s));
                    if (integer(published.get(12 + s)) != counts.values()[g][position]) throw new IOException("Original contrast raw count/sample column differs at " + gene);
                    equal(counts.values()[g][position] / counts.factors()[position], numeric(published.get(12 + selectedSamples.size() + s)), "Original contrast normalized sample " + gene);
                }
            }
            var filter = Deseq2IndependentFiltering.filter(baseMeans, pValues, 0.1);
            Table curve = table(worker.resolve(id + ".filter.tsv"), false); header(curve, List.of("theta", "cutoff", "rejections", "smoothed")); rows(curve, 50);
            double[] theta = filter.theta(), cutoffs = filter.cutoffs(), smooth = filter.smoothed(), adjusted = filter.adjusted(); int[] rejections = filter.rejections();
            for (int i = 0; i < 50; i++) {
                List<String> source = curve.data().get(i); equal(numeric(source.get(0)), theta[i], "Original filter percentile"); equal(numeric(source.get(1)), cutoffs[i], "Original Type7 cutoff");
                if (integer(source.get(2)) != rejections[i]) throw new IOException("Original complete BH-family rejection count differs"); equal(numeric(source.get(3)), smooth[i], "Original LOWESS filter curve");
            }
            Table choice = table(worker.resolve(id + ".selection.tsv"), false); header(choice, List.of("alpha", "theta", "cutoff")); rows(choice, 1);
            equal(0.1, numeric(choice.data().get(0).get(0)), "Original source filtering alpha"); equal(filter.selectedTheta(), numeric(choice.data().get(0).get(1)), "Original filter selection"); equal(filter.selectedCutoff(), numeric(choice.data().get(0).get(2)), "Original selected cutoff");
            for (int g = 0; g < genes; g++) equal(numeric(model.data().get(g).get(6)), adjusted[g], "Original complete adjusted-value population");
            var exports = new LinkedHashMap<String, Object>();
            for (String cutoff : List.of("0.01", "0.05")) {
                double threshold = Double.parseDouble(cutoff); var keep = new ArrayList<Integer>();
                for (int g = 0; g < genes; g++) { double value = numeric(model.data().get(g).get(6)); if (!Double.isNaN(value) && value < threshold) keep.add(g); }
                Table filtered = table(outputs.get(resultBase + ".FDR" + cutoff + ".results.txt"), false); header(filtered, original.header()); rows(filtered, keep.size());
                List<String> bed = lines(outputs.get(resultBase + ".FDR" + cutoff + ".results.bed")); if (bed.size() != keep.size()) throw new IOException("Original filtered BED population differs");
                for (int i = 0; i < keep.size(); i++) {
                    List<String> expected = original.data().get(keep.get(i));
                    if (!filtered.data().get(i).equals(expected)) throw new IOException("Original strict FDR row/value/order differs; no fold-change filter or rounded-value membership inferred");
                    String expectedBed = String.join("\t", expected.get(1), expected.get(2), expected.get(3), expected.get(0), expected.get(7), expected.get(4));
                    if (!bed.get(i).equals(expectedBed)) throw new IOException("Original filtered BED coordinate/effect projection differs; source coordinates were not incremented");
                }
                exports.put(cutoff, Map.of("strict_less_than", true, "rows", keep.size()));
            }
            details.add(Map.of("source_prefix", prefix, "control_minus_treatment", List.of(groups.get(a), groups.get(b)), "complete_genes", genes,
                "defined_normal_Wald_probabilities_checked", wald, "original_missing_pvalues", missingP, "native_filter_index_zero_based", filter.selectedIndex(), "FDR_exports", exports));
            all.add(new Comparison(id, groups.get(a), groups.get(b), prefix, model, original)); index++;
        }
        master(outputs.get(publishedPrefix + "/" + modelPrefix + ".results.txt"), counts, all);
        var report = new LinkedHashMap<String, Object>(); report.put("complete_genes", genes); report.put("analysis_samples", counts.samples());
        report.put("native_size_factors", Arrays.stream(counts.factors()).boxed().toList()); report.put("source_model_metadata", metadata); report.put("comparisons", details);
        report.put("replaced_gene_ids", replaced); report.put("original_NA_replacement_flag_gene_ids", missingReplacement); report.put("nonconverged_gene_ids", nonconverged); report.put("warnings", warnings);
        report.put("scientific_result_admitted", false); report.put("nfcore_tool_credit", 0); report.put("negative_binomial_model_refitted", false);
        report.put("stored_transform_scope", "Complete stored transform identities/finiteness checked; rlog/vst values are not independently recomputed by this audit.");
        return report;
    }
    private static Counts counts(Path file, List<NfcoreChipseq121Consensus.Row> intervals, List<String> libraries, String suffix) throws IOException {
        Table source = table(file, true); if (source.header().size() <= 6 || !source.header().subList(0, 6).equals(PREFIX)) throw new IOException("Original featureCounts six-column prefix differs");
        if (intervals.isEmpty() || intervals.size() > 1000000 || libraries.isEmpty() || libraries.size() > 10000 || (long) intervals.size() * libraries.size() > 20000000) throw new IOException("Complete model population is empty or exceeds explicit capacity");
        var physical = new ArrayList<String>();
        for (String name : source.header().subList(6, source.header().size())) {
            if (!name.endsWith(suffix) || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw new IOException("Require original staged BAM basenames and exact source suffix; no R identifier repair inferred");
            physical.add(name.substring(0, name.length() - suffix.length()));
        }
        if (new HashSet<>(physical).size() != physical.size() || new HashSet<>(libraries).size() != libraries.size() || !new HashSet<>(physical).equals(new HashSet<>(libraries))) throw new IOException("Original featureCounts sample set differs from complete checked IP graph");
        var samples = new ArrayList<>(libraries); samples.sort(NfcoreDeseq126Audit::compareText); rows(source, intervals.size());
        var prefixes = new ArrayList<List<String>>(); int[][] values = new int[intervals.size()][samples.size()];
        for (int g = 0; g < intervals.size(); g++) {
            var interval = intervals.get(g); List<String> row = source.data().get(g);
            List<String> expected = List.of(interval.id(), interval.contig(), Long.toString(interval.start()), Long.toString(interval.end()), "+", Long.toString(Math.addExact(interval.end() - interval.start(), 1)));
            if (!row.subList(0, 6).equals(expected)) throw new IOException("Original featureCounts gene/SAF geometry or source inclusive length differs at " + interval.id());
            prefixes.add(List.copyOf(row.subList(0, 6)));
            for (int s = 0; s < samples.size(); s++) values[g][s] = integer(row.get(6 + physical.indexOf(samples.get(s))));
        }
        double[] factors;
        try { var result = Deseq2SizeFactors.estimate(values, Deseq2SizeFactors.Type.RATIO); if (!result.usableForFit()) throw new IOException("Unassessable complete native default normalization"); factors = result.factors(); }
        catch (IllegalArgumentException unsupported) { throw new IOException("Original default normalization is unassessable; no factor substituted", unsupported); }
        return new Counts(List.copyOf(prefixes), List.copyOf(samples), values, factors);
    }
    private static void design(Path worker, Counts counts, List<String> groups) throws IOException {
        var coefficients = new ArrayList<String>(); coefficients.add("Intercept");
        for (String group : groups.subList(1, groups.size())) coefficients.add("condition_" + group + "_vs_" + groups.get(0));
        if (!lines(worker.resolve("result-names.txt")).equals(coefficients)) throw new IOException("Original model coefficient names differ from source default treatment coding");
        Table matrix = table(worker.resolve("model-matrix.tsv"), false); header(matrix, joined(List.of("sample"), coefficients)); rows(matrix, counts.samples().size());
        for (int s = 0; s < counts.samples().size(); s++) {
            List<String> row = matrix.data().get(s); if (!row.get(0).equals(counts.samples().get(s))) throw new IOException("Saved fitted matrix sample order differs");
            equal(1, numeric(row.get(1)), "Saved fitted intercept");
            for (int column = 1; column < groups.size(); column++) equal(group(row.get(0)).equals(groups.get(column)) ? 1 : 0, numeric(row.get(column + 1)), "Saved fitted treatment coding");
        }
    }
    private static void master(Path file, Counts counts, List<Comparison> comparisons) throws IOException {
        var expected = new ArrayList<>(PREFIX);
        for (Comparison comparison : comparisons) for (String metric : METRICS) expected.add(comparison.prefix() + "." + metric);
        for (String sample : counts.samples()) expected.add(sample + ".raw"); for (String sample : counts.samples()) expected.add(sample + ".pseudo");
        Table table = table(file, false); header(table, expected); rows(table, counts.prefix().size());
        for (int g = 0; g < table.data().size(); g++) {
            List<String> row = table.data().get(g); if (!row.subList(0, 6).equals(counts.prefix().get(g))) throw new IOException("Original combined-result gene/coordinate order differs");
            int column = 6;
            for (Comparison comparison : comparisons) for (int m = 0; m < METRICS.size(); m++) equal(numeric(comparison.worker().data().get(g).get(m + 1)), numeric(row.get(column++)), "Combined original contrast metric");
            for (int s = 0; s < counts.samples().size(); s++) if (integer(row.get(column++)) != counts.values()[g][s]) throw new IOException("Combined original raw sample count differs");
            for (int s = 0; s < counts.samples().size(); s++) equal(counts.values()[g][s] / counts.factors()[s], numeric(row.get(column++)), "Combined original normalized sample count");
        }
    }
    private static Table matrix(Path file, Counts counts) throws IOException {
        Table table = table(file, false); header(table, joined(List.of("gene"), counts.samples())); rows(table, counts.prefix().size());
        for (int g = 0; g < table.data().size(); g++) if (!table.data().get(g).get(0).equals(counts.prefix().get(g).get(0))) throw new IOException("Original matrix gene population/order differs: " + file.getFileName());
        return table;
    }
    private record Table(List<String> header, List<List<String>> data) { }
    private static Table table(Path file, boolean featureCounts) throws IOException {
        List<String> lines = lines(file); int from = 0;
        if (featureCounts) { if (lines.isEmpty() || !lines.get(0).startsWith("# Program:featureCounts v2.0.1;")) throw new IOException("Original featureCounts program/version header differs"); from++; }
        if (from >= lines.size()) throw new IOException("Missing original table header: " + file.getFileName());
        List<String> header = List.of(lines.get(from++).split("\t", -1));
        if (header.size() > 100000 || new HashSet<>(header).size() != header.size() || header.stream().anyMatch(String::isEmpty)) throw new IOException("Ambiguous/oversized original table columns: " + file.getFileName());
        var data = new ArrayList<List<String>>();
        for (int i = from; i < lines.size(); i++) { List<String> fields = List.of(lines.get(i).split("\t", -1)); if (fields.size() != header.size()) throw new IOException("Original table field count differs: " + file.getFileName() + " row " + (i + 1)); data.add(fields); }
        return new Table(header, List.copyOf(data));
    }
    private static List<String> lines(Path file) throws IOException {
        var result = new ArrayList<String>(); long characters = 0;
        try (BufferedReader input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            StringBuilder line = new StringBuilder(); int c;
            while ((c = input.read()) >= 0) {
                if (++characters > MAX_CHARACTERS) throw new IOException("Complete original differential metadata exceeds decoded-character capacity");
                if (c == '\n') { if (result.size() >= 1000001) throw new IOException("Complete original differential table exceeds row capacity"); result.add(line.toString()); line.setLength(0); }
                else if (c == '\r') { int after = input.read(); if (after >= 0 && ++characters > MAX_CHARACTERS) throw new IOException("Complete original differential metadata exceeds decoded-character capacity"); if (after != '\n' && after >= 0) throw new IOException("Embedded original table carriage return"); if (result.size() >= 1000001) throw new IOException("Complete original differential table exceeds row capacity"); result.add(line.toString()); line.setLength(0); }
                else { if (line.length() >= 2000000 || c < 32 && c != '\t') throw new IOException("Oversized/control-containing original differential row"); line.append((char) c); }
            }
            if (!line.isEmpty()) { if (result.size() >= 1000001) throw new IOException("Complete original differential table exceeds row capacity"); result.add(line.toString()); }
        }
        return List.copyOf(result);
    }
    private static Map<String, String> keyValues(Table table) throws IOException {
        header(table, List.of("key", "value")); var result = new LinkedHashMap<String, String>();
        for (List<String> row : table.data()) if (result.putIfAbsent(row.get(0), row.get(1)) != null) throw new IOException("Duplicate original model metadata key"); return result;
    }
    private static void header(Table table, List<String> expected) throws IOException { if (!table.header().equals(expected)) throw new IOException("Original differential column identities/order differ; expected " + expected); }
    private static void rows(Table table, int expected) throws IOException { if (table.data().size() != expected) throw new IOException("Complete original differential row population differs: expected " + expected + ", observed " + table.data().size()); }
    private static List<String> joined(List<String> a, List<String> b) { var result = new ArrayList<>(a); result.addAll(b); return result; }
    private static List<String> resultHeader(List<String> samples) { var result = new ArrayList<>(PREFIX); result.addAll(METRICS); for (String sample : samples) result.add(sample + ".raw"); for (String sample : samples) result.add(sample + ".pseudo"); return result; }
    private static String group(String sample) { int index = sample.lastIndexOf('_'); return index < 0 ? "" : sample.substring(0, index); }
    private static int compareText(String a, String b) { var x = a.codePoints().iterator(); var y = b.codePoints().iterator(); while (x.hasNext() && y.hasNext()) { int c = Integer.compare(x.nextInt(), y.nextInt()); if (c != 0) return c; } return Boolean.compare(x.hasNext(), y.hasNext()); }
    private static int integer(String value) throws IOException {
        try { if (value == null || value.length() > 128 || !value.matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw new NumberFormatException();
            int result = new java.math.BigDecimal(value).intValueExact(); if (result < 0) throw new NumberFormatException(); return result;
        } catch (NumberFormatException | ArithmeticException invalid) { throw new IOException("Unassessable original nonnegative integer; exact source decimal/scientific notation accepted without rounding: " + value, invalid); }
    }
    private static boolean flag(String value) throws IOException { if (!value.equals("TRUE") && !value.equals("FALSE")) throw new IOException("Unassessable original logical marker: " + value); return value.equals("TRUE"); }
    private static double numeric(String value) throws IOException {
        switch (value) { case "NA": return Deseq2SizeFactors.MISSING; case "NaN": return Double.NaN; case "Inf": return Double.POSITIVE_INFINITY; case "-Inf": return Double.NEGATIVE_INFINITY; default: break; }
        try { if (!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw new NumberFormatException(); double result = Double.parseDouble(value); if (!Double.isFinite(result)) throw new NumberFormatException(); return result; }
        catch (NumberFormatException invalid) { throw new IOException("Unassessable original numeric token retained unchanged: " + value, invalid); }
    }
    private static void equal(double expected, double observed, String label) throws IOException {
        if (Double.isNaN(expected)) { if (!Double.isNaN(observed) || Deseq2SizeFactors.isMissing(expected) != Deseq2SizeFactors.isMissing(observed)) throw new IOException(label + ": source NA/NaN kind differs"); return; }
        if (Double.isInfinite(expected)) { if (expected != observed) throw new IOException(label + ": original infinity differs"); return; }
        double tolerance = Math.max(16 * Math.ulp(expected), 2e-12 * Math.abs(expected));
        if (!Double.isFinite(observed) || (expected == 0 || observed == 0 ? expected != observed : Math.abs(expected - observed) > tolerance)) throw new IOException(label + ": source numeric value differs (expected " + expected + ", observed " + observed + ")");
    }
    private static void display(double expected, String source, int digits, String label) throws IOException {
        double observed = numeric(source); double tolerance = 0.5 * Math.pow(10, Math.floor(Math.log10(expected)) - digits + 1) + 2 * Math.ulp(expected);
        if (!Double.isFinite(observed) || Math.abs(observed - expected) > tolerance) throw new IOException(label + ": source display precision differs; original token retained");
    }
}
