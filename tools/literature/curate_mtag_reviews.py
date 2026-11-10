# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Save five source-bound MTAG role drafts without applying catalog metadata or implementation credit."""
import argparse
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs
from setup_deseq2_reference import ROOT, pin, save
from setup_mtag_reference import BASE

FACTS = {
    'P077': dict(version='1.0.8', support=[53],
        use='Jointly analyzed four summary-statistic sets: aortic valve area, mean gradient, peak velocity and aortic stenosis. The Methods report MTAG1.0.8 with default settings and calculation of maxFDR. Enhanced statistics were subsequently used for polygenic scores.',
        boundary='GWAS, METAL meta-analysis, phenotype construction, PRScs scores and external validation are separate. Complete literal flags, source commit, input files and maxFDR grid are unreported; default settings do not supply a complete command.'),
    'P088': dict(version='1.0.8', support=[27],
        use='Initially assessed genetic correlations across DCM, NICM, HCM and eight left-ventricular MRI traits. Selected Ecc and indexed LV end-systolic volume from two clusters with positive rg>0.7, then combined those two GWAS with DCM using default MTAG parameters. Reported maxFDR0.03 and an approximately73% effective-sample-size increase for DCM.',
        boundary='Trait screening and the initial correlation matrix are distinct from the final three-trait run. Supply declared per-SNP effective sample sizes from the authors method. Full input bytes, source commit, exact flag list and maxFDR grid remain unknown; do not replace positive rg>0.7 by an absolute-correlation rule.'),
    'P090': dict(version='1.0.8', support=[],
        use='Combined the primary GWAS with cardiac-MRI traits selected for absolute genetic correlation |rg|>0.7 after separate LDSC screening of ten traits. The paragraph explicitly reports mtag1.0.8 and maxFDR2.7%.',
        boundary='METAL meta-analysis and LDSC1.0.1 screening are separate steps. Complete selected input files, commands, covariance/reference choices and maxFDR grid are not specified in this paragraph; do not infer the final trait set from the ten screening candidates.'),
    'P112': dict(version=None, support=[],
        use='Jointly analyzed EUR cannabis-use-disorder summary statistics with alcohol-use disorder and FTND nicotine dependence. Removed duplicate SNPs, MAF<=0.01 and strand-ambiguous variants beforehand. Reported5894946 cannabis variants after QC and3540940 after the joint MTAG analysis; significance P<5e-8.',
        boundary='Upstream EUR GWAS/meta-analysis and duplicate/MAF/strand QC are separate. MTAG version, exact commands, per-SNP sample-size inputs and covariance/reference settings are not reported here. The reported total AUD/FTND sample sizes do not establish every per-SNP N.'),
    'P115': dict(version=None, support=[15,16,43],
        use='Compared six selections of MDD-related GWAS inputs to create integrated LifetimeMDD statistics. Evaluated sensitivity to the input phenotypes and ten-fold prediction with GWAS and MTAG confined to each90% training fold, followed by separate PRSice scoring on held-out participants. Used MTAG-derived effective sample sizes for downstream LDSC.',
        boundary='Imputation and PRS construction/evaluation are separate. Complete six trait sets require the figure/supplement; the primary paragraphs do not give an exact MTAG version, command or maxFDR grid. MTAG effective sample size, case-control effective N and imputation-related effective N are distinct definitions and must not be interchanged.')
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    papers = json.loads((ROOT / 'docs/literature/omics-2023-2026/papers.json').read_bytes())
    reviews = []
    for paper in papers:
        if paper['paper_id'] not in FACTS:
            continue
        fact = FACTS[paper['paper_id']]
        tool = next(t for t in paper['tools'] if t['tool_id'] == 'mtag')
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        by = {p['paragraph_index']: p for p in paragraphs(ET.fromstring(raw))}
        pointers = []
        for i in [tool['evidence_paragraph_index'], *fact['support']]:
            p = by[i]
            pointers.append(dict(paragraph_index=i, section=p['section'], paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()))
        assert pointers[0]['paragraph_sha256'] == tool['evidence_paragraph_sha256']
        reviews.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=paper['xml_sha256'],
            tool_id='mtag', reported_version=fact['version'], use_summary=fact['use'], implementation_scope_note=fact['boundary'], evidence=pointers,
            whole_paper_reproduced=False, historical_source_commit_known=False))
    assert len(reviews) == 5
    save(out / 'paper-use-reviews.json', dict(status='FIVE_SOURCE_BOUND_MTAG_ROLE_DRAFTS_NOT_APPLIED', applied_to_catalog=False,
        reviews=reviews, full_article_prose_redistributed=False, catalog_implementation_credit=False))
    save(out / 'COMPLETED.json', dict(status='PASS_FIVE_MTAG_ROLE_DRAFTS_ONLY', papers=5, reviews=pin(out / 'paper-use-reviews.json'),
        curator=pin(Path(__file__)), catalog_modified=False, new_catalog_credit=False))
    print('PASS five MTAG role drafts; no catalog mutation or integration credit')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable primary source and complete role checks')
    main()
