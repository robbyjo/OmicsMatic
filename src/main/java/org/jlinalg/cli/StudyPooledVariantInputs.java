/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyAnalysisReferences;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyVariantFollowupPolicy;

/** One verified official pool, with no reconstruction of its component cohort estimates. */
final class StudyPooledVariantInputs {
    private StudyPooledVariantInputs() { }
    static void add(Context c, StudyVariantUniverse catalog, Set<String> selected) throws IOException {
        if (StudyPrimaryResults.producers(c).stream().noneMatch(p -> p.type().equals("meta_analysis")) && !StudyPrimaryResults.partialPoolExpected(c)) return;
        var primary = StudyPrimaryResults.official(c);
        if (primary == null || !primary.source().producer().type().equals("meta_analysis")) return;
        String metadata = text(primary.receipt().get("source_metadata_snapshot"));
        boolean supplied = !metadata.isBlank() && !metadata.equals("not_supplied");
        var source = supplied ? StudyYaml.loadArtifact(c.outputDirectory().resolve(metadata)) : Map.<String,Object>of();
        var scope = map(source.get("scope"));
        var types = new LinkedHashSet<String>(); var namespaces = new LinkedHashSet<String>(); var builds = new LinkedHashSet<String>();
        for (var dataset : StudyBioinformatics.datasets(c)) {
            if (!selected.contains(text(dataset.get("id")))) continue;
            if (StudyAnalysisReferences.isReference(dataset) && !text(source.get("artifact_id")).equals(text(dataset.get("supplied_result_id")))) continue;
            types.add(text(dataset.get("type"))); namespaces.add(text(dataset.get("feature_id_type")));
            if (!text(dataset.get("genome_build")).isBlank()) builds.add(StudyVariantUniverse.build(text(dataset.get("genome_build"))));
        }
        String modality = scope.containsKey("modality") ? text(scope.get("modality")) : types.size()==1 ? types.iterator().next() : "unknown";
        String namespace = scope.containsKey("feature_id_type") ? text(scope.get("feature_id_type")) : namespaces.size()==1 ? namespaces.iterator().next() : namespaces.isEmpty()?"":"incompatible_declared_namespaces";
        boolean applicable = StudyVariantFollowupPolicy.variantScope(modality, namespace);
        var audit = new LinkedHashMap<String,Object>();
        audit.put("source_kind", "official_meta_analysis"); audit.put("source_operation_id", primary.source().producer().id());
        audit.put("source_sha256", primary.source().sha256()); audit.put("modality", modality); audit.put("feature_id_type", namespace);
        audit.put("variant_scope_applicable", applicable); audit.put("selected_term", primary.term());
        var originalFamily = map(source.get("family"));
        audit.put("source_testing_family", org.jlinalg.study.StudySuppliedResultPolicy.aggregateFamily(originalFamily));
        audit.put("selection_testing_family", org.jlinalg.study.StudySuppliedResultPolicy.aggregateFamily(primary.family()));
        catalog.pooledSources.add(audit);
        if (!applicable) {
            Set<String> nonvariants=Set.of("gene_symbol","ensembl_gene","probe","ensembl_transcript","protein","uniprot","uniprot_accession","uniprot_isoform","metabolite");
            catalog.unresolvedScope = !nonvariants.contains(namespace) && (Set.of("gwas","genomics").contains(modality)||Set.of("","unknown","auto").contains(modality));
            audit.put("status", catalog.unresolvedScope ? "variant_scope_requires_declaration" : "nonvariant_primary");
            return;
        }
        String declaredBuild = text(scope.get("genome_build"));
        String build = declaredBuild.isBlank() ? builds.size()==1 ? builds.iterator().next() : "" : StudyVariantUniverse.build(declaredBuild);
        if (!declaredBuild.isBlank() && build.isBlank()) throw new IOException("Unsupported primary-pool genome build: " + declaredBuild);
        Path snapshot = c.outputDirectory().resolve("primary-pooled-results.tsv");
        if (!Files.exists(snapshot)) Files.copy(primary.source().path(), snapshot);
        if (!FollowupSupport.hash(snapshot).equals(primary.source().sha256()) || !FollowupSupport.hash(primary.source().path()).equals(primary.source().sha256()))
            throw new IOException("Primary pool changed while preserving its variant catalogue source");
        catalog.sourceHashes.put("official_meta_analysis:"+primary.source().producer().id(), primary.source().sha256());
        boolean complete = supplied ? "complete".equals(originalFamily.get("coverage")) && primary.completeObservedFamily()
            && (!(originalFamily.get("tested_count") instanceof Number n)||n.longValue()<=primary.original().rows().size()) : primary.completeObservedFamily();
        catalog.completeSourceCoverage &= complete;
        audit.put("status", "pooled_variant_catalogue"); audit.put("complete_source_family", complete);
        audit.put("source_rows", primary.original().rows().size()); audit.put("selected_contrast_rows", primary.table().rows().size());
        audit.put("eligible_statistical_rows", primary.probabilities().size()); audit.put("significance_filter_applied", false);
        audit.put("source_coverage", supplied ? originalFamily.getOrDefault("coverage","unknown") : "native_scoped_pool");
        audit.put("required_manuscript_caveat", "Annotation covers the available registered primary contrast, without P/FDR selection. Source completeness and QC are declarations, not independently reproduced. A selected/unknown or incomplete export cannot establish complete GWAS annotation. Predictions do not validate an association or causal mechanism.");
        // A cohort can identify a pooled rsID only through one compatible explicit
        // reference/alternate identity. Effect/other alleles are never used here.
        var identities = new LinkedHashMap<String,Set<StudyVariantUniverse.Key>>();
        for (var item : catalog.items) if (item.key != null) identities.computeIfAbsent(item.feature,k->new LinkedHashSet<>()).add(item.key);
        var table = primary.table();
        for (String[] row : table.rows()) {
            String feature = row[table.column("feature_id")]; var key = StudyVariantUniverse.from(table,row,build);
            var known = identities.getOrDefault(feature,Set.of());
            if (key != null && !known.isEmpty() && (known.size()!=1 || !known.contains(key)))
                throw new IOException("Pooled variant identity conflicts with cohort catalogue: " + feature);
            if (key == null && known.size()==1) {
                var candidate = known.iterator().next();
                if (!build.isBlank() && !candidate.build().equals(build)) throw new IOException("Pooled genome build conflicts with cohort identity: " + feature);
                key = candidate;
            }
            boolean eligible = primary.probabilities().containsKey(feature);
            catalog.add("",feature,key,eligible,eligible?"official_primary_pool_eligible":"excluded_from_primary_statistical_family","official_meta_analysis",primary.source().producer().id());
        }
    }
}
