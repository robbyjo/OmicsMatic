/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudySuppliedResultPolicy;

/** Validate supplied analysis artifacts without rerunning the external analysis. */
final class StudySuppliedResults implements StudyOperationExecutor {
    private static final Set<String> STATISTICAL = Set.of("meta_analysis", "discovery_meta_analysis", "gene_ontology", "pathway_enrichment", "ranked_gsea", "gwas_enrichment", "module_gene_ontology", "mendelian_randomization", "rare_variant_analysis", "rare_set_omnibus", "risk_score_related_phenotypes", "subgroup_interactions");
    private static final Set<String> NUMERIC = Set.of("source_adjusted_p","source_testing_denominator","selection_testing_denominator","testing_denominator","association_testing_denominator","log_p_value","log_adjusted_p","beta", "se", "p_value", "adjusted_p", "ci_lower", "ci_upper", "n", "n_cohorts", "n_sources", "reported_n_sum", "q", "i_squared", "tau_squared", "q_p_value", "weight", "correlation", "n_matched", "enrichment_score", "normalized_enrichment_score", "overlap_genes", "term_genes", "module_genes", "selected_genes", "universe_genes", "fold_enrichment_unweighted", "pp_h0", "pp_h1", "pp_h2", "pp_h3", "pp_h4", "pip", "n_instruments", "r_squared", "auc", "r_squared_score", "plddt", "pae", "raw_score", "quantile_score", "score", "estimate", "rmse", "baseline_rmse", "brier", "baseline_brier", "log_loss", "baseline_log_loss", "calibration_intercept", "calibration_slope", "bootstrap_valid", "bootstrap_attempted", "independent_units", "target_length", "observed_residues", "confidence_residues", "mean_plddt", "coverage", "fraction_plddt_lt50", "fraction_plddt_50_70", "fraction_plddt_70_90", "fraction_plddt_ge90", "pae_dimension", "metric_value", "threshold", "association_adjusted_p", "q_df");
    private static final Set<String> PROBABILITIES = Set.of("source_adjusted_p","p_value", "adjusted_p", "q_p_value", "pp_h0", "pp_h1", "pp_h2", "pp_h3", "pp_h4", "pip", "auc", "coverage", "fraction_plddt_lt50", "fraction_plddt_50_70", "fraction_plddt_70_90", "fraction_plddt_ge90");
    private static final Set<String> GWAS_NUMERIC=StudyGwasAnnotationColumns.NUMERIC;
    private static final Set<String> GSEA_COUNTS=Set.of("ranked_members","null_states","same_sign_states","tail_states");
    private static final Set<String> GSEA_PROBABILITIES=Set.of("p_resolution","p_mc_lower_95","p_mc_upper_95");
    private static final Set<String> GSEA_LABELS=Set.of("leading_edge_genes","null_method");
    private static final Set<String> LABELS = Set.of("overlap_id","enrichment_id","enrichment_source","association_adjustment","feature_id", "term_id", "term_name", "term", "status", "direction", "method", "test", "mask_id", "scope", "is_primary_test", "result_type", "variant_id", "region_id", "gene_id", "gene_symbol", "transcript_id", "genome_build", "chromosome", "position", "ref", "alt", "effect_allele", "other_allele", "consequence", "scorer", "ontology_curie", "score_name", "model_version", "resource_version", "hit_genes", "node_id", "module_id", "module_type", "network_id", "source", "target", "node_type", "edge_type", "score_id", "phenotype", "evaluation_population", "protein_id", "sequence_sha256", "structure_id", "discovery_selected", "replicated", "left", "right", "evidence_grade", "required_manuscript_caveat", "metric", "subgroup_column", "subgroup", "family", "adjustment", "dataset_id", "isoform_id", "taxon_id", "sequence_version", "model_id", "model_source", "structure_kind", "seed", "chain", "sequence_match", "assay_mapping_status", "assay_mapping_source", "assay_mapping_limitations", "set_id", "cohort", "role", "in_universe", "source_estimable", "in_selection_family", "discovery_candidate", "same_sign_reference", "selected", "in_alert_scope", "high_heterogeneity", "q_definition", "threshold_comparison", "source_coverage", "source_sha256", "pae_sha256", "limitations", "directory");
    private static final Map<String, String> OUTPUTS = Map.ofEntries(
        Map.entry("meta_analysis", "meta-analysis.tsv"), Map.entry("discovery_meta_analysis", "discovery-results.tsv"),
        Map.entry("discovery_replication", "replication.tsv"), Map.entry("effect_comparison", "effect-comparisons.tsv"), Map.entry("cohort_overlap","cohort-overlap-membership.tsv"), Map.entry("rare_cohort_overlap","cohort-overlap-membership.tsv"), Map.entry("heterogeneity_review","heterogeneity-features.tsv"),
        Map.entry("network_modules", "network-modules.tsv"), Map.entry("module_gene_ontology", "module-gene-ontology.tsv"), Map.entry("gene_ontology", "gene-ontology.tsv"), Map.entry("pathway_enrichment", "pathway-enrichment.tsv"),
        Map.entry("gwas_enrichment", "gwas-enrichment.tsv"), Map.entry("ranked_gsea", "ranked-gsea.tsv"), Map.entry("mendelian_randomization", "mr-primary-results.tsv"),
        Map.entry("rare_variant_analysis", "rare-results.tsv"), Map.entry("rare_set_omnibus", "rare-omnibus-results.tsv"),
        Map.entry("colocalization", "colocalization-results.tsv"), Map.entry("fine_mapping", "fine-mapping-results.tsv"), Map.entry("gwas_diagnostics","gwas-diagnostic-summary.tsv"),
        Map.entry("subgroup_interactions","subgroup-interactions.tsv"), Map.entry("network_preservation","network-preservation.tsv"),
        Map.entry("network_preservation_null","network-preservation-tests.tsv"),
        Map.entry("risk_score_evaluation", "risk-score-evaluation.tsv"), Map.entry("risk_score_related_phenotypes","related-phenotypes.tsv"), Map.entry("variant_annotation", "variant-consequences.tsv"),
        Map.entry("variant_scoring", "variant-scores.tsv"), Map.entry("protein_structure", "protein-structures.tsv"));

