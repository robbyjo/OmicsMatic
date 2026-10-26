/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import static org.jlinalg.cli.StudyAssistance.json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Full local inventory; AI payloads contain aggregate result fields, never participant files. */
final class StudyFindingInventory implements StudyOperationExecutor {
    private static final Set<String> TABLE_TYPES = Set.of("validate_harmonize", "association", "meta_analysis", "discovery_meta_analysis",
        "discovery_replication", "rare_variant_analysis", "rare_set_omnibus", "gene_ontology", "pathway_enrichment", "ranked_gsea", "gwas_enrichment", "sensitivity_analysis", "fine_mapping", "colocalization", "mendelian_randomization");
    private static final Set<String> FIELDS = Set.of("unit_type", "expected_components", "component_count", "ineligible_components", "unresolved_components", "effective_method", "source_set_ids", "log_adjusted_p", "feature_id", "mask_id", "test", "scope", "term", "status", "result_type", "beta", "se", "p_value", "FDR", "adjusted_p",
        "ci_lower", "ci_upper", "n", "N", "n_samples", "n_cases", "n_controls", "log_p_value", "statistic", "null_calibration", "allocation_states", "pilot_draws", "draws", "tail_draws", "p_resolution", "tail_probability_lower_95", "tail_probability_upper_95", "constant_evaluated_null", "n_cohorts", "n_sources", "n_unique_declared_cohorts", "q_definition", "n_variants", "direction", "i_squared", "tau_squared", "q", "q_df", "q_statistic", "q_p_value", "weights", "calibration",
        "replicated", "same_sign", "discovery_p", "discovery_adjusted_p", "validation_p", "validation_adjusted_p", "coverage", "p_adj", "term_id", "term_name",
        "primary_adjusted_p", "sensitivity_adjusted_p", "effect_direction_changed", "significance_changed", "filtered_present", "unfiltered_present",
        "pp_h0", "pp_h1", "pp_h2", "pp_h3", "pp_h4", "pip", "region_id", "variant_id", "evidence_grade", "required_manuscript_caveat");
    record Artifact(Path directory, Map<String, Object> manifest, String hypothesis, String type) { }
    private final Map<String, Map<String, Object>> sources = new TreeMap<>();
    private final Map<String, Map<String, Object>> findings = new TreeMap<>();
    private final Map<String, List<Object>> observations = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> annotations = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
    private final Map<String, StudyPrimaryResults.Selection> primarySelections = new LinkedHashMap<>();
    private final Set<String> unresolvedPrimaries = new LinkedHashSet<>();
    private Context context;

