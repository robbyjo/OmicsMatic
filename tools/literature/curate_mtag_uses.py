# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review/apply five MTAG primary-source roles, preserving1038 earlier reviews and every unrelated observation."""
import argparse
import copy
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE
from curate_shapeit_uses import inverse,serialize
from catalog_source_integrity import reverse_delta,verify_source
from signac_scan_evidence import byte_pin

PAPERS=['P077','P088','P090','P112','P115']
USES={
    'P077':'Jointly analyzed aortic valve area, mean gradient, peak velocity and aortic-stenosis GWAS summary statistics with MTAG 1.0.8 at reported default settings; computed maxFDR. The enhanced statistics were used later to construct polygenic scores.',
    'P088':'Screened DCM, NICM, HCM and eight left-ventricular MRI GWASs for genetic correlation. Selected Ecc and indexed LV end-systolic volume from two clusters with positive rg>0.7, then combined them with DCM in a three-trait MTAG 1.0.8 run using default parameters. Reported maxFDR 0.03 and an approximately 73% effective-sample-size increase for DCM.',
    'P090':'Combined the primary DCM GWAS with cardiac-MRI GWASs selected by absolute genetic correlation |rg|>0.7 after separate LDSC screening of ten traits. The paragraph explicitly reports MTAG 1.0.8 and maxFDR 2.7%.',
    'P112':'Jointly analyzed EUR cannabis-use-disorder, alcohol-use-disorder and FTND nicotine-dependence GWAS statistics. Removed duplicate, MAF<=0.01 and strand-ambiguous variants beforehand; reported 5,894,946 cannabis variants after QC and 3,540,940 after joint MTAG analysis, with significance P<5e-8.',
    'P115':'Compared six selections of MDD-related GWAS inputs to create integrated LifetimeMDD statistics. Repeated GWAS and MTAG within each 90% training fold before separate PRSice scoring of held-out participants. Used MTAG-derived effective N downstream in LDSC, and reported input-sensitive findings and heritability inflation.'}
COMMON='Scoped original MTAG1.0.8 common-SNP workflows with explicit two-to-eight-trait inputs, native effect/tail/identity/diagnostic audits and complete saved verification are available through mtag-tools; read MTAG_INTEGRATION.md. This tested source commit does not establish the paper historical commit or reconstruct its unreported settings. '
EXTRA={
    'P077':'The source explicitly reports a four-way default-settings analysis and maxFDR. REGENIE inputs, the aortic-stenosis METAL meta-analysis and downstream PRScs scoring remain separate. The complete literal command and source commit are unreported; do not infer them from a version number.',
    'P088':'The final run combines DCM, Ecc and LVESVi; the initial correlation screen is a different stage. The selection is positive rg>0.7, not an absolute-correlation rule. The authors separate JATS equation provides n_snp_eff=1/(2*MAF*(1-MAF)*SE^2), recorded in mtag-P088-equation-source-review.json; it is not attributed to the adjacent paragraph text. Supply explicitly prepared Z/N and preserve author units. The original source beta/SE input mode is disabled; no replacement input mode or effective-N definition is inferred.',
    'P090':'Separate LDSC1.0.1 screening considered ten MRI traits, with selection by absolute |rg|>0.7. The paragraph reports MTAG1.0.8 but does not enumerate the final selected trait files or give the complete command. Do not substitute the ten screening candidates for the final inputs.',
    'P112':'Pre-MTAG duplicate/MAF<=0.01/strand QC, original EUR GWAS and METAL meta-analysis are separate. The version, literal command, per-SNP Ns and covariance/reference settings are unstated. Reported total AUD/FTND Ns are not per-SNP Ns. Source keep-first deduplication does not reconstruct the paper unspecified duplicate-removal policy; provide explicitly prepared inputs and retain every disposition.',
    'P115':'Six input selections and fold-specific90% training analyses are separate requests; no automatic inclusion of217 phenotypes or reuse of held-out data is authorized by this description. The authors report input-sensitive results and heritability inflation. The full six sets require the figure/supplement. MTAG version/complete commands are unstated. The paper explicitly uses MTAG-derived effective N downstream in LDSC; distinguish it from per-SNP, case/control and imputation effective Ns, then choose and label the intended downstream value.'}