    @Override public Result execute(Context context) throws IOException {
        var definition = StudySuppliedResultPolicy.definition(context.study(), text(context.operation().get("hypothesis")), text(context.operation().get("supplied_result_id")));
        String kind = text(definition.get("kind"));
        boolean contribution = "contribution".equals(definition.get("role"));
        boolean partial = StudySuppliedResultPolicy.partialPrimary(definition);
        List<String> outputs = new ArrayList<>(), needs = new ArrayList<>();
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("schema_version", 1); metadata.put("artifact_id", definition.get("id")); metadata.put("kind", kind);
        metadata.put("role", definition.get("role")); metadata.put("reported_status", definition.get("reported_status"));
        metadata.put("execution_origin", "externally_produced"); metadata.put("analysis_executed_by_jlinalg", false);
        metadata.put("replaces_native_stage", StudySuppliedResultPolicy.ownsStage(definition));
        var completionOperations = list(context.plan().get("operations")).stream().map(StudyPublicEvidence::map)
            .filter(op -> kind.equals(op.get("type")) && context.operation().get("hypothesis").equals(op.get("hypothesis")) && !op.containsKey("supplied_result_id"))
            .map(op -> text(op.get("id"))).toList();
        if (partial) metadata.put("native_completion_operations", completionOperations);
        for (String key : List.of("scope", "method", "family", "provenance", "limitations", "depends_on")) metadata.put(key, definition.getOrDefault(key, key.equals("limitations") || key.equals("depends_on") ? List.of() : Map.of()));
        metadata.put("evidence_grade", definition.getOrDefault("evidence_grade", "source_coverage_unverified"));
        var upstream = new ArrayList<Object>();
        for (Path directory : context.dependencyDirectories().values()) {
            Path manifest = directory.resolve("manifest.yaml");
            if (Files.isRegularFile(manifest)) {
                var declared = StudyYaml.loadArtifact(manifest);
                upstream.add(Map.of("operation_id", declared.getOrDefault("operation_id", directory.getFileName().toString()),
                    "state", declared.getOrDefault("state", "unknown"), "manifest_sha256", FollowupSupport.hash(manifest),
                    "reported_status", map(declared.get("details")).getOrDefault("reported_status", "unknown"),
                    "manifest", context.project().relativize(manifest).toString().replace('\\','/'),
                    "required_manuscript_caveat", map(declared.get("details")).getOrDefault("required_manuscript_caveat", "")));
            }
        }
        metadata.put("upstream_imports", upstream);
        Map<String, Path> snapshots = new LinkedHashMap<>(); var sourceHashes = new LinkedHashMap<String, Object>();
        Path raw = context.outputDirectory().resolve("supplied"); Files.createDirectories(raw);
        for (var entry : map(definition.get("files")).entrySet()) {
            Path source = context.project().resolve(text(entry.getValue())).normalize();
            if (!Files.isRegularFile(source)) { needs.add("Missing supplied " + entry.getKey() + " file: " + entry.getValue()); continue; }
            String hash = FollowupSupport.hash(source);
            if (contribution && entry.getKey().equals("results") && !hash.equalsIgnoreCase(text(map(definition.get("provenance")).get("results_sha256"))))
                needs.add("Supplied contribution results do not match the registered provenance.results_sha256; inspect the registered source before reuse");
            String suffix = source.getFileName().toString().replaceFirst("^[^.]*", "");
            if (!suffix.matches("(?:\\.[A-Za-z0-9_-]+){1,3}")) suffix = ".data";
            Path snapshot = raw.resolve(entry.getKey() + suffix);
            Files.copy(source, snapshot);
            if (!hash.equals(FollowupSupport.hash(snapshot))) throw new IOException("Supplied file changed while taking its immutable snapshot: " + source);
            snapshots.put(entry.getKey(), snapshot); sourceHashes.put(entry.getKey(), Map.of("source", entry.getValue(), "sha256", hash, "snapshot", "supplied/" + snapshot.getFileName()));
            outputs.add("supplied/" + snapshot.getFileName());
        }
        metadata.put("source_files", sourceHashes);
        metadata.put("required_manuscript_caveat", "Externally produced analysis imported and schema-checked by JLinAlg; the original analysis, participant QC, calibration and scientific conclusions were not independently reproduced. Original source methods, provenance and free-text limitations remain local and require review before scientific use or transmission.");
        if (partial) metadata.put("required_manuscript_caveat", metadata.get("required_manuscript_caveat")
            + " The source explicitly reports a partial analysis. This import preserves available evidence and does not complete or replace the requested stage; any native completion is a separate analysis with its own methods and results.");
        for (Object rawDependency : upstream) {
            var dependency = map(rawDependency);
            String warning = "";
            if (!"complete".equals(dependency.get("state"))) warning += " Upstream supplied result " + dependency.get("operation_id") + " is " + dependency.get("state") + "; its unresolved scope also limits this imported downstream evidence.";
            if ("partial".equals(dependency.get("reported_status"))) warning += " Upstream supplied result " + dependency.get("operation_id") + " explicitly reports partial analysis coverage, even if its import validation completed.";
            if (!warning.isBlank()) metadata.put("required_manuscript_caveat", metadata.get("required_manuscript_caveat") + " " + warning);
        }
        if ("NONE".equalsIgnoreCase(text(map(definition.get("family")).get("adjustment"))))
            metadata.put("required_manuscript_caveat", metadata.get("required_manuscript_caveat") + " Supplied nominal P values use no multiplicity correction and are not FDR-controlled discoveries.");
        boolean sensitivity = "sensitivity".equals(definition.get("role"));
        metadata.put("primary_selection_eligible", false);
        var scopeValidation=StudySuppliedScope.assess(context,definition);
        if(!scopeValidation.compatible())needs.add("Supplied scientific scope conflicts with the registered target or dependency; see supplied-scope-validation.yaml. No replacement analysis was selected.");
        if(Set.of("network_modules","module_gene_ontology").contains(kind)&&!"complete".equals(definition.get("reported_status"))&&!partial)
            needs.add("Supplied modules or module GO are not reported complete. Retained source snapshots cannot replace the requested completed stage; supply the completed scope or revise the registered request.");
        if (needs.isEmpty()) {
            if (kind.equals("network_analysis")) network(context, definition, snapshots, metadata, outputs);
            else if (kind.equals("literature_search")) {
                var normalized=StudySuppliedLiterature.normalize(snapshots.get("results"),definition);
                String existingCaveat=text(metadata.get("required_manuscript_caveat"));
                metadata.putAll(normalized);
                metadata.put("required_manuscript_caveat",existingCaveat+" "+StudySuppliedLiterature.CAVEAT);
                write(context.outputDirectory().resolve("literature-search.yaml"),normalized);outputs.add("literature-search.yaml");
                var catalog=StudyReferenceCatalog.build(list(normalized.get("evidence")));
                write(context.outputDirectory().resolve("reference-catalog.yaml"),catalog);outputs.add("reference-catalog.yaml");
                Files.writeString(context.outputDirectory().resolve("reference-catalog.html"),StudyReferenceCatalog.html(catalog));outputs.add("reference-catalog.html");
            }
            else if (Set.of("source_discovery", "bioinformatics_investigation").contains(kind)) {
                var supplied = StudyYaml.loadArtifact(snapshots.get("results"));
                metadata.put("supplied_summary", supplied);
                String name = (partial ? "supplied-partial-" : "") + kind.replace('_', '-') + ".yaml";
                write(context.outputDirectory().resolve(name), Map.of("execution_origin", "externally_produced", "analysis_executed_by_jlinalg", false,
                    "supplied_artifact", definition.get("id"), "supplied_evidence", supplied, "required_manuscript_caveat", metadata.get("required_manuscript_caveat")));
                outputs.add(name);
            } else {
                Table table = read(snapshots.get("results"), definition, "results", kind);
                StudySuppliedScope.rows(scopeValidation,table.header,table.rows);
                if(!scopeValidation.compatible()) {
                    needs.add("Source rows contradict the declared scientific scope; see supplied-scope-validation.yaml. No canonical result was promoted.");
                    FollowupSupport.table(context.outputDirectory().resolve("supplied-results-scope-limited.tsv"),table.header,table.rows);
                    outputs.add("supplied-results-scope-limited.tsv");metadata.put("scope_limited_result_rows",table.rows.size());
                } else {
                if (contribution) needs.addAll(StudyMrContributions.validateTable(table.header, table.rows, definition));
                metadata.put("aggregate_result_metadata",table.aggregateMetadata);
                var family = partial ? new LinkedHashMap<String,Object>(map(definition.get("family")))
                    : kind.equals("rare_set_omnibus") ? StudySuppliedRareOmnibus.families(table.header, table.rows, definition, metadata, needs) : family(table, definition, needs);
                if (partial) metadata.put("partial_family_handling", "Source declaration and existing adjusted values retained; no correction recomputed from an explicitly incomplete analysis.");
                metadata.put("resolved_family", family);
                if (kind.equals("rare_variant_analysis")) {
                    var primaryFamily = new LinkedHashMap<String,Object>(family);
                    primaryFamily.put("scope", "primary"); primaryFamily.put("testing_denominator", family.getOrDefault("tested_count", "not_declared"));
                    metadata.put("families", List.of(primaryFamily));
                    metadata.put("primary_identity", Map.of("scope", "supplied_primary", "artifact", "rare-results.tsv", "rule", "Source-declared primary tests and full joint mask/test family; no best-P selection"));
                }
                boolean completedScope = "complete".equals(definition.get("reported_status"))
                    || contribution && "not_estimable".equals(definition.get("reported_status"));
                boolean selection = !sensitivity && completedScope && needs.isEmpty();
                if(kind.equals("rare_set_omnibus"))selection=!sensitivity&&completedScope&&Boolean.TRUE.equals(metadata.get("rare_primary_complete"));
                metadata.put("primary_selection_eligible", selection && !contribution && STATISTICAL.contains(kind) && (!kind.equals("rare_set_omnibus") || org.jlinalg.study.StudyRareOmnibusPolicy.primary(StudyResultTables.analysis(context))));
                metadata.put("contribution_reuse_eligible", selection && contribution);
                metadata.put("result_rows", table.rows.size());
                metadata.put("result_columns", table.header);
                String name = partial ? "supplied-results-limited.tsv" : sensitivity ? "supplied-sensitivity.tsv" : contribution && selection ? "supplied-contribution.tsv" : selection || !STATISTICAL.contains(kind) ? OUTPUTS.get(kind) : "supplied-results-limited.tsv";
                FollowupSupport.table(context.outputDirectory().resolve(name), table.header, table.rows);
                outputs.add(name); metadata.put("canonical_results", name); metadata.put("canonical_sha256", FollowupSupport.hash(context.outputDirectory().resolve(name)));
                if (!partial && Set.of("fine_mapping", "colocalization", "mendelian_randomization").contains(kind)) genetic(context, kind, table, metadata, outputs);
                if(Set.of("network_modules","module_gene_ontology").contains(kind)&&selection)StudySuppliedModules.write(context,kind,table.header,table.rows,definition,snapshots,metadata,outputs);
                if (!partial) StudySuppliedCohorts.write(context,kind,table.header,table.rows,definition,metadata,needs,outputs);
                }
            }
            if (!partial && scopeValidation.compatible() && snapshots.containsKey("feature_annotation")) annotation(context, definition, snapshots.get("feature_annotation"), outputs);
        }
        if (partial && completionOperations.isEmpty() && list(StudyResultTables.analysis(context).get("goals")).contains(kind))
            needs.add("The supplied source explicitly reports partial " + kind + ". No native completion can be scheduled from the registered inputs. Supply the required source data or a completed analysis for this scope; the partial import cannot replace this requested stage.");
        if (partial && kind.equals("network_analysis") && !completionOperations.isEmpty())
            partialNetworkCoverage(context, definition, metadata, needs);
        scopeValidation.finish(context);metadata.put("scope_validation",scopeValidation.report);
        outputs.add("supplied-scope-validation.yaml");outputs.add("supplied-scope-comparisons.tsv");
        var method = new LinkedHashMap<String, Object>(); method.put("operation", kind); method.put("performed", true);
        method.put("execution_role", "import_validation"); method.put("analysis_executed_by_jlinalg", false);
        method.put("external_method_details", "Original source method and parameters require local review in supplied-analysis.yaml");
        method.put("scope_validation_status",scopeValidation.report.get("status"));
        method.put("known_scope_conflicts",scopeValidation.issues.size());method.put("missing_context_fields",scopeValidation.missing.size());
        var safeFamily=new LinkedHashMap<String,Object>();var family=map(metadata.getOrDefault("resolved_family",definition.getOrDefault("family",Map.of())));
        for(String key:List.of("id","coverage","adjustment","tested_count","alpha","adjusted_p_from_full_family","adjusted_values_origin"))if(family.containsKey(key))safeFamily.put(key,family.get(key));
        method.put("parameters",Map.of("source_role",definition.get("role"),"family",safeFamily));
        method.put("interpretation", "The import and integrity checks ran locally; the external statistical analysis was not rerun.");
        metadata.put("method_report", method);
        List<Object> typedNeeds = new ArrayList<>();
        for (String need : needs) typedNeeds.add(StudyNeed.distinctReview("supplied-result:" + context.operation().get("hypothesis") + ":" + definition.get("id"),
            "What is needed to reuse supplied analysis " + definition.get("id") + "?", need, true).document());
        metadata.put("needs", typedNeeds);
        metadata.put("interpretation", "Completed import validation is distinct from independent statistical or scientific reproduction. Sensitivity evidence cannot replace a primary result; unavailable upstream inputs remain unavailable.");
        write(context.outputDirectory().resolve("supplied-analysis.yaml"), metadata); outputs.add("supplied-analysis.yaml");
        return new Result(needs.isEmpty() ? State.COMPLETE : State.WAITING_FOR_INPUT,
            needs.isEmpty() ? partial ? "Retained partial supplied " + kind + "; completion is separate from import validation" : "Imported supplied " + kind + " without rerunning its analysis" : "Retained supplied " + kind + "; analysis completion or scope needs input", outputs, metadata);
    }

