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
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyPoolingPolicy;

/** Known incompatibility is never silently treated as between-cohort heterogeneity. */
final class StudyPoolingAudit {
    record Audit(Map<String, Object> details, List<StudyNeed> needs) {
        Result waiting() { return new Result(State.WAITING_FOR_INPUT, "Pooling or independent replication prerequisites require review", List.of("pooling-audit.yaml", "pooling-compatibility.tsv", "pooling-variant-compatibility.tsv"), details); }
    }
    static Audit assess(Context context, Map<String, Path> inputs, boolean replication) throws IOException {
        Set<String> selected = new LinkedHashSet<>(inputs.keySet());
        if (replication) {
            var roles=map(context.operation().get("roles"));
            for(String role:List.of("discovery","replication")) for(Object id:list(roles.get(role))) selected.add(text(id));
        }
        Files.createDirectories(context.outputDirectory());
        var policy = StudyPoolingPolicy.resolve(map(StudyResultTables.analysis(context).get("pooling")));
        var common = map(policy.get("effect_contract")); boolean correlated = !map(policy.get("dependence")).get("method").equals("independent");
        Map<String, Map<String, Object>> datasets = new LinkedHashMap<>();
        for (Object raw : list(context.study().get("datasets"))) { var dataset=map(raw); if(selected.contains(text(dataset.get("id")))) datasets.put(text(dataset.get("id")),dataset); }
        List<Object> issues = new ArrayList<>(); List<String[]> rows = new ArrayList<>();
        if (!replication && Set.of("meta_analysis","discovery_meta_analysis").contains(context.operation().get("type"))) {
            for(Object id:list(context.operation().get("datasets"))) for(Object raw:list(context.study().get("datasets"))) {
                var dataset=map(raw);
                if(id.equals(dataset.get("id"))&&org.jlinalg.study.StudyAnalysisReferences.isReference(dataset))
                    issues.add(Map.of("kind","pooled_reference_is_not_per_cohort_estimates","dataset",id));
            }
        }
        Map<String, Map<String, String>> signatures = new LinkedHashMap<>(); List<String> missing = new ArrayList<>();
        for (var entry : datasets.entrySet()) {
            var dataset = entry.getValue(); var effect = new LinkedHashMap<>(common); effect.putAll(map(dataset.get("effect_contract")));
            for (var declared : map(dataset.get("effect_contract")).entrySet()) if (common.containsKey(declared.getKey()) && !common.get(declared.getKey()).equals(declared.getValue())) issues.add(Map.of("kind", "dataset_contract_conflicts_with_common_contract", "dataset", entry.getKey(), "field", declared.getKey(), "common", common.get(declared.getKey()), "dataset_value", declared.getValue()));
            Map<String, String> signature = new LinkedHashMap<>();
            for (String field : StudyPoolingPolicy.EFFECT_FIELDS) {
                String value = text(effect.get(field)); signature.put(field, value);
                if (value.isBlank()) missing.add(entry.getKey() + ":" + field);
                rows.add(new String[]{entry.getKey(), field, value.isBlank() ? "NA" : value, map(dataset.get("effect_contract")).containsKey(field) ? "dataset_effect_contract" : common.containsKey(field) ? "hypothesis_effect_contract" : "unavailable"});
            }
            Path source=inputs.get(entry.getKey());
            if(source!=null){var table=DelimitedData.readResult(source);for(String field:List.of("effect_scale","effect_units"))if(table.header().contains(field)){
                var observed=new LinkedHashSet<String>();for(String[] row:table.rows()){String value=row[table.column(field)];if(!Set.of("","NA",".").contains(value))observed.add(value);}
                if(observed.size()>1)issues.add(Map.of("kind","mixed_source_"+field,"dataset",entry.getKey(),"source_values",List.copyOf(observed)));
                else if(observed.size()==1){String value=observed.iterator().next(),declared=signature.get(field);if(!declared.isBlank()&&!declared.equals(value))issues.add(Map.of("kind","source_contract_conflicts_with_declared_"+field,"dataset",entry.getKey(),"source_value",value,"declared",declared));signature.put(field,value);missing.remove(entry.getKey()+":"+field);rows.add(new String[]{entry.getKey(),field,value,"typed_result_field"});}
            }}
            for (String field : List.of("modality", "species", "assay", "tissue", "genome_build", "feature_id_type")) {
                String value = field.equals("modality") ? text(dataset.getOrDefault("modality", dataset.get("type"))) : text(dataset.get(field));
                signature.put(field, canonical(field, value)); rows.add(new String[]{entry.getKey(), field, value.isBlank() ? "NA" : value, "dataset_context"});
            }
            signatures.put(entry.getKey(), signature);
        }
        for (String field : signatures.values().stream().flatMap(row -> row.keySet().stream()).distinct().toList()) {
            Map<String, List<String>> values = new LinkedHashMap<>();
            signatures.forEach((id, row) -> { String value = row.get(field); if (!value.isBlank()) values.computeIfAbsent(value, ignored -> new ArrayList<>()).add(id); });
            if (values.size() > 1 && !map(policy.get("context_exceptions")).containsKey(field)) issues.add(Map.of("kind", "incompatible_" + field, "datasets_by_value", values));
        }
        if (!missing.isEmpty() && Boolean.TRUE.equals(policy.get("require_complete_contract"))) issues.add(Map.of("kind", "missing_effect_contract", "fields", missing));
        List<String> ids = new ArrayList<>(datasets.keySet());
        for (int i=0;i<ids.size();i++) for(int j=0;j<i;j++) {
            String left=ids.get(j),right=ids.get(i); Set<String> leftCohorts=cohorts(datasets.get(left)),rightCohorts=cohorts(datasets.get(right));
            var shared=new LinkedHashSet<>(leftCohorts); shared.retainAll(rightCohorts);
            if ((!shared.isEmpty() || sameResultFile(context,datasets.get(left),datasets.get(right))) && (replication || !correlated)) {
                if (!replication || crossesValidation(context,left,right)) issues.add(Map.of("kind","reused_cohort_or_source","datasets",List.of(left,right),"shared_cohorts",List.copyOf(shared)));
            }
        }
        for(Object raw:list(context.study().get("cohort_relationships"))) {
            var relation=map(raw); if ("retired".equals(relation.get("status"))) continue;
            boolean independent = "independent".equals(relation.get("type"));
            boolean observedOverlap = relation.get("shared_participants") instanceof Number count && count.doubleValue()>0 || relation.get("overlap_fraction") instanceof Number fraction && fraction.doubleValue()>0;
            if (independent && !observedOverlap) continue;
            Set<String> involved=new LinkedHashSet<>(list(relation.get("between")).stream().map(Object::toString).toList());
            for(int i=0;i<ids.size();i++)for(int j=0;j<i;j++){String left=ids.get(j),right=ids.get(i);if(cohorts(datasets.get(left)).stream().anyMatch(involved::contains)&&cohorts(datasets.get(right)).stream().anyMatch(involved::contains)&&(!correlated||replication&&crossesValidation(context,left,right)||independent&&observedOverlap))
                issues.add(Map.of("kind",independent&&observedOverlap?"contradictory_independence_declaration":replication?"dependent_discovery_validation":"unmodeled_sampling_dependence","datasets",List.of(left,right),"relationship",relation));}
        }
        if (!correlated || replication) for(Object raw:list(context.study().get("assumptions"))) {
            var assumption=map(raw); if(!"potentially_dependent".equals(assumption.get("type")))continue;
            Set<String> involved=new LinkedHashSet<>(list(assumption.get("between")).stream().map(Object::toString).toList());
            Set<String> selectedCohorts=new LinkedHashSet<>();datasets.values().forEach(dataset->selectedCohorts.addAll(cohorts(dataset)));
            boolean relevant = !replication || !correlated;
            if (replication && correlated) for (String left : ids) for (String right : ids) if (crossesValidation(context,left,right) && cohorts(datasets.get(left)).stream().anyMatch(involved::contains) && cohorts(datasets.get(right)).stream().anyMatch(involved::contains)) relevant = true;
            if(involved.size()>1&&selectedCohorts.containsAll(involved)&&relevant)issues.add(Map.of("kind","unresolved_sampling_dependence","assumption",assumption));
        }
        if (replication && correlated) {
            Path matrix = context.project().resolve(text(map(policy.get("dependence")).get("matrix"))).normalize();
            if (!Files.isRegularFile(matrix)) issues.add(Map.of("kind", "sampling_matrix_unavailable_for_validation_audit"));
            else try {
                var table = DelimitedData.readResult(matrix);
                for (String[] row : table.rows()) {
                    String left=row[table.column("dataset_a")],right=row[table.column("dataset_b")];
                    if (!selected.contains(left) || !selected.contains(right) || !crossesValidation(context,left,right)) continue;
                    double value = Double.parseDouble(row[table.column("value")]);
                    if (!Double.isFinite(value) || value != 0) issues.add(Map.of("kind", "sampling_matrix_links_discovery_validation", "datasets", List.of(left,right), "feature_id", row[table.column("feature_id")]));
                }
            } catch (IllegalArgumentException invalid) { issues.add(Map.of("kind", "sampling_matrix_invalid_for_validation_audit")); }
        }
        List<String[]> variantIssues = new ArrayList<>(); List<String> missingVariantContracts = new ArrayList<>();
        Map<String, Map<String, String>> variantDefinitions = new LinkedHashMap<>();
        for (var input : inputs.entrySet()) {
            var dataset = datasets.get(input.getKey()); if (dataset == null || !Set.of("gwas", "genomics").contains(text(dataset.get("type")))) continue;
            var table = DelimitedData.readResult(input.getValue());
            if (!table.header().containsAll(List.of("effect_allele", "other_allele"))) { missingVariantContracts.add(input.getKey() + ":effect_allele/other_allele"); variantIssues.add(new String[]{"*", "effect_allele/other_allele", input.getKey(), "NA", "NA", "unavailable_effect_coding"}); }
            for (String[] values : table.rows()) {
                String feature = values[table.column("feature_id")]; var reference = variantDefinitions.computeIfAbsent(feature, ignored -> new LinkedHashMap<>());
                for (String field : List.of("chromosome", "position", "reference_allele", "alternate_allele", "effect_allele", "other_allele", "genome_build")) {
                    if (!table.header().contains(field)) continue; String value = values[table.column(field)];
                    if (Set.of("", "NA", ".").contains(value)) { if (field.equals("effect_allele") || field.equals("other_allele")) { missingVariantContracts.add(input.getKey() + ":" + feature + ":" + field); variantIssues.add(new String[]{feature, field, input.getKey(), "NA", "NA", "unavailable_effect_coding"}); } continue; }
                    if (field.endsWith("allele")) value = value.toUpperCase(Locale.ROOT);
                    if (field.equals("chromosome")) value = value.replaceFirst("(?i)^chr", "");
                    if (field.equals("genome_build")) value = canonical(field, value);
                    String old = reference.putIfAbsent(field, value);
                    if (old != null && !old.equals(value)) variantIssues.add(new String[]{feature, field, input.getKey(), value, old, "conflicting_identity_or_effect_coding_no_automatic_flip"});
                }
            }
        }
        long variantConflicts = variantIssues.stream().filter(row -> row[5].equals("conflicting_identity_or_effect_coding_no_automatic_flip")).count();
        if (variantConflicts > 0) issues.add(Map.of("kind", "incompatible_variant_identity_or_effect_coding", "conflicting_fields", variantConflicts, "artifact", "pooling-variant-compatibility.tsv"));
        if (!missingVariantContracts.isEmpty() && Boolean.TRUE.equals(policy.get("require_complete_contract"))) issues.add(Map.of("kind", "missing_variant_effect_coding", "missing_fields", missingVariantContracts.size()));
        List<StudyNeed> needs=new ArrayList<>();
        if(!issues.isEmpty()) needs.add(StudyNeed.review("pooling-compatibility:"+context.operation().get("hypothesis"),"Resolve incompatible or dependent cohort inference", "See pooling-audit.yaml. Use separate compatible hypotheses/analysis blocks, properly harmonized effects, or a supported sampling covariance model. A covariance model does not make dependent validation independent.",true));
        Map<String,Object> details=new LinkedHashMap<>(); details.put("schema_version",1); details.put("datasets",ids); details.put("policy",policy); details.put("issues",issues); details.put("missing_effect_fields",missing); details.put("needs",needs.stream().map(StudyNeed::document).toList());
        details.put("missing_variant_effect_coding", missingVariantContracts.size());
        details.put("compatibility_status", !issues.isEmpty()?"incompatible_or_unresolved":missing.isEmpty()&&missingVariantContracts.isEmpty()?"declared_compatible":"partially_declared_not_certified");
        details.put("required_manuscript_caveat", (missing.isEmpty()?"Effect contracts are declarations; source/model calibration and covariance validity require scientific review.":"Effect comparability is incompletely declared. Missing units, transformations or estimands cannot be inferred from assay units, matching IDs or effect correlation.") + (missingVariantContracts.isEmpty()?"":" Variant effect-allele coding is incompletely declared; matching IDs do not establish allele orientation."));
        FollowupSupport.table(context.outputDirectory().resolve("pooling-compatibility.tsv"),List.of("dataset_id","field","value","source"),rows); write(context.outputDirectory().resolve("pooling-audit.yaml"),details);
        FollowupSupport.table(context.outputDirectory().resolve("pooling-variant-compatibility.tsv"),List.of("feature_id","field","dataset_id","value","reference_value","disposition"),variantIssues);
        return new Audit(details,needs);
    }
    static String canonical(String field,String value) {
        if(field.equals("effect_units"))return value.strip();
        String key=value.strip().toLowerCase(Locale.ROOT);
        if(field.equals("feature_id_type")&&Set.of("auto","unknown").contains(key))return "";
        if(field.equals("species"))return switch(key){case "human","homo sapiens","9606"->"9606";case "mouse","mus musculus","10090"->"10090";default->key;};
        if(field.equals("modality"))return switch(key){case "gwas","genomics"->"genomics";case "expression","transcriptomics"->"transcriptomics";default->key;};
        if(field.equals("genome_build")){String build=StudyVariantUniverse.build(value);if(!build.isBlank())return build;}
        return key;
    }
    static Set<String> cohorts(Map<String,Object> dataset){var result=new LinkedHashSet<>(list(dataset.get("cohorts")).stream().map(Object::toString).toList());if(!text(dataset.get("cohort")).isBlank())result.add(text(dataset.get("cohort")));return result;}
    private static boolean sameResultFile(Context context,Map<String,Object> left,Map<String,Object> right)throws IOException{
        String a=text(map(left.get("files")).get("results")),b=text(map(right.get("files")).get("results"));return !a.isBlank()&&!b.isBlank()&&Files.exists(context.project().resolve(a))&&Files.exists(context.project().resolve(b))&&Files.isSameFile(context.project().resolve(a),context.project().resolve(b));
    }
    static boolean crossesValidation(Context context,String left,String right){var roles=map(context.operation().get("roles"));if(roles.isEmpty())for(Object raw:list(context.study().get("hypotheses")))if(map(raw).get("id").equals(context.operation().get("hypothesis")))roles=map(map(raw).get("inputs"));return list(roles.get("discovery")).contains(left)&&list(roles.get("replication")).contains(right)||list(roles.get("discovery")).contains(right)&&list(roles.get("replication")).contains(left);}
}