def primary_equation(paper,raw):
    root=ET.fromstring(raw);equations=[e for e in root.iter('disp-formula') if e.get('id')=='Equa'];assert len(equations)==1
    equation=equations[0];math_node=equation.find('{http://www.w3.org/1998/Math/MathML}math');assert math_node is not None and math_node.get('id')=='M2'
    tokens=''.join(''.join(equation.itertext()).split());assert tokens=='nsnp-eff=1/(2×MAF×(1−MAF)×(s.e.2))',tokens
    canonical=ET.canonicalize(ET.tostring(equation,encoding='unicode'),rewrite_prefixes=True).encode('utf8')
    return dict(status='REVIEWED_ADDITIONAL_PRIMARY_MTAG_INPUT_EQUATION',paper_id='P088',pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],source_url=paper['fulltext_url'],
        jats_locator='disp-formula[@id="Equa"]/MathML:math[@id="M2"]',canonical_xml=byte_pin(canonical),canonicalization='ElementTree C14N2, rewrite_prefixes=True',
        expression='n_snp_eff = 1 / (2 * MAF * (1 - MAF) * SE^2)',adjacent_paragraph=26,formula_is_separate_from_paragraph_text=True,
        author_effect_units_preserved=True,automatic_beta_se_or_sample_size_substitution=False,article_prose_redistributed=False,whole_paper_reproduced=False)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true');p.add_argument('--dimensions',type=Path);a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    (out/'curator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    data=ROOT/'docs/literature/omics-2023-2026';rp=ROOT/'tools/literature/reviewed-uses.json';pp=data/'papers.json';before=dict(reviews=rp.read_bytes(),papers=pp.read_bytes())
    old=json.loads(before['reviews']);old_papers=json.loads(before['papers']);reviews=copy.deepcopy(old);papers=copy.deepcopy(old_papers)
    assert len(old['reviews'])==1038 and len(papers)==250 and sum(len(p['tools']) for p in papers)==2475
    drafts=json.loads((ROOT/'docs/literature/mtag-native-development/paper-use-reviews.json').read_bytes());assert drafts['status']=='FIVE_SOURCE_BOUND_MTAG_ROLE_DRAFTS_NOT_APPLIED'
    assert sorted(d['paper_id'] for d in drafts['reviews'])==PAPERS and not any(r['tool_id']=='mtag' for r in old['reviews'])
    registry={r['id']:r for r in json.loads((ROOT/'tools/literature/tool-registry.json').read_bytes())};updates=[];source_rows=[];equation=None
    allowed={'use_summary','implementation_scope_note','version_reported_near_name','supporting_evidence','review_status','evidence_selection'}
    for draft in drafts['reviews']:
        paper=next(p for p in papers if p['paper_id']==draft['paper_id']);name=paper['paper_id'];assert (paper['pmcid'],paper['doi'],paper['xml_sha256'])==(draft['pmcid'],draft['doi'],draft['xml_sha256'])
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
        source=paragraphs(ET.fromstring(raw));anchor=draft['evidence'][0];summary=USES[name]
        review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='mtag',evidence_paragraph_sha256=anchor['paragraph_sha256'],use_summary=summary,
                    implementation_scope_note=COMMON+EXTRA[name],version_reported_near_name=draft['reported_version'],supporting_evidence=draft['evidence'][1:])
        validate_supporting_evidence(source,[review],registry)
        matches=tool_matches(source,[registry['mtag']],{'mtag':review['evidence_paragraph_sha256']});assert len(matches)==1 and matches[0]['evidence_paragraph_index']==anchor['paragraph_index']
        checked=publication_record(dict(paper,tools=matches),[review])['tools'][0];index=next(i for i,t in enumerate(paper['tools']) if t['tool_id']=='mtag');previous=paper['tools'][index]
        assert {k:v for k,v in previous.items() if k not in allowed}=={k:v for k,v in checked.items() if k not in allowed}
        paper['tools'][index]=checked;reviews['reviews'].append(review);updates.append(dict(paper_id=name,review=review,before_observation=previous,after_observation=checked))
        source_rows.append(dict(paper_id=name,xml_sha256=paper['xml_sha256'],primary=anchor,supporting=draft['evidence'][1:],reported_version=draft['reported_version']))
        if name=='P088':equation=primary_equation(paper,raw)
    assert reviews['reviews'][:1038]==old['reviews'] and len(reviews['reviews'])==1043 and sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews'])==374
    for prior,current in zip(old_papers,papers,strict=True):
        assert {k:v for k,v in prior.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'}
        assert [t for t in prior['tools'] if t['tool_id']!='mtag']==[t for t in current['tools'] if t['tool_id']!='mtag']
    after=dict(reviews=serialize(reviews,before['reviews']),papers=serialize(papers,before['papers']))
    for key,raw in before.items():(out/('before-'+key+'.json.gz')).write_bytes(gzip.compress(raw,mtime=0))
    for key,raw in after.items():(out/('after-'+key+'.json')).write_bytes(raw)
    save(out/'applied-reviews.json',dict(status='FIVE_MTAG_PRIMARY_ROLE_REVIEWS',updates=updates));save(out/'P088-equation-source-review.json',equation)
    report=dict(schema_version=1,status='PASS_FIVE_MTAG_PRIMARY_ROLES_AND_SEPARATE_AUTHOR_EQUATION',applied=False,papers=PAPERS,added_reviews=5,prior_reviews_preserved=1038,
        reviewed_roles=1043,supporting_pointers=374,observations=2475,unrelated_observations_preserved=2470,source_proof=source_rows,
        current_reviews=byte_pin(after['reviews']),current_papers=byte_pin(after['papers']),reviews_before=byte_pin(before['reviews']),papers_before=byte_pin(before['papers']),
        curator_pin=pin(Path(__file__)),draft_pin=pin(ROOT/'docs/literature/mtag-native-development/paper-use-reviews.json'),whole_paper_reproduced=False,new_catalog_credit=False)
    if a.apply:
        assert a.dimensions is not None
        dimensions=json.loads((a.dimensions/'COMPLETED.json').read_bytes());assert dimensions['status']=='PASS_MTAG4_THROUGH8_ORIGINAL_WORKFLOWS_DEFAULT4_TRAIT_GRID_AND15_SAVED_REPLAYS'
        assert len(dimensions['records'])==5 and all(r['passed'] for r in dimensions['records'])
        from verify_mtag_dimensions import inspect as verify_dimensions,HOME as dimension_home
        dimension_proof,dimension_files=verify_dimensions()
        targets=[data/name for name in ['mtag-prior-reviews.json.gz','mtag-prior-papers.json.gz','mtag-applied-reviews.json','mtag-source-role-coverage.json','mtag-reviews-shared-source-compatibility.json','mtag-P088-equation-source-review.json']];assert not any(path.exists() for path in targets)
        assert rp.read_bytes()==before['reviews'] and pp.read_bytes()==before['papers']
        for key in before:(data/('mtag-prior-'+key+'.json.gz')).write_bytes(gzip.compress(before[key],mtime=0))
        save(data/'mtag-applied-reviews.json',dict(status='FIVE_MTAG_PRIMARY_ROLE_REVIEWS',updates=updates));save(data/'mtag-P088-equation-source-review.json',equation)
        delta=inverse(before['reviews'],after['reviews']);delta['path']='tools/literature/reviewed-uses.json';assert reverse_delta(after['reviews'],delta)==before['reviews']
        save(data/'mtag-reviews-shared-source-compatibility.json',dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='mtag',scope='Only five appended MTAG reviews;1038 earlier reviews and all scientific sources remain unchanged. The author equation has a separate primary-source receipt.',paths=[delta]))
        rp.write_bytes(after['reviews']);pp.write_bytes(after['papers'])
        report.update(applied=True,prior_review_archive=pin(data/'mtag-prior-reviews.json.gz'),prior_papers_archive=pin(data/'mtag-prior-papers.json.gz'),applied_reviews=pin(data/'mtag-applied-reviews.json'),
            separate_author_equation=dict(path=(data/'mtag-P088-equation-source-review.json').relative_to(ROOT).as_posix(),**pin(data/'mtag-P088-equation-source-review.json')),
            dimension_controls=pin(a.dimensions/'COMPLETED.json'),dimension_evidence=dict(path=(dimension_home/'VALIDATION.json').relative_to(ROOT).as_posix(),**pin(dimension_home/'VALIDATION.json')))
        save(data/'mtag-source-role-coverage.json',report);verify_source(ROOT,data,delta['path'],delta['before_sha256'])
    save(out/'COMPLETED.json',report);print(report['status'],'applied',report['applied'])


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-source, exact review extension or unrelated-observation preservation checks')
    main()