    private record Table(List<String> header, List<String[]> rows,Map<String,Object> aggregateMetadata) { }
    private static void partialNetworkCoverage(Context c, Map<String,Object> definition, Map<String,Object> metadata, List<String> needs) {
        var datasets=new LinkedHashMap<String,Map<String,Object>>();
        for(Object raw:list(c.study().get("datasets"))){var d=map(raw);if(!"retired".equals(d.get("status")))datasets.put(text(d.get("id")),d);}
        var selected=new LinkedHashSet<String>();
        for(Object raw:list(c.study().get("hypotheses"))){var h=map(raw);if(!h.get("id").equals(c.operation().get("hypothesis")))continue;
            for(Object value:map(h.get("inputs")).values())for(Object id:list(value))selected.add(text(id));}
        if(selected.isEmpty())selected.addAll(datasets.keySet());
        var target=org.jlinalg.study.StudyNetworkReuse.declaredCoverage(definition,datasets,List.copyOf(selected));
        var completed=org.jlinalg.study.StudyNetworkReuse.coverage(StudyResultTables.analysis(c),datasets,List.copyOf(selected));
        var missing=new LinkedHashSet<>(target.covered());missing.removeAll(completed.covered());
        for(Object raw:list(c.plan().get("operations"))){var op=map(raw);if("network_analysis".equals(op.get("type"))&&c.operation().get("hypothesis").equals(op.get("hypothesis"))&&!op.containsKey("supplied_result_id"))missing.remove(text(op.get("dataset_id")));}
        metadata.put("partial_network_requested_datasets",target.covered().stream().sorted().toList());
        metadata.put("partial_network_unscheduled_datasets",missing.stream().sorted().toList());
        if(!target.resolved())needs.add("The partial graph's population cannot be matched to registered datasets/cohorts. Declare its scope before native work can establish completion of this graph.");
        else if(!missing.isEmpty())needs.add("The partial graph still needs completion for datasets "+String.join(", ",missing)+". Native graphs from other datasets do not complete this source scope; supply these matrices or a completed scoped graph.");
    }
    private static Table read(Path path, Map<String, Object> definition, String role, String kind) throws IOException {
        if(Set.of("network_modules","module_gene_ontology").contains(kind)&&Files.size(path)>134217728L)throw new IOException("Supplied module table exceeds the 128 MiB import bound");
        var input = DelimitedData.readResult(path);
        var configured = map(definition.get("columns"));
        var mapping = configured.get(role) instanceof Map<?, ?> ? map(configured.get(role)) : configured;
        if(mapping==configured&&configured.entrySet().stream().anyMatch(e->!e.getKey().equals("result_metadata")&&e.getValue() instanceof Map<?,?>)){
            mapping=new LinkedHashMap<>();for(var entry:configured.entrySet())if(entry.getValue() instanceof String||entry.getKey().equals("result_metadata"))mapping.put(entry.getKey(),entry.getValue());
        }
        var header = new ArrayList<>(input.header());
        for (var entry : mapping.entrySet()) {
            if(entry.getKey().equals("result_metadata")){if(!(entry.getValue() instanceof Map<?,?>))throw new IOException("columns.result_metadata must be a mapping");continue;}
            if (!(entry.getValue() instanceof String from)) throw new IOException("Supplied column mappings must name source columns");
            int column = input.column(from);
            if (header.contains(entry.getKey()) && !header.get(column).equals(entry.getKey())) throw new IOException("Ambiguous supplied column mapping for " + entry.getKey());
            header.set(column, entry.getKey());
        }
        if (new LinkedHashSet<>(header).size() != header.size()) throw new IOException("Duplicate logical supplied result columns");
        var rows = input.rows().stream().map(String[]::clone).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if(Set.of("network_modules","module_gene_ontology").contains(kind))StudySuppliedModules.normalize(header,rows,kind);
        var required = switch (kind) {
            case "meta_analysis", "discovery_meta_analysis" -> List.of("feature_id", "beta", "se", "p_value");
            case "gene_ontology", "pathway_enrichment", "ranked_gsea", "gwas_enrichment" -> List.of("term_id", "p_value");
            case "mendelian_randomization" -> List.of("feature_id", "beta", "se", "p_value");
            case "rare_variant_analysis" -> List.of("feature_id", "mask_id", "test", "p_value", "is_primary_test");
            case "rare_set_omnibus" -> List.of("feature_id", "p_value");
            case "colocalization" -> List.of("feature_id", "region_id", "pp_h4");
            case "fine_mapping" -> List.of("feature_id", "variant_id", "pip");
            case "subgroup_interactions" -> List.of("feature_id","dataset_id","group_id","reference_level","comparison_level","beta","se","p_value");
            case "discovery_replication" -> List.of("feature_id", "discovery_selected", "replicated");
            case "effect_comparison" -> List.of("left", "right", "n_matched", "correlation");
            case "gwas_diagnostics" -> StudyGwasDiagnostics.SUMMARY_COLUMNS;
            case "network_preservation" -> StudyNetworkPreservation.COLUMNS;
            case "network_preservation_null" -> StudyNetworkNull.COLUMNS;
            case "network_nodes" -> List.of("node_id");
            case "network_edges" -> List.of("source", "target");
            case "network_modules" -> List.of("node_id", "module_id", "module_type");
            case "module_gene_ontology" -> List.of("module_id", "module_type", "term_id", "p_value");
            case "risk_score_related_phenotypes" -> List.of("score_id","phenotype","evaluation_population","beta","se","p_value");
            case "risk_score_evaluation" -> List.of("score_id", "phenotype", "evaluation_population");
            case "variant_annotation", "variant_scoring" -> List.of("variant_id");
            case "protein_structure" -> List.of("protein_id", "sequence_sha256");
            case "cohort_overlap" -> List.of("feature_id","set_id","in_universe","source_estimable","selected");
            case "rare_cohort_overlap" -> List.of("feature_id","mask_id","test","set_id","in_universe","source_estimable","selected");
            case "heterogeneity_review" -> List.of("feature_id","metric","metric_value","in_alert_scope","high_heterogeneity");
            default -> throw new IOException("No typed table adapter for supplied " + kind);
        };
        if(Set.of("network_modules","module_gene_ontology").contains(kind)&&header.contains("network_id")){required=new ArrayList<>(required);required.add("network_id");}
        for (String field : required) if (!header.contains(field)) throw new IOException("Supplied " + kind + " requires logical column " + field + "; declare columns mappings where needed");
        if(Set.of("gene_ontology","pathway_enrichment").contains(kind)&&header.contains("mask_id")&&!header.contains("enrichment_id")){
            int mask=header.indexOf("mask_id"),term=header.indexOf("term_id");append(header,rows,"enrichment_id","");for(String[] row:rows)row[header.indexOf("enrichment_id")]="rare-mask-"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(row[mask].getBytes(java.nio.charset.StandardCharsets.UTF_8))+":"+row[term];
        }
        if(kind.equals("rare_set_omnibus")) StudySuppliedRareOmnibus.normalize(header, rows, definition);
        var identities = new LinkedHashSet<String>();var interactionContrasts=new LinkedHashMap<String,List<String>>();
        for (String[] row : rows) {
            var identity = new ArrayList<String>();
            for (String field : required) if (!NUMERIC.contains(field) && !Set.of("beta", "se", "p_value", "discovery_selected", "replicated", "is_primary_test").contains(field)) {
                String value = row[header.indexOf(field)]; if (value.isBlank()) throw new IOException("Empty supplied " + field);
                identity.add(value);
            }
            if(kind.equals("rare_set_omnibus"))identity.add(row[header.indexOf("scope")]);
            if(Set.of("gene_ontology","pathway_enrichment").contains(kind)&&header.contains("enrichment_id")){String identityValue=row[header.indexOf("enrichment_id")];if(identityValue.isBlank()||identityValue.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid supplied enrichment identity");identity.clear();identity.add(identityValue);}
            if(kind.equals("protein_structure")){for(String dimension:List.of("feature_id","model_id","structure_id","chain"))if(header.contains(dimension))identity.add(row[header.indexOf(dimension)]);if(!row[header.indexOf("sequence_sha256")].matches("[a-fA-F0-9]{64}"))throw new IOException("Supplied protein structure needs an exact SHA-256 sequence hash");for(String score:List.of("plddt","mean_plddt"))if(header.contains(score)&&!missing(row[header.indexOf(score)])){double value=Double.parseDouble(row[header.indexOf(score)]);if(!Double.isFinite(value)||value<0||value>100)throw new IOException("Supplied pLDDT outside [0,100]");}}
            if(kind.equals("risk_score_evaluation"))for(String dimension:List.of("metric","subgroup_column","subgroup"))if(header.contains(dimension))identity.add(row[header.indexOf(dimension)]);
            if(kind.equals("gwas_diagnostics")){identity.clear();for(String field:List.of("source_id","contrast","stratum"))identity.add(row[header.indexOf(field)]);}
            if(kind.equals("network_preservation")){identity.clear();for(String field:StudyNetworkPreservation.COLUMNS.subList(0,4))identity.add(row[header.indexOf(field)]);StudyNetworkPreservation.validateSupplied(header,row,definition);}
            if(kind.equals("network_preservation_null")){identity.clear();for(String field:StudyNetworkNull.COLUMNS.subList(0,5))identity.add(row[header.indexOf(field)]);}
            if(kind.equals("cohort_overlap")){identity.clear();identity.add(row[header.indexOf("feature_id")]);identity.add(row[header.indexOf("set_id")]);}
            if(kind.equals("rare_cohort_overlap")){identity.clear();identity.add(StudyRareCohortOverlap.key(row[header.indexOf("feature_id")],row[header.indexOf("mask_id")],row[header.indexOf("test")]).id());identity.add(row[header.indexOf("set_id")]);}
            if(kind.equals("heterogeneity_review")){identity.clear();identity.add(row[header.indexOf("feature_id")]);identity.add(row[header.indexOf("metric")]);}
            if(kind.equals("colocalization"))StudySuppliedColocalization.validate(header,row,definition,identity);
            if(StudySuppliedVariantRows.applies(kind))StudySuppliedVariantRows.validate(header,row,identity);
            if(kind.equals("gwas_enrichment"))StudyGwasAnnotationColumns.validate(header,row);
            if(kind.equals("subgroup_interactions")){identity.clear();for(String field:List.of("dataset_id","group_id","feature_id"))identity.add(row[header.indexOf(field)]);StudySubgroupInteractions.validateSupplied(header,row);String scope=StudyAssistance.json(identity.subList(0,2));var levels=List.of(row[header.indexOf("reference_level")],row[header.indexOf("comparison_level")]);var previous=interactionContrasts.putIfAbsent(scope,levels);if(previous!=null&&!previous.equals(levels))throw new IOException("A supplied subgroup ID has inconsistent contrast orientation across features");}
            if(kind.equals("subgroup_interactions"))for(int i=0;i<header.size();i++)if(StudySubgroupInteractions.COLUMNS.contains(header.get(i))&&(row[i].length()>16000||row[i].chars().anyMatch(Character::isISOControl)))throw new IOException("Unbounded supplied interaction context");
            if (!identity.isEmpty() && !identities.add(String.join("\u001f", identity))) throw new IOException("Duplicate scoped supplied result identity: " + identity);
            for (int i = 0; i < header.size(); i++) if ((NUMERIC.contains(header.get(i))||kind.equals("gwas_enrichment")&&GWAS_NUMERIC.contains(header.get(i))||GSEA_COUNTS.contains(header.get(i))||GSEA_PROBABILITIES.contains(header.get(i))) && !missing(row[i])) {
                double value;
                try { value = Double.parseDouble(row[i]); } catch (NumberFormatException invalid) { throw new IOException("Non-numeric supplied " + header.get(i)); }
                if (!Double.isFinite(value) || (PROBABILITIES.contains(header.get(i))||GSEA_PROBABILITIES.contains(header.get(i))) && (value < 0 || value > 1)
                        || GSEA_COUNTS.contains(header.get(i)) && (value<0||value!=Math.rint(value))
                        || header.get(i).equals("se") && value <= 0) throw new IOException("Invalid supplied " + header.get(i));
            }
            for (String field : List.of("discovery_selected", "replicated", "is_primary_test", "in_universe", "source_estimable", "selected", "in_alert_scope", "high_heterogeneity")) if (header.contains(field)) {
                int column = header.indexOf(field);
                if(field.equals("high_heterogeneity")&&kind.equals("heterogeneity_review")&&header.contains("in_alert_scope")&&row[header.indexOf("in_alert_scope")].equalsIgnoreCase("false")&&missing(row[column]))continue;
                if (!Set.of("true", "false").contains(row[column].toLowerCase(Locale.ROOT))) throw new IOException("Supplied " + field + " must be true or false");
                row[column] = row[column].toLowerCase(Locale.ROOT);
            }
        }
        if(Set.of("cohort_overlap","rare_cohort_overlap").contains(kind))for(String[] row:rows)if(row[header.indexOf("selected")].equals("true")&&(!row[header.indexOf("source_estimable")].equals("true")||!row[header.indexOf("in_universe")].equals("true")))throw new IOException("A selected supplied overlap feature must be estimable and inside its declared universe");
        if (Set.of("meta_analysis", "discovery_meta_analysis").contains(kind)) {
            if (!header.contains("term")) append(header, rows, "term", "pooled");
            if (!header.contains("status")) append(header, rows, "status", "ok");
        }
        if (kind.equals("rare_variant_analysis")) {
            if (!header.contains("scope")) append(header, rows, "scope", "primary");
            if (!header.contains("status")) append(header, rows, "status", "ok");
            if (!header.contains("result_type")) {
                if (header.contains("beta") || header.contains("se")) throw new IOException("Signed supplied set tests require an explicit result_type; P-only sets do not acquire a beta or direction");
                append(header, rows, "result_type", "unsigned_set");
            }
            var primaryMasks = new LinkedHashSet<String>(); var masks = new LinkedHashSet<String>();
            for (String[] row : rows) {
                String mask = row[header.indexOf("feature_id")] + "\u001f" + row[header.indexOf("mask_id")]; masks.add(mask);
                if (row[header.indexOf("is_primary_test")].equalsIgnoreCase("true") && !primaryMasks.add(mask)) throw new IOException("A supplied set/mask has multiple primary tests; declare the prespecified primary rather than selecting P values");
            }
            if (!primaryMasks.equals(masks)) throw new IOException("Every supplied set/mask needs its prespecified primary test row, including failed primary tests");
        }
        if(kind.equals("gwas_diagnostics"))for(String[] row:rows){
            for(String field:List.of("source_id","contrast","stratum")){String label=row[header.indexOf(field)];if(label.length()>512||label.chars().anyMatch(Character::isISOControl))throw new IOException("Supplied diagnostic identities must be bounded scientific labels");}
            if(!Set.of("complete","unknown","selected_only","selected_only_or_unknown","incomplete_export","incomplete_producer").contains(row[header.indexOf("testing_coverage")]))throw new IOException("Unknown supplied diagnostic export coverage");
            if(!Set.of("descriptive_P_transform","unavailable_incomplete_or_unknown_export","unavailable_too_few_tests","unavailable_numeric_transform","unavailable_no_eligible_tests").contains(row[header.indexOf("lambda_status")]))throw new IOException("Unknown supplied lambda disposition");
            if(!Set.of("not_requested_for_stratum","held_mixed_genome_builds","held_coordinate_range","held_contributor_coordinate_conflicts","unavailable_coordinates","partial_coordinates","observed_coordinates","observed_coordinates_build_unknown").contains(row[header.indexOf("coordinate_status")]))throw new IOException("Unknown supplied coordinate disposition");
            for(String field:List.of("observed_tests","diagnostic_tests","unresolved_tests","coordinate_tests")){double value=Double.parseDouble(row[header.indexOf(field)]);if(!Double.isFinite(value)||value<0||value!=Math.rint(value))throw new IOException("Supplied diagnostic counts must be finite nonnegative integers");}
            String value=row[header.indexOf("lambda_p_chisq1")];if(!missing(value)&&(!Double.isFinite(Double.parseDouble(value))||Double.parseDouble(value)<0))throw new IOException("Supplied lambda must be finite and nonnegative");
            double tested=Double.parseDouble(row[header.indexOf("diagnostic_tests")]),observed=Double.parseDouble(row[header.indexOf("observed_tests")]),unresolved=Double.parseDouble(row[header.indexOf("unresolved_tests")]),coordinates=Double.parseDouble(row[header.indexOf("coordinate_tests")]);
            if(tested+unresolved>observed||coordinates>tested)throw new IOException("Supplied diagnostic counts have incompatible scopes");
            boolean available=row[header.indexOf("lambda_status")].equals("descriptive_P_transform");
            if(available&&(missing(value)||!row[header.indexOf("testing_coverage")].equals("complete")||unresolved>0||tested<2)||!available&&!missing(value))throw new IOException("Supplied lambda conflicts with its completeness/disposition declarations");
        }
        List<Integer> retained = new ArrayList<>();
        for (int i=0;i<header.size();i++) if (NUMERIC.contains(header.get(i)) || kind.equals("gwas_enrichment")&&(GWAS_NUMERIC.contains(header.get(i))||StudyGwasAnnotationColumns.LABELS.contains(header.get(i))) || LABELS.contains(header.get(i))||GSEA_COUNTS.contains(header.get(i))||GSEA_PROBABILITIES.contains(header.get(i))||GSEA_LABELS.contains(header.get(i))||StudySuppliedVariantRows.applies(kind)&&StudySuppliedVariantRows.retain(header.get(i))||kind.equals("colocalization")&&StudySuppliedColocalization.retain(header.get(i))||kind.equals("subgroup_interactions")&&StudySubgroupInteractions.COLUMNS.contains(header.get(i))||"contribution".equals(definition.get("role"))&&header.get(i).equals("reason")) retained.add(i);
        if(kind.equals("gwas_diagnostics")){retained.clear();for(int i=0;i<header.size();i++)if(StudyGwasDiagnostics.SUMMARY_COLUMNS.contains(header.get(i)))retained.add(i);}
        if(kind.equals("network_preservation")){retained.clear();for(int i=0;i<header.size();i++)if(StudyNetworkPreservation.COLUMNS.contains(header.get(i)))retained.add(i);}
        if(kind.equals("network_preservation_null")){StudyNetworkNull.validateSupplied(header,rows,definition);retained.clear();for(int i=0;i<header.size();i++)if(StudyNetworkNull.COLUMNS.contains(header.get(i)))retained.add(i);}
        if(kind.equals("rare_set_omnibus")){retained.clear();for(int i=0;i<header.size();i++)if(StudyRareOmnibus.COLUMNS.contains(header.get(i)))retained.add(i);}
        List<String> logical = retained.stream().map(header::get).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<String[]> normalized = new ArrayList<>();
        for (String[] row : rows) normalized.add(retained.stream().map(i -> row[i]).toArray(String[]::new));
        Map<String,Object> aggregateAudit=Map.of();
        if(Set.of("meta_analysis","discovery_meta_analysis","mendelian_randomization","rare_variant_analysis","risk_score_evaluation","risk_score_related_phenotypes").contains(kind)) {
            if(configured.containsKey("result_metadata")&&!(configured.get("result_metadata") instanceof Map<?,?>))throw new IOException("columns.result_metadata must be a mapping");
            var explicit=new LinkedHashMap<String,Object>(map(configured.get("result_metadata")));
            if(mapping!=configured)for(var field:map(mapping.get("result_metadata")).entrySet()){var previous=explicit.putIfAbsent(field.getKey(),field.getValue());if(previous!=null&&!previous.equals(field.getValue()))throw new IOException("Conflicting explicit metadata mappings for "+field.getKey());}
            for(String key:org.jlinalg.study.StudyResultMetadataPolicy.names()) {
                if(mapping.get(key) instanceof String original){var previous=explicit.putIfAbsent(key,original);if(previous!=null&&!previous.equals(original))throw new IOException("Conflicting explicit metadata mappings for "+key);}
                else if(LABELS.contains(key)&&input.header().contains(key))explicit.putIfAbsent(key,key);
            }
            var aggregate=new StudyResultMetadata(input,Map.of("result_metadata",explicit));
            for(String key:aggregate.header())if(!logical.contains(key))logical.add(key);
            for(int index=0;index<normalized.size();index++) {
                var values=aggregate.values(input.rows().get(index));aggregate.retained(values);String[] row=java.util.Arrays.copyOf(normalized.get(index),logical.size());
                for(int field=0;field<aggregate.header().size();field++)row[logical.indexOf(aggregate.header().get(field))]=values.get(field);
                normalized.set(index,row);
            }
            aggregateAudit=aggregate.audit();
        }
        return new Table(logical, normalized,aggregateAudit);
    }

    private static Map<String, Object> family(Table table, Map<String, Object> definition, List<String> needs) throws IOException {
        var family = new LinkedHashMap<>(map(definition.get("family")));
        if (!STATISTICAL.contains(definition.get("kind"))) return family;
        String id = table.header.contains("enrichment_id")?"enrichment_id":table.header.contains("feature_id") ? "feature_id" : table.header.contains("score_id")?"phenotype":"term_id";
        var probabilities = new LinkedHashMap<String, Double>();
        for (String[] row : table.rows) if (!missing(row[table.header.indexOf("p_value")]))
            probabilities.put(testIdentity(table,row,id), Double.parseDouble(row[table.header.indexOf("p_value")]));
        boolean complete = "complete".equals(family.get("coverage"));
        boolean expectsSourceAdjustment = Boolean.TRUE.equals(family.get("adjusted_p_from_full_family"));
        if (expectsSourceAdjustment && !table.header.contains("adjusted_p")) {
            needs.add("Declared source full-family adjustment requires logical adjusted_p; supply its column mapping instead of silently recomputing it");
            family.put("adjusted_values_origin", "declared_source_adjustment_missing");
            return family;
        }
        boolean sourceAdjusted = table.header.contains("adjusted_p") && (expectsSourceAdjustment || complete && !Boolean.FALSE.equals(family.get("adjusted_p_from_full_family")));
        long denominator = family.get("tested_count") instanceof Number n ? n.longValue() : -1;
        if (sourceAdjusted && denominator < probabilities.size()) needs.add("Source-adjusted P values require a declared tested_count covering all supplied tests");
        else if (!sourceAdjusted && (!complete || denominator != probabilities.size()))
            needs.add("Automatic selection requires full-family adjusted_p with tested_count, or complete P values whose declared tested_count matches the estimable family. Significant-only rows cannot define a new FDR family.");
        else if(!sourceAdjusted&&definition.get("kind").equals("module_gene_ontology")) {
            String adjustment=text(family.getOrDefault("adjustment","BH")).toUpperCase(Locale.ROOT);StudySuppliedModules.adjust(table.header,table.rows,adjustment,needs);family.put("adjustment",adjustment);
        }
        else if (!sourceAdjusted) {
            String adjustment = text(family.getOrDefault("adjustment", "BH")).toUpperCase(Locale.ROOT);
            Map<String, Double> adjusted = switch (adjustment) {
                case "BH" -> StudyCliExecutor.bh(probabilities);
                case "BY" -> StudyCohortResults.adjust(probabilities, "BY");
                case "NONE" -> probabilities;
                case "BONFERRONI" -> probabilities.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> Math.min(1.0, e.getValue() * denominator), (a,b) -> a, LinkedHashMap::new));
                default -> Map.of();
            };
            if (adjusted.isEmpty() && !probabilities.isEmpty()) needs.add("A PROVIDED adjustment requires source adjusted_p values and their full-family declaration");
            else {
                if (!table.header.contains("adjusted_p")) append(table.header, table.rows, "adjusted_p", "NA");
                for (String[] row : table.rows) row[table.header.indexOf("adjusted_p")] = adjusted.containsKey(testIdentity(table,row,id)) ? String.valueOf(adjusted.get(testIdentity(table,row,id))) : "NA";
                family.put("adjustment", adjustment);
            }
        }
        if (sourceAdjusted) for (String[] row : table.rows) if (!missing(row[table.header.indexOf("p_value")]) && missing(row[table.header.indexOf("adjusted_p")]))
            needs.add("Every estimable supplied test requires its declared full-family adjusted_p; missing values are not zero");
        family.put("adjusted_values_origin", sourceAdjusted ? "supplied_full_family" : "computed_from_declared_complete_family");
        family.put("supplied_estimable_tests", probabilities.size());
        return family;
    }
    private static String testIdentity(Table table,String[] row,String id) {
        if(table.header.contains("module_id")&&table.header.contains("module_type")&&table.header.contains("term_id"))return StudySuppliedModules.identity(table.header,row);
        if(table.header.contains("group_id")&&table.header.contains("dataset_id"))return StudyAssistance.json(List.of(row[table.header.indexOf("dataset_id")],row[table.header.indexOf("group_id")],row[table.header.indexOf(id)]));
        if(table.header.contains("score_id")&&table.header.contains("phenotype"))return StudyAssistance.json(List.of(row[table.header.indexOf("score_id")],row[table.header.indexOf("phenotype")],row[table.header.indexOf("evaluation_population")]));
        if(table.header.contains("mask_id") && table.header.contains("test"))
            return StudyAssistance.json(List.of(row[table.header.indexOf(id)],row[table.header.indexOf("mask_id")],row[table.header.indexOf("test")]));
        return row[table.header.indexOf(id)];
    }

