# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare nine primary-bound Monocle role drafts; never modify the published catalog or award credit."""
import json
from setup_deseq2_reference import ROOT,pin,save

FACTS={
 'P021':('1.0.0','trajectory-and-custom-gene-tests',
    'Used Monocle3 1.0.0 on the reported UMAT embedding to reconstruct lineage trajectories and select shortest paths; a modified graph_test with covariates identified trajectory-associated genes.',
    'The paper reports UMAT, gene/cell filtering, selected paths and a modified covariate-aware Moran test. Do not silently rename the embedding, infer the custom implementation or equate stock graph_test with its sex/batch/PMI/log_n_genes-adjusted version. The native component handles only supplied projected-graph ordering; graph learning, the modified test, metacell smoothing, spline models and clustering remain separate workflow requirements.'),
 'P024':(None,'python-wrapper-trajectory-comparison',
    'Compared Monocle 3 pseudotime through the separate py-monocle Python wrapper with neural-network predictions and Palantir in microglia; tried 200 eligible starting cells and retained the highest Braak-stage correlation.',
    'The exact Monocle/wrapper edition and graph options are unstated. py-monocle is named executable software and requires its own source/runtime review; an R adapter does not establish wrapper parity. Starting-cell eligibility and selection using observed Braak correlation are part of this comparison, not a general root-selection default or independent predictive validation. Native prepared-graph ordering does not reproduce that selection pipeline.'),
 'P043':(None,'Cicero-platform-context',
    'Used Cicero for Monocle 3 to infer cis-co-accessible cCRE pairs per cell subclass, using mm10, default Cicero parameters and 500 kb genomic windows.',
    'This paragraph identifies Monocle as the platform for Cicero and does not establish a Monocle trajectory/pseudotime run. Cicero, shuffled-background fitting, FDR filtering and distal/proximal annotation are distinct analyses. Exact Monocle/Cicero patches and author-specific preparation remain unresolved; do not credit them to the native pseudotime component or a stock Monocle trajectory adapter.'),
 'P051':('1.3.7','integrated-pseudotime',
    'Applied the Monocle3 pipeline with Batchelor 1.20.0 integration, Monocle3 1.3.7 dimensional reduction/clustering and pseudotime; a separate Wilcoxon test compared immature control and Tgfbr2-mutant cells.',
    'The complete paragraph explicitly reports Monocle3 1.3.7, correcting the unreviewed missing-version extraction. Exact preprocessing, integration settings, graph controls, roots and transformations are unstated. The separate Wilcoxon test used continuity correction. The current 1.4.27 readiness profile and native prepared-graph component do not establish historical 1.3.7/Batchelor parity or that complete experiment.'),
 'P085':('1.3.1','gene-wise-spatial-GLMs',
    'Used monocle3 1.3.1 fit_models GLMs to relate spatial gene expression to amyloid Getis-Ord Gi* hotspot scores, adjusting for biological sample and UMI count; followed significant Wald-test results with positive Pearson-correlation filtering.',
    'This is gene-wise GLM inference, not Monocle pseudotime. The model family, exact formula/encoding, normalization and full testing family are unstated. Human grey-matter filtering, mouse regional fits, separate stains and subsequent Pearson filtering remain explicit upstream/downstream steps. The attempted original 1.3.1 runtime is skipped because grr does not compile under R 4.6.1; a current 1.4.27 synthetic quasipoisson probe is not a silent version/family substitution.'),
 'P099':(None,'Monocle2-differential-testing',
    'Used Monocle 2 differentialGeneTest for differential gene expression across cell types, with reported FDR, fold-change and TPM marker filters.',
    'Other named Methods paragraphs apply Monocle 2 tests to subclusters, age/AD comparisons with sex covariates, cell fractions, accessibility peaks and human AD/control expression. They require separate source pointers and preserve their distinct sampling/filtering populations. The exact patch, expression family, formulas and normalization are not fully resolved. TPM marker cutoffs do not establish count-model input eligibility. Monocle 3 GLMs or native pseudotime are not interchangeable with these Monocle 2 likelihood-ratio workflows.'),
 'P117':('1.3.3','prepared-embedding-trajectory',
    'Constructed trajectories on selected transition regions with monocle3 1.3.3 learn_graph, varying ncenter across 130/140/150 and minimal_branch_len across 15/20, with close_loop=FALSE; ordered from a terminal node in the assumed transition direction.',
    'The exact selected embedding population, root identifier and mapping of graph-control combinations to transitions are unstated. The controls and assumed direction must remain explicit. Native ordering of a supplied projected graph covers only the final geodesic step. The current 1.4.27 readiness probe does not validate original 1.3.3 graph learning or infer those missing paper choices.'),
 'P134':(None,'protein-activity-pseudotrajectory',
    'Inferred Monocle pseudotrajectories from metaVIPER protein-activity profiles of 1,856 malignant pancreatic cells, alongside separate Seurat/Louvain state clustering.',
    'The Monocle generation/patch, model family, graph algorithm/options and roots are unstated. Protein-activity values must not be treated as raw RNA counts or assumed eligible for a count-model adapter. Native prepared-graph ordering requires the caller to supply the actual matched embedding/graph; metaVIPER/ARACNe inference, clustering, cross-cohort enrichment and biological state interpretation remain separate analyses.'),
 'P233':(None,'condition-separated-trajectories',
    'Converted Seurat objects to cell_data_set objects through a wrapper and inferred Monocle 3 stromal trajectories after condition-specific FD/WT subsetting and reclustering.',
    'The exact Monocle patch, graph controls, roots and complete conversion/embedding details are unstated. Results describe separately reclustered FD and WT objects to limit condition effects; do not replace this with a pooled trajectory. Seurat/Harmony/Liger processing and wrapper conversion remain separate from native projected-graph ordering and from the current original-package readiness probe.')}


