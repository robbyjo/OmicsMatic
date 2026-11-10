/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.GenesisPcairPartition;
import static org.jlinalg.cli.SnprelateRequest.need;

/** Captured, explicit identities and typed numeric transport. No sorting or statistical imputation. */
final class GenesisInputs {
    private GenesisInputs() { }
    record Id(String fid, String iid) { }
    record Table(List<String> header, List<String[]> rows) { }
    record Matrix(List<Integer> aliases, double[][] values) { }
    record Data(List<Id> ids, SnprelateAudit.Data genotypes, Table phenotypes, List<Integer> phenotypeAliases,
                Matrix kinship, Matrix divergence, List<Integer> selected, List<Integer> forced,
                List<Integer> training, Table pcs, List<Matrix> covariances, long inputRows) { }

    static Map<String, Path> sources(GenesisRequest.Request r) {
        var result = new LinkedHashMap<String, Path>(); var i = r.inputs();
        if (i.bfile() != null) for (String ext : List.of("bed", "bim", "fam")) result.put("cohort." + ext, Path.of(i.bfile() + "." + ext));
        if (i.phenotypes() != null) result.put("phenotypes.tsv", i.phenotypes());
        matrixSources(result, "kinship", i.kinship()); matrixSources(result, "divergence", i.divergence());
        if (i.selected() != null) result.put("selection.tsv", i.selected());
        if (i.forced() != null) result.put("forcing.tsv", i.forced());
        if (i.training() != null) result.put("training.tsv", i.training());
        if (i.pcs() != null) result.put("pcs.tsv", i.pcs());
        for (var c : i.covariance()) matrixSources(result, "covariance-" + c.name(), c);
        return result;
    }
    private static void matrixSources(Map<String, Path> result, String name, GenesisRequest.MatrixInput input) {
        if (input != null) { result.put(name + ".values.tsv", input.values()); result.put(name + ".samples.tsv", input.samples()); }
    }
    static SnprelateRequest.Request genotypeRequest(GenesisRequest.Request r) {
        return new SnprelateRequest.Request("grm", r.inputs().bfile(), r.profile(), r.out(), r.layout(), false, false,
            Double.NaN, Double.NaN, "GCTA", "", 0, 0, null, 0, "", null, r.limits());
    }
    static Table table(Path path, int maximum, Runnable check) throws IOException {
        var records = new ArrayList<String[]>(); List<String> header;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = reader.readLine(); need(line != null && line.length() <= 1_000_000, "Missing/oversized complete TSV header: " + path.getFileName());
            header = List.of(line.split("\t", -1)); need(new HashSet<>(header).size() == header.size(), "Duplicate TSV columns");
            for (String h : header) field(h);
            while ((line = reader.readLine()) != null) {
                check.run(); need(records.size() < maximum && line.length() <= 1_000_000, "Complete TSV row/line capacity exceeded: " + path.getFileName());
                String[] f = line.split("\t", -1); need(f.length == header.size(), "Incomplete/ragged TSV: " + path.getFileName());
                for (String value : f) need(value.chars().noneMatch(c -> c < 32 || c == 127), "Invalid control character in TSV field"); records.add(f);
            }
        }
        return new Table(header, List.copyOf(records));
    }
    static void header(Table t, String... names) throws IOException { need(t.header().equals(List.of(names)), "Unexpected complete TSV columns: " + t.header()); }
    static void field(String text) throws IOException {
        need(!text.isEmpty() && text.chars().noneMatch(c -> c <= 32 || c == 127), "Empty/whitespace/control identifier is outside this explicit transport profile");
        for (int i = 0; i < text.length(); i++) if (Character.isSurrogate(text.charAt(i))) {
            need(Character.isHighSurrogate(text.charAt(i)) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(++i)), "Invalid Unicode identifier");
        }
    }
    static Id id(String[] row) throws IOException { field(row[0]); field(row[1]); return new Id(row[0], row[1]); }
    static List<Id> identities(Table table) throws IOException {
        need(table.header().size() >= 2 && table.header().subList(0, 2).equals(List.of("FID", "IID")), "Explicit FID/IID columns must lead the table; literal NA/NaN identifiers are retained");
        var ids = new ArrayList<Id>(); var seen = new HashSet<Id>();
        for (String[] row : table.rows()) { Id id = id(row); need(seen.add(id), "Duplicate FID/IID identity; no merging"); ids.add(id); }
        return List.copyOf(ids);
    }
    static List<Integer> aliases(List<Id> values, Map<Id, Integer> map) throws IOException {
        var result = new ArrayList<Integer>(); for (Id id : values) { need(map.containsKey(id), "Unmapped FID/IID; no ID repair: " + id); result.add(map.get(id)); } return List.copyOf(result);
    }
    private static List<Integer> selection(Path home, String stem, Path declared, Map<Id, Integer> aliases, GenesisRequest.Request r, Runnable check) throws IOException {
        if (declared == null) return null;
        Table t = table(home.resolve(stem + ".tsv"), r.limits().samples(), check); header(t, "FID", "IID");
        var result = aliases(identities(t), aliases); need(!result.isEmpty(), "An explicit empty sample set is not silently treated as NULL"); return result;
    }
    static double finite(String value) throws IOException { double d = SnprelateAudit.number(value); need(Double.isFinite(d), "Finite complete numeric input required; NA/NaN/Inf are not repaired"); return d; }
    static int index(String value, int size) throws IOException {
        try { int i = Integer.parseInt(value); need(i > 0 && i <= size && Integer.toString(i).equals(value), "Invalid explicit 1-based source row"); return i - 1; }
        catch (NumberFormatException error) { throw new IOException("Invalid integer source row", error); }
    }
    private static Matrix matrix(Path home, String stem, GenesisRequest.MatrixInput declared, Map<Id, Integer> aliases,
                                 GenesisRequest.Request r, Runnable check) throws IOException {
        if (declared == null) return null;
        Table samples = table(home.resolve(stem + ".samples.tsv"), r.limits().samples(), check); header(samples, "FID", "IID");
        List<Integer> ids = aliases(identities(samples), aliases); int n = ids.size(); need(n >= 2, "At least two matrix participants required in this profile");
        need(32L * n * n <= r.limits().workingBytes() && (long)n * n <= r.limits().rows(), "Complete matrix storage/row capacity exceeded");
        Table cells = table(home.resolve(stem + ".values.tsv"), r.limits().rows(), check); header(cells, "row", "column", "value_hex");
        need(cells.rows().size() == (long)n * n, "Complete matrix cells required; no symmetry/diagonal completion");
        double[][] values = new double[n][n]; boolean[][] seen = new boolean[n][n];
        for (String[] row : cells.rows()) { int i = index(row[0], n), j = index(row[1], n); need(!seen[i][j], "Duplicate matrix cell"); seen[i][j] = true; values[i][j] = finite(row[2]); }
        for (int i = 0; i < n; i++) for (int j = 0; j < i; j++) need(values[i][j] == values[j][i], "Asymmetric matrix; no averaging or repair");
        return new Matrix(ids, values);
    }
    static Data read(Path root, GenesisRequest.Request r, Runnable check) throws IOException {
        Path home = root.resolve("inputs"); SnprelateAudit.Data genotype = null; var ids = new ArrayList<Id>(); Table traits = null;
        if (r.inputs().bfile() != null) {
            genotype = SnprelateAudit.inputs(root, genotypeRequest(r), check);
            for (String[] row : genotype.fam()) { need(List.of("0", "1", "2").contains(row[4]), "Explicit PLINK sex0/1/2 required for the declared GWASTools mapping"); ids.add(id(row)); }
            for (int chr : genotype.chromosomes()) need(chr <= 22, "This GENESIS transport profile requires declared autosomal hard-call SNPs; no chromosome dropping");
        }
        if (r.model() != null) {
            traits = table(home.resolve("phenotypes.tsv"), r.limits().samples(), check); var expected = new ArrayList<>(List.of("FID", "IID", r.model().outcome())); expected.addAll(r.model().covariates());
            if (r.model().group() != null) { need(!expected.contains(r.model().group()), "Group variable needs a separate explicit string column"); expected.add(r.model().group()); }
            need(traits.header().equals(expected), "Phenotype TSV must contain exactly FID/IID, outcome, declared covariates and optional group in that order");
            List<Id> phenotypeIds = identities(traits); need(phenotypeIds.size() >= 2, "At least two complete phenotype participants required");
            if (genotype == null) ids.addAll(phenotypeIds);
            for (String[] row : traits.rows()) {
                for (int j = 2; j < 3 + r.model().covariates().size(); j++) finite(row[j]);
                double y = finite(row[2]);
                if (r.model().family().equals("binomial")) need(y == 0 || y == 1, "This binomial profile requires explicit binary0/1 outcomes; no prevalence inference");
                if (r.model().family().equals("poisson")) need(y >= 0 && y == Math.rint(y), "Poisson outcomes must be nonnegative integer counts");
                if (r.model().group() != null) field(row[row.length - 1]);
            }
        }
        var mapping = new LinkedHashMap<Id, Integer>(); for (int i = 0; i < ids.size(); i++) need(mapping.put(ids.get(i), i + 1) == null, "Duplicate complete participant identity");
        need(ids.size() <= r.limits().samples(), "Complete sample capacity exceeded");
        List<Integer> phenotypeAliases = traits == null ? List.of() : aliases(identities(traits), mapping);
        if (traits != null && genotype != null) need(phenotypeAliases.size() == ids.size(), "This association profile requires exact complete genotype/phenotype participant sets; no implicit row dropping");
        Matrix kinship = matrix(home, "kinship", r.inputs().kinship(), mapping, r, check), divergence = matrix(home, "divergence", r.inputs().divergence(), mapping, r, check);
        if (kinship != null) need(kinship.aliases().size() == ids.size(), "Complete PC-AiR kinship matrix required; selected subsets remain explicit");
        if (divergence != null) need(new HashSet<>(divergence.aliases()).equals(new HashSet<>(kinship.aliases())), "Kinship/divergence participant sets differ");
        var selected = selection(home, "selection", r.inputs().selected(), mapping, r, check);
        var forced = selection(home, "forcing", r.inputs().forced(), mapping, r, check);
        var training = selection(home, "training", r.inputs().training(), mapping, r, check);
        if (forced != null) need(selected == null || selected.containsAll(forced), "Forced unrelated samples outside declared selection");
        if (training != null) need(selected == null || selected.containsAll(training), "Training samples outside declared selection");
        Table pcs = null;
        if (r.relatedness() != null) {
            pcs = table(home.resolve("pcs.tsv"), r.limits().samples(), check); var expected = new ArrayList<>(List.of("FID", "IID")); expected.addAll(r.relatedness().pcColumns());
            need(pcs.header().equals(expected), "Declare every PC column explicitly and in order");
            var pcIds = aliases(identities(pcs), mapping); need(pcIds.size() == ids.size(), "Complete supplied PC participant set required; no recomputation");
            for (String[] row : pcs.rows()) for (int j = 2; j < row.length; j++) finite(row[j]);
        }
        var covariance = new ArrayList<Matrix>();
        for (var c : r.inputs().covariance()) {
            Matrix matrix = matrix(home, "covariance-" + c.name(), c, mapping, r, check);
            need(new HashSet<>(matrix.aliases()).equals(new HashSet<>(phenotypeAliases)), "Covariance/phenotype participant sets differ");
            if (r.model().alignment().equals("require-order")) need(matrix.aliases().equals(phenotypeAliases), "Covariance order differs; select phenotype-order explicitly for derived permutation");
            covariance.add(matrix);
        }
        long n = ids.size(), m = genotype == null ? 0 : genotype.variants();
        long matrixBytes = Math.multiplyExact(32 * n * n, covariance.size() + (kinship == null ? 0 : 1) + (divergence == null ? 0 : 1) + 10L);
        long estimate = Math.addExact(matrixBytes, genotype == null ? 65536 : genotype.estimatedBytes());
        need(estimate <= r.limits().workingBytes(), "Complete declared dense model/transport working-storage capacity exceeded; this is not an OS quota");
        long operations = Math.addExact(Math.multiplyExact(n * n, Math.max(1, m)), Math.multiplyExact(n * n * n, r.model() == null ? 1 : r.model().iterations() + 1L));
        need(operations <= r.limits().operations(), "Complete declared genotype/model operation capacity exceeded");
        long rows = Math.addExact(matrixBytes / 32, Math.addExact(n * Math.max(1, m), m * 20));
        need(rows <= r.limits().rows(), "Complete logical input/output row capacity exceeded");
        return new Data(List.copyOf(ids), genotype, traits, phenotypeAliases, kinship, divergence, selected, forced, training, pcs, List.copyOf(covariance), rows);
    }
    static String idsText(List<Integer> aliases) { var s = new StringBuilder("sample_alias\n"); for (int id : aliases) s.append(id).append('\n'); return s.toString(); }
    static void write(Path path, String value) throws IOException { Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
    static Map<String, String> transport(Data d, GenesisRequest.Request r) {
        var files = new LinkedHashMap<String, String>(); var text = new StringBuilder("sample_alias\tFID\tIID\n");
        for (int i = 0; i < d.ids().size(); i++) text.append(i + 1).append('\t').append(d.ids().get(i).fid()).append('\t').append(d.ids().get(i).iid()).append('\n');
        files.put("sample-map.tsv", text.toString());
        if (d.selected() != null) files.put("selection.ids.tsv", idsText(d.selected()));
        if (d.forced() != null) files.put("forcing.ids.tsv", idsText(d.forced()));
        if (d.training() != null) files.put("training.ids.tsv", idsText(d.training()));
        matrixTransport(files, "kinship", d.kinship(), null); matrixTransport(files, "divergence", d.divergence(), null);
        for (int k = 0; k < d.covariances().size(); k++) matrixTransport(files, "covariance-" + r.inputs().covariance().get(k).name(), d.covariances().get(k), d.phenotypeAliases());
        var mapping = new HashMap<Id, Integer>(); for (int i = 0; i < d.ids().size(); i++) mapping.put(d.ids().get(i), i + 1);
        for (String stem : List.of("phenotypes", "pcs")) {
            Table table = stem.equals("phenotypes") ? d.phenotypes() : d.pcs(); if (table == null) continue;
            text = new StringBuilder("sample_alias"); for (String h : table.header().subList(2, table.header().size())) text.append('\t').append(h); text.append('\n');
            for (String[] row : table.rows()) { text.append(mapping.get(new Id(row[0], row[1]))); for (int j = 2; j < row.length; j++) text.append('\t').append(row[j]); text.append('\n'); }
            files.put(stem + ".tsv", text.toString());
        }
        return files;
    }
    private static void matrixTransport(Map<String, String> files, String stem, Matrix matrix, List<Integer> order) {
        if (matrix == null) return; List<Integer> ids = order == null ? matrix.aliases() : order;
        files.put(stem + ".ids.tsv", idsText(ids)); var text = new StringBuilder("row\tcolumn\tvalue_hex\n");
        int[] positions = new int[ids.size()]; for (int i = 0; i < positions.length; i++) positions[i] = matrix.aliases().indexOf(ids.get(i));
        for (int i = 0; i < ids.size(); i++) for (int j = 0; j < ids.size(); j++) text.append(i + 1).append('\t').append(j + 1).append('\t')
            .append(Double.toHexString(matrix.values()[positions[i]][positions[j]])).append('\n');
        files.put(stem + ".values.tsv", text.toString());
    }
    static void prepare(Path root, GenesisRequest.Request r, Data d, Runnable check) throws IOException {
        if (d.genotypes() != null) SnprelateAudit.writeInputs(root, d.genotypes(), genotypeRequest(r), check);
        Files.createDirectory(root.resolve("transport")); for (var e : transport(d, r).entrySet()) write(root.resolve("transport").resolve(e.getKey()), e.getValue());
    }
    static void verifyTransport(Path root, GenesisRequest.Request r, Data d) throws IOException {
        var expected = transport(d, r); var actual = new HashSet<String>();
        try (var paths = Files.list(root.resolve("transport"))) { for (Path path : paths.toList()) { need(Files.isRegularFile(path), "Unexpected transport member"); actual.add(path.getFileName().toString()); } }
        need(actual.equals(expected.keySet()), "Complete typed transport file population differs");
        for (var e : expected.entrySet()) need(Files.readString(root.resolve("transport").resolve(e.getKey()), StandardCharsets.UTF_8).equals(e.getValue()), "Derived typed transport differs: " + e.getKey());
        if (d.genotypes() != null) {
            Table calls = table(root.resolve("inputs/native-calls.tsv"), d.genotypes().variants(), () -> { });
            var header = new ArrayList<String>(); header.add("input_variant_row"); for (int i = 1; i <= d.ids().size(); i++) header.add(Integer.toString(i));
            need(calls.header().equals(header) && calls.rows().size() == d.genotypes().variants(), "Complete native genotype transport shape differs");
            for (int v = 0; v < calls.rows().size(); v++) { String[] row = calls.rows().get(v); need(index(row[0], calls.rows().size()) == v, "Native genotype row differs"); byte[] values = d.genotypes().calls(v);
                for (int i = 0; i < values.length; i++) need(row[i + 1].equals(values[i] < 0 ? "3" : Byte.toString(values[i])), "Native genotype call differs"); }
            Table qc = table(root.resolve("native-variant-qc.tsv"), d.genotypes().variants(), () -> { });
            header(qc, "input_variant_row", "samples", "called", "allele_copies", "AF_hex", "MAF_hex", "missing_hex", "selected", "disposition");
            need(qc.rows().size() == d.genotypes().variants(), "Complete native input variant census differs");
            var layout = r.layout().equals("snp-first") ? org.jlinalg.genetics.SnprelateVariantQc.Layout.SNP_BY_SAMPLE : org.jlinalg.genetics.SnprelateVariantQc.Layout.SAMPLE_BY_SNP;
            for (int v = 0; v < qc.rows().size(); v++) {
                var c = d.genotypes().counts().get(v); var s = org.jlinalg.genetics.SnprelateVariantQc.statistics(c, layout);
                String[] expectedRow = {(v + 1) + "", c.samples() + "", c.called() + "", c.alleleCopies() + "", Double.toHexString(s.alleleFrequency()), Double.toHexString(s.minorAlleleFrequency()), Double.toHexString(s.missingRate()), "true", "SELECTED_QC_BYPASSED"};
                need(Arrays.equals(expectedRow, qc.rows().get(v)), "Native input variant census value differs");
            }
        }
    }
    static GenesisPcairPartition.Matrix partitionMatrix(Matrix input) {
        return new GenesisPcairPartition.Matrix(input.aliases().stream().map(Object::toString).toList(), input.values());
    }
}