    private static void network(Context context, Map<String, Object> definition, Map<String, Path> snapshots,
            Map<String, Object> metadata, List<String> outputs) throws IOException {
        String prefix = StudySuppliedResultPolicy.partialPrimary(definition) ? "supplied-partial-" : "";
        Table nodes = read(snapshots.get("nodes"), definition, "nodes", "network_nodes");
        Table edges = read(snapshots.get("edges"), definition, "edges", "network_edges");
        Set<String> ids = nodes.rows.stream().map(row -> row[nodes.header.indexOf("node_id")]).collect(java.util.stream.Collectors.toSet());
        for (String[] edge : edges.rows) if (!ids.contains(edge[edges.header.indexOf("source")]) || !ids.contains(edge[edges.header.indexOf("target")]))
            throw new IOException("A supplied edge references a node absent from files.nodes");
        FollowupSupport.table(context.outputDirectory().resolve(prefix + "network-nodes.tsv"), nodes.header, nodes.rows);
        FollowupSupport.table(context.outputDirectory().resolve(prefix + "network-edges.tsv"), edges.header, edges.rows);
        outputs.addAll(List.of(prefix + "network-nodes.tsv", prefix + "network-edges.tsv"));
        metadata.put("nodes", nodes.rows.size()); metadata.put("edges", edges.rows.size());
        if (snapshots.containsKey("modules")) {
            Table modules = read(snapshots.get("modules"), definition, "modules", "network_modules");
            for (String[] row : modules.rows) if (!ids.contains(row[modules.header.indexOf("node_id")])) throw new IOException("Supplied module membership references an unknown node");
            FollowupSupport.table(context.outputDirectory().resolve(prefix + "network-modules.tsv"), modules.header, modules.rows); outputs.add(prefix + "network-modules.tsv");
        }
        write(context.outputDirectory().resolve(prefix + "network-analysis.yaml"), metadata); outputs.add(prefix + "network-analysis.yaml");
    }

