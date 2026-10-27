/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed capabilities available to the deterministic study planner. */
public final class StudyOperationRegistry {
    private StudyOperationRegistry() { }

    public record Descriptor(String type, String executor,
            Set<String> acceptedStages, Set<String> modalities,
            List<String> outputs, List<String> diagnostics) {
        public Descriptor {
            acceptedStages = Set.copyOf(acceptedStages);
            modalities = Set.copyOf(modalities);
            outputs = List.copyOf(outputs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final Set<String> ALL_STAGES = Set.of(
        "raw_quantified", "quantified_matrix", "processed_matrix",
        "association_results", "annotated_results", "meta_analysis_results",
        "enrichment_results", "variant_scores", "fine_mapping_results",
        "colocalization_results", "mr_results", "evidence_results", "rare_score_summary", "set_association_results", "analysis_reference");
    private static final Set<String> ALL_MODALITIES = Set.of(
        "generic", "transcriptomics", "expression", "mirna", "lincrna",
        "lncrna", "extracellular_rna", "epigenomics", "ewas", "gwas",
        "genomics", "proteomics", "metabolomics", "single_cell_rna",
        "single_cell_protein", "spatial_transcriptomics",
        "spatial_proteomics", "microbiome", "multimodal", "phenotype");
    private static final Set<String> BULK_ASSOCIATION_MODALITIES = Set.of(
        "generic", "transcriptomics", "expression", "mirna", "lincrna",
        "lncrna", "extracellular_rna", "epigenomics", "ewas", "gwas",
        "genomics", "proteomics", "metabolomics", "phenotype");
    private static final Map<String, Descriptor> OPERATIONS = operations();

    public static Descriptor descriptor(String type) {
        Descriptor result = OPERATIONS.get(type);
        if (result == null)
            throw new IllegalArgumentException("Unregistered study operation: " + type);
        return result;
    }

    public static Map<String, Descriptor> descriptors() {
        return Map.copyOf(OPERATIONS);
    }

    private static Map<String, Descriptor> operations() {
        Map<String, Descriptor> result = new LinkedHashMap<>();
        add(result,"mr_family_adjustment","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("cross-hypothesis primary MR family", "original source adjustments", "member completeness receipt"),List.of("declared hypothesis membership", "raw primary P values", "no unresolved-family joint discovery"));
        add(result, "supplied_sensitivity", "jlinalg", ALL_STAGES, ALL_MODALITIES,
            List.of("typed external sensitivity evidence", "immutable supplied inputs"), List.of("scope", "provenance", "primary separation"));
        add(result, "supplied_partial", "jlinalg", ALL_STAGES, ALL_MODALITIES,
            List.of("retained incomplete external analysis", "immutable source snapshots"),
            List.of("no completed-stage ownership", "separate native completion or explicit missing-input need"));
        add(result, "supplied_contribution", "jlinalg", ALL_STAGES, ALL_MODALITIES,
            List.of("typed external MR or literature contribution", "immutable source snapshots"), List.of("kind-specific feature or query coverage", "source attribution", "separate joint MR correction or exact-scope literature reuse"));
        for (String type : List.of("network_analysis","pathway_network","network_modules","module_gene_ontology"))
            add(result,type,"jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("scoped network/module evidence"),List.of("independent units","covariate adjustment","signed edges","bounds","mapped network universe","joint testing family","disjoint supplied graph scopes","uncovered dataset completion","explicit supplied-only mode"));
        add(result,"network_preservation","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("network-preservation.tsv","frozen reference memberships","graph comparability and source snapshots"),List.of("registered node namespaces","complete thresholded signed graphs","matched estimator and threshold","shared node coverage","edge retention and sign agreement","no preservation P values","no primary graph selection"));
        add(result,"network_preservation_null","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("network-preservation-tests.tsv","network-null-statistic-counts.tsv","network-preservation-null.yaml"),List.of("prespecified target-label exchangeability","frozen memberships and complete compatible signed graphs","exact full-group or plus-one sampled tails","complete module/target family correction","context only; no association candidate selection"));
        add(result,"rare_set_omnibus","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("rare-omnibus-results.tsv","component inventory","separate complete unit families"),List.of("prespecified masks/tests","reviewed gene grouping","independent component eligibility","complete family coverage","finite log tails","no aggregate effect direction","source FDR retained"));
        add(result,"ranked_gsea","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("complete ranked-gsea.tsv","leading edges","retained gene-set/ranking snapshots","PNG/PDF curves"),List.of("complete gene universe","namespace/collapse/ties","declared gene-set or shared model null","exact/finite Monte Carlo precision","complete term family"));
        for(String type:List.of("protein_structure","protein_structure_assessment"))add(result,type,"jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("sequence-bound protein context","residue/peptide/variant/region evidence","offline C-alpha view","confidence PNG/PDF","source snapshots"),List.of("exact accession/isoform/taxon/sequence","assay mapping","source version/hash","pLDDT versus B-factor","directional PAE","all models/seeds retained"));
        for(String type:List.of("risk_score_evaluation","risk_score_related_phenotypes"))add(result,type,"jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("frozen score and baseline","held-out performance","related-phenotype family","local partition audits"),List.of("development/evaluation independence","training-only preprocessing","conditional cluster bootstrap","external overlap declarations","source provenance"));
        add(result,"gwas_enrichment","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("S-LDSC target family", "overlap-aware heritability and enrichment", "SNP QC and block deletes"),List.of("complete regression universe", "population/build matched LD", "binary annotation moments", "full target correction", "weak heritability"));
        add(result,"gwas_diagnostics","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("source/contrast/stratum diagnostics","full QQ and coordinate tables","PNG/PDF QQ and Manhattan plots","immutable source snapshots"),List.of("complete versus selected exports","natural log P and unresolved zeros","frequency/count/INFO/test strata","mixed/missing builds and coordinates","display bounds","no genomic-control correction"));
        add(result,"rare_set_followup","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("verified primary gene/mask projection","explicit feature-to-gene mappings"),List.of("original joint testing family","complete-primary partial-work retention","no minimum-P mask selection","unmapped/ambiguous sets"));
        add(result,"subgroup_interactions","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("adjusted interaction contrasts","group-specific slopes and uncertainty","complete correction-family dispositions"),List.of("prespecified contrasts","independent units","covariate interactions","group MAC","no histogram or primary-P gate","immutable primary"));
        add(result, "finding_inventory", "jlinalg", ALL_STAGES, ALL_MODALITIES, List.of("uncapped finding inventory", "source-linked aggregate dossiers", "registered LD redundancy components"), List.of("full families", "eligibility", "source hashes", "unavailable evidence", "allele-resolved reference coverage", "distinct principal findings"));
        add(result, "impact_assessment", "jlinalg", ALL_STAGES, ALL_MODALITIES, List.of("study-wide AI impact assessment", "3–5 finding manuscript outline"), List.of("complete candidate coverage", "rubric", "cross-batch comparison", "caveats", "distinctness", "word allocation"));
        add(result, "validate_harmonize", "jlinalg", ALL_STAGES, ALL_MODALITIES,
            List.of("harmonized inputs", "input manifest", "metadata-only reference inventory"),
            List.of("identity alignment", "missing columns", "input hashes", "reference ownership"));
        add(result, "association", "jlinalg",
            Set.of("raw_quantified", "quantified_matrix", "processed_matrix"),
            BULK_ASSOCIATION_MODALITIES, List.of("association results"),
            List.of("sample alignment", "model convergence", "tested family"));
        add(result, "assay_qc", "jlinalg", Set.of("raw_quantified", "quantified_matrix", "processed_matrix"), StudyAssayQcPolicy.MODALITIES,
            List.of("source assay QC", "sample and feature decisions", "optional derived matrix"),
            List.of("fixed source denominators", "missingness", "scale", "detection", "immutable source", "supplied decision coverage"));
        add(result, "participant_qc", "jlinalg",
            Set.of("raw_quantified", "quantified_matrix", "processed_matrix"), BULK_ASSOCIATION_MODALITIES,
            List.of("local analysis phenotype table", "prefit distributions", "sample exclusion summary"),
            List.of("sample alignment", "complete cases", "call rate", "distribution and subgroup warnings"));
        add(result, "rare_variant_masks", "jlinalg", ALL_STAGES, Set.of("gwas", "genomics", "generic"),
            List.of("prespecified annotation masks", "allele-resolved membership"), List.of("annotation release", "genome build", "coverage"));
        add(result, "rare_variant_scores", "jlinalg", Set.of("raw_quantified", "quantified_matrix", "processed_matrix"), Set.of("gwas", "genomics"),
            List.of("Gaussian score/covariance or private conditional binary inputs", "independent set eligibility QC"), List.of("sample alignment", "low-MAC retention", "null model", "quality"));
        add(result, "rare_variant_analysis", "jlinalg", ALL_STAGES, Set.of("gwas", "genomics", "generic"),
            List.of("typed signed and unsigned set tests", "set-family FDR", "discovery and validation"), List.of("score compatibility", "mask coverage", "multiplicity", "direction applicability"));
        add(result, "effect_comparison", "jlinalg",
            Set.of("association_results", "annotated_results"),
            ALL_MODALITIES, List.of("pairwise effect scatterplots", "effect correlations"),
            List.of("Pearson correlation", "effect-scale agreement", "matched features"));
        add(result,"cohort_overlap","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("exact multiway membership","tested/frozen families","Venn/UpSet PNG/PDF","zero-shared-hit review"),List.of("producer hashes","observed/common universes","source adjustments","missing coverage","direction","cohort identities"));
        add(result,"rare_cohort_overlap","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("exact primary gene/mask/test membership","rare-set Venn/UpSet","complete-scope partial-work retention"),List.of("joint source mask/test FDR","frozen all-test validation families","unsigned direction unavailable","coverage-aware alerts","primary unchanged"));
        add(result,"heterogeneity_review","jlinalg",ALL_STAGES,ALL_MODALITIES,List.of("registered feature/widespread heterogeneity alerts"),List.of("primary producer identity","I-squared or covariance-aware Q","source coverage","complete Q testing family","minimum sources/features"));
        add(result, "discovery_replication", "jlinalg",
            Set.of("association_results", "annotated_results", "analysis_reference"),
            ALL_MODALITIES, List.of("replication comparison", "frozen source-scope audit"),
            List.of("sign concordance", "replication counts", "source membership/trait", "explicit cross-cohort independence", "source effect/coding compatibility"));
        add(result, "meta_analysis", "jlinalg",
            Set.of("association_results", "annotated_results"),
            ALL_MODALITIES, List.of("pooled effects"),
            List.of("heterogeneity", "cohort directions"));
        add(result, "discovery_meta_analysis", "jlinalg",
            Set.of("association_results", "annotated_results"), ALL_MODALITIES,
            List.of("discovery-only pooled effects", "frozen discovery input inventory"),
            List.of("validation exclusion", "heterogeneity", "discovery testing family"));
        add(result, "gene_ontology", "jlinalg",
            Set.of("association_results", "annotated_results",
                "meta_analysis_results"),
            ALL_MODALITIES, List.of("ontology enrichment"),
            List.of("tested background", "set overlap"));
        add(result, "pathway_enrichment", "jlinalg",
            Set.of("association_results", "annotated_results",
                "meta_analysis_results"),
            ALL_MODALITIES, List.of("pathway enrichment"),
            List.of("tested background", "set overlap"));
        add(result, "variant_annotation", "jlinalg", ALL_STAGES, Set.of("gwas", "genomics"),
            List.of("allele-resolved consequence inventory", "pre-association rare annotations"), List.of("reference agreement", "per-variant coverage", "annotation release"));
        add(result, "variant_scoring", "jlinalg",
            Set.of("association_results", "annotated_results"),
            Set.of("gwas", "genomics"), List.of("variant scores"),
            List.of("allele alignment", "annotation coverage"));
        add(result, "fine_mapping", "jlinalg",
            Set.of("association_results", "meta_analysis_results"),
            Set.of("gwas", "genomics"), List.of("credible sets", "PIPs"),
            List.of("LD alignment", "credible-set coverage"));
        add(result, "colocalization", "jlinalg",
            Set.of("association_results", "fine_mapping_results"),
            ALL_MODALITIES, List.of("colocalization posteriors"),
            List.of("allele alignment", "overlap", "prior sensitivity"));
        add(result, "mendelian_randomization", "jlinalg",
            Set.of("association_results", "meta_analysis_results"),
            ALL_MODALITIES, List.of("causal estimates"),
            List.of("instrument strength", "heterogeneity", "pleiotropy"));
        add(result, "literature_support", "evidence_request",
            ALL_STAGES, ALL_MODALITIES, List.of("literature evidence request"),
            List.of("query provenance", "manual or service disposition"));
        add(result, "candidate_prioritization", "jlinalg",
            ALL_STAGES, ALL_MODALITIES,
            List.of("transparent candidate evidence table"),
            List.of("evidence availability", "ranking limitations"));
        add(result, "diagnostic_review", "jlinalg",
            ALL_STAGES, ALL_MODALITIES, List.of("diagnostic observations"),
            List.of("unusual patterns", "technical alternatives"));
        add(result, "report", "jlinalg", ALL_STAGES, ALL_MODALITIES,
            List.of("traceable report"), List.of("result lineage completeness"));
        add(result, "participant_characteristics", "jlinalg", ALL_STAGES, ALL_MODALITIES,
            List.of("local aggregate participant characteristics"), List.of("analysis population", "missingness", "sex coding", "visit alignment"));
        add(result, "irqtl_candidate_review", "jlinalg", ALL_STAGES, ALL_MODALITIES,
            List.of("local irQTL candidate review"), List.of("exact transcript matching", "regional completeness", "instrument review flags"));
        for (String type : List.of("advisor_review", "related_phenotype_advice", "manuscript_draft", "sensitivity_analysis"))
            add(result, type, "jlinalg", ALL_STAGES, ALL_MODALITIES,
                List.of("decision record", "audited outputs"), List.of("permissions", "prerequisites", "lineage"));
        for (String type : List.of("source_discovery", "bioinformatics_investigation", "literature_search"))
            add(result, type, "jlinalg", ALL_STAGES, ALL_MODALITIES,
                List.of("cited public evidence", "request and response audit"),
                List.of("scientific relevance", "disclosure policy", "search completeness", "source availability"));
        for (String kind : StudySuppliedResultPolicy.KINDS) if (!result.containsKey(kind))
            add(result, kind, "jlinalg", ALL_STAGES, ALL_MODALITIES,
                List.of("typed analysis artifacts"), List.of("external provenance", "scope", "testing family", "coverage"));
        for (String kind : StudySuppliedResultPolicy.KINDS) {
            var existing=result.get(kind);var outputs=new ArrayList<>(existing.outputs());
            outputs.add("supplied scientific-scope audit and comparisons");
            var diagnostics=new ArrayList<>(existing.diagnostics());
            diagnostics.add("registered trait/population/context compatibility");
            diagnostics.add("local source prose review; aggregate automatic reports");
            add(result,kind,existing.executor(),existing.acceptedStages(),existing.modalities(),outputs,diagnostics);
        }
        return result;
    }

    private static void add(Map<String, Descriptor> target, String type,
            String executor, Set<String> stages, Set<String> modalities,
            List<String> outputs, List<String> diagnostics) {
        target.put(type, new Descriptor(type, executor, stages, modalities,
            outputs, diagnostics));
    }
}
