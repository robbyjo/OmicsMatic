/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.mr.LinkedVariantMr;
import org.jlinalg.mr.LinkedVariantMr.Association;
import org.jlinalg.mr.LinkedVariantMr.Policy;
import org.jlinalg.mr.LinkedVariantMr.Proxy;
import org.jlinalg.mr.LinkedVariantMr.Result;

/** Offline, auditable single-signal MR; never sends cohort variant lists to services. */
final class LinkedMrCli {
    private LinkedMrCli() { }
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final String CAVEAT = "MR association and shared genetic signal do not establish mediation. "
        + "Horizontal pleiotropy, other isoforms, total gene expression, selection bias and winner's curse remain possible. "
        + "A null outcome is retained; poor measurement or absent data are not evidence of no effect. "
        + "Colocalization reuses association evidence and is not independent replication. Reusing the same GWAS with another QTL cohort is not independent outcome replication.";

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 1 && Set.of("--help", "-h").contains(args[0])) { out.println(help()); return 0; }
            if (args.length == 2 && args[0].equals("--verify")) {
                verify(Path.of(args[1])); out.println("Verified linked-MR bundle integrity (not scientific validation or authentication)."); return 0;
            }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) {
                if (!Set.of("--request", "--out").contains(args[i]) || i + 1 == args.length || args[i+1].isBlank()
                        || options.put(args[i], args[i+1]) != null)
                    throw new IllegalArgumentException("Use mr-linked --request FILE --out NEW_DIRECTORY or --verify DIRECTORY; no duplicate options");
            }
            if (options.size() != 2) throw new IllegalArgumentException("Both --request and --out are required");
            execute(Path.of(options.get("--request")).toAbsolutePath().normalize(), Path.of(options.get("--out")).toAbsolutePath().normalize(), args);
            out.println("Completed linked MR: " + options.get("--out")); out.println(CAVEAT); return 0;
        } catch (IOException | RuntimeException failure) {
            err.println("mr-linked: " + failure.getMessage()); return 2;
        }
    }

    private static void execute(Path source, Path output, String[] args) throws IOException {
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Output exists; choose a NEW directory");
        if (Files.size(source) > 2_000_000) throw new IOException("Request exceeds 2 MB");
        var request = StudyYaml.loadMap(source);
        keys(request, "schema_version", "family_id", "family_size", "alpha", "selection_basis", "policy", "tests");
        if (integer(request, "schema_version") != 1) throw new IllegalArgumentException("Unsupported request schema");
        required(request, "family_id");
        if (!required(request, "selection_basis").equals("exposure_only"))
            throw new IllegalArgumentException("Instrument selection must be exposure_only");
        int family = integer(request, "family_size");
        double alpha = numeric(request, "alpha");
        if (!(alpha > 0 && alpha < 1)) throw new IllegalArgumentException("alpha must be in (0,1)");
        var tests = list(request.get("tests"));
        if (tests.isEmpty() || tests.size() > 10000 || family < tests.size())
            throw new IllegalArgumentException("Require 1..10000 tests and family_size >= declared test count");
        Policy policy = policy(object(request.get("policy")));
        Policy strict = new Policy(policy.instrumentP(), policy.minimumF(), policy.imputationMetric(),
            Math.max(.9, policy.minimumQuality()), Math.max(.9, policy.minimumProxyR2()), policy.confidenceLevel());
        Set<String> ids = new HashSet<>(), identities = new HashSet<>();
        Files.createDirectories(output.getParent()); Files.createDirectory(output);
        try {
            Files.copy(source, output.resolve("request.yaml"));
            Files.writeString(output.resolve("command.txt"), "mr-linked " + String.join(" ", args) + "\nworking_directory="
                + Path.of("").toAbsolutePath() + "\nstarted=" + Instant.now() + "\n");
            var sources = new ArrayList<Object>(); var results = new ArrayList<Object>(); var rows = new ArrayList<String[]>();
            for (Object raw : tests) {
                var test = object(raw);
                keys(test, "id", "feature_id", "chromosome", "cis_start", "cis_end", "sample_overlap", "sample_overlap_basis",
                    "exposure", "outcome", "ld", "colocalization");
                String id = required(test, "id"), feature = required(test, "feature_id");
                if (!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") || !ids.add(id))
                    throw new IllegalArgumentException("Unsafe or duplicate test ID: " + id);
                if (!required(test, "sample_overlap").equals("none"))
                    throw new IllegalArgumentException("This first-order workflow requires independent exposure/outcome samples");
                required(test, "sample_overlap_basis");
                var xSource = object(test.get("exposure")); var ySource = object(test.get("outcome"));
                validateSource(xSource); validateSource(ySource); compatible(xSource, ySource);
                if (!feature.equals(required(xSource, "trait_id"))) throw new IllegalArgumentException("Exposure trait_id must match the exact feature_id");
                String identity = feature + "\t" + required(xSource, "source_id") + "\t" + required(ySource, "source_id") + "\t" + required(ySource, "trait_id");
                if (!identities.add(identity)) throw new IllegalArgumentException("Duplicate exposure/outcome hypothesis");
                Path directory = Files.createDirectory(output.resolve(id));
                Path input = Files.createDirectory(directory.resolve("inputs"));
                Path xFile = capture(source.getParent(), xSource, input.resolve("exposure.tsv"), sources);
                Path yFile = capture(source.getParent(), ySource, input.resolve("outcome.tsv"), sources);
                List<Association> x = associations(xFile), y = associations(yFile);
                List<Proxy> proxies = List.of();
                if (test.containsKey("ld")) {
                    var ld = object(test.get("ld"));
                    keys(ld, "file", "source_id", "genome_build", "ancestry", "sample_size", "reference_basis");
                    required(ld, "source_id"); required(ld, "reference_basis");
                    if (integer(ld, "sample_size") < 2) throw new IllegalArgumentException("LD sample_size must be >=2");
                    compatible(xSource, ld);
                    Path file = capture(source.getParent(), ld, input.resolve("ld.tsv"), sources);
                    var table = DelimitedData.readResult(file);
                    for (String name : List.of("target_id", "proxy_id", "r_squared")) table.column(name);
                    var loaded = new ArrayList<Proxy>();
                    for (String[] row : table.rows()) loaded.add(new Proxy(cell(table, row, "target_id"), cell(table, row, "proxy_id"), number(cell(table, row, "r_squared"))));
                    proxies = loaded;
                }
                long start = positiveLong(test, "cis_start"), end = positiveLong(test, "cis_end");
                Result primary = LinkedVariantMr.analyze(x, y, proxies, required(test, "chromosome"), start, end, policy);
                Result sensitivity = LinkedVariantMr.analyze(x, y, proxies, required(test, "chromosome"), start, end, strict);
                var result = document(primary, family, alpha);
                result.put("test_id", id); result.put("feature_id", feature);
                result.put("exposure_source", xSource); result.put("outcome_source", ySource);
                result.put("input_rows", Map.of("exposure", x.size(), "outcome", y.size(), "ld_pairs", proxies.size()));
                result.put("strict_quality_ld_sensitivity", document(sensitivity, family, alpha));
                result.put("sensitivity_selection_changed", !primary.selectedVariant().equals(sensitivity.selectedVariant()));
                writeDecisions(directory.resolve("selection.tsv"), primary);
                writeDecisions(directory.resolve("strict-selection.tsv"), sensitivity);
                String coloc = test.containsKey("colocalization")
                    ? coloc(source.getParent(), directory, object(test.get("colocalization")), xSource, ySource, sources)
                    : "not_run_requires_dense_regional_data";
                result.put("colocalization_status", coloc); result.put("causal_mediation", "not_established");
                result.put("interpretation", CAVEAT);
                StudyPublicEvidence.write(directory.resolve("result.yaml"), result); results.add(result);
                rows.add(new String[]{id, feature, primary.status(), primary.leadVariant(), primary.selectedVariant(),
                    primary.originalOutcomeDisposition(), string(result, "estimate"), string(result, "standard_error"),
                    string(result, "confidence_lower"), string(result, "confidence_upper"), string(result, "mr_p"),
                    string(result, "bonferroni_p"), string(result, "association_supported"), coloc, "not_established"});
            }
            FollowupSupport.table(output.resolve("results.tsv"), List.of("test_id", "feature_id", "status", "lead_variant", "selected_variant",
                "original_outcome_disposition", "estimate", "standard_error", "confidence_lower", "confidence_upper", "mr_p", "bonferroni_p",
                "association_supported", "colocalization_status", "causal_mediation"), rows);
            var report = new LinkedHashMap<String, Object>();
            report.put("schema_version", 1); report.put("family_id", request.get("family_id")); report.put("family_size", family);
            report.put("tests_requested", tests.size()); report.put("additional_declared_tests_not_run", family - tests.size());
            report.put("adjustment", "Bonferroni over the full declared family including unavailable tests; sensitivity results are not additional primary discoveries");
            report.put("strict_sensitivity_thresholds", Map.of("minimum_quality", strict.minimumQuality(), "minimum_proxy_r2", strict.minimumProxyR2()));
            report.put("software_version", JLinAlgCli.version()); report.put("java_version", System.getProperty("java.version"));
            report.put("producer_class_sha256", producer());
            report.put("inputs", sources); report.put("results", results); report.put("interpretation", CAVEAT);
            report.put("evidence", "algorithm_executed; source declarations, instrument validity, regional completeness and replication require scientific review");
            report.put("authentication", false);
            StudyPublicEvidence.write(output.resolve("report.yaml"), report);
            Files.writeString(output.resolve("README.txt"), CAVEAT + "\n\nEach test uses one exposure-selected cis signal and same-variant effects. "
                + "Reported GWAS P is never a selection criterion. Imputation quality is not LD and never rescales beta. "
                + "First-order Wald P is the selected variant's beta/SE outcome Wald P, which may differ from a published score-test P. "
                + "No cohort data were sent to external services. Input copies may be controlled: this bundle is not automatically redistributable.\n"
                + "Integrity: mr-linked --verify THIS_DIRECTORY. This verifies hashes, not a signature or scientific validity.\n"
                + "Reproduction: copy request.yaml, replace file paths with the captured per-test inputs, and run mr-linked into a new directory. "
                + "Keep the original request and all failed outputs.\n");
            var inventory = inventory(output);
            StudyPublicEvidence.write(output.resolve("manifest.yaml"), Map.of("files", inventory));
            Files.writeString(output.resolve("COMPLETED"), FollowupSupport.hash(output.resolve("manifest.yaml")) + "\n", StandardOpenOption.CREATE_NEW);
            verify(output);
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(output.resolve("COMPLETED"));
            Files.writeString(output.resolve("FAILED.txt"), failure.getClass().getSimpleName() + ": " + failure.getMessage() + "\n");
            throw failure;
        }
    }

    private static Map<String, Object> document(Result result, int family, double alpha) {
        var row = new LinkedHashMap<String, Object>();
        row.put("status", result.status()); row.put("lead_variant", result.leadVariant()); row.put("selected_variant", result.selectedVariant());
        row.put("original_outcome_disposition", result.originalOutcomeDisposition());
        row.put("association_supported", false);
        if (result.estimate() != null) {
            var estimate = result.estimate(); var instrument = result.instrument();
            double adjusted = LinkedVariantMr.bonferroni(estimate.pValue(), family);
            row.put("estimate", estimate.estimate()); row.put("standard_error", estimate.standardError());
            row.put("confidence_lower", estimate.confidenceLower()); row.put("confidence_upper", estimate.confidenceUpper());
            row.put("mr_p", estimate.pValue()); row.put("bonferroni_p", adjusted); row.put("association_supported", adjusted < alpha);
            row.put("proxy_r_squared", result.proxyR2()); row.put("exposure_beta", instrument.exposureEffect());
            row.put("outcome_beta_aligned", instrument.outcomeEffect()); row.put("outcome_se", instrument.outcomeStandardError());
            row.put("effect_allele", instrument.effectAllele()); row.put("other_allele", instrument.otherAllele());
            row.put("outcome_flipped", instrument.outcomeEffectFlipped()); row.put("strand_complemented", instrument.strandComplemented());
            row.put("exposure_F", Math.pow(instrument.exposureEffect() / instrument.exposureStandardError(), 2));
        }
        return row;
    }

    private static String coloc(Path parent, Path directory, Map<String, Object> config,
            Map<String, Object> exposure, Map<String, Object> outcome, List<Object> sources) throws IOException {
        keys(config, "trait1_effects", "trait2_effects", "trait1_log", "trait2_log", "exposure_source_id", "outcome_source_id", "exposure_trait_id", "outcome_trait_id",
            "genome_build", "ancestry", "complete_region", "regional_inputs_reviewed", "p1", "p2", "shared_priors", "minimum_overlap");
        compatible(exposure, config);
        if (!required(exposure, "source_id").equals(required(config, "exposure_source_id"))
                || !required(outcome, "source_id").equals(required(config, "outcome_source_id")))
            throw new IllegalArgumentException("Colocalization source IDs differ from the MR sources");
        if (!required(exposure, "trait_id").equals(required(config, "exposure_trait_id"))
                || !required(outcome, "trait_id").equals(required(config, "outcome_trait_id")))
            throw new IllegalArgumentException("Colocalization trait IDs differ from the MR traits");
        if (!Boolean.TRUE.equals(config.get("complete_region")) || !Boolean.TRUE.equals(config.get("regional_inputs_reviewed")))
            throw new IllegalArgumentException("Colocalization requires reviewed dense regional inputs including nonsignificant variants");
        var paths = new LinkedHashMap<String, Path>();
        for (String name : List.of("trait1_effects", "trait2_effects", "trait1_log", "trait2_log")) {
            var descriptor = Map.<String, Object>of("file", required(config, name), "source_id", name);
            paths.put(name, capture(parent, descriptor, directory.resolve("inputs").resolve(name + (name.endsWith("log") ? ".log" : ".tsv")), sources));
        }
        for (String name : List.of("trait1_log", "trait2_log")) {
            var convergence = Files.readAllLines(paths.get(name)).stream().map(String::trim).filter(s -> s.startsWith("converged=")).toList();
            if (!convergence.equals(List.of("converged=true"))) throw new IllegalArgumentException("SuSiE convergence is not established: " + name);
        }
        List<?> priors = list(config.get("shared_priors"));
        if (priors.size() < 2 || priors.size() > 10) throw new IllegalArgumentException("Declare 2..10 shared priors, primary first");
        Set<Double> unique = new HashSet<>(); int index = 0;
        for (Object raw : priors) {
            double prior = number(raw.toString());
            if (!(prior > 0 && prior <= Math.min(numeric(config, "p1"), numeric(config, "p2"))))
                throw new IllegalArgumentException("Shared prior must be positive and no larger than either trait prior");
            if (!unique.add(prior)) throw new IllegalArgumentException("Duplicate colocalization shared prior");
            Path result = directory.resolve("coloc-" + index++ + ".tsv");
            String[] command = {"--trait1", paths.get("trait1_effects").toString(), "--trait2", paths.get("trait2_effects").toString(),
                "--trait1-prior", Double.toString(numeric(config, "p1")), "--trait2-prior", Double.toString(numeric(config, "p2")),
                "--shared-prior", Double.toString(prior), "--minimum-overlap", Double.toString(numeric(config, "minimum_overlap")), "--out", result.toString()};
            try (var log = new PrintStream(Files.newOutputStream(directory.resolve("coloc-" + (index - 1) + "-command.log")), true, StandardCharsets.UTF_8)) {
                log.println("coloc " + String.join(" ", command));
                if (FineMappingCli.run("coloc", command, log, log) != 0) throw new IOException("Colocalization failed; see retained command log");
            }
        }
        // Do not select a favorable signal pair or turn H4 into a mediation assertion.
        return "completed_prior_sensitivity_requires_signal_and_coverage_review";
    }

    private static List<Association> associations(Path file) throws IOException {
        var table = DelimitedData.readResult(file);
        for (String key : List.of("variant_id", "chromosome", "position", "effect_allele", "other_allele", "beta", "se", "p", "eaf", "n",
                "qc_pass", "measurement", "quality_metric", "quality")) table.column(key);
        if (table.rows().size() > 100000) throw new IOException("Use regional inputs of at most 100000 variants");
        var result = new ArrayList<Association>();
        for (String[] row : table.rows()) {
            String qc = cell(table, row, "qc_pass"), quality = cell(table, row, "quality"), metric = cell(table, row, "quality_metric");
            if (!Set.of("true", "false").contains(qc)) throw new IllegalArgumentException("qc_pass must be true or false");
            result.add(new Association(cell(table, row, "variant_id"), cell(table, row, "chromosome"), Long.parseLong(cell(table, row, "position")),
                cell(table, row, "effect_allele"), cell(table, row, "other_allele"), number(cell(table, row, "beta")), number(cell(table, row, "se")),
                number(cell(table, row, "p")), number(cell(table, row, "eaf")), Integer.parseInt(cell(table, row, "n")), Boolean.parseBoolean(qc),
                cell(table, row, "measurement"), metric.equals("NA") ? "" : metric, quality.equals("NA") ? Double.NaN : number(quality)));
        }
        return result;
    }
    private static Path capture(Path parent, Map<String, Object> source, Path destination, List<Object> audit) throws IOException {
        Path path = parent.resolve(required(source, "file")).normalize();
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) throw new IOException("Missing input or file exceeds 64 MiB: " + path);
        // Normalize CSV/TSV interpretation explicitly: this workflow accepts tab-separated files only.
        Files.copy(path, destination);
        String original = FollowupSupport.hash(path), copied = FollowupSupport.hash(destination);
        if (!original.equals(copied)) throw new IOException("Input changed while capturing: " + path);
        audit.add(Map.of("source", path.toAbsolutePath().toString(), "source_id", required(source, "source_id"), "captured", destination.getParent().getParent().getFileName() + "/inputs/" + destination.getFileName(),
            "size", Files.size(destination), "modified", Files.getLastModifiedTime(path).toString(), "sha256", copied));
        return destination;
    }
    private static void writeDecisions(Path file, Result result) throws IOException {
        FollowupSupport.table(file, List.of("stage", "variant_id", "decision", "r_squared"), result.decisions().stream()
            .map(d -> new String[]{d.stage(), d.variant(), d.reason(), Double.isNaN(d.rSquared()) ? "NA" : Double.toString(d.rSquared())}).toList());
    }
    private static Policy policy(Map<String, Object> p) {
        keys(p, "instrument_p", "minimum_f", "imputation_metric", "minimum_quality", "minimum_proxy_r2", "confidence_level");
        return new Policy(numeric(p, "instrument_p"), numeric(p, "minimum_f"), required(p, "imputation_metric"),
            numeric(p, "minimum_quality"), numeric(p, "minimum_proxy_r2"), numeric(p, "confidence_level"));
    }
    private static void validateSource(Map<String, Object> s) {
        keys(s, "file", "source_id", "trait_id", "units", "genome_build", "ancestry", "effect_model");
        for (String key : List.of("file", "source_id", "trait_id", "units", "ancestry")) required(s, key);
        if (!required(s, "effect_model").equals("additive_per_allele"))
            throw new IllegalArgumentException("Require signed additive per-allele effects; omnibus genotypic tests are unsupported");
        if (!Set.of("GRCh37", "GRCh38").contains(required(s, "genome_build"))) throw new IllegalArgumentException("Explicit GRCh37/GRCh38 required");
    }
    private static void compatible(Map<String, Object> left, Map<String, Object> right) {
        for (String key : List.of("genome_build", "ancestry")) if (!required(left, key).equals(required(right, key)))
            throw new IllegalArgumentException("Incompatible " + key + "; no implicit liftover or ancestry substitution");
    }
    private static Map<String, Object> inventory(Path root) throws IOException {
        var result = new LinkedHashMap<String, Object>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(root.toRealPath())) throw new IOException("Linked bundle entry");
                if (Files.isRegularFile(path) && !Set.of("manifest.yaml", "COMPLETED").contains(root.relativize(path).toString()))
                    result.put(root.relativize(path).toString().replace('\\', '/'), FollowupSupport.hash(path));
            }
        }
        return result;
    }
    private static void verify(Path root) throws IOException {
        root = root.toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("COMPLETED"))) throw new IOException("No completed bundle marker");
        if (!Files.readString(root.resolve("COMPLETED")).trim().equals(FollowupSupport.hash(root.resolve("manifest.yaml")))) throw new IOException("Manifest hash mismatch");
        if (!object(StudyYaml.loadMap(root.resolve("manifest.yaml")).get("files")).equals(inventory(root))) throw new IOException("Bundle file inventory/hash mismatch");
    }
    private static Map<String, Object> producer() throws IOException {
        var hashes = new LinkedHashMap<String, Object>();
        for (Class<?> type : List.of(LinkedMrCli.class, LinkedVariantMr.class, org.jlinalg.mr.MendelianRandomization.class,
                FineMappingCli.class, org.jlinalg.coloc.ColocSusie.class, jdistlib.Normal.class)) classHashes(type, hashes);
        return hashes;
    }
    private static void classHashes(Class<?> type, Map<String, Object> hashes) throws IOException {
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (input == null) throw new IOException("Producer class unavailable: " + type.getName());
            try { hashes.put(type.getName(), java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))); }
            catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
        }
        for (Class<?> nested : type.getDeclaredClasses()) classHashes(nested, hashes);
    }
    private static String cell(DelimitedData table, String[] row, String key) { return row[table.column(key)].trim(); }
    private static String string(Map<String, Object> row, String key) { return String.valueOf(row.getOrDefault(key, "NA")); }
    private static String required(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof String s) || s.isBlank() || s.matches("(?s).*[\\t\\r\\n].*")) throw new IllegalArgumentException("Nonblank text required: " + key);
        return s;
    }
    private static double numeric(Map<String, Object> row, String key) { return number(String.valueOf(row.get(key))); }
    private static double number(String text) {
        double result = Double.parseDouble(text);
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Nonfinite numeric value");
        return result;
    }
    private static int integer(Map<String, Object> row, String key) {
        long value = positiveLong(row, key);
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("Integer exceeds range: " + key);
        return (int) value;
    }
    private static long positiveLong(Map<String, Object> row, String key) {
        long result = Long.parseLong(String.valueOf(row.get(key)));
        if (result < 1) throw new IllegalArgumentException("Positive integer required: " + key);
        return result;
    }
    private static Map<String, Object> object(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) throw new IllegalArgumentException("Expected mapping");
        var result = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> result.put(key.toString(), value)); return result;
    }
    private static List<?> list(Object raw) {
        if (!(raw instanceof List<?> values)) throw new IllegalArgumentException("Expected list"); return values;
    }
    private static void keys(Map<String, Object> value, String... allowed) {
        var set = Set.of(allowed);
        for (String key : value.keySet()) if (!set.contains(key)) throw new IllegalArgumentException("Unknown field: " + key);
    }
    private static String help() {
        return """
            Usage: mr-linked --request FILE.yaml --out NEW_DIRECTORY
                   mr-linked --verify DIRECTORY

            Exposure-selected cis single-signal MR with exact-first outcome QC and
            high-LD replacement using the proxy's OWN exposure and outcome effects.
            Retains reliable null outcomes; never selects by outcome P, rescales by
            INFO, or calls missing/poorly measured outcomes null. All files are local.
            Emits primary and stricter-quality/LD sensitivity results, full-family
            Bonferroni correction, decision audits, input snapshots and checksums.
            Optional reviewed SuSiE sidecars invoke regional coloc across declared
            priors. Colocalization is separate evidence, never proof of mediation.
            See docs/vignettes/linked-variant-mr.md and examples/mr-linked/request.yaml.
            """;
    }
}