    private static void annotation(Context context, Map<String, Object> definition, Path source, List<String> outputs) throws IOException {
        var table = DelimitedData.readResult(source);
        if (!table.header().contains("feature_id")) throw new IOException("Supplied feature_annotation requires feature_id");
        List<String> columns = List.of("feature_id", "gene_id", "gene_symbol", "chromosome", "start", "end", "genome_build", "mapping_status");
        var rows = new ArrayList<String[]>(); var ids = new LinkedHashSet<String>();
        for (String[] row : table.rows()) {
            String id = row[table.column("feature_id")]; if (id.isBlank() || !ids.add(id)) throw new IOException("Supplied feature annotations must have unique nonempty feature_id");
            String[] normalized = new String[columns.size()];
            for (int i=0;i<columns.size();i++) normalized[i]=table.header().contains(columns.get(i))?row[table.column(columns.get(i))]:"";
            String coordinates = text(map(definition.get("scope")).get("coordinate_system"));
            if (!normalized[4].isBlank() || !normalized[5].isBlank()) {
                if (!Set.of("1-based-closed", "0-based-half-open").contains(coordinates))
                    throw new IOException("Supplied feature locations require scope.coordinate_system: 1-based-closed or 0-based-half-open");
                long start, end;
                try { start=Long.parseLong(normalized[4]); end=Long.parseLong(normalized[5]); }
                catch (NumberFormatException invalid) { throw new IOException("Invalid supplied feature interval: " + id); }
                if (coordinates.equals("0-based-half-open")) start++;
                if (start < 1 || end < start) throw new IOException("Invalid supplied feature interval: " + id);
                normalized[4]=Long.toString(start); normalized[5]=Long.toString(end);
            }
            String declaredBuild = text(map(definition.get("scope")).get("genome_build"));
            if (!normalized[6].isBlank() && !declaredBuild.isBlank() && !normalized[6].equals(declaredBuild)) throw new IOException("Supplied feature annotation build conflicts with its analysis scope");
            if (normalized[6].isBlank()) normalized[6]=declaredBuild;
            if (normalized[7].isBlank()) normalized[7] = normalized[3].isBlank() || normalized[4].isBlank() || normalized[5].isBlank() ? "missing_location" : "located";
            rows.add(normalized);
        }
        FollowupSupport.table(context.outputDirectory().resolve("feature-annotation.tsv"), columns, rows); outputs.add("feature-annotation.tsv");
    }