def main():
    base=ROOT/'build/literature-review/monocle-runtime/paper-role-audit-1'
    proof=json.loads((base/'COMPLETED.json').read_bytes());assert proof['source_xml_hashes_checked']==250 and len(proof['records'])==9
    drafts=[]
    for row in proof['records']:
        private=base/(row['paper_id']+'.json');assert pin(private)==row['private_record']
        context=json.loads(private.read_bytes());source=context['original_observation'];version,usage,summary,scope=FACTS[row['paper_id']]
        review=dict(pmcid=context['pmcid'],doi=context['doi'],tool_id='monocle',
            evidence_paragraph_sha256=row['paragraph_sha256'],use_summary=summary,
            implementation_scope_note=scope,version_reported_near_name=version,usage_context=usage,supporting_evidence=[])
        drafts.append(dict(paper_id=row['paper_id'],primary_paragraph=row['paragraph_index'],review=review,
            additional_named_context_paragraphs=[dict(paragraph_index=p['paragraph_index'],section=p['section'])
                for p in context['private_context'] if p['paragraph_index']!=row['paragraph_index'] and 'monocle' in p['match_text'].lower()]))
        assert review['evidence_paragraph_sha256']==source['evidence_paragraph_sha256']
    target=ROOT/'tools/literature/monocle-use-review-drafts.json';assert not target.exists();save(target,drafts)
    assert len(drafts)==9 and next(d for d in drafts if d['paper_id']=='P051')['review']['version_reported_near_name']=='1.3.7'
    save(ROOT/'build/literature-review/monocle-runtime/role-drafts-1.json',dict(status='prepared-nine-primary-bound-role-drafts-not-applied',drafts=pin(target),
        source_pin=pin(ROOT/'tools/literature/prepare_monocle_reviews.py'),published_papers_unchanged=True,published_reviews_unchanged=True,
        named_software_recovery_pending=['Cicero','py-monocle'],implementation_credit=False))
    print('Prepared nine source-bound drafts; published catalog and all 905 prior reviews unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-context bindings')
    main()
