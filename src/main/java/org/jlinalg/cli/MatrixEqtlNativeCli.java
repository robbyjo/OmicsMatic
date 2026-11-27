/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.qtl.MatrixEqtlLinear;
import static org.jlinalg.cli.NextflowFiles.*;

/** Source-scoped native MatrixEQTL additive scan with retained, replayable inputs. */
public final class MatrixEqtlNativeCli {
    private MatrixEqtlNativeCli() { }
    private static final String SCOPE = "Native Java FP64 MatrixEQTL 2.3 modelLINEAR: source row-mean imputation, intercept/covariate projection, optional positive-definite error-covariance whitening, inclusive cis intervals, source t tests, normal-minimum p floor and separate total-test BH FDR. Derived SE is an extension and is NA for unidentifiable rows. Caller sample/feature order is strict. Whole package, author recipes and biological conclusions are unqualified.";
    private static final String ACTION = "Require analysis-status.json result_admitted=true and inspect WARNINGS.md. SKIP3: supply the named permitted prerequisite or enable the explicit execution policy, then retry a NEW output. FAIL2: fix the named input, integrity, numerical, cancellation or capacity failure and retry a NEW output; no partial results admitted. Retain failed attempts and original inputs. No external R installation is needed for this native analysis. Paper-specific PEER/PC preparation, sex/cell strata, allele orientation, twin-block permutations and empirical permutation FDR remain caller responsibilities; package BH is not permutation FDR. ANOVA, LINEAR_CROSS and noFDRsaveMemory are unsupported in this profile.";
    private static final Set<String> PATHS = Set.of("snps", "genes", "covariates", "snp_positions", "gene_positions", "error_covariance");
    private static final Set<String> KEYS = Set.of("schema_version", "source_version", "model", "snps", "genes", "covariates", "snp_positions", "gene_positions", "error_covariance", "threshold", "cis_threshold", "cis_distance", "block_size", "max_input_bytes", "max_entries", "max_pairs", "max_retained", "max_seconds", "execution_policy", "population", "preparation", "allele_orientation", "out");
    private record Matrix(List<String> ids, List<String> samples, double[][] rows) { }
    private static final class Skipped extends IOException {
        private static final long serialVersionUID = 1L;
        Skipped(String message) { super(message); }
    }
    private static final class Clock implements Runnable {
        final long started = System.nanoTime(), seconds;
        Clock(long seconds) { this.seconds = seconds; }
        public void run() {
            require(!Thread.currentThread().isInterrupted(), "Analysis cancelled; no result admitted");
            require((System.nanoTime() - started) / 1e9 <= seconds, "Complete analysis time capacity exceeded");
        }
    }
    public static void main(String[] args) { int code = run(args, System.out, System.err); if (code != 0) System.exit(code); }
    public static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        Path out = null; boolean owned = false, admitted = false; int code = 2;
        String status = "FAIL2: Invalid arguments";
        try {
            if (Arrays.equals(args, new String[]{"--help"})) {
                stdout.println("matrixeqtl-native --request UTF8_JSON\nmatrixeqtl-native --verify SAVED_BUNDLE --out NEW_DIRECTORY\n" + SCOPE + "\n" + ACTION + "\nSchema and a complete synthetic request: docs/matrixeqtl-native.md"); return 0;
            }
            if (args.length == 4 && args[0].equals("--verify") && args[2].equals("--out")) {
                Path saved = absolute(args[1]); directory(saved); out = absolute(args[3]); newOutput(out, List.of(saved)); owned = true;
                var done = object(json(saved.resolve("analysis-status.json"), 1048576));
                require("PASS_NATIVE_MATRIXEQTL23_LINEAR".equals(done.get("status")) && Boolean.TRUE.equals(done.get("result_admitted")) && integer(done.get("exit_code"), 0, 0) == 0, "Saved native analysis was not admitted");
                var manifest = object(json(saved.resolve("manifest.json"), 4194304));
                require(manifest.get("producer").equals(code()), "Saved/current defining numerical code differs");
                var members = object(manifest.get("files"));
                var current = tree(saved, 128, 4294967296L, 2147483646L, () -> { });
                var names = new TreeSet<>(current.keySet()); names.removeAll(Set.of("manifest.json", "analysis-status.json"));
                require(names.equals(members.keySet()), "Saved bundle member population changed");
                for (String name : names) {
                    var entry = current.get(name); require(entry.kind().equals("file") && entry.pin().equals(fingerprint(members.get(name), 2147483646L)), "Saved member changed: " + name);
                }
                var parameters = object(json(saved.resolve("parameters.json"), 1048576));
                var clock = new Clock(integer(parameters.get("max_seconds"), 1, 86400));
                var files = captured(saved, parameters);
                String actual = analyze(parameters, files, out, clock);
                require(actual.equals(Files.readString(saved.resolve("results.json"), StandardCharsets.UTF_8)), "Saved/fresh full native results differ");
                // Recheck every saved byte after computation, including provenance/status.
                require(current.equals(tree(saved, 128, 4294967296L, 2147483646L, clock)), "Saved bundle changed during replay");
                writeJson(out.resolve("verification.json"), Map.of("status", "PASS_COMPLETE_SAVED_INPUT_RECOMPUTATION", "saved_manifest", pin(saved.resolve("manifest.json"), 4194304, clock), "producer", code(), "producer_authenticated", false));
                status = "PASS_COMPLETE_SAVED_INPUT_RECOMPUTATION"; admitted = true; code = 0;
            } else {
                require(args.length == 2 && args[0].equals("--request"), "Require --request JSON, --verify BUNDLE --out NEW_DIRECTORY or --help");
                Path request = absolute(args[1]); var requestPin = pin(request, 1048576, () -> { }); byte[] raw = bytes(request, 1048576);
                require(requestPin.equals(new Pin(raw.length, sha(raw))), "Request changed during capture");
                Map<?,?> p = object(NextflowParameters.parse(utf8(raw), 1048576, 100000));
                require(p.keySet().equals(KEYS), "Unexpected/missing request fields: " + p.keySet());
                out = resolve(request.getParent(), text(p.get("out"))); newOutput(out, List.of(request)); owned = true;
                Files.write(out.resolve("request.json"), raw, StandardOpenOption.CREATE_NEW);
                validate(p); var clock = new Clock(integer(p.get("max_seconds"), 1, 86400));
                if (text(p.get("execution_policy")).equals("FORBID")) throw new Skipped("Native execution explicitly FORBID");
                var paths = new TreeMap<String,Path>(); var pins = new TreeMap<String,Pin>();
                long bytes = 0, maximum = integer(p.get("max_input_bytes"), 1, 2147483646L);
                Files.createDirectory(out.resolve("inputs"));
                for (String name : new TreeSet<>(PATHS)) {
                    Object value = p.get(name); if (value == null) continue;
                    Path path = resolve(request.getParent(), text(value));
                    require(!path.startsWith(out) && !out.startsWith(path), "Output overlaps input: " + name);
                    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new Skipped("Missing declared " + name + ": " + path);
                    var identity = pin(path, maximum, clock); bytes = Math.addExact(bytes, identity.bytes());
                    require(bytes <= maximum, "Complete input byte capacity exceeded"); pins.put(name, identity); paths.put(name, path);
                    Path target = out.resolve("inputs/" + name + ".tsv"); Files.copy(path, target);
                    require(identity.equals(pin(target, maximum, clock)), "Input changed during capture: " + name);
                }
                var parameters = new LinkedHashMap<String,Object>();
                for (var e : p.entrySet()) if (!PATHS.contains(e.getKey()) && !e.getKey().equals("out")) parameters.put(text(e.getKey()), e.getValue());
                parameters.put("input_names", new ArrayList<>(paths.keySet())); writeJson(out.resolve("parameters.json"), parameters);
                var provenance = new TreeMap<String,Object>();
                for (String name : paths.keySet()) provenance.put(name, Map.of("source", paths.get(name).toString(), "identity", pins.get(name)));
                writeJson(out.resolve("input-provenance.json"), provenance);
                var producer = code();
                analyze(parameters, captured(out, parameters), out, clock);
                for (String name : paths.keySet()) require(pins.get(name).equals(pin(paths.get(name), maximum, clock)), "Input changed during scan: " + name);
                require(requestPin.equals(pin(request, 1048576, clock)), "Request changed during scan");
                require(producer.equals(code()), "Defining numerical code changed during scan");
                writeWarnings(out, "PASS_NATIVE_MATRIXEQTL23_LINEAR");
                var manifest = new TreeMap<String,Object>();
                for (var e : tree(out, 128, 4294967296L, 2147483646L, clock).entrySet()) {
                    require(e.getValue().kind().equals("file"), "Unexpected output link/member"); manifest.put(e.getKey(), e.getValue().pin());
                }
                writeJson(out.resolve("manifest.json"), Map.of("schema_version", 1, "producer", producer, "files", manifest, "producer_authenticated", false));
                admitted = true; status = "PASS_NATIVE_MATRIXEQTL23_LINEAR"; code = 0;
            }
        } catch (Skipped e) { code = 3; status = "SKIP3: " + e.getMessage(); }
          catch (LinkageError e) { code = 3; status = "SKIP3: Missing/incompatible packaged Java dependency: " + e; }
          catch (OutOfMemoryError e) { code = 2; status = "FAIL2: Java heap exhausted; no partial result admitted"; }
          catch (Exception e) { code = 2; status = "FAIL2: " + e; }
        try {
            if (owned) {
                if (!Files.exists(out.resolve("WARNINGS.md"))) writeWarnings(out, status);
                else if (code != 0) Files.writeString(out.resolve("WARNINGS.md"), "\n\n**" + status + "**\nNo partial result admitted.\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                writeJson(out.resolve("analysis-status.json"), Map.of("status", status, "exit_code", code, "result_admitted", admitted && code == 0, "required_action", ACTION, "whole_package_qualified", false, "whole_goal_complete", false));
            }
        } catch (IOException e) { code = 2; status = "FAIL2: Cannot retain complete status/warnings: " + e; }
        (code == 0 ? stdout : stderr).println(status + (out == null ? "" : "\nOutput: " + out));
        stderr.println("WARNINGS / " + ACTION); return code;
    }
    private static String analyze(Map<?,?> p, Map<String,Path> files, Path out, Clock clock) throws IOException {
        long maximum = integer(p.get("max_entries"), 1, 100000000), remaining = maximum;
        Matrix snps = matrix(files.get("snps"), remaining, clock); remaining -= entries(snps);
        Matrix genes = matrix(files.get("genes"), remaining, clock); remaining -= entries(genes);
        require(snps.samples().equals(genes.samples()), "SNP/gene sample identifiers/order differ; no automatic alignment");
        Matrix cv = files.containsKey("covariates") ? matrix(files.get("covariates"), remaining, clock) : new Matrix(List.of(), snps.samples(), new double[0][]);
        remaining -= entries(cv); require(cv.samples().equals(snps.samples()), "Covariate sample identifiers/order differ");
        Matrix covariance = files.containsKey("error_covariance") ? matrix(files.get("error_covariance"), remaining, clock) : null;
        if (covariance != null) require(covariance.samples().equals(snps.samples()) && covariance.ids().equals(snps.samples()), "Covariance row/column sample identifiers/order differ");
        var sp = files.containsKey("snp_positions") ? positions(files.get("snp_positions"), snps.ids(), clock) : null;
        var gp = files.containsKey("gene_positions") ? positions(files.get("gene_positions"), genes.ids(), clock) : null;
        var options = new MatrixEqtlLinear.Options(number(p.get("threshold")), number(p.get("cis_threshold")), integer(p.get("cis_distance"), 0, 9007199254740991L), (int)integer(p.get("block_size"), 1, 4096), integer(p.get("max_pairs"), 1, 9007199254740991L), integer(p.get("max_retained"), 1, 10000000));
        var result = MatrixEqtlLinear.scan(snps.rows(), genes.rows(), cv.rows(), sp, gp, covariance == null ? null : covariance.rows(), options, clock::run);
        var report = new LinkedHashMap<String,Object>(); report.put("source_version", "2.3"); report.put("model", "modelLINEAR");
        report.put("samples", result.samples()); report.put("residual_df", result.residualDf()); report.put("evaluated_pairs", result.evaluatedPairs());
        report.put("imputed_cells", Map.of("snps", result.imputedSnps(), "genes", result.imputedGenes(), "covariates", result.imputedCovariates()));
        report.put("zero_residual_rows", Map.of("snps", result.zeroSnps(), "genes", result.zeroGenes()));
        report.put("cis", family(result.cis(), snps.ids(), genes.ids(), clock));
        report.put(number(p.get("cis_threshold")) > 0 ? "trans" : "all", family(result.transOrAll(), snps.ids(), genes.ids(), clock));
        String json = NextflowJson.json(report) + "\n"; Files.writeString(out.resolve("results.json"), json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); clock.run(); return json;
    }
    private static Object family(MatrixEqtlLinear.Family f, List<String> snps, List<String> genes, Clock clock) {
        var rows = new ArrayList<Object>();
        for (var a : f.associations()) {
            clock.run(); var row = new LinkedHashMap<String,Object>(); row.put("snp", snps.get(a.snp())); row.put("gene", genes.get(a.gene()));
            row.put("beta", token(a.beta())); row.put("statistic", token(a.statistic())); row.put("pvalue", token(a.pvalue())); row.put("FDR", token(a.fdr()));
            row.put("derived_SE", token(a.derivedSe())); row.put("zero_snp", a.zeroSnp()); row.put("zero_gene", a.zeroGene()); rows.add(row);
        }
        return Map.of("ntests", f.tests(), "eqtls", rows, "minimum_by_snp", minimum(snps, f.minimumBySnp()), "minimum_by_gene", minimum(genes, f.minimumByGene()));
    }
    private static Map<String,String> minimum(List<String> ids, double[] values) {
        var result = new LinkedHashMap<String,String>(); for (int i = 0; i < values.length; i++) result.put(ids.get(i), token(values[i])); return result;
    }
    private static Matrix matrix(Path path, long maximum, Clock clock) throws IOException {
        require(path != null, "SNP and gene paths are required");
        try (var reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)))) {
            String first = reader.readLine(); require(first != null, "Empty matrix"); String[] header = first.split("\t", -1);
            require(header.length > 1 && header[0].equals("id"), "Matrix header must be id followed by exact sample IDs");
            List<String> samples = List.of(Arrays.copyOfRange(header, 1, header.length)); unique(samples);
            var ids = new ArrayList<String>(); var rows = new ArrayList<double[]>(); long entries = 0;
            for (String line; (line = reader.readLine()) != null;) {
                clock.run(); String[] fields = line.split("\t", -1); require(fields.length == header.length, "Ragged/blank matrix record");
                entries = Math.addExact(entries, samples.size()); require(entries <= maximum, "Complete matrix entry capacity exceeded");
                ids.add(fields[0]); double[] row = new double[samples.size()];
                for (int i = 0; i < row.length; i++) {
                    if (Set.of("NA", "NaN").contains(fields[i + 1])) row[i] = Double.NaN;
                    else { row[i] = Double.parseDouble(fields[i + 1]); require(Double.isFinite(row[i]), "Only finite values or explicit NA/NaN missing tokens are allowed"); }
                }
                rows.add(row);
            }
            unique(ids); return new Matrix(List.copyOf(ids), samples, rows.toArray(double[][]::new));
        }
    }
    private static MatrixEqtlLinear.Position[] positions(Path path, List<String> ids, Clock clock) throws IOException {
        var rows = Files.readAllLines(path, StandardCharsets.UTF_8); require(rows.size() == ids.size() + 1 && rows.get(0).equals("id\tchr\tstart\tend"), "Complete ordered position table required");
        var result = new MatrixEqtlLinear.Position[ids.size()];
        for (int i = 0; i < result.length; i++) {
            clock.run(); var f = rows.get(i + 1).split("\t", -1); require(f.length == 4 && f[0].equals(ids.get(i)), "Position feature identifiers/order differ");
            require(f[2].matches("[1-9][0-9]*") && f[3].matches("[1-9][0-9]*"), "Positive integer positions required");
            long start = Long.parseLong(f[2]), end = Long.parseLong(f[3]); require(start <= 9007199254740991L && end <= 9007199254740991L, "Coordinates exceed exact source-double integer range");
            result[i] = new MatrixEqtlLinear.Position(f[1], start, end);
        }
        return result;
    }
    private static void validate(Map<?,?> p) throws IOException {
        require(integer(p.get("schema_version"), 1, 1) == 1 && text(p.get("source_version")).equals("2.3") && text(p.get("model")).equals("modelLINEAR"), "Require schema 1, source 2.3 and modelLINEAR");
        for (String n : List.of("snps", "genes", "population", "preparation", "allele_orientation")) require(!text(p.get(n)).isBlank(), "Explicit nonempty " + n + " required");
        require(Set.of("ALLOW", "FORBID").contains(text(p.get("execution_policy"))), "Execution policy must be ALLOW or FORBID");
        integer(p.get("max_input_bytes"), 1, 2147483646L); integer(p.get("max_entries"), 1, 100000000); integer(p.get("max_seconds"), 1, 86400);
        new MatrixEqtlLinear.Options(number(p.get("threshold")), number(p.get("cis_threshold")), integer(p.get("cis_distance"), 0, 9007199254740991L), (int)integer(p.get("block_size"), 1, 4096), integer(p.get("max_pairs"), 1, 9007199254740991L), integer(p.get("max_retained"), 1, 10000000));
        require(number(p.get("cis_threshold")) == 0 || (p.get("snp_positions") != null && p.get("gene_positions") != null), "Cis scan requires both complete position tables");
    }
    private static Map<String,Path> captured(Path root, Map<?,?> p) throws IOException {
        var result = new TreeMap<String,Path>();
        for (Object x : list(p.get("input_names"))) { String name = text(x); require(PATHS.contains(name) && !result.containsKey(name), "Invalid/duplicate captured input name"); result.put(name, root.resolve("inputs/" + name + ".tsv")); }
        return result;
    }
    private static Map<String,String> code() throws IOException {
        var result = new TreeMap<String,String>();
        for (Class<?> c : List.of(MatrixEqtlNativeCli.class, MatrixEqtlLinear.class, NextflowFiles.class, NextflowParameters.class, NextflowJson.class, SimpleJson.class, MonocleMathIdentity.class)) classes(c, result);
        // Complete mathematical class profile, including Student-t and CPU BLAS/eigen dependencies.
        var math = new TreeMap<>(MonocleMathIdentity.verify());
        math.remove("path"); math.remove("pin"); math.remove("packaging");
        result.put("jdistlib_profile", NextflowJson.json(math)); return result;
    }
    private static void classes(Class<?> c, Map<String,String> result) throws IOException {
        String name = c.getName().replace('.', '/') + ".class";
        try (var stream = c.getResourceAsStream("/" + name)) { if (stream == null) throw new IOException("Missing defining class " + name); result.put(name, sha(stream.readAllBytes())); }
        for (Class<?> child : c.getDeclaredClasses()) classes(child, result);
    }
    private static double number(Object value) throws IOException { double d = Double.parseDouble(value instanceof NextflowParameters.NumberToken n ? n.text() : String.valueOf(value)); require(Double.isFinite(d), "Finite JSON number required"); return d; }
    private static long entries(Matrix matrix) { return Math.multiplyExact((long)matrix.rows().length, matrix.samples().size()); }
    private static void unique(List<String> ids) { require(ids.stream().noneMatch(x -> x.isBlank() || x.chars().anyMatch(c -> c < 32 || c == 127)) && new HashSet<>(ids).size() == ids.size(), "Empty/control/duplicate identifier"); }
    private static String token(double d) { return Double.isNaN(d) ? "NA" : d == Double.POSITIVE_INFINITY ? "Inf" : d == Double.NEGATIVE_INFINITY ? "-Inf" : Double.toString(d); }
    private static Path absolute(String text) { return Path.of(text).toAbsolutePath().normalize(); }
    private static Path resolve(Path parent, String text) { return parent.resolve(text).toAbsolutePath().normalize(); }
    private static void newOutput(Path out, List<Path> protectedPaths) throws IOException {
        require(!Files.exists(out, LinkOption.NOFOLLOW_LINKS), "Output must be NEW; retain previous attempts"); directory(out.getParent());
        for (Path path : protectedPaths) require(!out.startsWith(path) && !path.startsWith(out), "Output overlaps protected input/evidence"); Files.createDirectory(out);
    }
    private static void writeWarnings(Path out, String status) throws IOException { Files.writeString(out.resolve("WARNINGS.md"), "# " + status + "\n\n" + SCOPE + "\n\n**Warnings and required actions:** " + ACTION + "\n\nNA/NaN inputs are explicitly imputed by the original row-mean rule (all-missing rows become zero); counts are reported. Constant/collinear rows remain in test denominators with the original zero statistic. Original infinite statistics and p floors are retained. The native profile requires unique IDs, complete positions, positive coordinates and exact sample order; it does not apply the original partial location matching, chromosome sorting, graphical histograms or direct-memory saver. Features and input matrices are resident in bounded heap memory; block products limit temporary scan storage. Output ordering breaks exact statistic ties by input SNP/gene order.\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
}
