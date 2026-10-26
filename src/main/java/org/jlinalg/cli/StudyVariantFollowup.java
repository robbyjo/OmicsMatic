/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyVariantFollowupPolicy;

/** Whole-universe consequences and molecular scores, before masks or after GWAS QC. */
final class StudyVariantFollowup implements StudyOperationExecutor {
    @FunctionalInterface interface Consequences { List<Map<String, Object>> run(Context context, Path batch, List<StudyVariantUniverse.Key> keys, Map<String, Object> configuration) throws IOException; }
    private final Consequences consequences;
    private final StudyAlphaGenome alpha;
    private record ImportRows(List<String> header, Map<String, List<String>> rows, int count, String sha256) { }
    StudyVariantFollowup() { this(nativeConsequences(), new StudyAlphaGenome()); }
    private static Consequences nativeConsequences() {
        Map<String, ImportRows> imports = new LinkedHashMap<>();
        return (context, folder, keys, config) -> consequences(context, folder, keys, config, imports);
    }
    StudyVariantFollowup(Consequences consequences, StudyAlphaGenome alpha) { this.consequences = consequences; this.alpha = alpha; }
    @Override public Result execute(Context context) throws IOException {
        Files.createDirectories(context.outputDirectory());
        boolean before = "variant_annotation".equals(context.operation().get("type"));
        var policy = StudyVariantFollowupPolicy.resolve(map(StudyResultTables.analysis(context).get("variant_followup")));
        StudyVariantUniverse catalog;
        try { catalog = StudyVariantUniverse.load(context, before); }
        catch (IOException invalid) {
            var need = StudyNeed.review("variant-identity:" + context.operation().get("hypothesis"), "Resolve variant identity inputs", invalid.getMessage(), true);
            var details = Map.<String, Object>of("status", "variant_input_unresolved", "needs", List.of(need.document()));
            write(context.outputDirectory().resolve("variant-followup.yaml"), details);
            return new Result(State.WAITING_FOR_INPUT, "Variant identity input requires review", List.of("variant-followup.yaml"), details);
        }
        if (catalog.items.isEmpty()) return new Result(State.NOT_APPLICABLE, "No genomic variant inputs are declared", List.of(), Map.of("performed", false));
        var annotation = map(policy.get("consequences")); var genome = map(policy.get("alphagenome"));
        Map<String, String> reference = new LinkedHashMap<>(), annotationStatus = new LinkedHashMap<>();
        Map<String, StudyVariantUniverse.Key> eligible = new LinkedHashMap<>();
        String referenceHash = "not_supplied"; String referenceError = "";
        StudyVariantReference fasta = null;
        if (policy.containsKey("reference_fasta")) try {
            fasta = new StudyVariantReference(context.project().resolve(text(policy.get("reference_fasta"))).normalize()); referenceHash = fasta.sha256;
        } catch (IOException invalid) { referenceError = invalid.getMessage(); }
        try {
            for (var key : catalog.queries.values()) {
                String status = fasta == null ? referenceError.isBlank() ? "reference_required" : "reference_unavailable" : fasta.check(key);
                if (status.equals("reference_matched") && !key.snp() && !Boolean.TRUE.equals(policy.get("reference_normalized"))) status = "indel_normalization_declaration_required";
                reference.put(key.id(), status); if (status.equals("reference_matched")) eligible.put(key.id(), key);
            }
        } finally { if (fasta != null) fasta.close(); }
        List<Map<String, Object>> annotations = new ArrayList<>();
        var batches = new StudyVariantBatchCache(); Map<String, Object> provider = null; List<Object> consequenceBatches = new ArrayList<>();
        String blocked = StudyPublicEvidence.blocked(context.study()); int batchNumber = 0;
        if (Boolean.TRUE.equals(annotation.get("enabled"))) {
            Map<String, List<StudyVariantUniverse.Key>> builds = new LinkedHashMap<>(); eligible.values().forEach(key -> builds.computeIfAbsent(key.build(), ignored -> new ArrayList<>()).add(key));
            for (var keys : builds.values()) for (int offset = 0; offset < keys.size(); offset += ((Number) policy.get("maximum_variants_per_batch")).intValue()) {
                var batch = keys.subList(offset, Math.min(keys.size(), offset + ((Number) policy.get("maximum_variants_per_batch")).intValue()));
                if (annotation.get("engine").equals("vep-rest") && !blocked.isBlank()) { batch.forEach(key -> annotationStatus.put(key.id(), blocked)); continue; }
                Path folder = context.outputDirectory().resolve("consequences/batch-" + String.format(java.util.Locale.ROOT, "%06d", ++batchNumber)); Files.createDirectories(folder);
                try {
                    if (provider == null) provider = StudyVariantBatchCache.provider(context, annotation);
                    String identity = StudyVariantBatchCache.identity(provider, Map.of("kind", "consequences", "reference_sha256", referenceHash, "variants", batch.stream().map(StudyVariantUniverse.Key::id).toList()));
                    boolean reused = batches.reuse(context, identity, folder);
                    var rows = reused ? readConsequences(folder.resolve("batch-consequences.tsv")) : consequences.run(context, folder, batch, annotation);
                    Set<String> expected = new LinkedHashSet<>(batch.stream().map(StudyVariantUniverse.Key::id).toList()), returned = new LinkedHashSet<>();
                    Map<String, String> batchStatuses = new LinkedHashMap<>();
                    for (var row : rows) {
                        String id = text(row.get("variant_id")); if (!expected.contains(id)) throw new IOException("Consequence response identifies an unrequested variant");
                        String status = text(row.get("status")); if (!Set.of("annotated", "not_found").contains(status)) throw new IOException("Unknown consequence result status");
                        if (batchStatuses.containsKey(id) && !batchStatuses.get(id).equals(status)) throw new IOException("Conflicting consequence dispositions");
                        returned.add(id); batchStatuses.put(id, status);
                    }
                    if (!returned.equals(expected)) throw new IOException("Consequence coverage differs from the requested batch");
                    annotationStatus.putAll(batchStatuses); annotations.addAll(rows);
                    consequenceBatches.add(Map.of("identity", identity, "variants", batch.size(), "reused", reused, "artifact", context.outputDirectory().relativize(folder).toString().replace('\\', '/')));
                    if (!reused) {
                        List<String> fields = List.of("variant_id", "gene", "transcript", "consequence", "detail", "status");
                        FollowupSupport.table(folder.resolve("batch-consequences.tsv"), fields, rows.stream().map(row -> fields.stream().map(field -> text(row.getOrDefault(field, "NA"))).toArray(String[]::new)).toList());
                        StudyVariantBatchCache.seal(folder, identity);
                    }
                } catch (IOException invalid) { batch.forEach(key -> annotationStatus.put(key.id(), "provider_or_input_error")); write(folder.resolve("failure.yaml"), Map.of("reason", invalid.getMessage(), "scientific_interpretation", "Unavailable annotation is not a negative functional result")); }
            }
        } else eligible.keySet().forEach(id -> annotationStatus.put(id, "disabled_by_protocol"));
        StudyAlphaGenome.Result predicted;
        try { predicted = before ? new StudyAlphaGenome.Result(List.of(), Map.of(), List.of()) : alpha.execute(context, eligible, genome); }
        catch (IOException invalid) {
            Map<String, String> unavailable = new LinkedHashMap<>(); eligible.keySet().forEach(id -> unavailable.put(id, "score_input_or_response_invalid"));
            write(context.outputDirectory().resolve("alphagenome-failure.yaml"), Map.of("status", "score_input_or_response_invalid", "reason", invalid.getMessage()));
            predicted = new StudyAlphaGenome.Result(List.of(), unavailable, List.of());
        }
        List<String> annotationFields = List.of("variant_id", "gene", "transcript", "consequence", "detail", "status");
        FollowupSupport.table(context.outputDirectory().resolve("variant-consequences.tsv"), annotationFields, annotations.stream().map(row -> annotationFields.stream().map(field -> text(row.getOrDefault(field, "NA"))).toArray(String[]::new)).toList());
        FollowupSupport.table(context.outputDirectory().resolve("alphagenome-scores.tsv"), StudyAlphaGenome.FIELDS, predicted.scores().stream().map(row -> StudyAlphaGenome.FIELDS.stream().map(field -> text(row.getOrDefault(field, "NA"))).toArray(String[]::new)).toList());
        Map<String, List<Map<String, Object>>> byVariant = new LinkedHashMap<>(), scoresByVariant = new LinkedHashMap<>(); annotations.forEach(row -> byVariant.computeIfAbsent(text(row.get("variant_id")), ignored -> new ArrayList<>()).add(row));
        predicted.scores().forEach(row -> scoresByVariant.computeIfAbsent(text(row.get("variant_id")), ignored -> new ArrayList<>()).add(row));
        List<String[]> inventory = new ArrayList<>(), evidence = new ArrayList<>(); int unresolved = 0;
        for (var item : catalog.items) {
            String id = item.key == null ? "NA" : item.key.id(), ref = reference.getOrDefault(id, item.eligible ? "identity_unresolved" : "excluded_from_primary");
            String consequence = annotationStatus.getOrDefault(id, !item.eligible ? "excluded_from_primary" : "reference_or_identity_prerequisite");
            String score = before ? "not_requested_before_masks" : predicted.statuses().getOrDefault(id, !item.eligible ? "excluded_from_primary" : "reference_or_identity_prerequisite");
            boolean annotationReady = !Boolean.TRUE.equals(annotation.get("enabled")) || Set.of("annotated", "not_found").contains(consequence);
            boolean scoreReady = before || !Boolean.TRUE.equals(genome.get("enabled")) || Set.of("complete", "imported", "not_found", "no_matching_tracks", "not_in_score_import", "atlas_snp_only", "unsupported_genome_build", "unsupported_chromosome").contains(score);
            boolean requested = Boolean.TRUE.equals(annotation.get("enabled")) || !before && Boolean.TRUE.equals(genome.get("enabled"));
            if (item.eligible && requested && (!ref.equals("reference_matched") || !annotationReady || !scoreReady)) unresolved++;
            inventory.add(new String[]{item.dataset, item.feature, id, Boolean.toString(item.eligible), item.disposition, ref, consequence, score});
            for (var row : byVariant.getOrDefault(id, List.of())) evidence.add(new String[]{item.feature, item.dataset, id, "consequence", text(row.getOrDefault("gene", "NA")), text(row.getOrDefault("transcript", "NA")), text(row.get("consequence")), "NA", "NA", "NA", "NA", "NA", consequence, text(annotation.get("release"))});
            for (var row : scoresByVariant.getOrDefault(id, List.of())) evidence.add(new String[]{item.feature, item.dataset, id, "alphagenome", text(row.get("gene_id")), text(row.get("transcript_id")), "NA", text(row.get("scorer")), text(row.get("ontology_curie")), text(row.get("raw_score")), text(row.get("quantile_score")), text(row.get("model_version")), score, text(row.get("resource_version"))});
        }
        FollowupSupport.table(context.outputDirectory().resolve("variant-inventory.tsv"), List.of("dataset_id", "feature_id", "variant_id", "eligible", "disposition", "reference_status", "consequence_status", "alphagenome_status"), inventory);
        FollowupSupport.table(context.outputDirectory().resolve("variant-evidence.tsv"), List.of("feature_id", "dataset_id", "variant_id", "kind", "gene_id", "transcript_id", "consequence", "scorer", "ontology_curie", "raw_score", "quantile_score", "model_version", "status", "resource_version"), evidence);
        if (before) {
            Files.createDirectories(context.outputDirectory().resolve("rare-annotation"));
            List<String[]> mappingAudit = new ArrayList<>();
            for (var dataset : StudyBioinformatics.datasets(context)) {
                String datasetId = text(dataset.get("id")); List<String[]> rows = new ArrayList<>();
                for (var item : catalog.items) if (item.dataset.equals(datasetId) && item.key != null) for (var row : byVariant.getOrDefault(item.key.id(), List.of())) {
                    String gene = text(row.get("gene")), consequence = text(row.get("consequence")).replace("[", "").replace("]", "").replace(", ", ",");
                    String disposition = !gene.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}") || Set.of("NA", ".").contains(gene) ? "missing_or_aggregated_gene" : consequence.isBlank() || Set.of("NA", ".").contains(consequence) ? "missing_consequence" : "eligible_annotation_record";
                    mappingAudit.add(new String[]{datasetId, item.feature, item.key.id(), gene, consequence, disposition});
                    if (!disposition.equals("eligible_annotation_record")) continue;
                    rows.add(new String[]{item.key.locus(), gene, consequence, item.key.build()});
                }
                FollowupSupport.table(context.outputDirectory().resolve("rare-annotation/" + datasetId + ".tsv"), List.of("variant_id", "set_id", "consequence", "genome_build"), rows);
            }
            for (var item : catalog.items) if (item.key == null || byVariant.getOrDefault(item.key.id(), List.of()).isEmpty()) mappingAudit.add(new String[]{item.dataset, item.feature, item.key == null ? "NA" : item.key.id(), "NA", "NA", "identity_reference_or_annotation_unavailable"});
            FollowupSupport.table(context.outputDirectory().resolve("rare-annotation-coverage.tsv"), List.of("dataset_id", "feature_id", "variant_id", "gene_id", "consequence", "disposition"), mappingAudit);
        }
        Map<String, Object> details = new LinkedHashMap<>(); details.put("schema_version", 1); details.put("scope", before ? "pre_association_variant_catalog_for_declared_masks" : "primary_eligible_and_rare_set_QC_candidates_without_significance_selection");
        details.put("input_records", catalog.items.size()); details.put("unique_eligible_variants", catalog.queries.size()); details.put("reference_matched", eligible.size()); details.put("unresolved_records", unresolved);
        Map<String, Object> releases = new LinkedHashMap<>();
        Path consequenceRoot = context.outputDirectory().resolve("consequences");
        if (Files.isDirectory(consequenceRoot)) try (var paths = Files.walk(consequenceRoot)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().equals("provider-release.yaml")).sorted().toList()) {
                var metadata = StudyYaml.loadArtifact(path); String build = text(metadata.get("genome_build")); Object values = metadata.get("releases");
                if (releases.containsKey(build) && !releases.get(build).equals(values)) throw new IOException("Ensembl release changed within the operation; mixed consequence releases cannot be pooled");
                releases.put(build, values);
            }
        }
        String annotationRelease = releases.isEmpty() ? text(annotation.get("release")) : "Ensembl:" + releases;
        details.put("source_sha256", catalog.sourceHashes); details.put("reference_sha256", referenceHash); details.put("annotation_release", annotationRelease); details.put("annotation_source_releases", releases); details.put("policy", policy); details.put("alphagenome_requests", predicted.requests());
        details.put("consequence_batches", consequenceBatches);
        details.put("method_report", StudyMethods.record("Allele-resolved functional consequences and AlphaGenome molecular predictions", Map.of("scope", details.get("scope"), "policy", policy, "eligible_variants", catalog.queries.size(), "reference_sha256", referenceHash),
            List.of("https://annovar.openbioinformatics.org/en/latest/", "https://www.ensembl.org/info/docs/tools/vep/", "https://www.alphagenomedocs.com/"), null, List.of("variant consequences", "AlphaGenome")));
        details.put("interpretation", "Consequence/AlphaGenome records are functional predictions, not association P values, causal posteriors or validated pathogenicity. Transcript/tissue/scorer context and unavailable coverage remain explicit. Rare masks use only unambiguous gene/consequence records, without inferring loss-of-function confidence or splitting aggregated ANNOVAR gene relationships. Reference checks establish agreement with the supplied FASTA, not independent assembly certification.");
        if (unresolved > 0) details.put("needs", List.of(StudyNeed.review("variant-followup:" + context.operation().get("hypothesis") + ":" + context.operation().get("type"),
            "Resolve variant annotation or score prerequisites", unresolved + " eligible input records have unresolved identity, reference, tool or prediction coverage. See variant-inventory.tsv, policy and per-batch audits. No annotation/score was invented.", true).document()));
        write(context.outputDirectory().resolve("variant-followup.yaml"), details);
        List<String> outputs; try (var files = Files.walk(context.outputDirectory())) { outputs = files.filter(Files::isRegularFile).map(context.outputDirectory()::relativize).map(path -> path.toString().replace('\\', '/')).sorted().toList(); }
        return new Result(unresolved == 0 ? State.COMPLETE : State.WAITING_FOR_INPUT, "Variant follow-up: " + catalog.queries.size() + " eligible variants, " + unresolved + " unresolved input records", outputs, details);
    }

    private static List<Map<String, Object>> consequences(Context context, Path folder, List<StudyVariantUniverse.Key> keys, Map<String, Object> config, Map<String, ImportRows> imports) throws IOException {
        if (config.get("engine").equals("vep-rest")) {
            Path metadata = context.outputDirectory().resolve("consequences/ensembl-" + keys.get(0).build() + "-info-data.json");
            if (!Files.isRegularFile(metadata)) {
                String host = keys.get(0).build().equals("GRCh38") ? "rest.ensembl.org" : "grch37.rest.ensembl.org";
                try {
                    FollowupSupport.request("https://" + host + "/info/data?content-type=application/json", null, "application/json", metadata, Map.of("timeout", text(config.get("timeout_seconds")), "max-download-mb", "1"));
                    if (list(map(SimpleJson.parse(Files.readString(metadata))).get("releases")).isEmpty()) throw new IOException("Ensembl release metadata is missing");
                } catch (Exception unavailable) { throw new IOException("Ensembl release metadata could not be verified", unavailable); }
            }
            Files.copy(metadata, folder.resolve("ensembl-info-data.json"));
            write(folder.resolve("provider-release.yaml"), Map.of("genome_build", keys.get(0).build(), "releases", map(SimpleJson.parse(Files.readString(metadata))).get("releases"), "raw_sha256", FollowupSupport.hash(metadata)));
        }
        Path input = folder.resolve("variants.tsv");
        FollowupSupport.table(input, List.of("variant_id", "genome_build", "chrom", "pos", "ref", "alt"), keys.stream().map(key -> new String[]{key.id(), key.build(), key.chromosome(), Long.toString(key.position()), key.reference(), key.alternate()}).toList());
        String engine = text(config.get("engine")); List<String> args = new ArrayList<>(List.of("--input", input.toString(), "--genome-build", keys.get(0).build(), "--engine", engine, "--out", folder.resolve("output").toString(), "--timeout", text(config.get("timeout_seconds"))));
        Path subset = engine.equals("import") ? importSubset(context, folder, keys, config, imports) : null;
        for (String field : List.of("annotations", "executable", "perl", "database", "cache", "fasta")) if (config.containsKey(field)) {
            String value = field.equals("annotations") && subset != null ? subset.toString() : text(config.get(field)); Path candidate = context.project().resolve(value).normalize(); if (Files.exists(candidate)) value = candidate.toString(); args.addAll(List.of("--" + field, value));
        }
        for (String field : List.of("format", "protocol", "operation", "cache_version")) if (config.containsKey(field)) args.addAll(List.of("--" + field.replace('_', '-'), text(config.get(field))));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream(), stderr = new ByteArrayOutputStream();
        int code = VariantFollowupCli.run("variant-consequence", args.toArray(String[]::new), new PrintStream(stdout), new PrintStream(stderr));
        Files.writeString(folder.resolve("console.txt"), stdout + "\n" + stderr);
        if (code != 0) throw new IOException("Consequence engine failed: " + stderr.toString().strip());
        // Preserve the standalone audit under an unambiguous name; study manifests
        // are discovered recursively and must always identify an operation.
        if (Files.isRegularFile(folder.resolve("output/manifest.yaml"))) Files.move(folder.resolve("output/manifest.yaml"), folder.resolve("output/tool-manifest.yaml"));
        Path output = folder.resolve("output/consequences.tsv"); if (!Files.isRegularFile(output)) output = folder.resolve("output/annotations.tsv");
        return readConsequences(output);
    }
    private static List<Map<String, Object>> readConsequences(Path output) throws IOException {
        var table = DelimitedData.readResult(output); List<Map<String, Object>> rows = new ArrayList<>();
        for (String[] values : table.rows()) {
            var row = new LinkedHashMap<String, Object>(); row.put("variant_id", values[table.column("variant_id")]); row.put("status", values[table.column("status")]);
            for (String field : List.of("gene", "transcript", "consequence", "detail")) {
                String column = table.header().contains(field) ? field : "annotation." + field;
                row.put(field, table.header().contains(column) ? values[table.column(column)] : "NA");
            }
            rows.add(row);
        }
        return rows;
    }

    private static Path importSubset(Context context, Path folder, List<StudyVariantUniverse.Key> keys, Map<String, Object> config, Map<String, ImportRows> imports) throws IOException {
        String source = text(config.get("annotations")); if (source.isBlank()) throw new IOException("Consequence import requires annotations");
        Path raw = context.project().resolve(source).normalize(), subset = folder.resolve("annotation-subset.txt");
        String cacheKey = context.outputDirectory() + "\n" + raw + "\n" + config.get("format");
        ImportRows indexed = imports.get(cacheKey);
        if (indexed == null) { indexed = indexImport(raw, text(config.get("format"))); imports.put(cacheKey, indexed); }
        List<String> selected = new ArrayList<>(indexed.header); int selectedRows = 0;
        for (var key : keys) for (String row : indexed.rows.getOrDefault(key.id(), List.of())) { selected.add(row); selectedRows++; }
        Files.write(subset, selected);
        write(folder.resolve("import-selection.yaml"), Map.of("source_sha256", indexed.sha256, "input_rows", indexed.count, "selected_rows", selectedRows, "scope", "one indexed source snapshot per operation; exact canonical requested alleles"));
        return subset;
    }
    private static ImportRows indexImport(Path raw, String format) throws IOException {
        List<String> selected = new ArrayList<>(); Map<String, List<String>> rows = new LinkedHashMap<>(); int inputRows = 0;
        if (format.equals("canonical")) {
            var table = DelimitedData.readResult(raw); selected.add(String.join("\t", table.header()));
            for (String[] row : table.rows()) {
                inputRows++; StudyVariantUniverse.Key key;
                try { key = StudyVariantUniverse.key(StudyVariantUniverse.build(row[table.column("genome_build")]), row[table.column("chrom")], Long.parseLong(row[table.column("pos")]), row[table.column("ref")], row[table.column("alt")]); }
                catch (IllegalArgumentException invalid) { throw new IOException("Invalid canonical consequence identity", invalid); }
                if (key == null) throw new IOException("Invalid canonical consequence identity");
                if (table.header().contains("variant_id") && !row[table.column("variant_id")].equals(key.id())) throw new IOException("Consequence ID conflicts with canonical alleles");
                rows.computeIfAbsent(key.id(), ignored -> new ArrayList<>()).add(String.join("\t", row));
            }
        } else {
            List<String> header = null;
            for (String line : Files.readAllLines(raw)) {
                if (line.isBlank()) continue;
                if (line.startsWith("#")) {
                    selected.add(line); if (line.startsWith("#Uploaded_variation")) header = List.of(line.substring(1).split("\t", -1)); continue;
                }
                inputRows++; String[] row = line.split("\t", -1); String id;
                if (format.equals("vep")) {
                    if (header == null || !header.contains("Uploaded_variation") || row.length != header.size()) throw new IOException("VEP import requires its full Uploaded_variation header");
                    id = row[header.indexOf("Uploaded_variation")]; if (StudyVariantUniverse.parseId(id, "") == null) throw new IOException("VEP import requires canonical variant IDs");
                } else {
                    if (row.length < 8) throw new IOException("ANNOVAR import requires VCF records"); id = row[2];
                    var key = StudyVariantUniverse.parseId(id, "");
                    try { if (key == null || !key.equals(StudyVariantUniverse.key(key.build(), row[0], Long.parseLong(row[1]), row[3], row[4]))) throw new IOException("ANNOVAR canonical ID differs from returned alleles"); }
                    catch (NumberFormatException invalid) { throw new IOException("Invalid ANNOVAR coordinate", invalid); }
                }
                rows.computeIfAbsent(id, ignored -> new ArrayList<>()).add(line);
            }
        }
        return new ImportRows(selected, rows, inputRows, FollowupSupport.hash(raw));
    }
}
