# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply explicitly reviewed SNPRelate/GENESIS primary roles; retain ambiguity and exclude generic-word matches."""
import json
from setup_deseq2_reference import ROOT,pin,save

REVIEWS={
 ('P059','genesis',67):('excluded-generic-word',None,'Biological prose uses the ordinary word genesis; no GENESIS software use.',None),
 ('P084','genesis',29):('excluded-generic-word',None,'Biological prose uses the ordinary word genesis; no GENESIS software use.',None),
 ('P128','genesis',36):('software-use','2.16.1','Relatedness and population-substructure assessment.',
    'Supplementary Note 7 is cited but was not reviewed here; the primary paragraph does not identify the exact functions or settings.'),
 ('P128','genesis',51):('software-use','2.16.1','Association of Alzheimer disease polygenic risk scores with disease, adjusting for covariates and a kinship matrix.',
    'Do not infer an exact GENESIS fitting function, link or covariance settings from this paragraph.'),
 ('P130','genesis',52):('software-use','2.26.0','PC-AiR generated ten genetic principal components used as fixed-effect covariates.',
    'No PC-AiR settings or exact call are supplied in this paragraph.'),
 ('P130','snprelate',53):('software-use','1.30.1','Named in a collective package list for the GRM/null-model stage of cohort association analyses.',
    'The paragraph groups GWASTools, SNPRelate, POLMM, GRAB and/or SAIGE; it does not uniquely assign a function or null-model fit to SNPRelate. Do not invent that assignment.'),
 ('P142','genesis',30):('software-use',None,'Generalized linear mixed-model association tests in cis and trans, with a sparse fourth-degree PC-Relate kinship matrix as a random effect.',
    'Exact GENESIS version, function calls and sparsification settings are not specified in this paragraph.'),
 ('P191','snprelate',15):('software-use',None,'KING-robust pairwise relatedness supplied to GENESIS PC-AiR for principal components in non-European ancestry groups.',
    'This is the SNPRelate-hosted method. The European PLINK2 pca approx and SAIGE sparse-GRM steps are distinct; the paragraph does not specify SNPRelate version or KING filtering/family settings.'),
 ('P191','genesis',15):('software-use',None,'PC-AiR principal components for non-European ancestry groups using SNPRelate KING-robust relatedness.',
    'Exact GENESIS version and PC-AiR settings are unreported; do not attribute the separate PLINK2/SAIGE steps to GENESIS.'),
 ('P216','snprelate',28):('software-use',None,'snpgdsLDpruning generated a set of 149605 SNPs used by PLINK2 for principal components in European-ancestry men.',
    'Exact SNPRelate version, LD method, random seed and pruning settings are unreported. Do not substitute PLINK clumping/pruning semantics.'),
 ('P216','snpgds_function',28):('function-evidence',None,'Explicit snpgdsLDpruning call name supports the SNPRelate role in this same paragraph.',
    'Supporting evidence for the package role, not an additional observed package or integration.'),
 ('P219','genesis',18):('software-use',None,'Twenty genetic principal components from common HapMap3 SNPs after exclusion of long-range LD regions; used in ancestry-outlier assessment.',
    'The paragraph does not identify the exact GENESIS function or version; PC-AiR must not be inferred automatically.')
}


def main():
    intake=ROOT/'build/literature-review/snprelate-runtime/primary-intake-1'
    done=json.loads((intake/'COMPLETED.json').read_bytes())
    assert done['status']=='PASS250_PRIMARY_SNPRELATE_GENESIS_MENTION_INTAKE_ONLY' and done['papers']==250
    source=json.loads((intake/'source-pointers.json').read_bytes())['pointers']
    assert {(r['paper_id'],r['tool'],r['paragraph_index']) for r in source}==set(REVIEWS)
    rows=[]
    for row in source:
        classification,version,role,limits=REVIEWS[(row['paper_id'],row['tool'],row['paragraph_index'])]
        rows.append(dict(**row,classification=classification,reported_version=version,reviewed_role=role,unresolved_scope=limits))
    target=ROOT/'docs/literature/omics-2023-2026/snprelate-genesis-primary-roles.json';assert not target.exists()
    result=dict(status='reviewed-primary-role-supplement-registry-admission-pending',schema_version=1,cohort_papers=250,
        software_papers={tool:sorted({r['paper_id'] for r in rows if r['tool']==tool and r['classification']=='software-use'}) for tool in ['snprelate','genesis']},
        roles=rows,source_pointer_intake=pin(intake/'source-pointers.json'),producer=pin(ROOT/'tools/literature/curate_snprelate_genesis_roles.py'),
        catalog_registry_applied=False,new_catalog_credit=False,whole_goal_complete=False,article_prose_redistributed=False)
    assert len(result['software_papers']['snprelate'])==3 and len(result['software_papers']['genesis'])==5
    save(target,result);print(result['status'],json.dumps(result['software_papers']))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete primary-role census checks')
    main()
