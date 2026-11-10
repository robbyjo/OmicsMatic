# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Correct the raw-name census with citation-normalized P170 PCA roles; preserve frozen earlier evidence."""
import json
from setup_deseq2_reference import ROOT,pin,save
from curate_snprelate_genesis_roles import REVIEWS as PREVIOUS_REVIEWS


def main():
    intake=ROOT/'build/literature-review/snprelate-runtime/primary-intake-citation-normalized-1'
    done=json.loads((intake/'COMPLETED.json').read_bytes());assert done['status']=='PASS250_CITATION_NORMALIZED_SNPRELATE_GENESIS_MENTION_CENSUS_ONLY'
    reviews=dict(PREVIOUS_REVIEWS)
    reviews[('P170','snprelate',28)]=('software-use',None,
        'Genotype principal component analysis on508883 variants previously selected with PLINK indep-pairwise in the IAV perturbation cohort.',
        'No SNPRelate version, exact PCA function, eigen solver or component count is reported. PLINK pruning is a separate upstream step; do not replace the SNPRelate PCA host or infer its defaults.')
    reviews[('P170','snprelate',29)]=('software-use',None,
        'Genotype principal component analysis on292681 variants previously selected with PLINK indep-pairwise in the CA/PA/MTB perturbation cohort.',
        'No SNPRelate version, exact PCA function, eigen solver or component count is reported. Picard liftover and PLINK pruning remain separate operations.')
    pointers=json.loads((intake/'source-pointers.json').read_bytes())['pointers']
    assert {(r['paper_id'],r['tool'],r['paragraph_index']) for r in pointers}==set(reviews)
    rows=[]
    for row in pointers:
        classification,version,role,limits=reviews[(row['paper_id'],row['tool'],row['paragraph_index'])]
        rows.append(dict(**row,classification=classification,reported_version=version,reviewed_role=role,unresolved_scope=limits))
    target=ROOT/'docs/literature/omics-2023-2026/snprelate-genesis-primary-roles-v2.json';assert not target.exists()
    predecessor=ROOT/'docs/literature/omics-2023-2026/snprelate-genesis-primary-roles.json'
    result=dict(status='reviewed-citation-normalized-primary-role-supplement-registry-admission-pending',schema_version=2,cohort_papers=250,
        software_papers={tool:sorted({r['paper_id'] for r in rows if r['tool']==tool and r['classification']=='software-use'}) for tool in ['snprelate','genesis']},roles=rows,
        source_pointer_intake=pin(intake/'source-pointers.json'),producer=pin(ROOT/'tools/literature/curate_snprelate_genesis_roles_v2.py'),
        supersedes_for_source_completeness=dict(path=predecessor.relative_to(ROOT).as_posix(),pin=pin(predecessor),
            cause='Raw JATS paragraph text joined citation numbers directly to SNPRelate in P170, defeating a raw-text name boundary. The existing citation-normalized matcher finds both PCA paragraphs. Raw primary paragraph hashes remain unchanged.'),
        previous_arithmetic_evidence_invalidated=False,catalog_registry_applied=False,new_catalog_credit=False,whole_goal_complete=False,article_prose_redistributed=False)
    assert len(result['software_papers']['snprelate'])==4 and len(result['software_papers']['genesis'])==5
    save(target,result);print(result['status'],json.dumps(result['software_papers']))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact prior-role preservation and full-census checks')
    main()
