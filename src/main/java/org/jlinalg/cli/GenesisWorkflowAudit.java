/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.GenesisPcairPartition;
import org.jlinalg.genetics.GenesisRankNormalisation;
import org.jlinalg.genetics.SnprelateVariantQc;
import static org.jlinalg.cli.GenesisInputs.*;
import static org.jlinalg.cli.SnprelateRequest.need;
import static org.jlinalg.cli.SnprelateAudit.map;

/** Replays typed identities, finite scientific fields and stated algebraic invariants; does not refit GENESIS. */
final class GenesisWorkflowAudit {
    private GenesisWorkflowAudit() { }
    record NumericMatrix(List<String> rows, List<String> columns, double[][] values) { }
    record Cell(String number, String text, boolean missing, boolean nan) { }
    record Frame(List<String> columns, List<String> types, List<String> rows, Cell[][] values) {
        Cell cell(int row, String column) throws IOException { int i = columns.indexOf(column); need(i >= 0, "Missing original field: " + column); return values[row][i]; }
        double number(int row, String column) throws IOException { Cell c = cell(row, column); need(!c.missing() && !c.nan(), "Original required scientific field is unassessable: " + column); return finite(c.number()); }
        String text(int row, String column) throws IOException { Cell c = cell(row, column); need(!c.missing(), "Original identity is missing"); return c.text(); }
    }
    static String unhex(String raw) throws IOException {
        try { need(raw.length() % 2 == 0 && raw.matches("[a-f0-9]*"), "Invalid original UTF8 hex field"); return NextflowFiles.utf8(HexFormat.of().parseHex(raw)); }
        catch (IllegalArgumentException error) { throw new IOException("Invalid original UTF8 hex field", error); }
    }
    static boolean bool(String raw) throws IOException { need(raw.equals("TRUE") || raw.equals("FALSE"), "Invalid original logical field"); return raw.equals("TRUE"); }
    private static void same(double a, double b, String cause) throws IOException { need(Double.doubleToLongBits(a) == Double.doubleToLongBits(b), "SCIENTIFIC DISCREPANCY: " + cause); }
    private static void near(double a, double b, String cause) throws IOException { need(Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= 1e-8 * Math.max(1, Math.max(Math.abs(a), Math.abs(b))), "SCIENTIFIC DISCREPANCY: " + cause); }
    private static Cell cell(String number, String text, String missing, String nan) throws IOException {
        boolean na = bool(missing), isNan = bool(nan); need(!isNan || na, "Original NaN must also have is.na=TRUE");
        if (!number.isEmpty()) {
            if (na) need(isNan ? number.equals("NaN") : number.equals("NA"), "Original R NA/NaN distinction differs");
            else need(Double.isFinite(finite(number)), "Nonfinite source value is outside this finite typed field profile");
        }
        return new Cell(number, unhex(text), na, isNan);
    }
    private static List<String> labels(Path path, int maximum, String kind, Runnable check) throws IOException {
        Table t = table(path, maximum, check); header(t, kind, kind + "_name_utf8_hex", "label_present"); var result = new ArrayList<String>(); Boolean present = null;
        for (int i = 0; i < t.rows().size(); i++) { String[] f = t.rows().get(i); need(index(f[0], t.rows().size()) == i, "Original label row order differs"); boolean p = bool(f[2]); if (present == null) present = p;
            need(present == p && (p || f[1].isEmpty()), "Inconsistent original label-presence flags"); result.add(unhex(f[1])); }
        return List.copyOf(result);
    }
    static NumericMatrix matrix(Path home, String stem, GenesisRequest.Request r, Runnable check) throws IOException {
        List<String> rows = labels(home.resolve(stem + ".rows.tsv"), r.limits().rows(), "row", check), columns = labels(home.resolve(stem + ".columns.tsv"), r.limits().rows(), "column", check);
        int n = rows.size(), k = columns.size(); need((long)n * k <= r.limits().rows() && 16L * n * k <= r.limits().workingBytes(), "Complete original matrix capacity exceeded");
        Table t = table(home.resolve(stem + ".values.tsv"), r.limits().rows(), check); header(t, "row", "column", "value_hex", "is_na", "is_nan");
        need(t.rows().size() == (long)n * k, "Incomplete original matrix"); double[][] values = new double[n][k]; boolean[][] seen = new boolean[n][k];
        for (String[] f : t.rows()) { int i = index(f[0], n), j = index(f[1], k); need(!seen[i][j], "Duplicate original matrix cell"); seen[i][j] = true;
            Cell c = cell(f[2], "", f[3], f[4]); need(!c.missing(), "Original required matrix contains unassessable NA/NaN; no repair"); values[i][j] = finite(c.number()); }
        return new NumericMatrix(rows, columns, values);
    }
    static Frame frame(Path home, String stem, GenesisRequest.Request r, Runnable check) throws IOException {
        Table schema = table(home.resolve(stem + ".schema.tsv"), 1000, check); header(schema, "column", "name_utf8_hex", "type");
        var columns = new ArrayList<String>(); var types = new ArrayList<String>();
        for (int j = 0; j < schema.rows().size(); j++) { String[] f = schema.rows().get(j); need(index(f[0], schema.rows().size()) == j, "Original frame schema order differs"); columns.add(unhex(f[1])); types.add(f[2]); }
        need(new HashSet<>(columns).size() == columns.size() && types.stream().allMatch(t -> Set.of("integer", "double", "character", "logical").contains(t)), "Unsupported/duplicate original frame column");
        Table rownames = table(home.resolve(stem + ".rownames.tsv"), r.limits().rows(), check); header(rownames, "row", "name_utf8_hex"); var rows = new ArrayList<String>();
        for (int i = 0; i < rownames.rows().size(); i++) { String[] f = rownames.rows().get(i); need(index(f[0], rownames.rows().size()) == i, "Original frame row order differs"); rows.add(unhex(f[1])); }
        int n = rows.size(), k = columns.size(); need((long)n * k <= r.limits().rows() && 64L * n * k <= r.limits().workingBytes(), "Complete original frame storage capacity exceeded");
        Table cells = table(home.resolve(stem + ".cells.tsv"), r.limits().rows(), check); header(cells, "row", "column", "value_hex", "text_utf8_hex", "is_na", "is_nan"); need(cells.rows().size() == (long)n * k, "Incomplete original frame cells");
        Cell[][] values = new Cell[n][k];
        for (String[] f : cells.rows()) { int i = index(f[0], n), j = index(f[1], k); need(values[i][j] == null, "Duplicate original frame cell"); values[i][j] = cell(f[2], f[3], f[4], f[5]);
            if (types.get(j).equals("character")) need(f[2].isEmpty() && !values[i][j].nan(), "Original text field type differs");
            else need(!f[2].isEmpty() && f[3].isEmpty(), "Original numeric field type differs"); }
        return new Frame(List.copyOf(columns), List.copyOf(types), List.copyOf(rows), values);
    }
    static Map<String, Object> outputs(Path root, GenesisRequest.Request r, Data d, Runnable check, boolean createReports) throws IOException {
        verifyTransport(root, r, d); Path home = root.resolve("worker");
        Table parameters = table(home.resolve("original-effective-parameters.tsv"), 100, check); header(parameters, "key", "value"); var actual = new LinkedHashMap<String, String>();
        for (String[] f : parameters.rows()) need(actual.put(f[0], f[1]) == null, "Duplicate effective parameter"); need(actual.equals(GenesisRequest.parameters(r)), "Original effective scientific settings differ");
        Table versions = table(home.resolve("original-package-versions.tsv"), 8, check); header(versions, "package", "version"); var packages = new HashMap<String, String>();
        for (String[] f : versions.rows()) need(packages.put(f[0], f[1]) == null, "Duplicate original version");
        need(packages.equals(Map.of("GENESIS", "2.42.0", "GWASTools", "1.58.0", "SNPRelate", "1.46.0", "gdsfmt", "1.48.2", "RhpcBLASctl", "0.23-42", "BiocParallel", "1.46.0", "Matrix", "1.7-5", "igraph", "2.3.4")), "Original complete dependency versions differ");
        if (d.genotypes() != null) {
            Table geno = table(home.resolve("original-complete-genotypes.tsv"), r.limits().variants(), check); int n = d.ids().size(), m = d.genotypes().variants();
            var expected = new ArrayList<String>(); expected.add("input_variant_row"); for (int i = 1; i <= n; i++) expected.add(Integer.toString(i));
            need(geno.header().equals(expected) && geno.rows().size() == m, "Complete original genotype transport shape differs");
            for (int v = 0; v < m; v++) { String[] row = geno.rows().get(v); need(index(row[0], m) == v, "Original genotype variant row differs"); byte[] calls = d.genotypes().calls(v);
                for (int i = 0; i < n; i++) need(row[i + 1].equals(calls[i] < 0 ? "NA" : Byte.toString(calls[i])), "SCIENTIFIC DISCREPANCY: original genotype transport changed a call"); }
        }
        var rawObjects = new ArrayList<String>();
        if (r.pca() != null || r.relatedness() != null) rawObjects.add("original-result");
        if (r.relatedness() != null) { rawObjects.addAll(List.of("original-kinship-self", "original-kinship-between")); if (r.relatedness().covariance()) rawObjects.add("original-derived-covariance"); }
        if (r.model() != null) rawObjects.addAll(List.of("original-null-model", "original-fixed-effects", "original-sample-fits"));
        if (r.model() != null && r.model().twoStage()) rawObjects.addAll(List.of("original-stage1-null-model", "original-stage1-fixed-effects", "original-stage1-sample-fits"));
        if (r.mode().equals("association")) rawObjects.add("original-association");
        if (r.mode().equals("association") && (!r.model().test().equals("Score") || r.model().interaction() != null)) rawObjects.add("original-score-control");
        for (String stem : rawObjects) {
            Path raw = home.resolve(stem + ".rds"), typed = home.resolve(stem + "-typed.tsv"); need(Files.isRegularFile(raw) && Files.size(raw) >= 20 && Files.isRegularFile(typed) && Files.size(typed) > 100, "Missing original RDS/typed evidence: " + stem);
            try (var stream = Files.newInputStream(raw)) { need(stream.read() == 31 && stream.read() == 139, "Original saved-RDS gzip header differs; raw object is not independently decoded by Java"); }
        }
        var audit = map("mode", r.mode(), "samples", d.ids().size(), "genotype_variants", d.genotypes() == null ? 0 : d.genotypes().variants(),
            "complete_genotype_transport_checked", d.genotypes() != null, "phenotype_order_retained", d.phenotypes() != null, "downstream_eligible", true,
            "native_model_estimator_fit_performed", false, "software_authenticity_proven", false);
        if (r.pca() != null) audit.putAll(pca(home, r, d, check));
        else if (r.relatedness() != null) audit.putAll(relatedness(home, r, d, check));
        else { audit.putAll(model(home, r, d, check)); if (r.mode().equals("association")) audit.putAll(association(home, r, d, check, createReports)); }
        return audit;
    }
    static List<String> stringIds(List<Integer> ids) { return ids == null ? null : ids.stream().map(Object::toString).toList(); }
    private static Map<String, Object> pca(Path home, GenesisRequest.Request r, Data d, Runnable check) throws IOException {
        List<String> related, unrelated; var all = new ArrayList<Integer>(); for (int i = 1; i <= d.ids().size(); i++) all.add(i);
        var expected = map("native_partition_replayed", d.kinship() != null);
        if (d.kinship() != null) {
            var p = r.pca(); var result = GenesisPcairPartition.partition(partitionMatrix(d.kinship()), d.divergence() == null ? null : partitionMatrix(d.divergence()),
                new GenesisPcairPartition.Options(p.kinshipThreshold(), p.divergenceThreshold(), stringIds(d.forced()), stringIds(d.selected())),
                new GenesisPcairPartition.Limits(r.limits().samples(), r.limits().operations(), r.limits().workingBytes()));
            related = result.related(); unrelated = result.unrelated(); expected.put("source_unrelated_pairs_above_threshold", result.unrelatedPairsAboveThreshold());
            need(result.unrelatedSetPassesDeclaredKinshipCutoff(), "Original partition leaves related pairs in unrelated set; preserve source finding and skip dependent PCA use");
        } else {
            List<Integer> include = d.selected() == null ? all : d.selected(); unrelated = stringIds(d.forced() == null ? include : d.forced());
            related = include.stream().map(Object::toString).filter(id -> !unrelated.contains(id)).toList();
        }
        need(unrelated.size() >= 2, "Unrelated PCA population is too small in the validated finite profile");
        Table partition = table(home.resolve("original-PC-partition.tsv"), r.limits().samples(), check); header(partition, "set", "source_order", "sample_alias"); var gotRelated = new ArrayList<String>(); var gotUnrelated = new ArrayList<String>();
        for (String[] f : partition.rows()) { List<String> set = f[0].equals("related") ? gotRelated : f[0].equals("unrelated") ? gotUnrelated : null; need(set != null && index(f[1], d.ids().size()) == set.size(), "Original partition set/order differs"); set.add(f[2]); }
        need(gotRelated.equals(related) && gotUnrelated.equals(unrelated), "SCIENTIFIC DISCREPANCY: original/native ordered PC-AiR partition differs");
        NumericMatrix vectors = matrix(home, "original-PC-vectors", r, check); int k = vectors.columns().size();
        var selected = new HashSet<String>(unrelated); selected.addAll(related); List<String> order = all.stream().map(Object::toString).filter(selected::contains).toList();
        need(vectors.rows().equals(order) && k > 0 && k == (r.pca().components() == 0 ? unrelated.size() : Math.min(r.pca().components(), unrelated.size())), "Original PCA dimensions/participant order differ; no reshaping malformed source output");
        double orthogonality = 0;
        for (int a = 0; a < k; a++) for (int b = 0; b <= a; b++) { double dot = 0; for (String id : unrelated) { int i = order.indexOf(id); dot += vectors.values()[i][a] * vectors.values()[i][b]; } orthogonality = Math.max(orthogonality, Math.abs(dot - (a == b ? 1 : 0))); }
        need(orthogonality <= 1e-8, "Original unrelated PCA basis fails1e-8 orthogonality; projected relatives are not forced orthogonal");
        var qc = new ArrayList<Integer>(); var filters = new SnprelateVariantQc.Filters(r.pca().removeMono(), r.pca().maf(), r.pca().missing());
        var layout = r.layout().equals("snp-first") ? SnprelateVariantQc.Layout.SNP_BY_SAMPLE : SnprelateVariantQc.Layout.SAMPLE_BY_SNP;
        for (int v = 0; v < d.genotypes().variants(); v++) { byte[] calls = d.genotypes().calls(v), subset = new byte[unrelated.size()]; for (int i = 0; i < subset.length; i++) subset[i] = calls[index(unrelated.get(i), calls.length)];
            if (SnprelateVariantQc.selected(SnprelateVariantQc.decide(SnprelateVariantQc.statistics(SnprelateVariantQc.count(subset), layout), filters))) qc.add(v + 1); }
        need(!qc.isEmpty(), "Empty unrelated PCA QC population"); Table sourceQc = table(home.resolve("original-PC-QC-variants.tsv"), r.limits().variants(), check); header(sourceQc, "result_row", "input_variant_row");
        need(sourceQc.rows().size() == qc.size(), "Original unrelated PCA QC population differs"); for (int i = 0; i < qc.size(); i++) need(index(sourceQc.rows().get(i)[0], qc.size()) == i && sourceQc.rows().get(i)[1].equals(qc.get(i).toString()), "Original/native PCA QC row differs");
        Table summary = table(home.resolve("original-PC-summary.tsv"), 1, check); header(summary, "method", "samples", "variants", "kinship_threshold_hex", "divergence_threshold_hex"); need(summary.rows().size() == 1, "Missing original PCA summary"); String[] s = summary.rows().get(0);
        need(s[0].equals(related.isEmpty() ? "Standard PCA" : "PC-AiR") && s[1].equals(Integer.toString(order.size())) && s[2].equals(Integer.toString(qc.size())), "Original PCA summary differs"); same(r.pca().kinshipThreshold(), finite(s[3]), "PCA kinship cutoff"); same(r.pca().divergenceThreshold(), finite(s[4]), "PCA divergence cutoff");
        Table eigen = table(home.resolve("original-PC-eigenvalues.tsv"), r.limits().samples(), check); header(eigen, "component", "value_hex", "is_na", "is_nan"); need(eigen.rows().size() == k, "Original eigenvalue count differs");
        for (int i = 0; i < k; i++) { String[] f = eigen.rows().get(i); need(index(f[0], k) == i && !cell(f[1], "", f[2], f[3]).missing(), "Unassessable or misordered requested eigenvalue"); }
        Table prop = table(home.resolve("original-PC-variance-proportions.tsv"), r.limits().samples(), check); header(prop, "component", "value_hex", "is_na", "is_nan"); int placeholders = 0;
        need(prop.rows().size() >= k, "Missing original variance proportions"); for (int i = 0; i < prop.rows().size(); i++) { String[] f = prop.rows().get(i); need(index(f[0], prop.rows().size()) == i, "Variance proportion order differs"); Cell c = cell(f[1], "", f[2], f[3]); if (c.missing()) { need(i >= k && c.nan(), "Unexpected required variance NA/NaN"); placeholders++; }
            if (i < k) { need(!c.missing(), "Missing requested variance proportion"); near(finite(eigen.rows().get(i)[1]) / (unrelated.size() - 1), finite(c.number()), "PCA eigenvalue/variance proportion relationship on the unrelated population"); }
            if (i >= k && r.pca().algorithm().equals("exact") && r.pca().solver().equals("DSPEVX")) need(c.nan(), "Original uncomputed DSPEVX variance placeholder differs");
        }
        expected.putAll(map("related_samples", related.size(), "unrelated_samples", unrelated.size(), "selected_qc_variants", qc.size(), "components", k, "maximum_unrelated_orthogonality_error", orthogonality, "uncomputed_variance_NaN_placeholders_retained", placeholders)); return expected;
    }
    private static Map<String, Object> relatedness(Path home, GenesisRequest.Request r, Data d, Runnable check) throws IOException {
        Frame self = frame(home, "original-kinship-self", r, check), between = frame(home, "original-kinship-between", r, check);
        var include = d.selected() == null ? new HashSet<Integer>() : new HashSet<>(d.selected()); if (d.selected() == null) for (int i = 1; i <= d.ids().size(); i++) include.add(i);
        int n = include.size(), m = d.genotypes().variants(); need(self.rows().size() == n && between.rows().size() == (long)n * (n - 1) / 2, "Complete PC-Relate family pair population differs");
        var selfIds = new HashSet<Integer>(); double[][] kin = new double[d.ids().size()][d.ids().size()]; boolean[][] seen = new boolean[d.ids().size()][d.ids().size()]; long negativeIbd = 0;
        for (int i = 0; i < n; i++) { int id = index(self.text(i, "ID"), d.ids().size()) + 1; need(include.contains(id) && selfIds.add(id), "Original PC-Relate self identity differs"); double f = self.number(i, "f"); count(self.number(i, "nsnp"), m); kin[id - 1][id - 1] = r.relatedness().scaleKin() * (0.5 * (1 + f)); }
        for (int i = 0; i < between.rows().size(); i++) { int a = index(between.text(i, "ID1"), d.ids().size()), b = index(between.text(i, "ID2"), d.ids().size()); need(a != b && include.contains(a + 1) && include.contains(b + 1) && !seen[a][b], "Duplicate/unmapped original PC-Relate pair"); seen[a][b] = seen[b][a] = true;
            kin[a][b] = kin[b][a] = r.relatedness().scaleKin() * between.number(i, "kin"); count(between.number(i, "nsnp"), m);
            if (r.relatedness().ibd()) for (String field : List.of("k0", "k2")) if (between.number(i, field) < 0) negativeIbd++;
        }
        int blocks = 0;
        if (r.relatedness().covariance()) {
            NumericMatrix covariance = matrix(home, "original-derived-covariance", r, check); need(covariance.rows().equals(covariance.columns()) && covariance.rows().size() == n, "Derived covariance labelled square shape differs");
            var ids = new ArrayList<Integer>(); for (String id : covariance.rows()) ids.add(index(id, d.ids().size())); need(new HashSet<>(ids).size() == n && ids.stream().allMatch(i -> include.contains(i + 1)), "Derived covariance participant set differs");
            int[] component = new int[d.ids().size()]; for (int i = 0; i < component.length; i++) component[i] = i;
            for (int a : ids) for (int b : ids) if (a < b && (r.relatedness().covarianceThreshold() == null ? kin[a][b] != 0 : kin[a][b] > r.relatedness().covarianceThreshold())) { int x = component[a], y = component[b]; for (int k = 0; k < component.length; k++) if (component[k] == y) component[k] = x; }
            var groups = new HashSet<Integer>(); for (int a : ids) groups.add(component[a]); blocks = groups.size();
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) { int a = ids.get(i), b = ids.get(j); same(component[a] == component[b] ? kin[a][b] : 0, covariance.values()[i][j], "PC-Relate covariance connected-component cell; cutoff is applied after scaling, weak within-block edges are retained"); }
        }
        return map("complete_relatedness_pairs", between.rows().size(), "negative_source_IBD_fields_retained", negativeIbd, "covariance_cells_independently_checked", r.relatedness().covariance() ? (long)n * n : 0, "covariance_connected_components", blocks, "ancestry_PCs_recomputed", false);
    }
    private static void count(double value, int maximum) throws IOException { need(value >= 0 && value <= maximum && value == Math.rint(value), "Invalid original contributing-variant count"); }
    private static String scalar(Table ledger, String type, String... path) throws IOException {
        int parent = 1;
        for (String name : path) {
            int found = 0;
            for (String[] row : ledger.rows()) if (row[1].equals(Integer.toString(parent)) && !row[2].equals("attribute") && unhex(row[3]).equals(name)
                    && row[6].isEmpty() && row[7].isEmpty() && row[8].isEmpty()) {
                need(found == 0, "Duplicate named original model field: " + name); found = index(row[0], ledger.rows().size()) + 1;
            }
            need(found != 0, "Missing named original model field: " + name); parent = found;
        }
        String value = null;
        for (String[] row : ledger.rows()) if (row[0].equals(Integer.toString(parent)) && row[6].equals("1")) {
            need(value == null && row[4].equals(type) && row[5].equals("1") && !bool(row[9]) && !bool(row[10]), "Unassessable/non-scalar original model field");
            value = type.equals("character") ? unhex(row[8]) : row[7];
        }
        need(value != null, "Missing original model scalar value"); return value;
    }
    private static Map<String, Object> model(Path home, GenesisRequest.Request r, Data d, Runnable check) throws IOException {
        double[] original = new double[d.phenotypeAliases().size()]; for (int i = 0; i < original.length; i++) original[i] = finite(d.phenotypes().rows().get(i)[2]);
        if (!r.model().twoStage()) return modelStage(home, r, d, check, "original-", original, false);
        var first = modelStage(home, r, d, check, "original-stage1-", original, false);
        Frame firstFit = frame(home, "original-stage1-sample-fits", r, check);
        double[] residuals = new double[original.length]; for (int i = 0; i < residuals.length; i++) residuals[i] = firstFit.number(i, "resid.marginal");
        boolean byGroup = r.model().normalisation().equals("by.group"); need(!byGroup || r.model().group() != null, "Declare groups for within-group two-stage normalisation"); var groups = new ArrayList<String>();
        for (int i = 0; i < residuals.length; i++) groups.add(byGroup ? d.phenotypes().rows().get(i)[d.phenotypes().header().indexOf(r.model().group())] : "__all__");
        var limits = new GenesisRankNormalisation.Limits(r.limits().samples(), r.limits().workingBytes());
        var rank = byGroup ? GenesisRankNormalisation.normalizeByGroup(residuals, groups, limits) : GenesisRankNormalisation.normalize(residuals, limits);
        double[] averageRanks = rank.averageRanks(), unitScores = rank.scores(), expected = new double[residuals.length];
        Table report = table(home.resolve("original-normalisation.tsv"), r.limits().samples(), check);
        header(report, "model_row", "sample_alias", "group_utf8_hex", "residual_hex", "average_rank_hex", "unit_score_hex", "variance_hex", "scale_hex", "normalised_outcome_hex");
        need(report.rows().size() == residuals.length, "Complete original two-stage normalisation row count differs");
        Table components = table(home.resolve("original-stage1-variance-components.tsv"), 100, check);
        header(components, "component", "name_utf8_hex", "name_present", "value_hex", "is_na", "is_nan");
        var componentValues = new ArrayList<Double>(); var componentNames = new HashMap<String, Double>();
        for (int i = 0; i < components.rows().size(); i++) { String[] row = components.rows().get(i); need(index(row[0], 100) == i && !bool(row[4]) && !bool(row[5]), "Unassessable original first-stage variance component"); double value = finite(row[3]); componentValues.add(value); if (bool(row[2])) need(componentNames.put(unhex(row[1]), value) == null, "Duplicate original variance component name"); }
        var groupVariances = new HashMap<String, Double>();
        for (String group : new HashSet<>(groups)) {
            var selected = new ArrayList<Integer>(); for (int i = 0; i < groups.size(); i++) if (groups.get(i).equals(group)) selected.add(i);
            double variance;
            if (r.model().rescale().equals("none")) variance = 1;
            else if (r.model().rescale().equals("residSD")) variance = sampleVariance(residuals, selected);
            else {
                need(byGroup && componentValues.size() >= d.covariances().size(), "Original model-variance scaling needs explicit groups/components"); variance = 0;
                for (int k = 0; k < d.covariances().size(); k++) {
                    Matrix covariance = d.covariances().get(k); double diagonal = 0;
                    for (int i : selected) { int row = covariance.aliases().indexOf(d.phenotypeAliases().get(i)); diagonal += covariance.values()[row][row]; }
                    variance += componentValues.get(k) * (diagonal / selected.size());
                }
                need(componentNames.containsKey("V_" + group), "Original group residual variance component is missing"); variance += componentNames.get("V_" + group);
            }
            need(Double.isFinite(variance) && variance >= 0, "Unassessable original/native residual variance; no repair"); groupVariances.put(group, variance);
        }
        for (int i = 0; i < residuals.length; i++) {
            String[] row = report.rows().get(i); need(index(row[0], residuals.length) == i && row[1].equals(d.phenotypeAliases().get(i).toString()) && unhex(row[2]).equals(groups.get(i)), "Original two-stage row identity/group differs");
            same(residuals[i], finite(row[3]), "Original first-stage residual"); same(averageRanks[i], finite(row[4]), "Original within-declared-population average rank");
            need(Math.abs(unitScores[i] - finite(row[5])) <= 2e-14 * Math.max(1, Math.abs(unitScores[i])), "Original/native normal quantile differs from the validated2e-14 bound");
            double variance = groupVariances.get(groups.get(i)), scale = Math.sqrt(variance);
            near(variance, finite(row[6]), "Original first-stage variance rescaling"); near(scale, finite(row[7]), "Original first-stage standard-deviation rescaling");
            expected[i] = unitScores[i] * scale; near(expected[i], finite(row[8]), "Original/native two-stage transformed outcome");
        }
        var result = modelStage(home, r, d, check, "original-", expected, true);
        result.putAll(map("two_stage_normalisation", r.model().normalisation(), "two_stage_rescale", r.model().rescale(), "two_stage_native_rank_quantiles_checked", residuals.length,
            "two_stage_extra_original_control_fit", true, "first_stage_scientific_audit", first, "native_model_fit_performed", false,
            "fitted_rows_correspond_to_phenotype_order", true, "phenotype_sample_alias_order", d.phenotypeAliases())); return result;
    }
    private static double sampleVariance(double[] values, List<Integer> indices) throws IOException {
        need(indices.size() >= 2, "Sample-variance rescaling needs at least two original residuals per declared group");
        var context = new MathContext(80); BigDecimal sum = BigDecimal.ZERO; for (int i : indices) sum = sum.add(new BigDecimal(values[i]));
        BigDecimal mean = sum.divide(BigDecimal.valueOf(indices.size()), context), squared = BigDecimal.ZERO;
        for (int i : indices) { BigDecimal difference = new BigDecimal(values[i]).subtract(mean); squared = squared.add(difference.multiply(difference, context), context); }
        return squared.divide(BigDecimal.valueOf(indices.size() - 1), context).doubleValue();
    }
    private static Map<String, Object> modelStage(Path home, GenesisRequest.Request r, Data d, Runnable check, String stem, double[] expectedOutcomes, boolean allowPositionalLabels) throws IOException {
        NumericMatrix design = matrix(home, stem + "design", r, check), betaCov = matrix(home, stem + "beta-covariance", r, check); var model = r.model();
        Table ledger = table(home.resolve(stem + "null-model-typed.tsv"), r.limits().rows(), check); header(ledger, "node", "parent", "slot", "name_utf8_hex", "type", "length", "element", "value_hex", "text_utf8_hex", "is_na", "is_nan");
        boolean mixed = bool(scalar(ledger, "logical", "model", "family", "mixedmodel")), heterogeneous = bool(scalar(ledger, "logical", "model", "hetResid"));
        need(bool(scalar(ledger, "logical", "converged")) && scalar(ledger, "character", "model", "family", "family").equals(model.family()), "Original model object did not converge in the requested family");
        var columns = new ArrayList<>(List.of("(Intercept)")); columns.addAll(model.covariates());
        need(design.rows().equals(stringIds(d.phenotypeAliases())) && design.columns().equals(columns), "Original model design dropped/reordered a requested covariate or phenotype row; no silent omission");
        for (int i = 0; i < design.rows().size(); i++) for (int j = 0; j < columns.size(); j++) same(j == 0 ? 1 : finite(d.phenotypes().rows().get(i)[j + 2]), design.values()[i][j], "Complete requested model design cell");
        Frame fixed = frame(home, stem + "fixed-effects", r, check), fit = frame(home, stem + "sample-fits", r, check);
        var positionalLabels = new ArrayList<String>(); for (int i = 1; i <= d.ids().size(); i++) positionalLabels.add(Integer.toString(i));
        boolean positional = !fit.rows().equals(stringIds(d.phenotypeAliases()));
        need(fixed.rows().equals(columns) && (!positional || allowPositionalLabels && fit.rows().equals(positionalLabels)), "Original fixed-effect/sample-fit identities differ");
        need(betaCov.rows().equals(columns) && betaCov.columns().equals(columns), "Original beta-covariance identities differ");
        boolean squared = mixed || heterogeneous;
        List<String> fields = squared ? List.of("Est", "SE", "Stat", "pval") : model.family().equals("gaussian") ? List.of("Estimate", "Std. Error", "t value", "Pr(>|t|)") : List.of("Estimate", "Std. Error", "z value", "Pr(>|z|)");
        need(fixed.columns().equals(fields), "Original effective-model fixed-effect column names differ; no renaming or generic statistic substitution");
        for (int i = 0; i < columns.size(); i++) { double beta = fixed.number(i, fields.get(0)), se = fixed.number(i, fields.get(1)), stat = fixed.number(i, fields.get(2)), p = fixed.number(i, fields.get(3)); need(se > 0 && p >= 0 && p <= 1, "Invalid original fixed-effect uncertainty/P value"); double z = beta / se; near(squared ? z * z : z, stat, "Effective-model fixed-effect statistic"); near(se * se, betaCov.values()[i][i], "Fixed-effect covariance diagonal"); }
        for (int i = 0; i < fit.rows().size(); i++) {
            if (allowPositionalLabels) near(expectedOutcomes[i], fit.number(i, "outcome"), "Original fitted outcome differs from the declared two-stage transformation");
            else same(expectedOutcomes[i], fit.number(i, "outcome"), "Original fitted outcome differs from the captured input");
            for (String field : fit.columns()) fit.number(i, field);
        }
        Table status = table(home.resolve(stem + "model-status.tsv"), 1, check); header(status, "converged", "effective_family", "effective_mixed", "zero_flags"); need(status.rows().size() == 1, "Missing original model status"); String[] s = status.rows().get(0);
        need(bool(s[0]) && s[1].equals(model.family()), "Original model did not converge in the requested family; admit no downstream association");
        need(bool(s[2]) == mixed, "Original model status and full typed object disagree on effective engine"); boolean fallback = !d.covariances().isEmpty() && !mixed && !heterogeneous;
        if (fallback) need(Files.readString(home.resolve("original-messages.txt"), StandardCharsets.UTF_8).contains("All variance components estimated as zero, using glm"), "Original mixed-model fallback message is missing");
        return map("model_converged", true, "source_fit_row_labels_are_positional", positional, "requested_family", model.family(), "effective_mixed_model", mixed, "effective_heterogeneous_residuals", heterogeneous,
            "fixed_effect_statistic", squared ? "squared Wald statistic" : model.family().equals("gaussian") ? "signed t statistic" : "signed z statistic",
            "original_fixed_effect_columns", fields, "source_zero_flags", s[3], "original_GLM_fallback", fallback, "model_design_columns", columns, "boundary_variance_auxiliary_NA_retained_in_raw_RDS_and_typed_ledger", true);
    }
    private record Variant(int inputRow, int called, long copies, int codedCarriers, String disposition) { }
    private static List<Variant> variants(GenesisRequest.Request r, Data d, boolean restrictFrequency) throws IOException {
        var result = new ArrayList<Variant>();
        for (int v = 0; v < d.genotypes().variants(); v++) {
            byte[] calls = d.genotypes().calls(v); int count = 0, carriers = 0, first = -1; long sum = 0; boolean variable = false;
            for (byte g : calls) if (g >= 0) { count++; sum += g; if (first < 0) first = g; else variable |= g != first;
                if (r.model().coding().equals("recessive") ? g == 2 : g > 0) carriers++; }
            String disposition = count == 0 ? "ALL_MISSING" : !variable ? "MONOMORPHIC" : restrictFrequency && (double)sum / (2 * count) > .5 ? "REPORTED_EFFECT_ALLELE_FREQUENCY_GT_HALF" :
                !r.model().coding().equals("additive") && (carriers == 0 || carriers == count) ? "CONSTANT_AFTER_DECLARED_CODING" : "TESTED_ORIGINAL_DERIVED_INPUT_MEAN_IMPUTATION";
            result.add(new Variant(v, count, sum, carriers, disposition));
        }
        return result;
    }
    private static List<Variant> tested(List<Variant> variants) { return variants.stream().filter(v -> v.disposition().equals("TESTED_ORIGINAL_DERIVED_INPUT_MEAN_IMPUTATION")).toList(); }
    private static void summary(Frame frame, int row, Variant variant, GenesisRequest.Request r, Data d) throws IOException {
        int v = variant.inputRow(); need(frame.number(row, "variant.id") == v + 1 && frame.text(row, "chr").equals(Integer.toString(d.genotypes().chromosomes()[v])) && frame.number(row, "pos") == d.genotypes().positions()[v], "Original association variant identity/order differs");
        same(variant.called(), frame.number(row, "n.obs"), "Original called-sample count"); same((double)variant.copies() / (2 * variant.called()), frame.number(row, "freq"), "Original genotype allele frequency");
        same(Math.min(variant.copies(), 2 * variant.called() - variant.copies()), frame.number(row, "MAC"), "Original minor allele count");
        if (!r.model().coding().equals("additive")) same(variant.codedCarriers(), frame.number(row, r.model().coding().equals("dominant") ? "n.any.eff" : "n.hom.eff"), "Original nonmissing carrier count for declared coding");
    }
    private static double probability(Frame frame, int row, String field) throws IOException {
        double p = frame.number(row, field);
        String location = frame.columns().contains("variant.id") ? " at original input variant row " + (long)frame.number(row, "variant.id") : " at original report row " + (row + 1);
        need(p >= 0 && p <= 1, "Original " + field + "=" + p + " (" + Double.toHexString(p) + ")" + location + " is outside0..1. The source value is retained without clipping; NO RESULT ADMITTED"); return p;
    }
    private static void scoreFields(Frame result, int i, String pField) throws IOException {
        double score = result.number(i, "Score"), se = result.number(i, "Score.SE"), z = result.number(i, "Score.Stat"); need(se > 0, "Unassessable original score standard error");
        near(score / se, z, "Score.Stat is the signed standardized score"); near(score / (se * se), result.number(i, "Est"), "Score effect estimate"); near(1 / se, result.number(i, "Est.SE"), "Score effect standard error"); probability(result, i, pField);
    }
    private static Map<String, Object> association(Path home, GenesisRequest.Request r, Data d, Runnable check, boolean createReports) throws IOException {
        Frame result = frame(home, "original-association", r, check); int m = d.genotypes().variants(), n = d.ids().size();
        Table selection = table(home.resolve("original-association-sample-selection.tsv"), n, check); header(selection, "model_row", "input_sample_row", "source_scan_id", "model_sample_alias"); need(selection.rows().size() == n, "Complete original association sample-selection population differs");
        for (int i = 0; i < n; i++) { String[] row = selection.rows().get(i); String alias = d.phenotypeAliases().get(i).toString(); need(index(row[0], n) == i && row[1].equals(alias) && row[2].equals(alias) && row[3].equals(alias), "SCIENTIFIC DISCREPANCY: original genotype/phenotype association sample order differs"); }
        Table modelStatus = table(home.resolve("original-model-status.tsv"), 1, check); boolean mixed = bool(modelStatus.rows().get(0)[2]);
        String requested = r.model().test(), effective = r.model().interaction() != null ? "Wald.GxE" : requested.equals("Score.SPA") && !r.model().family().equals("binomial") ? "Score" : requested.equals("CMP") && !mixed ? "BinomiRare" : requested;
        boolean controlExecuted = !requested.equals("Score") || r.model().interaction() != null;
        Table status = table(home.resolve("original-association-status.tsv"), 1, check); header(status, "requested_test", "effective_test", "variants", "interaction", "score_control_executed"); need(status.rows().size() == 1, "Missing original association status"); String[] actualStatus = status.rows().get(0);
        need(actualStatus[0].equals(requested) && actualStatus[1].equals(effective) && actualStatus[2].equals(Integer.toString(result.rows().size())) && actualStatus[3].equals(r.model().interaction() == null ? "NULL" : r.model().interaction()) && bool(actualStatus[4]) == controlExecuted, "Original requested/effective test status differs from source dispatch");
        var all = variants(r, d, false); var main = variants(r, d, Set.of("BinomiRare", "CMP").contains(requested)); var expected = tested(main);
        need(result.rows().size() == expected.size() && !expected.isEmpty(), "Original association variant disposition differs or no variants are testable");
        Frame score = controlExecuted ? frame(home, "original-score-control", r, check) : result; var scoreVariants = tested(all); var scoreRows = new HashMap<Integer, Integer>();
        need(score.rows().size() == scoreVariants.size(), "Complete separate original Score control population differs");
        for (int i = 0; i < scoreVariants.size(); i++) { summary(score, i, scoreVariants.get(i), r, d); scoreFields(score, i, "Score.pval"); scoreRows.put(scoreVariants.get(i).inputRow(), i); }
        int spaRecalculated = 0, spaNotRecalculated = 0, spaNonconverged = 0; Map<String, Object> extra = new LinkedHashMap<>();
        for (int i = 0; i < expected.size(); i++) {
            Variant v = expected.get(i); summary(result, i, v, r, d); int controlRow = scoreRows.get(v.inputRow());
            if (effective.equals("Wald.GxE")) {
                String interaction = r.model().interaction(); double se = result.number(i, "SE.G:" + interaction), beta = result.number(i, "Est.G:" + interaction), statistic = result.number(i, "GxE.Stat"), joint = result.number(i, "Joint.Stat");
                need(se > 0 && result.number(i, "SE.G") > 0 && statistic >= 0 && joint >= 0, "Unassessable original GxE Wald fields"); near(Math.abs(beta / se), statistic, "Single-environment GxE.Stat is the square root of the Wald statistic");
                near(jdistlib.ChiSquare.cumulative(statistic * statistic, 1, false, false), probability(result, i, "GxE.pval"), "Original GxE Wald P value");
                near(jdistlib.ChiSquare.cumulative(joint * joint, 2, false, false), probability(result, i, "Joint.pval"), "Original joint Wald P value uses Joint.Stat squared");
            } else if (effective.equals("Score") || effective.equals("Score.SPA")) {
                scoreFields(result, i, effective.equals("Score.SPA") ? "SPA.pval" : "Score.pval");
                if (controlExecuted) for (String field : List.of("Score", "Score.SE", "Score.Stat", "Est", "Est.SE", "PVE")) same(score.number(controlRow, field), result.number(i, field), "Original Score control field " + field);
                if (effective.equals("Score.SPA")) {
                    double ordinaryP = probability(score, controlRow, "Score.pval"); Cell convergence = result.cell(i, "SPA.converged");
                    if (ordinaryP > r.model().recalcThreshold()) { need(convergence.missing() && !convergence.nan(), "SPA status must remain original NA when not recalculated"); same(ordinaryP, probability(result, i, "SPA.pval"), "Non-recalculated SPA retains the original Score P value"); spaNotRecalculated++; }
                    else { need(!convergence.missing(), "Missing original recalculated SPA convergence status"); double converged = finite(convergence.number()); need(converged == 0 || converged == 1, "Invalid original SPA convergence field"); spaRecalculated++;
                        if (converged == 0) { same(ordinaryP, probability(result, i, "SPA.pval"), "Nonconverged original SPA retains its original Score P value"); spaNonconverged++; } }
                } else if (controlExecuted) same(probability(score, controlRow, "Score.pval"), probability(result, i, "Score.pval"), "Original fallback Score P value");
            } else if (effective.equals("BinomiRare")) { probability(result, i, "pval"); probability(result, i, "mid.pval"); }
            else need(false, "This upstream effective test returned an unvalidated result shape; preserve it without admission: " + effective);
        }
        if (effective.equals("BinomiRare")) extra.putAll(binomiRare(home, r, d, result, score, scoreRows, expected, mixed, check));
        Table packages = table(home.resolve("original-optional-package-versions.tsv"), 3, check); header(packages, "package", "installed", "version", "namespace_loaded");
        var optional = new HashMap<String, String[]>(); for (String[] row : packages.rows()) need(optional.put(row[0], row) == null, "Duplicate optional package metadata");
        need(optional.keySet().equals(Set.of("SPAtest", "poibin", "COMPoissonReg")), "Optional original package population differs");
        String dependency = effective.equals("Score.SPA") ? "SPAtest" : effective.equals("BinomiRare") ? "poibin" : effective.equals("CMP") ? "COMPoissonReg" : null;
        if (dependency != null) { String[] row = optional.get(dependency); need(bool(row[1]) && bool(row[3]) && row[2].equals(dependency.equals("SPAtest") ? "3.1.2" : dependency.equals("poibin") ? "1.6" : "0.8.2"), "Required exact optional test package was not loaded"); }
        var disposition = new StringBuilder("input_variant_row\tdisposition\tcalled\tmissing\tallele_copies\n");
        for (Variant v : main) disposition.append(v.inputRow() + 1).append('\t').append(v.disposition()).append('\t').append(v.called()).append('\t').append(n - v.called()).append('\t').append(v.copies()).append('\n');
        Path report = home.getParent().resolve("native-association-variant-dispositions.tsv"); if (createReports) write(report, disposition.toString()); else need(Files.readString(report, StandardCharsets.UTF_8).equals(disposition.toString()), "Native association variant disposition report differs");
        extra.putAll(map("tested_variants", expected.size(), "excluded_variants", m - expected.size(), "original_calls_retained", true, "mean_imputation_only_in_original_derived_test_inputs", true,
            "requested_association_test", requested, "effective_association_test", effective, "source_CMP_to_BinomiRare_fallback", requested.equals("CMP") && effective.equals("BinomiRare"),
            "source_SPA_to_Score_fallback", requested.equals("Score.SPA") && effective.equals("Score"), "SPA_recalculated", spaRecalculated, "SPA_not_recalculated", spaNotRecalculated, "SPA_not_converged_retaining_original_Score_P", spaNonconverged,
            "GxE_statistic_interpretation", "GxE.Stat and Joint.Stat are square roots of their Wald statistics", "Score_Stat_interpretation", "signed standardized score (Z)", "additional_original_Score_control_executed", controlExecuted)); return extra;
    }
    private static Map<String, Object> binomiRare(Path home, GenesisRequest.Request r, Data d, Frame result, Frame score, Map<Integer, Integer> scoreRows, List<Variant> expected, boolean mixed, Runnable check) throws IOException {
        int n = d.ids().size(); Frame fit = frame(home, "original-sample-fits", r, check); Table probabilities = table(home.resolve("original-association-probabilities.tsv"), n, check); header(probabilities, "model_row", "sample_alias", "probability_hex"); need(probabilities.rows().size() == n, "Complete original conditional probability vector differs");
        double[] probs = new double[n];
        for (int i = 0; i < n; i++) { String[] row = probabilities.rows().get(i); need(index(row[0], n) == i && row[1].equals(d.phenotypeAliases().get(i).toString()), "Original conditional-probability sample order differs"); probs[i] = finite(row[2]); need(probs[i] >= 0 && probs[i] <= 1, "Unassessable original Bernoulli probability");
            double expectedProbability = mixed ? Math.exp(fit.number(i, "linear.predictor")) / (1 + Math.exp(fit.number(i, "linear.predictor"))) : fit.number(i, "fitted.values"); near(expectedProbability, probs[i], "Original conditional probability follows effective model dispatch"); }
        Table pmfs = table(home.resolve("original-poibin-control-pmfs.tsv"), r.limits().rows(), check); header(pmfs, "input_variant_row", "successes", "probability_hex"); var sourceMass = new HashMap<Integer, Map<Integer, Double>>();
        for (String[] row : pmfs.rows()) { int v = index(row[0], d.genotypes().variants()), k; try { k = Integer.parseInt(row[1]); } catch (NumberFormatException error) { throw new IOException("Invalid original PMF support", error); } need(k >= 0 && k <= n && Integer.toString(k).equals(row[1]), "Original PMF support differs"); need(sourceMass.computeIfAbsent(v, ignored -> new HashMap<>()).put(k, finite(row[2])) == null, "Duplicate original PMF support cell"); }
        var usedMass = new HashSet<Integer>(); long compared = 0, orderingDisagreements = 0, negativeSourceMass = 0;
        for (int i = 0; i < expected.size(); i++) {
            check.run(); Variant variant = expected.get(i); byte[] calls = d.genotypes().calls(variant.inputRow()); var carrierRows = new ArrayList<Integer>(); var carrierProbabilities = new ArrayList<Double>(); double observed = 0, mean = 0;
            for (int j = 0; j < n; j++) { byte call = calls[d.phenotypeAliases().get(j) - 1]; boolean carrier = call < 0 || (r.model().coding().equals("recessive") ? call == 2 : call > 0);
                if (carrier) { carrierRows.add(j); carrierProbabilities.add(probs[j]); mean += probs[j]; observed += fit.number(j, "outcome"); } }
            int carriers = carrierRows.size(); same(carriers, result.number(i, "n.carrier"), "Original mean-imputed carrier count"); same(observed, result.number(i, "n.D.carrier"), "Original carrier outcome count"); near(mean, result.number(i, "expected.n.D.carrier"), "Original expected carrier outcome count");
            double ordinaryP = probability(score, scoreRows.get(variant.inputRow()), "Score.pval");
            if (ordinaryP > r.model().recalcThreshold()) { same(ordinaryP, probability(result, i, "pval"), "Non-recalculated original BinomiRare uses Score P"); same(ordinaryP, probability(result, i, "mid.pval"), "Non-recalculated original mid-P uses Score P"); continue; }
            need(observed >= 0 && observed <= carriers && observed == Math.rint(observed), "Original recomputed Poisson-binomial outcome count is unassessable"); Map<Integer, Double> mass = sourceMass.get(variant.inputRow()); need(mass != null && mass.size() == carriers + 1, "Complete original poibin PMF control differs"); usedMass.add(variant.inputRow());
            double[] p = new double[carriers]; for (int k = 0; k < carriers; k++) p[k] = carrierProbabilities.get(k); var nativeDistribution = new jdistlib.PoissonBinomial(p); double atObservation = mass.get((int)observed), nativeAtObservation = nativeDistribution.density(observed, false), lowerDensitySum = 0;
            for (int k = 0; k <= carriers; k++) { need(mass.containsKey(k), "Missing original PMF support cell"); double source = mass.get(k), nativeValue = nativeDistribution.density(k, false); need(Math.abs(source - nativeValue) <= 2e-12, "Original/native Poisson-binomial PMF differs beyond the declared2e-12 absolute bound"); compared++; if (source < 0) negativeSourceMass++;
                if ((source < atObservation) != (nativeValue < nativeAtObservation)) orderingDisagreements++; if (source < atObservation) lowerDensitySum += source; }
            double expectedP = atObservation + lowerDensitySum, expectedMid = .5 * atObservation + lowerDensitySum;
            need(Math.abs(expectedP - probability(result, i, "pval")) <= 2e-12 && Math.abs(expectedMid - probability(result, i, "mid.pval")) <= 2e-12, "Original strict-smaller-density BinomiRare P/mid-P differs; source FFT densities are retained without clipping or tie repair");
        }
        need(sourceMass.keySet().equals(usedMass), "Unexpected/missing original poibin control variants");
        return map("native_poisson_binomial_PMF_cells_checked", compared, "source_PMF_density_ordering_roundoff_disagreements", orderingDisagreements, "negative_source_FFT_PMF_values_retained", negativeSourceMass,
            "strict_density_tail_uses_original_saved_FFT_values", true, "requested_family_is_binomial", r.model().family().equals("binomial"));
    }
}