    private static void genetic(Context context, String kind, Table table, Map<String, Object> metadata, List<String> outputs) throws IOException {
        var summary = new LinkedHashMap<>(metadata);
        summary.put("result_rows", table.rows.size()); summary.put("pairs", List.of());
        summary.put("full_results_artifact", metadata.get("canonical_results"));
        summary.put("interpretation", "Externally produced genetic results; source/method declarations are retained. No instruments, LD, regional statistics, model calibration or causal claims were independently reconstructed.");
        if (kind.equals("colocalization")) {
            String grade = text(metadata.get("evidence_grade"));
            if (grade.isBlank() || grade.equals("{}")) grade = "source_coverage_unverified";
            summary.put("evidence_grade", grade);
            if (grade.equals("exploratory_partial_region_major_limitation")) {
                String caveat = "Colocalization posterior probabilities are conditional on the supplied selected variants. Incomplete regional coverage can bias posteriors; a null result is not evidence against a shared causal variant. " + metadata.get("required_manuscript_caveat");
                summary.put("required_manuscript_caveat", caveat); metadata.put("required_manuscript_caveat", caveat);
            }
        }
        write(context.outputDirectory().resolve("genetic-evidence.yaml"), summary); outputs.add("genetic-evidence.yaml");
    }

    private static void append(List<String> header, List<String[]> rows, String column, String value) {
        header.add(column);
        for (int i=0;i<rows.size();i++) {
            String[] before=rows.get(i), after=java.util.Arrays.copyOf(before,before.length+1); after[before.length]=value; rows.set(i,after);
        }
    }
    private static boolean missing(String value) { return value.isBlank() || Set.of("na", "nan", "null", ".").contains(value.toLowerCase(Locale.ROOT)); }
}