    @Override public Result execute(Context value) throws IOException {
        context = value; sources.clear(); findings.clear(); observations.clear(); annotations.clear(); groups.clear(); primarySelections.clear(); unresolvedPrimaries.clear(); Files.createDirectories(context.outputDirectory());
        List<Artifact> artifacts = new ArrayList<>(); List<Object> unavailable = new ArrayList<>();
        for (var entry : context.dependencyDirectories().entrySet()) {
            Path path = entry.getValue().resolve("manifest.yaml");
            if (!Files.isRegularFile(path)) throw new IOException("Missing finding-inventory dependency manifest: " + entry.getKey());
            var manifest = StudyYaml.loadArtifact(path); String type = text(manifest.get("operation_type")), hypothesis = text(manifest.get("hypothesis"));
            // Verify every sealed output, including caveats in incomplete source searches.
            for (var output : map(manifest.get("output_sha256")).entrySet()) verified(entry.getValue(), output.getKey(), text(output.getValue()));
            var artifact = new Artifact(entry.getValue(), manifest, hypothesis, type); artifacts.add(artifact);
            for (String output : map(manifest.get("output_sha256")).keySet()) source(artifact, output);
            if (!"complete".equals(manifest.get("state")) && !"not_applicable".equals(manifest.get("state")))
                unavailable.add(Map.of("operation_id", manifest.get("operation_id"), "hypothesis", hypothesis, "type", type, "state", manifest.get("state"), "summary", manifest.getOrDefault("summary", "")));
        }
        for (Object raw : list(context.study().get("hypotheses"))) {
            var h = map(raw); if ("retired".equals(h.get("status"))) continue;
            String id = text(h.get("id"));
            var op = new LinkedHashMap<String,Object>(context.operation()); op.put("hypothesis", id);
            var inputs = new LinkedHashSet<String>(); map(h.get("inputs")).values().forEach(ids -> list(ids).forEach(v -> inputs.add(text(v))));
            op.put("datasets", List.copyOf(inputs));
            var scoped = new Context(context.project(), context.outputDirectory(), op, context.study(), context.plan(), context.dependencyDirectories());
            try {
                var selected = StudyPrimaryResults.official(scoped);
                if (selected != null) primarySelections.put(id, selected);
            } catch (StudyPrimaryResults.Unavailable blocked) {
                unresolvedPrimaries.add(id); unavailable.add(blocked.receipt());
            }
        }
        readGroups();
        for (Artifact artifact : artifacts) readAnnotations(artifact);
        for (Artifact artifact : artifacts) readTables(artifact);
        for (Artifact artifact : artifacts) readContext(artifact);
        for (Artifact artifact : artifacts) genotypeQcContext(artifact);
        for (Artifact artifact : artifacts) assayQcContext(artifact);
        for (Artifact artifact : artifacts) survivalContext(artifact);
        var literatureCoverage=literatureCoverage(artifacts);
        var mrFamilies=mrFamilyContext(artifacts);
        var ldGrouping=StudyFindingGrouping.apply(context,findings,observations,groups,sources,primarySelections);
        List<String[]> inventory = new ArrayList<>();
        try (var writer = Files.newBufferedWriter(context.outputDirectory().resolve("finding-inventory.jsonl"))) {
            for (var finding : findings.values()) {
                String key = key(text(finding.get("hypothesis")), text(finding.get("feature_id")));
                var annotation = annotations.getOrDefault(key, Map.of()); finding.put("annotation", annotation);
                var evidence=new ArrayList<Object>(observations.getOrDefault(key,List.of()));
                var coverage=literatureCoverage.forFinding(text(finding.get("hypothesis")),text(finding.get("feature_id")),annotation);
                if(!coverage.isEmpty()){
                    finding.put("literature_coverage",coverage);var observed=new LinkedHashMap<String,Object>();
                    for(String field:List.of("query_evidence_state","unique_queries","reported_complete_queries","incomplete_or_unknown_queries","source_reported_queries","native_retrieval_or_cache_queries","duplicate_reports_collapsed","systematic_review_established","source_support_established","scientific_appraisal","source_ids","required_manuscript_caveat"))observed.put(field,coverage.get(field));
                    observed.put("kind","literature_coverage");observed.put("primary",false);observed.put("native_allocation_statuses",list(coverage.get("native_allocations")).stream().map(raw->map(raw).get("status")).distinct().toList());evidence.add(observed);
                }
                finding.put("observations",evidence);
                var declared = groups.getOrDefault(key, Map.of());
                String gene = text(annotation.get("gene_id"));
                String group = text(declared.get("group_id"));
                if (group.isBlank()) group = gene.isBlank() ? "feature:" + finding.get("feature_id") : "gene:" + gene;
                finding.put("story_group", group); finding.put("group_basis", declared.isEmpty() ? gene.isBlank() ? "feature_identity_only" : "shared_gene_conservative_group" : "declared_group");
                finding.put("group_review", declared);
                var ldGroup=ldGrouping.assignments().getOrDefault(key,Map.of());
                if(!ldGroup.isEmpty()){finding.put("ld_grouping",ldGroup);finding.put("redundancy_groups",List.of(ldGroup.get("component_id")));}
                boolean estimable = Boolean.TRUE.equals(finding.get("estimable"));
                boolean interactionCandidate=list(finding.get("observations")).stream().map(StudyPublicEvidence::map).anyMatch(row->"subgroup_interaction".equals(row.get("kind"))&&Boolean.TRUE.equals(row.get("sensitivity_candidate")));
                boolean candidate = estimable && (Boolean.TRUE.equals(finding.get("significant")) || interactionCandidate || Boolean.TRUE.equals(declared.get("include_null")));
                finding.put("candidate", candidate);
                boolean nominal=list(finding.get("observations")).stream().map(StudyPublicEvidence::map).anyMatch(row->Boolean.TRUE.equals(row.get("primary"))&&"NONE".equalsIgnoreCase(text(row.get("adjustment"))));
                finding.put("candidate_reason", !estimable ? "not_estimable_or_excluded_retained" : Boolean.TRUE.equals(finding.get("significant")) ? finding.containsKey("mr_reporting_family")?"cross_hypothesis_primary_MR_family_eligible":nominal?"nominal_threshold_eligible_no_FDR_control":"full_family_FDR_eligible" : interactionCandidate?"separate_registered_interaction_family_eligible": candidate ? "declared_null_or_discordant_finding" : "subgroup_interaction".equals(finding.get("finding_type"))?"interaction_context_retained_not_individually_AI_ranked":"nonsignificant_retained_not_individually_AI_ranked");
                List<Object> caveats = new ArrayList<>();
                caveats.add(caveat("appraisal", "Scientific appraisal, claim strength and distinctness require review; database/search availability is contextual evidence.", List.of()));
                if ("enrichment".equals(finding.get("finding_type"))) caveats.add(caveat("enrichment_dependence", "Enriched terms can overlap and share ontology ancestry; multiple terms do not provide independent biological validation or distinct stories without review of their genes and tested background.", List.of()));
                if(list(finding.get("observations")).stream().map(StudyPublicEvidence::map).anyMatch(row->Set.of("rare_variant_analysis","rare_set_omnibus").contains(text(row.get("operation_type"))))&&list(finding.get("observations")).stream().map(StudyPublicEvidence::map).anyMatch(row->Set.of("association","meta_analysis","validate_harmonize").contains(text(row.get("operation_type")))))caveats.add(caveat("source_dependence","Association and rare-set source families may share participants, genes and variants. Separate testing families do not establish sampling independence or independent replication.",List.of()));
                if (list(finding.get("observations")).stream().map(StudyPublicEvidence::map).noneMatch(row -> "true".equals(text(row.get("replicated")))))
                    caveats.add(caveat("replication", "Independent replication is not established by the retained evidence; failure, absence and unavailable coverage must remain distinguishable.", List.of()));
                if (!"BH".equalsIgnoreCase(adjustment(text(finding.get("hypothesis"))))) caveats.add(caveat("multiplicity", "The declared primary family does not use BH FDR; nominal or alternative-threshold significance must not be described as BH FDR control.", List.of()));
                if(nominal)caveats.add(caveat("nominal_multiplicity","At least one primary observation uses no multiplicity correction; its threshold is nominal and must not be described as FDR control.",List.of()));
                if (!Set.of("enrichment", "mendelian_randomization", "risk_score").contains(String.valueOf(finding.get("finding_type"))) && map(map(finding.get("design")).get("association")).isEmpty())
                    caveats.add(caveat("model", "The upstream association model is not declared in this dossier; calibration, adjustment and estimand suitability remain unverified.", List.of()));
                if (!"enrichment".equals(finding.get("finding_type")) && list(finding.get("observations")).stream().map(StudyPublicEvidence::map).noneMatch(row -> List.of("N", "n", "n_samples").stream().anyMatch(field -> positiveNumber(row.get(field)))))
                    caveats.add(caveat("sample_size", "Participant sample size is not established in the retained result rows; do not substitute file length, cohort enrollment or variant counts.", List.of()));
                if (list(finding.get("observations")).stream().map(StudyPublicEvidence::map).anyMatch(row -> "p_value_only".equals(row.get("result_type"))))
                    caveats.add(caveat("p_value_only", "At least one observation is a P-value-only test. Its missing beta and SE do not establish effect magnitude or beta direction. A separately reported centered-score direction is test context; do not invent an effect estimate or use this test in inverse-variance pooling.", List.of()));
                if (list(finding.get("observations")).stream().map(StudyPublicEvidence::map).anyMatch(row -> row.containsKey("reported_n_sum")))
                    caveats.add(caveat("sample_coverage", "Pooled reported_n_sum combines available cohort-reported counts; it is not a deduplicated participant count or effective sample size. Check contributing/missing counts and cohort dependence.", List.of()));
                if (list(finding.get("observations")).stream().map(StudyPublicEvidence::map).anyMatch(row -> "provisional_unresolved_primary_tests".equals(row.get("family_status"))))
                    caveats.add(caveat("mr_family", "The primary MR family is unresolved. Its adjustment bounds are provisional and do not establish significant primary MR; missing source coverage and non-estimable tests are distinct from null effects.", List.of()));
                if (list(finding.get("observations")).stream().map(StudyPublicEvidence::map).anyMatch(row -> row.containsKey("family_status") && "NONE".equals(row.get("adjustment")) && list(row.get("source_ids")).stream().anyMatch(id->"mendelian_randomization".equals(map(sources.get(text(id))).get("operation_type")))))
                    caveats.add(caveat("mr_multiplicity", "The declared primary MR policy uses nominal P values without multiplicity correction; these are not MR FDR-controlled discoveries.", List.of()));
                var reviewedLocations=list(finding.get("observations")).stream().map(StudyPublicEvidence::map).filter(row->"mr_feature_location_context".equals(row.get("kind"))&&"reviewed_pair_feature_location".equals(row.get("origin"))).toList();
                if(!reviewedLocations.isEmpty())caveats.add(caveat("mr_feature_identity",StudyGeneticFeatureLocation.CAVEAT,reviewedLocations.stream().flatMap(row->list(row.get("source_ids")).stream()).distinct().toList()));
                var reusedMr=list(finding.get("observations")).stream().map(StudyPublicEvidence::map).filter(row->Boolean.TRUE.equals(row.get("reused_supplied_result"))).toList();
                if(!reusedMr.isEmpty())caveats.add(caveat("supplied_mr",StudyMrContributions.CAVEAT,reusedMr.stream().flatMap(row->list(row.get("source_ids")).stream()).distinct().toList()));
                if(!ldGroup.isEmpty())caveats.add(caveat("ld_grouping",StudyFindingGrouping.CAVEAT,list(ldGroup.get("source_ids"))));
                if (!"enrichment".equals(finding.get("finding_type")) && ldGroup.isEmpty() && isGenetic(text(finding.get("hypothesis"))))
                    caveats.add(caveat("locus", "Locus/LD grouping is not established; separate variants cannot be assumed to be independent discoveries.", List.of()));
                for (Object raw : list(finding.get("observations"))) {
                    var row = map(raw);
                    if (!text(row.get("required_manuscript_caveat")).isBlank()) caveats.add(caveat("partial", text(row.get("required_manuscript_caveat")), list(row.get("source_ids"))));
                }
                finding.put("caveats", caveats.stream().distinct().toList());
                writer.write(json(finding)); writer.newLine();
                inventory.add(new String[] {text(finding.get("finding_id")), text(finding.get("hypothesis")), text(finding.get("feature_id")), group,
                    Boolean.toString(candidate), text(finding.get("candidate_reason")), Boolean.toString(estimable), text(finding.get("significant")), Integer.toString(list(finding.get("observations")).size())});
            }
        }
        FollowupSupport.table(context.outputDirectory().resolve("finding-inventory.tsv"), List.of("finding_id", "hypothesis", "feature_id", "story_group", "candidate", "disposition", "estimable", "significant", "observation_count"), inventory);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schema_version", 1); summary.put("scope", "study_wide_all_active_hypotheses"); summary.put("findings", findings.size());
        summary.put("candidates", findings.values().stream().filter(row -> Boolean.TRUE.equals(row.get("candidate"))).count());
        var literatureStates=new TreeMap<String,Long>();for(var finding:findings.values()){String state=text(map(finding.get("literature_coverage")).get("query_evidence_state"));if(!state.isBlank())literatureStates.merge(state,1L,Long::sum);}if(!literatureStates.isEmpty())summary.put("literature_coverage",Map.of("finding_rows_by_state",literatureStates,"interpretation",StudyLiteratureCoverage.CAVEAT));
        summary.put("finding_grouping",ldGrouping.summary());
        if(ldGrouping.summary().containsKey("needs"))summary.put("needs",ldGrouping.summary().get("needs"));
        summary.put("sources", sources); summary.put("unavailable_operations", unavailable);
        if(!mrFamilies.isEmpty())summary.put("cross_hypothesis_mr_families",mrFamilies);
        summary.put("primary_selections", primarySelections.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().receipt(), (a,b)->a, TreeMap::new)));
        summary.put("unresolved_primary_hypotheses", List.copyOf(unresolvedPrimaries));
        summary.put("scientific_scope", "Primary signed or typed set-result rows are inventoried without a top-N cap. Full-family FDR eligibility or explicitly declared null findings define the AI candidate pool. Nonsignificant, excluded and unavailable evidence is retained. Unmapped contextual records remain in source artifacts; exact feature IDs govern attachments, never keyword proximity. Shared gene grouping is conservative, not proof of a single causal signal.");
        write(context.outputDirectory().resolve("finding-inventory.yaml"), summary);
        var outputs=new ArrayList<>(List.of("finding-inventory.yaml", "finding-inventory.jsonl", "finding-inventory.tsv", "primary-result-selection.yaml"));outputs.addAll(ldGrouping.outputs());
        return new Result(State.COMPLETE, "Built uncapped study-wide finding inventory and source-linked aggregate dossiers", outputs, summary);
    }

    private StudyLiteratureCoverage literatureCoverage(List<Artifact> artifacts)throws IOException {
        var requested=new LinkedHashSet<String>();for(Object raw:list(context.plan().get("operations"))){var operation=map(raw);if("literature_search".equals(operation.get("type")))requested.add(text(operation.get("hypothesis")));}
        var result=new StudyLiteratureCoverage(requested);
        for(var artifact:artifacts){var details=map(artifact.manifest.get("details"));if(!artifact.type.equals("literature_search")&&!"literature_search".equals(details.get("kind")))continue;
            var files=map(artifact.manifest.get("output_sha256"));Map<String,Object> document=Map.of();String summarySource;
            if(files.containsKey("literature-search.yaml")){document=StudyYaml.loadArtifact(path(artifact,"literature-search.yaml"));summarySource=source(artifact,"literature-search.yaml");}
            else {String hash=FollowupSupport.hash(artifact.directory.resolve("manifest.yaml"));summarySource="source-"+hash(text(artifact.manifest.get("operation_id"))+"\nmanifest.yaml\n"+hash).substring(0,24);sources.putIfAbsent(summarySource,Map.of("operation_id",artifact.manifest.get("operation_id"),"hypothesis",artifact.hypothesis,"operation_type",artifact.type,"artifact",context.project().relativize(artifact.directory.resolve("manifest.yaml")).toString().replace('\\','/'),"sha256",hash));}
            DelimitedData allocation=null;String allocationSource="";if(files.containsKey("literature-selection.tsv")){allocation=DelimitedData.readResult(path(artifact,"literature-selection.tsv"));allocationSource=source(artifact,"literature-selection.tsv");}
            boolean external="externally_produced".equals(details.get("execution_origin"));result.add(new StudyLiteratureCoverage.Input(artifact.hypothesis,text(artifact.manifest.get("state")),text(details.getOrDefault("role","primary")),summarySource,allocationSource,document,allocation,external));
        }return result;
    }

    private Map<String,Object> mrFamilyContext(List<Artifact> artifacts)throws IOException {
        var result=new LinkedHashMap<String,Object>();
        for(var policy:org.jlinalg.study.StudyMrFamilyPolicy.resolve(context.study())){
            String id=text(policy.get("id"));var matching=artifacts.stream().filter(a->a.type.equals("mr_family_adjustment")&&id.equals(map(map(a.manifest.get("details")).get("policy")).get("id"))).toList();
            if(matching.size()>1)throw new IOException("Ambiguous cross-hypothesis MR family producer: "+id);
            var rows=new LinkedHashMap<String,List<Map<String,Object>>>();boolean complete=false;String source="";
            if(!matching.isEmpty()){
                var a=matching.get(0);var details=map(a.manifest.get("details"));if(!policy.equals(map(details.get("policy"))))throw new IOException("MR family evidence does not match the current registered policy");
                complete="complete".equals(a.manifest.get("state"))&&"complete_cross_hypothesis_family".equals(details.get("status"));
                Path file=path(a,"mr-family-results.tsv");source=source(a,"mr-family-results.tsv");var table=DelimitedData.readResult(file);
                for(String[] row:table.rows()){
                    String h=row[table.column("hypothesis")],feature=row[table.column("feature_id")];if(!list(policy.get("hypotheses")).contains(h))throw new IOException("MR family result has an unregistered hypothesis");
                    var observation=new LinkedHashMap<String,Object>();for(String field:table.header())observation.put(field,row[table.column(field)]);
                    observation.put("kind","cross_hypothesis_mr_testing");observation.put("primary",false);observation.put("source_ids",List.of(source));observation.put("required_manuscript_caveat",StudyMrFamilyAdjustment.CAVEAT);observation.put("reporting_selection_complete",complete);
                    rows.computeIfAbsent(key(h,feature),ignored->new ArrayList<>()).add(observation);
                }
            }
            for(var entry:findings.entrySet()){
                var finding=entry.getValue();if(!list(policy.get("hypotheses")).contains(text(finding.get("hypothesis"))))continue;
                var evidence=rows.getOrDefault(entry.getKey(),List.of());
                if(!evidence.isEmpty())observations.computeIfAbsent(entry.getKey(),ignored->new ArrayList<>()).addAll(evidence);
                else observations.computeIfAbsent(entry.getKey(),ignored->new ArrayList<>()).add(Map.of("kind","cross_hypothesis_mr_testing","family_id",id,"primary",false,"reporting_selection_complete",complete,"disposition","no_joint_primary_MR_row","source_ids",source.isBlank()?List.of():List.of(source),"required_manuscript_caveat",StudyMrFamilyAdjustment.CAVEAT));
                if("mendelian_randomization".equals(finding.get("finding_type"))){
                    finding.put("source_primary_significant",finding.get("significant"));finding.put("mr_reporting_family",id);
                    finding.put("significant",complete&&evidence.stream().anyMatch(row->"true".equals(row.get("joint_significant_primary"))));
                }
            }
            result.put(id,Map.of("policy",policy,"status",complete?"complete":"unresolved","scope","joint primary MR reporting; association and other analysis families remain separate","required_manuscript_caveat",StudyMrFamilyAdjustment.CAVEAT));
        }
        return result;
    }

    private void readGroups() throws IOException {
        for (Object raw : list(context.study().get("datasets"))) {
            var dataset = map(raw); String filename = text(map(dataset.get("files")).get("finding_groups")); if (filename.isBlank()) continue;
            if ("retired".equals(dataset.get("status")) || !list(context.operation().get("datasets")).contains(dataset.get("id"))) continue;
            var table = DelimitedData.readResult(context.project().resolve(filename));
            for (String[] row : table.rows()) {
                String feature = row[table.column("feature_id")], group = row[table.column("group_id")], rationale = row[table.column("rationale")];
                if (group.isBlank() || group.length() > 150 || group.chars().anyMatch(Character::isISOControl) || rationale.isBlank() || rationale.length() > 4000) throw new IOException("Finding groups require bounded group_id and rationale");
                if (table.header().contains("include_null") && !Set.of("true", "false").contains(row[table.column("include_null")].toLowerCase(java.util.Locale.ROOT))) throw new IOException("Finding-group include_null must be true or false");
                boolean nullFinding = table.header().contains("include_null") && row[table.column("include_null")].equalsIgnoreCase("true");
                for (Object hypothesisRaw : list(context.study().get("hypotheses"))) {
                    var hypothesis = map(hypothesisRaw); String h = text(hypothesis.get("id"));
                    if ("retired".equals(hypothesis.get("status"))) continue;
                    if (table.header().contains("hypothesis") && !row[table.column("hypothesis")].equals(h)) continue;
                    if (!map(hypothesis.get("inputs")).values().stream().anyMatch(ids -> list(ids).contains(dataset.get("id")))) continue;
                    var declaration = Map.<String, Object>of("group_id", group, "rationale", rationale, "include_null", nullFinding, "source_sha256", FollowupSupport.hash(context.project().resolve(filename)));
                    var old = groups.putIfAbsent(key(h, feature), declaration);
                    if (old != null && (!old.get("group_id").equals(group) || !old.get("rationale").equals(rationale) || !old.get("include_null").equals(nullFinding))) throw new IOException("Conflicting study finding groups for " + h + "/" + feature);
                }
            }
        }
    }

    private void readAnnotations(Artifact artifact) throws IOException {
        if (!map(artifact.manifest.get("output_sha256")).containsKey("feature-annotation.tsv")) return;
        var table = DelimitedData.readResult(path(artifact, "feature-annotation.tsv"));
        for (String[] row : table.rows()) {
            var retained = new LinkedHashMap<String, Object>();
            for (String field : List.of("gene_id", "gene_symbol", "chromosome", "start", "end", "genome_build", "mapping_status")) if (table.header().contains(field)) retained.put(field, row[table.column(field)]);
            String key = key(artifact.hypothesis, row[table.column("feature_id")]);
            var previous = annotations.get(key); List<Object> ids = new ArrayList<>();
            if (previous != null) ids.addAll(list(previous.get("source_ids")));
            ids.add(source(artifact, "feature-annotation.tsv"));
            var facts = new LinkedHashMap<String, Object>(previous == null ? Map.of() : previous); facts.remove("source_ids");
            if (previous != null && !facts.equals(retained)) retained = new LinkedHashMap<>(Map.of("mapping_status", "conflicting_annotations", "candidates", List.of(previous, retained)));
            retained.put("source_ids", ids.stream().distinct().toList()); annotations.put(key, retained);
        }
    }

    private void readTables(Artifact artifact) throws IOException {
        if (artifact.type.equals("colocalization")) return; // Complete posterior context has no association FDR eligibility.
        boolean rare=Set.of("rare_variant_analysis","rare_set_omnibus").contains(artifact.type);
        boolean omnibus=artifact.type.equals("rare_set_omnibus");
        boolean omnibusPrimary=org.jlinalg.study.StudyRareOmnibusPolicy.primary(analysis(artifact.hypothesis));
        boolean partialRare=rare&&"waiting_for_input".equals(artifact.manifest.get("state"))&&Boolean.TRUE.equals(map(artifact.manifest.get("details")).get("rare_primary_complete"));
        if ((!"complete".equals(artifact.manifest.get("state"))&&!partialRare) || !TABLE_TYPES.contains(artifact.type)) return;
        boolean hasPost = map(artifact.manifest.get("output_sha256")).keySet().stream().anyMatch(name -> name.startsWith("post-effect-qc-results/"));
        for (String relative : map(artifact.manifest.get("output_sha256")).keySet()) {
            var imported = map(artifact.manifest.get("details"));
            if (relative.startsWith("supplied/") || "externally_produced".equals(imported.get("execution_origin")) && !relative.equals(imported.get("canonical_results"))) continue;
            if (!relative.endsWith(".tsv") || !selectedTable(artifact.type, relative, hasPost)) continue;
            if(partialRare&&!relative.equals(omnibus?"rare-omnibus-results.tsv":"rare-results.tsv"))continue;
            var table = DelimitedData.readResult(path(artifact, relative));
            if (Set.of("gene_ontology", "pathway_enrichment", "ranked_gsea", "gwas_enrichment").contains(artifact.type) && table.header().contains("term_id")) { enrichment(artifact, relative, table); continue; }
            if (!table.header().contains("feature_id")) continue;
            String source = source(artifact, relative);
            var selected = primarySelections.get(artifact.hypothesis);
            boolean selectedSource = selected != null && selected.owns(path(artifact, relative));
            boolean primary = selectedSource
                || artifact.type.equals("rare_variant_analysis") && relative.equals("rare-results.tsv") && !omnibusPrimary
                || omnibus && omnibusPrimary && relative.equals("rare-omnibus-results.tsv") && Boolean.TRUE.equals(map(artifact.manifest.get("details")).get("primary_selection_eligible"))
                || artifact.type.equals("mendelian_randomization") && Boolean.TRUE.equals(map(artifact.manifest.get("details")).get("primary_selection_eligible")) && selected == null && !unresolvedPrimaries.contains(artifact.hypothesis);
            Map<String, Double> probabilities = new LinkedHashMap<>();
            for (String[] row : table.rows()) if (selectedSource ? selected.selectedRow(table, row) : primaryRow(artifact.type, table, row)) {
                double p = numeric(row, table, "p_value");
                if (validStatus(row, table) && validEstimate(row, table) && Double.isFinite(p) && p >= 0 && p <= 1) probabilities.put(row[table.column("feature_id")], p);
            }
            Map<String, Double> adjusted = StudyCohortResults.adjust(probabilities, adjustment(artifact.hypothesis).toUpperCase(java.util.Locale.ROOT));
            Map<String, Object> importedFamily = Set.of("meta_analysis", "discovery_meta_analysis", "mendelian_randomization", "rare_variant_analysis", "rare_set_omnibus").contains(artifact.type)
                ? org.jlinalg.study.StudySuppliedResultPolicy.family(path(artifact, relative)) : Map.of();
            if (!selectedSource && !importedFamily.isEmpty() && !rare) adjusted = StudySuppliedFamilies.adjusted(path(artifact, relative), table, probabilities, adjusted);
            if (selectedSource) { probabilities = selected.probabilities(); adjusted = selected.adjusted(); importedFamily = selected.family(); }
            int number = 0;
            for (String[] row : table.rows()) {
                if(partialRare&&(!table.header().contains("scope")||!row[table.column("scope")].equals("primary")))continue;
                number++; String feature = row[table.column("feature_id")]; if (feature.isBlank()) continue;
                Map<String, Object> observation = new LinkedHashMap<>();
                for (String field : table.header()) if (FIELDS.contains(field) || org.jlinalg.study.StudyResultMetadataPolicy.names().contains(field)) observation.put(field, row[table.column(field)]);
                observation.put("source_ids", List.of(source)); observation.put("row_number", number); observation.put("operation_type", artifact.type); observation.put("artifact_role", relative); observation.put("primary", false);
                if (selectedSource) observation.put("primary_selection", selected.receipt());
                var details = map(artifact.manifest.get("details"));
                String poolingCaveat = text(map(details.get("pooling_audit")).get("required_manuscript_caveat"));
                String covarianceCaveat = text(details.get("required_manuscript_caveat"));
                if (!poolingCaveat.isBlank() || !covarianceCaveat.isBlank()) observation.put("required_manuscript_caveat", (poolingCaveat + " " + covarianceCaveat).strip());
                if(partialRare){observation.put("source_operation_state","waiting_for_input");observation.put("evidence_disposition","complete_primary_family_other_requested_scopes_unresolved");observation.put("primary_testing_family_complete",true);}
                String key = key(artifact.hypothesis, feature);
                observations.computeIfAbsent(key, ignored -> new ArrayList<>()).add(observation);
                if (Set.of("association", "validate_harmonize").contains(artifact.type)) findings.computeIfAbsent(key, ignored -> finding(artifact.hypothesis, feature));
                if (!primary || relative.startsWith("pre-effect-qc-results/") || !(selectedSource ? selected.selectedRow(table, row) : primaryRow(artifact.type, table, row))) continue;
                var finding = findings.computeIfAbsent(key, ignored -> finding(artifact.hypothesis, feature));
                if (primary && artifact.type.equals("mendelian_randomization")) finding.put("finding_type", "mendelian_randomization");
                double p = numeric(row, table, "p_value");
                double q = selectedSource ? adjusted.getOrDefault(feature, Double.NaN) : table.header().contains("adjusted_p") ? numeric(row, table, "adjusted_p") : adjusted.getOrDefault(feature, Double.NaN);
                observation.put("full_family_adjusted_p", finite(q));
                Object denominator = probabilities.size();
                if (rare) {
                    denominator = "not_declared";
                    for (Object rawFamily : list(map(artifact.manifest.get("details")).get("families"))) {
                        var family = map(rawFamily); if ("primary".equals(family.get("scope"))) denominator = family.get("testing_denominator");
                    }
                }
                observation.put("testing_denominator", importedFamily.getOrDefault("tested_count", denominator));
                observation.put("adjustment", importedFamily.getOrDefault("adjustment", omnibus?map(map(artifact.manifest.get("details")).get("effective_policy")).getOrDefault("adjustment","PROVIDED"):adjustment(artifact.hypothesis)));
                if (!importedFamily.isEmpty()) {
                    observation.put("testing_family_coverage", importedFamily.getOrDefault("coverage", "unknown"));
                    observation.put("execution_origin", "externally_produced");
                }
                boolean estimable = validStatus(row, table) && validEstimate(row, table) && Double.isFinite(p) && p >= 0 && p <= 1;
                finding.put("estimable", Boolean.TRUE.equals(finding.get("estimable")) || estimable);
                Object levelValue=importedFamily.get("alpha");if(omnibus&&levelValue==null)levelValue=map(details.get("effective_policy")).get("alpha");
                double declaredAlpha = levelValue instanceof Number level ? level.doubleValue() : alpha(artifact.hypothesis);
                finding.put("significant", Boolean.TRUE.equals(finding.get("significant")) || estimable && q < declaredAlpha);
                observation.put("primary", true);
            }
        }
    }

    private static String enrichmentKey(DelimitedData table,String[] row){return row[table.column(table.header().contains("enrichment_id")?"enrichment_id":"term_id")];}

    private void enrichment(Artifact artifact, String relative, DelimitedData table) throws IOException {
        String source = source(artifact, relative); int number = 0;
        Map<String, Double> p = new LinkedHashMap<>();
        for (String[] row : table.rows()) { double value = numeric(row, table, "p_value"); if (Double.isFinite(value) && value >= 0 && value <= 1) p.put(enrichmentKey(table,row), value); }
        var bh = StudySuppliedFamilies.adjusted(path(artifact, relative), table, p, StudyCliExecutor.bh(p));
        var importedFamily = org.jlinalg.study.StudySuppliedResultPolicy.family(path(artifact, relative));
        for (String[] row : table.rows()) {
            number++; String term = enrichmentKey(table,row), feature = artifact.type + ":" + term;
            var observation = new LinkedHashMap<String, Object>();
            for (String field : List.of("enrichment_id","mask_id","enrichment_source","association_adjustment","association_testing_denominator","term_id", "term_name", "status", "method", "universe_genes", "selected_genes", "term_genes", "overlap_genes", "p_value", "FDR", "log_FDR", "hit_genes", "enrichment_score", "normalized_enrichment_score", "reference_snp_proportion", "category_heritability", "heritability_proportion", "heritability_proportion_se", "enrichment", "enrichment_se", "per_snp_contrast", "per_snp_contrast_se", "annotation_type", "inference_target", "annotation_sum", "annotation_sd", "coefficient", "coefficient_se", "coefficient_p", "coefficient_log_p", "log_p_value", "log_adjusted_p", "standardized_coefficient", "standardized_coefficient_se", "ranked_members", "leading_edge_genes", "null_method", "null_states", "same_sign_states", "tail_states", "p_resolution", "p_mc_lower_95", "p_mc_upper_95")) if (table.header().contains(field)) observation.put(field, row[table.column(field)]);
            double probability = numeric(row, table, "p_value"), q = !importedFamily.isEmpty() ? bh.getOrDefault(term, Double.NaN)
                : table.header().contains("adjusted_p") ? numeric(row,table,"adjusted_p") : table.header().contains("FDR") ? numeric(row, table, "FDR") : bh.getOrDefault(term, Double.NaN);
            boolean estimable = validStatus(row,table) && Double.isFinite(probability) && probability >= 0 && probability <= 1;
            observation.put("source_ids", List.of(source)); observation.put("row_number", number); observation.put("operation_type", artifact.type); observation.put("primary", true);
            observation.put("full_family_adjusted_p", finite(q)); observation.put("testing_denominator", importedFamily.getOrDefault("tested_count", table.header().contains("testing_denominator")?numeric(row,table,"testing_denominator"):p.size()));
            var details=map(artifact.manifest.get("details"));observation.put("adjustment",details.getOrDefault("adjustment","BH"));
            if(details.containsKey("dataset_id"))observation.put("dataset_id",details.get("dataset_id"));
            if(!text(details.get("required_manuscript_caveat")).isBlank())observation.put("required_manuscript_caveat",details.get("required_manuscript_caveat"));
            if(table.header().contains("required_manuscript_caveat")&&!row[table.column("required_manuscript_caveat")].isBlank()){String existing=text(observation.get("required_manuscript_caveat")),local=row[table.column("required_manuscript_caveat")];observation.put("required_manuscript_caveat",existing.isBlank()||existing.equals(local)?local:existing+" "+local);}
            if(artifact.type.equals("gwas_enrichment")&&!artifact.manifest.containsKey("supplied_result_id")&&details.containsKey("total_heritability")){
                var fitContext=new LinkedHashMap<String,Object>();
                for(String field:List.of("method_model","annotation_mode","weight_model","official_family_complete","regression_snps","regression_chromosomes","reference_snps","reference_coverage","annotation_count","target_count","continuous_target_count","binary_target_count","total_heritability","total_heritability_se","total_heritability_z","intercept","intercept_se","mean_chi_square","sample_size_min","sample_size_max","sample_size_mean","testing_family","standardized_effect_definition","standardized_effect_se"))if(details.containsKey(field))fitContext.put(field,details.get(field));
                var ref=new LinkedHashMap<String,Object>();for(String field:List.of("population","genome_build","release","common_variant_definition","regression_snp_definition","annotation_model"))if(map(details.get("reference_scope")).containsKey(field))ref.put(field,map(details.get("reference_scope")).get(field));fitContext.put("reference",ref);
                var sourceScope=new LinkedHashMap<String,Object>();for(String field:List.of("population","genome_build","calibration","ascertainment"))if(map(details.get("summary_scope")).containsKey(field))sourceScope.put(field,map(details.get("summary_scope")).get(field));fitContext.put("source_declarations",sourceScope);
                observation.put("gwas_enrichment_context",fitContext);
            }
            if (!importedFamily.isEmpty()) {
                observation.put("adjustment", importedFamily.getOrDefault("adjustment", "PROVIDED"));
                observation.put("execution_origin", "externally_produced");
            }
            String key = key(artifact.hypothesis, feature); observations.computeIfAbsent(key, ignored -> new ArrayList<>()).add(observation);
            var finding = findings.computeIfAbsent(key, ignored -> finding(artifact.hypothesis, feature)); finding.put("finding_type", "enrichment");
            double declaredAlpha = importedFamily.get("alpha") instanceof Number level ? level.doubleValue() : alpha(artifact.hypothesis);
            finding.put("estimable", Boolean.TRUE.equals(finding.get("estimable"))||estimable); finding.put("significant", Boolean.TRUE.equals(finding.get("significant"))||estimable && q < declaredAlpha);
        }
    }

    private void readContext(Artifact artifact) throws IOException {
        if(artifact.type.equals("rare_set_omnibus")&&map(artifact.manifest.get("output_sha256")).containsKey("rare-omnibus-replication.tsv")){
            String file="rare-omnibus-replication.tsv";var table=DelimitedData.readResult(path(artifact,file));
            for(String[] row:table.rows()){var observation=new LinkedHashMap<String,Object>();for(String field:List.of("feature_id","discovery_datasets","validation_dataset","discovery_p","discovery_log_p","discovery_adjusted_p","discovery_log_adjusted_p","validation_p","validation_log_p","validation_adjusted_p","validation_log_adjusted_p","validation_testing_denominator","adjustment_scope","same_sign","replicated","coverage"))if(table.header().contains(field))observation.put(field,row[table.column(field)]);observation.put("kind","rare_unit_omnibus_replication");observation.put("primary",false);observation.put("source_ids",List.of(source(artifact,file)));observation.put("required_manuscript_caveat",StudyRareOmnibus.CAVEAT);observations.computeIfAbsent(key(artifact.hypothesis,row[table.column("feature_id")]),ignored->new ArrayList<>()).add(observation);}
        }
        subgroupContext(artifact);
        nativePairReuseContext(artifact);
        nativeFineMappingContext(artifact);
        mrFeatureContext(artifact);
        colocalizationContext(artifact);
        cohortContext(artifact);
        proteinContext(artifact);
        scoreContext(artifact);
        networkContext(artifact);
        gwasDiagnosticContext(artifact);
        networkPreservationContext(artifact);
        networkNullContext(artifact);
        if (map(artifact.manifest.get("output_sha256")).containsKey("variant-evidence.tsv")) {
            String source = source(artifact, "variant-evidence.tsv"); var table = DelimitedData.readResult(path(artifact, "variant-evidence.tsv"));
            for (String[] values : table.rows()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String field : List.of("feature_id", "dataset_id", "variant_id", "kind", "gene_id", "transcript_id", "consequence", "scorer", "ontology_curie", "raw_score", "quantile_score", "model_version", "status", "resource_version")) row.put(field, values[table.column(field)]);
                row.put("required_manuscript_caveat", "Functional predictions provide context; they are not association P values, causal probabilities or validated pathogenicity. Tissue, transcript and scorer coverage limit interpretation.");
                attach(artifact.hypothesis, row, "", source, 0);
            }
        }
        // Source summaries are sealed aggregate result documents, not participant inputs.
        for (String name : List.of("genetic-evidence.yaml", "bioinformatics-investigation.yaml", "literature-search.yaml", "diagnostics.yaml", "rare-analysis.yaml")) {
            if (!map(artifact.manifest.get("output_sha256")).containsKey(name)) continue;
            if (name.equals("genetic-evidence.yaml") && artifact.type.equals("colocalization") && map(artifact.manifest.get("output_sha256")).containsKey("colocalization-results.tsv")) continue;
            String source = source(artifact, name); var document = StudyYaml.loadArtifact(path(artifact, name));
            attach(artifact.hypothesis, document, "", source, 0);
        }
    }

    private void networkNullContext(Artifact artifact)throws IOException {
        var details=map(artifact.manifest.get("details"));if(!artifact.type.equals("network_preservation_null")&&!(artifact.type.equals("supplied_sensitivity")&&"network_preservation_null".equals(details.get("kind"))))return;
        boolean external="externally_produced".equals(details.get("execution_origin"));String filename=external?text(details.get("canonical_results")):"network-preservation-tests.tsv";var files=map(artifact.manifest.get("output_sha256"));if(filename.isBlank()||!files.containsKey(filename))return;
        var table=DelimitedData.readResult(path(artifact,filename));var memberships=new LinkedHashMap<String,Set<List<String>>>();
        if(files.containsKey("network-preservation-memberships.tsv")){var m=DelimitedData.readResult(path(artifact,"network-preservation-memberships.tsv"));for(String[] row:m.rows())memberships.computeIfAbsent(row[m.column("node_id")],ignored->new LinkedHashSet<>()).add(List.of(row[m.column("reference_graph")],row[m.column("module_id")],row[m.column("module_type")]));}
        for(var finding:findings.values())if(artifact.hypothesis.equals(finding.get("hypothesis"))){String feature=text(finding.get("feature_id"));var member=memberships.get(feature);var rows=member==null?table.rows():table.rows().stream().filter(row->member.contains(List.of(row[table.column("reference_graph")],row[table.column("module_id")],row[table.column("module_type")]))).toList();
            var observation=new LinkedHashMap<String,Object>();observation.put("kind","network_label_null_context");observation.put("primary",false);observation.put("context_scope",member==null?"study_graph_context_no_reference_membership_assignment":"reference_membership_context");observation.put("reported_comparisons",rows.size());observation.put("tested_comparisons",rows.stream().filter(row->row[table.column("disposition")].equals("tested")).count());
            for(String dimension:List.of("statistic","permutation_method","adjustment","family_status","family_size"))observation.put(dimension+"_values",rows.stream().map(row->row[table.column(dimension)]).distinct().sorted().toList());observation.put("exchangeability_scientific_review_required",true);observation.put("registered_alpha",external?map(details.get("family")).getOrDefault("alpha","not_declared"):map(details.get("effective_policy")).getOrDefault("alpha","not_recorded"));
            for(String metric:List.of("p_value","adjusted_p")){var numbers=rows.stream().map(row->row[table.column(metric)]).filter(v->!v.equals("NA")).mapToDouble(Double::parseDouble).summaryStatistics();if(numbers.getCount()>0)observation.put(metric+"_range",List.of(numbers.getMin(),numbers.getMax()));}
            observation.put("source_reported_significant_conditional_tests",rows.stream().filter(row->row[table.column("significant")].equals("true")).count());observation.put("execution_origin",external?"externally_produced":"native_conditional_label_null");observation.put("scientific_role","conditional_graph_context");observation.put("external_scientific_validity_reproduced",false);if(external){var safeFamily=new LinkedHashMap<String,Object>();var family=map(details.get("family"));for(String field:List.of("id","coverage","adjustment","tested_count","alpha","adjusted_p_from_full_family"))if(family.containsKey(field))safeFamily.put(field,family.get(field));observation.put("source_family",safeFamily);}observation.put("source_ids",List.of(source(artifact,filename)));observation.put("required_manuscript_caveat",StudyNetworkNull.CAVEAT);observations.computeIfAbsent(key(artifact.hypothesis,feature),ignored->new ArrayList<>()).add(observation);
        }
    }
    private void networkPreservationContext(Artifact artifact)throws IOException {
        var details=map(artifact.manifest.get("details"));if(!artifact.type.equals("network_preservation")&&!(artifact.type.equals("supplied_sensitivity")&&"network_preservation".equals(details.get("kind"))))return;
        boolean external="externally_produced".equals(details.get("execution_origin"));String filename=external?text(details.get("canonical_results")):"network-preservation.tsv";var files=map(artifact.manifest.get("output_sha256"));if(filename.isBlank()||!files.containsKey(filename))return;
        var table=DelimitedData.readResult(path(artifact,filename));var memberships=new LinkedHashMap<String,Set<List<String>>>();
        if(files.containsKey("network-preservation-memberships.tsv")){var m=DelimitedData.readResult(path(artifact,"network-preservation-memberships.tsv"));for(String[] row:m.rows())memberships.computeIfAbsent(row[m.column("node_id")],ignored->new LinkedHashSet<>()).add(List.of(row[m.column("reference_graph")],row[m.column("module_id")],row[m.column("module_type") ]));}
        for(var finding:findings.values())if(artifact.hypothesis.equals(finding.get("hypothesis"))){String feature=text(finding.get("feature_id"));var member=memberships.get(feature);var values=member==null?table.rows():table.rows().stream().filter(row->member.contains(List.of(row[table.column("reference_graph")],row[table.column("module_id")],row[table.column("module_type")]))).toList();
            var observation=new LinkedHashMap<String,Object>();observation.put("kind","network_preservation_context");observation.put("primary",false);observation.put("context_scope",member==null?"study_graph_context_no_reference_membership_assignment":"reference_membership_context");observation.put("reported_comparisons",values.size());observation.put("assessed_comparisons",values.stream().filter(row->row[table.column("disposition")].startsWith("assessed")).count());
            for(String metric:List.of("node_coverage","edge_retention","signed_agreement","thresholded_weight_cosine")){var numbers=values.stream().map(row->row[table.column(metric)]).filter(v->!v.equals("NA")).mapToDouble(Double::parseDouble).summaryStatistics();if(numbers.getCount()>0)observation.put(metric+"_range",List.of(numbers.getMin(),numbers.getMax()));}
            observation.put("execution_origin",external?"externally_produced":"native_descriptive_diagnostics");observation.put("scientific_role",external?details.getOrDefault("role","external_diagnostic_context"):"diagnostic_context");observation.put("source_ids",List.of(source(artifact,filename)));observation.put("required_manuscript_caveat",StudyNetworkPreservation.CAVEAT);observations.computeIfAbsent(key(artifact.hypothesis,feature),ignored->new ArrayList<>()).add(observation);
        }
    }
    private void gwasDiagnosticContext(Artifact artifact)throws IOException {
        var details=map(artifact.manifest.get("details"));
        if(!artifact.type.equals("gwas_diagnostics")&&!(artifact.type.equals("supplied_sensitivity")&&"gwas_diagnostics".equals(details.get("kind"))))return;
        boolean external="externally_produced".equals(details.get("execution_origin"));
        String filename=external?text(details.get("canonical_results")):"gwas-diagnostic-summary.tsv";
        if(filename.isBlank()||!map(artifact.manifest.get("output_sha256")).containsKey(filename))return;
        var table=DelimitedData.readResult(path(artifact,filename));var rows=new ArrayList<Object>();
        for(String[] values:table.rows())if(values[table.column("stratum")].equals("all")){
            var row=new LinkedHashMap<String,Object>();for(String field:StudyGwasDiagnostics.SUMMARY_COLUMNS)row.put(field,values[table.column(field)]);rows.add(row);
        }
        var observation=new LinkedHashMap<String,Object>();observation.put("kind","gwas_result_diagnostics");observation.put("operation_type","gwas_diagnostics");observation.put("primary",false);observation.put("source_diagnostics",rows);observation.put("execution_origin",external?"externally_produced":"native_descriptive_diagnostics");observation.put("external_calibration_reproduced",false);observation.put("required_manuscript_caveat",StudyGwasDiagnostics.CAVEAT);observation.put("source_ids",List.of(source(artifact,filename)));
        observation.put("scientific_role",external?details.getOrDefault("role","external_diagnostic_context"):"diagnostic_context");
        for(var finding:findings.values())if(artifact.hypothesis.equals(finding.get("hypothesis")))observations.computeIfAbsent(key(artifact.hypothesis,text(finding.get("feature_id"))),ignored->new ArrayList<>()).add(new LinkedHashMap<>(observation));
    }
    private void survivalContext(Artifact artifact)throws IOException {
        var files=map(artifact.manifest.get("output_sha256"));
        if(!artifact.type.equals("association")||!files.containsKey("survival-analysis.yaml")||!files.containsKey("survival-diagnostics.tsv"))return;
        var details=StudyYaml.loadArtifact(path(artifact,"survival-analysis.yaml"));var population=map(details.get("survival_population"));
        var table=DelimitedData.readResult(path(artifact,"survival-diagnostics.tsv"));
        for(String[] row:table.rows()){
            String key=key(artifact.hypothesis,row[table.column("feature_id")]);if(!findings.containsKey(key))continue;
            var observation=new LinkedHashMap<String,Object>();
            for(String field:List.of("analyzed_samples","events","censored","strata","delayed_entry_rows","total_at_risk_time","time_units"))if(population.containsKey(field))observation.put(field,population.get(field));
            for(String field:table.header())observation.put(field,row[table.column(field)]);
            observation.put("kind","survival_model_context");observation.put("operation_type","association");observation.put("dataset_id",details.get("dataset"));observation.put("primary",false);
            observation.put("association_family_status",details.get("association_family_status"));observation.put("source_operation_state",artifact.manifest.get("state"));
            if(details.containsKey("likelihood_existence"))observation.put("likelihood_existence",details.get("likelihood_existence"));
            observation.put("required_manuscript_caveat",details.get("required_manuscript_caveat"));
            observation.put("source_ids",List.of(source(artifact,"survival-analysis.yaml"),source(artifact,"survival-diagnostics.tsv")));
            observations.computeIfAbsent(key,ignored->new ArrayList<>()).add(observation);
        }
    }
    private void assayQcContext(Artifact artifact)throws IOException {
        if(!artifact.type.equals("assay_qc")||!map(artifact.manifest.get("output_sha256")).containsKey("assay-qc.yaml"))return;
        var qc=StudyYaml.loadArtifact(path(artifact,"assay-qc.yaml"));var observation=new LinkedHashMap<String,Object>();
        for(String field:List.of("dataset_id","source","mode","value_scale","policy_sha256","matrix_sha256","source_samples","source_features","retained_samples","retained_features","excluded_samples","excluded_features","invalid_values","detection_failed_values","proposed_masked_values","applied_masked_values","flagged_features","flagged_samples","feature_flag_counts","sample_missingness_denominator","feature_statistics_population","imputation","complete","appraisal_status","required_manuscript_caveat"))if(qc.containsKey(field))observation.put(field,qc.get(field));
        observation.put("kind","source_assay_qc");observation.put("primary",false);observation.put("source_ids",List.of(source(artifact,"assay-qc.yaml")));
        // Only aggregate source-population context is eligible for manuscripts/AI.
        // Source decision reasons and participant/feature QC vectors stay local.
        for(Object raw:list(context.study().get("hypotheses"))){var h=map(raw);boolean selected=map(h.get("inputs")).values().stream().anyMatch(v->v instanceof String text?text.equals(qc.get("dataset_id")):list(v).contains(qc.get("dataset_id")));if(!selected)continue;
            var policy=org.jlinalg.study.StudyAssayQcPolicy.forDataset(org.jlinalg.study.StudyAssayQcPolicy.resolve(map(map(h.get("analysis")).get("assay_qc"))),text(qc.get("dataset_id")));if(!org.jlinalg.study.StudyAssayQcPolicy.fingerprint(policy).equals(qc.get("policy_sha256")))continue;
            for(var finding:findings.values())if(h.get("id").equals(finding.get("hypothesis")))observations.computeIfAbsent(key(text(h.get("id")),text(finding.get("feature_id"))),ignored->new ArrayList<>()).add(new LinkedHashMap<>(observation));
        }
    }
    private void genotypeQcContext(Artifact artifact)throws IOException {
        if(!artifact.type.equals("participant_qc")||!map(artifact.manifest.get("output_sha256")).containsKey("genotype-qc-summary.yaml"))return;
        var qc=StudyYaml.loadArtifact(path(artifact,"genotype-qc-summary.yaml"));var observation=new LinkedHashMap<String,Object>();
        for(String field:List.of("dataset_id","candidate_samples","source_universe_complete","source_variants_examined","eligible_before_hash_sampling","hash_sampled_markers","ld_pruned_panel_markers","registered_pairs","related_pairs","unresolved_pairs","unrelated_basis_samples","proposed_retained_samples","heterozygosity_warnings","pca_computed","pca_status","pca_eigenvalues","pca_variance_fractions","pca_polymorphic_basis_markers","analysis_samples","pc_covariates","complete","problems","matrix_sha256","panel_source_sha256","inputs_stable"))if(qc.containsKey(field))observation.put(field,qc.get(field));
        for(String field:List.of("execution_origin","native_relatedness_computed","pca_origin","external_sample_qc_complete","external_qc_reexecuted","native_panel_filters_applied","external_source_samples","external_qc_retained_samples","external_relatedness_excluded_samples","external_evaluated_pairs","external_related_pairs","external_unresolved_pairs","external_basis_samples","external_pca_basis_samples","external_report_sha256"))if(qc.containsKey(field))observation.put(field,qc.get(field));
        boolean external=!"native".equals(qc.getOrDefault("execution_origin","native"));
        for(String field:List.of("external_pca_basis_scope","external_pca_reference_sha256","external_basis_membership_present","external_independent_basis_complete"))if(qc.containsKey(field))observation.put(field,qc.get(field));
        if(external)observation.put("external_limitations_review_required",true);
        observation.put("kind","genotype_sample_qc");observation.put("operation_type","participant_qc");observation.put("primary",false);observation.put("required_manuscript_caveat",(external?StudyExternalGenotypeQc.CAVEAT+("native".equals(qc.get("pca_origin"))?" Only the explicitly requested PCA was computed natively on the current eligible external basis. ":""):StudyGenotypeSampleQc.CAVEAT)+" This context concerns the current dataset QC; independently supplied results and their historical analysis populations are not retrospectively validated.");observation.put("source_ids",List.of(source(artifact,"genotype-qc-summary.yaml")));
        for(var finding:findings.values())if(artifact.hypothesis.equals(finding.get("hypothesis")))observations.computeIfAbsent(key(artifact.hypothesis,text(finding.get("feature_id"))),ignored->new ArrayList<>()).add(new LinkedHashMap<>(observation));
    }
    private void subgroupContext(Artifact artifact) throws IOException {
        var details=map(artifact.manifest.get("details"));
        if(!artifact.type.equals("subgroup_interactions")&&!(artifact.type.equals("supplied_sensitivity")&&"subgroup_interactions".equals(details.get("kind"))))return;
        var files=map(artifact.manifest.get("output_sha256"));
        boolean external="externally_produced".equals(details.get("execution_origin"));String filename=external?text(details.get("canonical_results")):"subgroup-interactions.tsv";
        if(!files.containsKey(filename))return;
        var table=DelimitedData.readResult(path(artifact,filename));String source=source(artifact,filename);
        var family=map(details.get("resolved_family"));double alpha=family.get("alpha") instanceof Number n?n.doubleValue():map(details.get("effective_policy")).get("alpha") instanceof Number n?n.doubleValue():.05;
        boolean complete=external?Boolean.TRUE.equals(details.get("primary_selection_eligible")):"complete_registered_family".equals(details.get("family_status"));
        for(String[] row:table.rows()) {
            String feature=row[table.column("feature_id")];var observation=new LinkedHashMap<String,Object>();
            for(String field:StudySubgroupInteractions.COLUMNS)if(table.header().contains(field))observation.put(field,row[table.column(field)]);
            String status=table.header().contains("status")?row[table.column("status")]:"complete";
            double p=numeric(row,table,"p_value"),q=numeric(row,table,"adjusted_p");boolean estimable=Set.of("complete","ok","tested").contains(status)&&Double.isFinite(p);
            boolean candidate=complete&&estimable&&Double.isFinite(q)&&q<alpha&&!"sensitivity".equals(details.get("role"))&&(!table.header().contains("in_testing_family")||!row[table.column("in_testing_family")].equals("false"));
            observation.put("kind","subgroup_interaction");observation.put("operation_type","subgroup_interactions");observation.put("primary",false);observation.put("sensitivity_candidate",candidate);observation.put("source_ids",List.of(source));
            observation.put("required_manuscript_caveat",StudySubgroupInteractions.CAVEAT+(external?" Externally produced interaction fits and their assumptions were not reproduced by JLinAlg; original full-family adjustments remain source-specific.":""));
            if(!complete)observation.put("required_manuscript_caveat",observation.get("required_manuscript_caveat")+" The registered interaction family is incomplete or context-only. Observed fits and source adjustments do not establish eligible interaction discoveries in this run.");
            if(external){observation.put("execution_origin","externally_produced");observation.putIfAbsent("method",text(map(details.get("method")).get("name")));observation.put("source_family",org.jlinalg.study.StudySuppliedResultPolicy.aggregateFamily(family));observation.put("adjustment",family.getOrDefault("adjustment","PROVIDED"));observation.put("source_adjusted_p",Double.isFinite(q)?q:"NA");observation.put("family_status",complete?"complete_supplied_family":"incomplete_supplied_family");}
            if("NONE".equals(observation.get("adjustment")))observation.put("required_manuscript_caveat",observation.get("required_manuscript_caveat")+" Interaction thresholds are nominal under the registered NONE policy; they are not FDR control.");
            String key=key(artifact.hypothesis,feature);observations.computeIfAbsent(key,ignored->new ArrayList<>()).add(observation);
            var finding=findings.computeIfAbsent(key,ignored->{var f=finding(artifact.hypothesis,feature);f.put("finding_type","subgroup_interaction");return f;});if(estimable)finding.put("estimable",true);
        }
    }

    private void nativeFineMappingContext(Artifact artifact) throws IOException {
        if (!artifact.type.equals("fine_mapping") || "externally_produced".equals(map(artifact.manifest.get("details")).get("execution_origin"))) return;
        var files = map(artifact.manifest.get("output_sha256"));
        if (!files.containsKey("genetic-evidence.yaml")) return;
        var document = StudyYaml.loadArtifact(path(artifact, "genetic-evidence.yaml"));
        for (Object raw : list(document.get("pairs"))) {
            var pair = map(raw); String id = text(pair.get("pair_id")), feature = text(pair.get("feature_id"));
            for (String trait : List.of("exposure", "outcome")) {
                String name = id + "/" + trait + "-fine-mapping.tsv";
                if (!files.containsKey(name)) continue;
                var table = DelimitedData.readResult(path(artifact, name));
                var parameters = map(map(pair.get("effective_parameters")).get(trait + "_fine_mapping"));
                for (String[] row : table.rows()) {
                    double pip = numeric(row, table, "pip"), mean = numeric(row, table, "posterior_mean");
                    if (!Double.isFinite(pip) || pip < 0 || pip > 1 || !Double.isFinite(mean)) throw new IOException("Invalid native fine-mapping posterior context");
                    var observation = new LinkedHashMap<String,Object>();
                    observation.put("kind", "fine_mapping_variant_posterior"); observation.put("primary", false);
                    observation.put("pair_id", id); observation.put("variant_id", row[table.column("variant_id")]);
                    observation.put("trait_role", trait); observation.put("pip", pip); observation.put("posterior_mean", mean);
                    observation.put("status", text(pair.get("status"))); observation.put("converged", Boolean.TRUE.equals(parameters.get("converged")));
                    observation.put("required_manuscript_caveat", "Fine-mapping PIPs and posterior means condition on the observed regional variants, allele alignment, LD and model. They are not association P values, independent replication or proof of biological causality; convergence and source coverage limit interpretation.");
                    observation.put("source_ids", List.of(source(artifact, name)));
                    observations.computeIfAbsent(key(artifact.hypothesis, feature), ignored -> new ArrayList<>()).add(observation);
                }
            }
        }
    }

    private void nativePairReuseContext(Artifact artifact) throws IOException {
        if (!Set.of("mendelian_randomization", "colocalization", "fine_mapping").contains(artifact.type)
                || !map(artifact.manifest.get("output_sha256")).containsKey("genetic-evidence.yaml")) return;
        var document = StudyYaml.loadArtifact(path(artifact, "genetic-evidence.yaml"));
        String source = source(artifact, "genetic-evidence.yaml");
        for (Object raw : list(document.get("pairs"))) {
            var pair = map(raw);
            if (!Boolean.TRUE.equals(pair.get("reused_native_result"))) continue;
            var observation = new LinkedHashMap<String,Object>();
            observation.put("kind", "native_genetic_pair_reuse"); observation.put("primary", false);
            observation.put("pair_id", text(pair.get("pair_id"))); observation.put("operation_type", artifact.type);
            observation.put("reused_native_result", true); observation.put("native_pair_attempted_this_run", false);
            observation.put("execution_origin", "native_prior_run"); observation.put("status", text(pair.get("status")));
            var reuse = map(pair.get("native_pair_reuse"));
            for (String field : List.of("key", "source_receipt", "source_receipt_sha256", "source_manifest_sha256"))
                observation.put(field, text(reuse.get(field)));
            observation.put("required_manuscript_caveat", StudyGeneticPairCache.CAVEAT);
            observation.put("source_ids", List.of(source));
            observations.computeIfAbsent(key(artifact.hypothesis, text(pair.get("feature_id"))), ignored -> new ArrayList<>()).add(observation);
        }
    }

    private void networkContext(Artifact artifact) throws IOException {
        var files=map(artifact.manifest.get("output_sha256"));String caveat=text(map(artifact.manifest.get("details")).get("required_manuscript_caveat"));
        if(artifact.type.equals("network_modules")&&files.containsKey("network-modules.tsv")){
            var table=DelimitedData.readResult(path(artifact,"network-modules.tsv"));String source=source(artifact,"network-modules.tsv");
            var details=map(artifact.manifest.get("details"));String prefix="pathway_gene_overlap".equals(details.get("source_network_type"))&&!text(details.get("source_kind")).isBlank()?text(details.get("source_kind"))+":":"";
            for(String[] row:table.rows())attach(artifact.hypothesis,Map.of("feature_id",prefix+row[table.column("node_id")],"module_id",row[table.column("module_id")],"module_type",row[table.column("module_type")],"dataset_id",details.getOrDefault("dataset_id",""),"kind","network_membership","status",artifact.manifest.get("state"),"required_manuscript_caveat",caveat),"",source,0);
        }
        if(!artifact.type.equals("module_gene_ontology")||!"complete".equals(artifact.manifest.get("state"))||!files.containsKey("module-gene-ontology.tsv"))return;
        Map<String,List<String>> symbols=new LinkedHashMap<>();
        for(var entry:annotations.entrySet())if(entry.getKey().startsWith(artifact.hypothesis+"\u001f")){String symbol=text(entry.getValue().get("gene_symbol"));if(!symbol.isBlank())symbols.computeIfAbsent(symbol,k->new ArrayList<>()).add(entry.getKey().substring(artifact.hypothesis.length()+1));}
        var table=DelimitedData.readResult(path(artifact,"module-gene-ontology.tsv"));String source=source(artifact,"module-gene-ontology.tsv");
        for(String[] row:table.rows())for(String symbol:row[table.column("hit_genes")].split(";"))for(String feature:symbols.getOrDefault(symbol,List.of())){
            var observation=new LinkedHashMap<String,Object>();observation.put("feature_id",feature);observation.put("kind","module_GO_context");observation.put("required_manuscript_caveat",caveat);
            for(String field:List.of("module_id","module_type","term_id","term_name","p_value","adjusted_p","universe_genes","module_genes","term_genes","overlap_genes"))observation.put(field,row[table.column(field)]);
            var details=map(artifact.manifest.get("details"));var family=map(details.get("resolved_family"));
            observation.put("family_size",family.getOrDefault("tested_count",table.rows().size()));
            observation.put("family_coverage",family.getOrDefault("coverage","complete_native_scoped_family"));
            observation.put("adjustment",family.getOrDefault("adjustment",details.getOrDefault("adjustment","not_reported")));
            for(String field:List.of("network_id","log_p_value","log_adjusted_p"))if(table.header().contains(field))observation.put(field,row[table.column(field)]);
            observation.put("execution_origin",details.getOrDefault("execution_origin","jlinalg"));attach(artifact.hypothesis,observation,"",source,0);
        }
    }

    private void mrFeatureContext(Artifact artifact)throws IOException {
        if(!artifact.type.equals("mendelian_randomization"))return;
        var files=map(artifact.manifest.get("output_sha256"));
        if(files.containsKey("mr-feature-dispositions.tsv")) {
            String source=source(artifact,"mr-feature-dispositions.tsv");var table=DelimitedData.readResult(path(artifact,"mr-feature-dispositions.tsv"));
            for(String[] row:table.rows()) {
                var observation=new LinkedHashMap<String,Object>();observation.put("kind","mr_feature_coverage_context");observation.put("primary",false);observation.put("source_ids",List.of(source));
                for(String field:List.of("disposition","primary_disposition","reason","annotation_status","association_p_value","association_adjusted_p","association_adjustment","association_testing_denominator","association_source_sha256"))if(table.header().contains(field))observation.put(field,row[table.column(field)]);
                observations.computeIfAbsent(key(artifact.hypothesis,row[table.column("feature_id")]),ignored->new ArrayList<>()).add(observation);
            }
        }
        if(!files.containsKey("genetic-evidence.yaml"))return;
        var document=StudyYaml.loadArtifact(path(artifact,"genetic-evidence.yaml"));String source=source(artifact,"genetic-evidence.yaml");
        for(Object raw:list(document.get("pairs"))) {
            var pair=map(raw);var location=map(pair.get("measured_feature_location"));if(location.isEmpty())continue;
            var observation=new LinkedHashMap<String,Object>();observation.put("kind","mr_feature_location_context");observation.put("primary",false);observation.put("source_ids",List.of(source));
            for(String field:List.of("feature_id","genome_build","chromosome","start","end","coordinate_system","origin","source_id","citation","rationale","original_annotation_status","gene_annotation_inferred","interpretation"))if(location.containsKey(field))observation.put(field,location.get(field));
            observations.computeIfAbsent(key(artifact.hypothesis,text(pair.get("feature_id"))),ignored->new ArrayList<>()).add(observation);
        }
    }
    private void attach(String hypothesis, Object value, String inherited, String source, int depth) {
        if (depth > 30) throw new IllegalArgumentException("Finding context nesting exceeds the supported depth");
        if (value instanceof List<?> values) { for (Object item : values) attach(hypothesis, item, inherited, source, depth + 1); return; }
        if (!(value instanceof Map<?, ?>)) return;
        var row = map(value); String feature = text(row.getOrDefault("feature_id", inherited));
        if (!feature.isBlank()) {
            Map<String, Object> observation = new LinkedHashMap<>();
            for(String field:List.of("reused_supplied_result","supplied_coverage_reserved","analysis_executed_by_jlinalg","execution_origin","analysis_id","source_family_id","source_adjustment","source_result_sha256")) {
                Object publicValue=row.get(field);if(publicValue instanceof Boolean)observation.put(field,publicValue);
                else if(publicValue instanceof String label){if(label.length()>4000)throw new IllegalArgumentException("MR source context exceeds the extraction budget");observation.put(field,label);}
            }
            for (String field : List.of("mask_id", "test", "overlap_id", "source_adjusted_p", "source_testing_denominator", "dataset_id", "variant_id", "gene_id", "transcript_id", "consequence", "scorer", "ontology_curie", "raw_score", "quantile_score", "model_version", "resource_version", "kind", "status", "method", "evidence_grade", "required_manuscript_caveat", "interpretation", "excerpt", "excerpt_status", "assessment", "doi", "pmid", "title", "search_strategy", "phenotype", "beta", "estimate", "se", "p_value", "p", "lower", "upper", "n_instruments", "f_statistic", "pp_h4", "pp_h3", "PP.H4.abf", "PP.H3.abf", "converged", "requested_estimator", "primary", "standard_error", "causal_p_value", "ci_lower", "ci_upper", "instruments", "adjustment", "adjusted_p", "log_p_value", "log_adjusted_p", "family_size", "family_status", "family_coverage", "network_id", "significant_primary", "mr_adjustment_status", "module_id", "module_type", "term_id", "term_name", "universe_genes", "module_genes", "term_genes", "overlap_genes", "set_id", "cohort", "role", "in_universe", "source_estimable", "in_selection_family", "discovery_candidate", "same_sign_reference", "selected", "metric", "metric_value", "threshold", "threshold_comparison", "in_alert_scope", "high_heterogeneity", "association_adjusted_p", "n_sources", "q", "q_df", "q_p_value", "i_squared", "tau_squared", "q_definition", "in_comparison_universe", "primary_beta", "sensitivity_beta", "beta_difference", "primary_se", "sensitivity_se", "primary_ci_lower", "primary_ci_upper", "sensitivity_ci_lower", "sensitivity_ci_upper", "primary_p_value", "sensitivity_p_value", "primary_adjusted_p", "sensitivity_adjusted_p", "association_threshold_changed", "effect_direction", "primary_n_sources", "sensitivity_n_sources", "primary_q", "sensitivity_q", "primary_q_df", "sensitivity_q_df", "primary_q_p_value", "sensitivity_q_p_value", "primary_i_squared", "sensitivity_i_squared", "primary_tau_squared", "sensitivity_tau_squared", "primary_metric", "sensitivity_metric", "primary_high", "sensitivity_high", "action", "variant"))
                if (row.get(field) instanceof String text) {
                    if (text.length() > 16000) throw new IllegalArgumentException("Finding context field exceeds the declared extraction budget: " + field);
                    observation.put(field, text);
                }
                else if (row.get(field) instanceof Number number && Double.isFinite(number.doubleValue())) observation.put(field, number);
                else if (row.get(field) instanceof Boolean valueBoolean) observation.put(field, valueBoolean);
            if (!observation.isEmpty()) { observation.put("source_ids", List.of(source)); observations.computeIfAbsent(key(hypothesis, feature), ignored -> new ArrayList<>()).add(observation); }
        }
        for (Object nested : row.values()) if (nested instanceof Map<?, ?> || nested instanceof List<?>) attach(hypothesis, nested, feature, source, depth + 1);
    }

    private String source(Artifact artifact, String relative) {
        String sha = text(map(artifact.manifest.get("output_sha256")).get(relative));
        String id = "source-" + hash(text(artifact.manifest.get("operation_id")) + "\n" + relative + "\n" + sha).substring(0, 24);
        sources.putIfAbsent(id, Map.of("operation_id", artifact.manifest.get("operation_id"), "hypothesis", artifact.hypothesis, "operation_type", artifact.type,
            "artifact", context.project().relativize(artifact.directory.resolve(relative)).toString().replace('\\', '/'), "sha256", sha));
        return id;
    }
    private Path path(Artifact artifact, String name) throws IOException { return verified(artifact.directory, name, text(map(artifact.manifest.get("output_sha256")).get(name))); }
    static Path verified(Path directory, String name, String expected) throws IOException {
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path) || !path.toRealPath().startsWith(directory.toRealPath()) || !FollowupSupport.hash(path).equals(expected)) throw new IOException("Missing or changed finding evidence: " + path);
        return path;
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private void colocalizationContext(Artifact artifact)throws IOException {
        if (!artifact.type.equals("colocalization")) return;
        var files=map(artifact.manifest.get("output_sha256"));var details=map(artifact.manifest.get("details"));
        for(String filename:List.of("colocalization-results.tsv","colocalization-feature-dispositions.tsv")){
            if(!files.containsKey(filename))continue;var table=DelimitedData.readResult(path(artifact,filename));if(!table.header().contains("feature_id"))continue;
            for(String[] row:table.rows()){
                String feature=row[table.column("feature_id")];if(feature.isBlank())continue;
                var observation=new LinkedHashMap<String,Object>();
                for(String field:table.header())if(StudyColocalizationReview.COLUMNS.contains(field)||List.of("reason","execution_origin","method","source_id","converged","coverage","phenotype","scope").contains(field))observation.put(field,row[table.column(field)]);
                observation.put("kind",filename.equals("colocalization-results.tsv")?"colocalization_signal_pair":"colocalization_disposition");observation.put("primary",false);observation.put("operation_type","colocalization");
                String grade=text(observation.getOrDefault("evidence_grade",details.getOrDefault("evidence_grade","source_coverage_unverified")));if(grade.isBlank())grade="source_coverage_unverified";observation.put("evidence_grade",grade);
                String caveat=(text(observation.get("required_manuscript_caveat"))+" "+text(details.get("required_manuscript_caveat"))+" "+(grade.equals("exploratory_partial_region_major_limitation")?StudyColocalizationReview.PARTIAL:StudyColocalizationReview.CAVEAT)).strip();observation.put("required_manuscript_caveat",caveat);
                observation.put("source_ids",List.of(source(artifact,filename)));String key=key(artifact.hypothesis,feature);findings.computeIfAbsent(key,ignored->finding(artifact.hypothesis,feature));observations.computeIfAbsent(key,ignored->new ArrayList<>()).add(observation);
            }
        }
    }
    private void proteinContext(Artifact artifact)throws IOException {
        if(!Set.of("protein_structure","protein_structure_assessment").contains(artifact.type)||!map(artifact.manifest.get("output_sha256")).containsKey("protein-structures.tsv"))return;
        var table=DelimitedData.readResult(path(artifact,"protein-structures.tsv"));if(!table.header().contains("feature_id"))return;
        int ordinal=0;for(String[] row:table.rows()){ordinal++;String feature=row[table.column("feature_id")];if(feature.isBlank())continue;String k=key(artifact.hypothesis,feature);findings.computeIfAbsent(k,ignored->finding(artifact.hypothesis,feature));var observation=new LinkedHashMap<String,Object>();for(String field:List.of("protein_id","isoform_id","taxon_id","sequence_sha256","sequence_version","model_id","model_source","structure_kind","method","model_version","seed","chain","sequence_match","target_length","observed_residues","coverage","confidence_residues","mean_plddt","fraction_plddt_lt50","fraction_plddt_50_70","fraction_plddt_70_90","fraction_plddt_ge90","pae_dimension","assay_mapping_status","assay_mapping_source","assay_mapping_limitations","source_sha256","pae_sha256","status","limitations"))if(table.header().contains(field))observation.put(field,row[table.column(field)]);observation.put("kind","protein_structure_context");observation.put("primary",false);observation.put("row_number",ordinal);observation.put("source_ids",List.of(source(artifact,"protein-structures.tsv")));observation.put("required_manuscript_caveat",StudyProteinStructures.CAVEAT);observations.computeIfAbsent(k,ignored->new ArrayList<>()).add(observation);}
    }
    private void cohortContext(Artifact artifact)throws IOException {
        if(artifact.type.equals("rare_cohort_overlap")&&!"complete".equals(artifact.manifest.get("state")))return;
        if(artifact.type.equals("sensitivity_analysis")){
            for(Object raw:list(map(map(artifact.manifest.get("details")).get("paired_meta_influence")).get("comparisons"))){var comparison=map(raw);String filename=text(comparison.get("feature_table"));if(!map(artifact.manifest.get("output_sha256")).containsKey(filename))throw new IOException("Paired influence table is not sealed in its producer");var table=DelimitedData.readResult(path(artifact,filename));for(String[] row:table.rows()){var observation=new LinkedHashMap<String,Object>();for(String field:List.of("feature_id","in_comparison_universe","status","primary_beta","sensitivity_beta","beta_difference","primary_se","sensitivity_se","primary_ci_lower","primary_ci_upper","sensitivity_ci_lower","sensitivity_ci_upper","primary_p_value","sensitivity_p_value","primary_adjusted_p","sensitivity_adjusted_p","association_threshold_changed","effect_direction","primary_n_sources","sensitivity_n_sources","primary_q","sensitivity_q","primary_q_df","sensitivity_q_df","primary_q_p_value","sensitivity_q_p_value","primary_i_squared","sensitivity_i_squared","primary_tau_squared","sensitivity_tau_squared","metric","primary_metric","sensitivity_metric","primary_high","sensitivity_high"))if(table.header().contains(field))observation.put(field,row[table.column(field)]);observation.put("kind","cohort_influence_context");observation.put("action",comparison.get("action"));observation.put("variant",comparison.get("variant"));observation.put("primary",false);observation.put("required_manuscript_caveat",StudyHeterogeneity.CAVEAT);attach(artifact.hypothesis,observation,"",source(artifact,filename),0);}}
            return;
        }
        String file=Set.of("cohort_overlap","rare_cohort_overlap").contains(artifact.type)?"cohort-overlap-membership.tsv":artifact.type.equals("heterogeneity_review")?"heterogeneity-features.tsv":"";if(file.isBlank()||!map(artifact.manifest.get("output_sha256")).containsKey(file))return;var table=DelimitedData.readResult(path(artifact,file));
        for(String[] row:table.rows()){var observation=new LinkedHashMap<String,Object>();for(String field:List.of("feature_id","mask_id","test","overlap_id","source_adjusted_p","source_testing_denominator","set_id","cohort","role","in_universe","source_estimable","in_selection_family","p_value","adjusted_p","beta","discovery_candidate","same_sign_reference","selected","status","metric","metric_value","threshold","threshold_comparison","adjustment","in_alert_scope","high_heterogeneity","association_adjusted_p","n_sources","q","q_df","q_p_value","i_squared","tau_squared","q_definition"))if(table.header().contains(field))observation.put(field,row[table.column(field)]);observation.put("kind",artifact.type.equals("rare_cohort_overlap")?"rare_cohort_overlap_context":artifact.type.equals("cohort_overlap")?"cohort_overlap_context":"heterogeneity_context");observation.put("primary",false);observation.put("required_manuscript_caveat",artifact.type.equals("rare_cohort_overlap")?StudyRareCohortOverlap.CAVEAT:artifact.type.equals("cohort_overlap")?StudyCohortOverlap.CAVEAT:StudyHeterogeneity.CAVEAT);String feature=text(observation.get("feature_id"));if(!feature.isBlank())findings.computeIfAbsent(key(artifact.hypothesis,feature),ignored->finding(artifact.hypothesis,feature));attach(artifact.hypothesis,observation,"",source(artifact,file),0);}
        if(artifact.type.equals("heterogeneity_review"))return;
    }
    private void scoreContext(Artifact artifact)throws IOException {
        if(!Set.of("risk_score_evaluation","risk_score_related_phenotypes").contains(artifact.type))return;
        boolean partialPerformance="waiting_for_input".equals(artifact.manifest.get("state"))&&Boolean.TRUE.equals(map(artifact.manifest.get("details")).get("score_evaluation_complete"));
        if(!"complete".equals(artifact.manifest.get("state"))&&!partialPerformance)return;
        var outputs=map(artifact.manifest.get("output_sha256"));var details=map(artifact.manifest.get("details"));
        var policy=list(context.study().get("hypotheses")).stream().map(StudyPublicEvidence::map).filter(h->artifact.hypothesis.equals(h.get("id"))).map(h->map(map(h.get("analysis")).get("risk_score"))).findFirst().orElse(Map.of());
        var identifierColumns=new java.util.HashSet<String>(list(policy.get("group_columns")).stream().map(StudyPublicEvidence::text).toList());identifierColumns.add(text(policy.getOrDefault("id_column","sample_id")));identifierColumns.add(text(policy.getOrDefault("partition_column","partition")));if(policy.containsKey("resampling_group"))identifierColumns.add(text(policy.get("resampling_group")));
        var privateLabels=new java.util.HashSet<String>();if(outputs.containsKey("participant-partitions.tsv")){var membership=DelimitedData.readResult(path(artifact,"participant-partitions.tsv"));for(String[] row:membership.rows()){privateLabels.add(row[membership.column("sample_id")]);privateLabels.add(row[membership.column("resampling_unit")]);}}
        for(String filename:List.of("risk-score-evaluation.tsv","related-phenotypes.tsv"))if(outputs.containsKey(filename)&&(!partialPerformance||filename.equals("risk-score-evaluation.tsv"))){
            var table=DelimitedData.readResult(path(artifact,filename));int ordinal=0;
            for(String[] row:table.rows()){ordinal++;
                if(filename.equals("risk-score-evaluation.tsv")&&table.header().contains("subgroup_column")&&!row[table.column("subgroup_column")].equals("all")){
                    String dimension=row[table.column("subgroup_column")],level=table.header().contains("subgroup")?row[table.column("subgroup")]:"";double count=numeric(row,table,"n");
                    if(identifierColumns.contains(dimension)||privateLabels.contains(level)||!Double.isFinite(count)||count<10||!Double.isFinite(numeric(row,table,"estimate")))continue;
                }
                String id=table.header().contains("score_id")?row[table.column("score_id")]:text(details.getOrDefault("score_id","score"));String feature="risk_score:"+id,k=key(artifact.hypothesis,feature);var finding=findings.computeIfAbsent(k,ignored->finding(artifact.hypothesis,feature));finding.put("finding_type","risk_score");
                var observation=new LinkedHashMap<String,Object>();for(String field:List.of("score_id","phenotype","family","evaluation_population","subgroup_column","subgroup","metric","estimate","ci_lower","ci_upper","n","independent_units","bootstrap_valid","bootstrap_attempted","status","beta","se","p_value","adjusted_p","adjustment","rmse","auc","r2","required_manuscript_caveat"))if(table.header().contains(field))observation.put(field,row[table.column(field)]);
                observation.put("source_ids",List.of(source(artifact,filename)));observation.put("row_number",ordinal);observation.put("operation_type",artifact.type);observation.put("kind",filename.equals("related-phenotypes.tsv")?"score_related_phenotype":"held_out_score_performance");observation.put("primary",true);observation.putIfAbsent("adjustment",filename.equals("risk-score-evaluation.tsv")?"not_applicable_performance_metric":details.getOrDefault("related_adjustment",map(details.get("resolved_family")).getOrDefault("adjustment","not_applicable")));observation.put("model_refitted",details.getOrDefault("model_refitted","source_not_declared"));observation.put("independence_evidence_status",details.getOrDefault("independence_evidence_status","external_source_requires_appraisal"));if(details.containsKey("required_manuscript_caveat"))observation.put("required_manuscript_caveat",details.get("required_manuscript_caveat"));
                var nested=map(details.get("nested_validation"));
                if("complete".equals(nested.get("status"))&&outputs.containsKey("nested-validation.yaml")){
                    var aggregate=new LinkedHashMap<String,Object>();for(String field:List.of("method","status","family","development_rows","development_units","selection_loss","loss_weighting","tie_break","performed_fit_calls","outer_prediction_rows","outer_row_weighted_loss","selected_final_lambda","evaluation_used_for_selection","required_manuscript_caveat"))if(nested.containsKey(field))aggregate.put(field,nested.get(field));
                    aggregate.put("population","out_of_fold_development; distinct from independent external evaluation");observation.put("nested_validation_context",aggregate);observation.put("source_ids",List.of(source(artifact,filename),source(artifact,"nested-validation.yaml")));
                }
                var genetic=map(details.get("genotype_score"));
                if("complete_in_every_registered_source".equals(genetic.get("model_coverage"))&&outputs.containsKey("genetic-score-application.yaml")){
                    var aggregate=new LinkedHashMap<String,Object>();for(String field:List.of("model_variants","genome_build","coefficient_scale","missing","prediction_link","score_center","score_scale","minimum_observed_fraction","decoded_genotype_cells","source_variants_scanned","source_formats","assumed_diploid_dosage_cells","model_coverage","required_manuscript_caveat"))if(genetic.containsKey(field))aggregate.put(field,genetic.get(field));
                    aggregate.put("source_count",list(genetic.get("sources")).size());observation.put("genetic_score_context",aggregate);observation.put("source_ids",List.of(source(artifact,filename),source(artifact,"genetic-score-application.yaml")));
                }
                if(partialPerformance){observation.put("primary",false);observation.put("evidence_disposition","complete_performance_context_related_family_unresolved");observation.put("related_testing_family_complete",false);observation.put("source_operation_state","waiting_for_input");observation.put("required_manuscript_caveat",text(observation.get("required_manuscript_caveat"))+" Held-out performance is complete, while the registered related-phenotype family remains unresolved. These performance metrics do not establish a significant related-phenotype finding.");}
                if(filename.equals("related-phenotypes.tsv")){double q=numeric(row,table,"adjusted_p");boolean valid=validStatus(row,table)&&Double.isFinite(q)&&q>=0&&q<=1;observation.put("full_family_adjusted_p",finite(q));observation.put("testing_denominator",details.getOrDefault("related_phenotypes_registered",map(details.get("resolved_family")).getOrDefault("tested_count","unknown")));finding.put("estimable",Boolean.TRUE.equals(finding.get("estimable"))||valid);finding.put("significant",Boolean.TRUE.equals(finding.get("significant"))||valid&&q<alpha(artifact.hypothesis));}
                else if(Double.isFinite(numeric(row,table,"estimate")))finding.put("estimable",true);
                observations.computeIfAbsent(k,ignored->new ArrayList<>()).add(observation);
            }
        }
    }
    private static String key(String hypothesis, String feature) { return hypothesis + "\u001f" + feature; }
    private Map<String, Object> finding(String hypothesis, String feature) {
        var result = new LinkedHashMap<String, Object>(); result.put("finding_id", "finding-" + hash(key(hypothesis, feature)).substring(0, 24));
        result.put("hypothesis", hypothesis); result.put("feature_id", feature); result.put("estimable", false); result.put("significant", false);
        var h = list(context.study().get("hypotheses")).stream().map(StudyPublicEvidence::map).filter(item -> hypothesis.equals(item.get("id"))).findFirst().orElse(Map.of());
        var design = new LinkedHashMap<String, Object>();
        for (String field : List.of("outcome", "exposure", "covariates")) if (h.containsKey(field)) design.put(field, h.get(field));
        var analysis = map(h.get("analysis")); var model = new LinkedHashMap<String, Object>();
        for (String field : List.of("model", "family", "link", "formula")) if (map(analysis.get("association")).containsKey(field)) model.put(field, map(analysis.get("association")).get(field));
        design.put("association", model); design.put("multiple_testing", map(analysis.get("multiple_testing")));
        result.put("design", design); return result;
    }
    private double alpha(String hypothesis) {
        for (Object raw : list(context.study().get("hypotheses"))) if (hypothesis.equals(map(raw).get("id")))
            return ((Number) map(map(map(raw).get("analysis")).get("multiple_testing")).getOrDefault("alpha", .05)).doubleValue();
        return .05;
    }
    private Map<String,Object> analysis(String hypothesis) {
        return list(context.study().get("hypotheses")).stream().map(StudyPublicEvidence::map).filter(h->hypothesis.equals(h.get("id"))).map(h->map(h.get("analysis"))).findFirst().orElse(Map.of());
    }
    private String adjustment(String hypothesis) {
        for (Object raw : list(context.study().get("hypotheses"))) if (hypothesis.equals(map(raw).get("id")))
            return text(map(map(map(raw).get("analysis")).get("multiple_testing")).getOrDefault("method", "BH"));
        return "BH";
    }
    private boolean isGenetic(String hypothesis) {
        var primary=primarySelections.get(hypothesis);if(primary!=null&&Set.of("gwas","genomics").contains(text(map(map(primary.source().producer().manifest().get("details")).get("scope")).get("modality"))))return true;
        var selected = new LinkedHashSet<String>();
        for (Object raw : list(context.study().get("hypotheses"))) if (hypothesis.equals(map(raw).get("id")))
            map(map(raw).get("inputs")).values().forEach(ids -> list(ids).forEach(id -> selected.add(text(id))));
        return list(context.study().get("datasets")).stream().map(StudyPublicEvidence::map).anyMatch(d -> selected.contains(d.get("id")) && Set.of("gwas", "genomics").contains(d.get("type")));
    }
    private static Object finite(double value) { return Double.isFinite(value) ? value : "NA"; }
    private static boolean positiveNumber(Object value) { try { double number = Double.parseDouble(text(value)); return Double.isFinite(number) && number > 0; } catch (NumberFormatException ignored) { return false; } }
    private static double numeric(String[] row, DelimitedData table, String field) {
        if (!table.header().contains(field)) return Double.NaN;
        try { return Double.parseDouble(row[table.column(field)]); } catch (NumberFormatException ignored) { return Double.NaN; }
    }
    private static boolean validStatus(String[] row, DelimitedData table) { return !table.header().contains("status") || Set.of("ok", "single_cohort", "tested", "complete").contains(row[table.column("status")]); }
    private static boolean validEstimate(String[] row, DelimitedData table) {
        if(StudyResultShape.pOnly(table,row))return StudyResultShape.missingEffects(table,row);
        if (table.header().contains("result_type") && "unsigned_set".equals(row[table.column("result_type")])) return true;
        return Double.isFinite(numeric(row, table, "beta")) && Double.isFinite(numeric(row, table, "se")) && numeric(row, table, "se") > 0;
    }
    private static boolean primaryRow(String type, DelimitedData table, String[] row) {
        if (type.equals("meta_analysis") && table.header().contains("term")) return row[table.column("term")].equals("pooled");
        if (Set.of("rare_variant_analysis","rare_set_omnibus").contains(type)) return table.header().contains("is_primary_test") && row[table.column("is_primary_test")].equals("true");
        return true;
    }
    private static boolean selectedTable(String type, String relative, boolean hasPost) {
        if(type.equals("mendelian_randomization"))return relative.equals("mr-primary-results.tsv");
        if(type.equals("fine_mapping"))return relative.equals("fine-mapping-results.tsv");
        if(type.equals("gwas_enrichment"))return relative.equals("gwas-enrichment.tsv");
        if(type.equals("ranked_gsea"))return relative.equals("ranked-gsea.tsv");
        if (Set.of("validate_harmonize", "association").contains(type)) return relative.matches("(?:pre-effect-qc-results|post-effect-qc-results|standard-results)/[^/]+\\.tsv");
        if(type.equals("rare_set_omnibus"))return relative.equals("rare-omnibus-results.tsv");
        if (type.equals("rare_variant_analysis")) return Set.of("rare-results.tsv", "rare-replication.tsv", "rare-influence.tsv").contains(relative);
        if (type.equals("meta_analysis")) return relative.equals("meta-analysis.tsv");
        if (type.equals("discovery_meta_analysis")) return relative.equals("discovery-results.tsv");
        if (type.equals("discovery_replication")) return relative.equals("replication.tsv");
        if (type.equals("sensitivity_analysis")) return relative.endsWith("feature-sensitivity.tsv");
        return !relative.contains("/native/") && !relative.startsWith("native/");
    }
    private static Map<String, Object> caveat(String kind, String text, List<?> sources) { return Map.of("id", "caveat-" + hash(kind + text).substring(0, 20), "kind", kind, "text", text, "source_ids", sources); }
}
