"""Separate paper statements, tested source counterparts and unqualified roles."""
import copy
from edger_catalog.primary import validate, require

NATIVE_EDITIONS = ['3.32.1', '3.36.0', '3.40.2', '3.42.4', '4.10.7']
MODEL_PROFILES = {v: 'edgeR-' + v + '-R4.6.1' for v in ['3.40.2', '3.42.4', '4.10.7']}
PRIMARY = {'P005': 67, 'P037': 48, 'P046': 30, 'P051': 79, 'P053': 73,
    'P087': 65, 'P088': 59, 'P090': 37, 'P104': 35, 'P108': 44, 'P158': 23,
    'P182': 13, 'P183': 13, 'P212': 10, 'P223': 10, 'P228': 9, 'P237': 12}
NATIVE = {
    'P037': ['TMM', 'CPM'], 'P046': ['TMM', 'RPKM'], 'P053': ['TMM'],
    'P087': ['TMM', 'CPM'], 'P108': ['TMM'], 'P182': ['TMM'], 'P183': ['TMM'],
    'P212': ['CPM', 'TMM'], 'P228': ['TMMwsp'], 'P237': ['CPM', 'log-CPM'],
}
MODELS = {
    'P005': ['supplied-count GLM fit and explicit contrast'],
    'P037': ['explicit legacy QL test counterpart; power trend unsupported'],
    'P051': ['prepared-count differential models; author test function unresolved'],
    'P053': ['glmFit and explicit between-species contrasts; locfit/tagwise dispersion'],
    'P090': ['prepared-count differential models; author test function unresolved'],
    'P104': ['filterByExpr with explicit group/design, library and threshold semantics'],
    'P158': ['prepared-count differential models; author test function unresolved'],
    'P182': ['filterByExpr with explicit group/design, library and threshold semantics'],
    'P223': ['prepared-count differential models; author test function unresolved'],
    'P228': ['estimateDisp and supplied-count GLM fit; corrected-contact intake unqualified'],
}
CAUTIONS = {
    'P005': 'Lipid MRM ion counts, experimental blank/intercept and log-library offset must be declared literally. CLAW preprocessing and author blank/offset convention are not reproduced; this is not RNA-seq attribution.',
    'P037': 'Exact 3.32.1 full inference is unavailable here. The reported power dispersion trend is not offered by the estimateDisp workflow. Preserve >=5 CPM in >=35% samples and both treatment/week and WLZ/week interaction hypotheses; a default locfit trend is not a substitute.',
    'P046': 'RPKM uses literal gene lengths and declared count/library semantics; replicate averaging is not composed here. DESeq2 differential inference and limma removeBatchEffect remain separate operations.',
    'P051': 'Exact 3.20.1 is unsupported. The paper names pairwise edgeR/limma use without a precise edgeR function. Nearby limma 3.32.2 is not an edgeR edition; the DESeq2 LRT is separate.',
    'P053': 'Exact 3.36.0 full inference is unavailable here. Preserve one-to-one orthologues, pseudobulk construction, between-species contrasts, FDR 8.33e-6 and fold-change/detection rules. RNA requires fold change >=2 and detection >=15% in the upregulated cells; peaks require fold change >=4 without that detection threshold. GO background filtering is an additional role.',
    'P087': 'The reported R4.3.2 host is not reproduced. Native TMM/raw CPM matches the named edgeR source operations under an explicit supported edition, not the author dataset/host. sctransform/PCA outliers and stats/rstatix correlation/Wilcoxon/BH operations are separate.',
    'P088': 'Code availability establishes a package-role association, not an edgeR operation. The author fork 3.22.3 and R4.0.0 are unsupported here. Audit supplements and the historical fork; do not infer edgeR normalization from the separate DESeq2 review, or edgeR fitting from LDSC context. Paragraph 70 duplicates paragraph 59 and is not another execution.',
    'P090': 'Exact 3.32.1 full inference is unavailable here. Preserve DCM/control pseudobulks, FDR <0.05, absolute log2 fold change >0.5 and the minimum normalized log2-count rule. Recover the cited prior method before selecting a specific edgeR test/design.',
    'P104': 'The literal prose rule (five reads in at least 40% of donors) needs reconciliation with filterByExpr CPM, library and group/design-leverage semantics. Do not replace it with raw-prevalence filtering or assume default parity. Pseudobulk donors require at least five nuclei.',
    'P108': '3.40.2 is reported only at developing-brain paragraph 47; GTEx paragraph 44 has no edgeR edition. Preserve gene/isoform TMM and the separate DESeq2-VST versus log-CPM wording without silently resolving their order/transform ambiguity. Covariate residualization and prediction are separate.',
    'P158': 'Exact 3.38.4 is unsupported. csaw/loess-normalized ChIP data, offsets, counts/units and model settings require separate intake qualification. Preserve the source threshold log fold change (FC) >2 and P <0.05, without changing it to FDR or a raw fold-change threshold.',
    'P182': 'Preserve at least 10 counts in at least 158 samples and the 226-sample reference-group context, while reconciling that prose with filterByExpr semantics. The reported R4.1.3/Bioconductor3.1 combination is retained literally as an unresolved runtime inconsistency. Robust-PCA outlier removal is separate.',
    'P183': 'TMM precedes rank-based inverse-normal transformation and PEER; no edgeR differential fit or edition is established. TPM >0.1, counts >6 in at least 20% and upstream cohort filtering remain separate.',
    'P212': 'Retain the literal removal rule (<5 counts in >=25% of individuals), calculated CPM then TMM wording and subsequent inverse-normal transform. The source order/units/prevalence ambiguity is unresolved; the native component offers explicit arithmetic operations, not an inferred repaired pipeline.',
    'P223': 'The paper reports edgeR differential expression for iNeuron day-0/later contrasts without edition/function/design. Salmon-derived count units need explicit declaration. HISAT2/StringTie/Ballgown, biomaRt and Rsubread/seqinr junction handling are separate.',
    'P228': 'Two roles are distinct: Hi-C estimateDisp/GLM after ICE/LOESS/contact processing, and snRNA TMMwsp before limma voomLmFit. Corrected-contact count units/offsets, upstream contact filters and the complete Hi-C design are unqualified. limma fitting/BH and adjusted P <0.2 for snRNA are not edgeR NB-test attribution.',
    'P237': 'sumTechReps is explicitly named but is not exposed by either component. Preserve filtering CPM >=2 in at least one fourth of samples before selected technical-replicate merging, then normalization; plotting cpm uses prior.count=2 and log2. Clone:dox contrasts, their between-replicate limitations and voomLmFit inference are separate; no edgeR NB-test function is inferred.',
}


def build(primary):
    validate(primary)
    census = primary['frozen_census']
    identities = {p['paper_id']: p for p in census['source_identities']}
    require(set(PRIMARY) == set(CAUTIONS) == {r['paper_id'] for r in census['roles']}, 'Different source-role decisions')
    roles = []
    for source in census['roles']:
        pid = source['paper_id']
        r = copy.deepcopy(source)
        r['frozen_native_checkpoint_caution'] = r.pop('scope_and_required_work')
        anchor = next(a for a in source['anchors'] if a['paragraph_index'] == PRIMARY[pid])
        versions = list(dict.fromkeys(v['version'] for v in source['reported_versions']))
        functions = [copy.deepcopy(w) for w in primary['explicit_functions'] if w['paper_id'] == pid]
        native = NATIVE.get(pid, [])
        models = MODELS.get(pid, [])
        r.update(source_xml_sha256=identities[pid]['xml_sha256'], primary_evidence=copy.deepcopy(anchor),
            primary_anchor_reported_versions=[v['version'] for v in source['reported_versions'] if v['paragraph_index'] == PRIMARY[pid]],
            explicit_edgeR_functions=functions, native_matrix_counterparts=list(native),
            original_prepared_count_counterparts=list(models),
            matching_reported_native_editions=[v for v in versions if native and v in NATIVE_EDITIONS],
            matching_reported_model_source_profiles=[MODEL_PROFILES[v] for v in versions if models and v in MODEL_PROFILES],
            unsupported_reported_model_editions=[v for v in versions if models and v not in MODEL_PROFILES],
            reported_editions_without_any_qualified_component=[v for v in versions if v not in set(NATIVE_EDITIONS) | set(MODEL_PROFILES)],
            operation_unresolved=(pid == 'P088'),
            counterparts_are_not_author_function_attribution=True,
            original_author_inputs_and_settings_recovered=False, author_R_and_dependency_profile_recovered=False,
            historical_author_host_reproduced=False, complete_author_workflow_qualified=False,
            full_reported_role_qualified=False, remaining_author_roles=[CAUTIONS[pid],
                'Original author inputs/settings/host and the whole reported workflow remain unqualified.'],
            canonical_review_appended=False, canonical_review_proposed=True)
        roles.append(r)
    return dict(schema_version=1, status='PASS_17_EDGER_SOURCE_ROLE_CROSSWALK_CUMULATIVE_ADMISSION_PROPOSED',
        roles=roles, source_roles=17, exact_primary_sources_scanned=250,
        explicit_function_mentions=len(primary['explicit_functions']), observations_to_add=['P088'],
        review_records_to_append=17, existing_review_records_to_preserve=1503,
        native_editions=list(NATIVE_EDITIONS), model_development_profiles=copy.deepcopy(MODEL_PROFILES),
        current_canonical_family_status='method-overlap', proposed_upstream_family_credit=1,
        actual_new_family_credit=0, native_family_credit_added=0, canonical_reviews_appended=0,
        source_function_attribution_invented=False, whole_tool_qualified=False,
        full_author_workflows_reproduced=0, whole_goal_complete=False)
