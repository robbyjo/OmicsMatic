# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only250-source audit of the applied988-role catalog and all five named followups."""
import argparse,gzip,hashlib,json,re,sys
from pathlib import Path
import xml.etree.ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,tool_matches,validate_supporting_evidence
from collect_htseq_omics import tool_matches as htseq_matches
from catalog_fixed_cohort import verify_fixed_cohort
from palantir_catalog import current_catalog_review_context,load_catalog_registry

DEFINITIONS=[dict(id='dorothea',name='DoRothEA',pattern=r'\bDoRothEA\b'),dict(id='scfates',name='scFates',pattern=r'\bscFates\b'),dict(id='palantir',name='Palantir',pattern=r'\bPalantir\b'),dict(id='stereo-saw',name='SAW',pattern=r'(?-i:\bSAW\b)'),dict(id='stereopy',name='Stereopy',pattern=r'\bStereo[Pp]y\b')]
for definition,role in zip(DEFINITIONS,['TF-target regulatory evidence resource','Principal-tree trajectory and pseudotime inference','Multiscale diffusion-space construction','Stereo-seq mapping/counting/segmentation','Spatial expression-file conversion'],strict=True):definition['role']=role

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers);registry={r['id']:r for r in load_catalog_registry(ROOT)};reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];observations=0;sources={};followups=[]
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw);assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(xml);sources[paper['pmcid']]=ps;by={r['paragraph_index']:r for r in ps};cache=json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes());cached={t['tool_id']:t for t in cache['tools']}
        for tool in paper['tools']:
            anchor=by[tool['evidence_paragraph_index']];assert anchor['text']==cached[tool['tool_id']]['evidence_text'] and hashlib.sha256(anchor['text'].encode()).hexdigest()==tool['evidence_paragraph_sha256'];assert tool['tool_id'] in registry
            if not re.search(registry[tool['tool_id']]['pattern'],anchor['match_text'],re.I):assert (paper['paper_id'],tool['tool_id'],anchor['paragraph_index'])==('P005','htseq',54) and htseq_matches([anchor],[registry['htseq']],{'htseq':tool['evidence_paragraph_sha256']})[0]['matched_text']=='HT-seq'
            observations+=1
        for match in tool_matches(ps,DEFINITIONS,{}):
            index=match['evidence_paragraph_index'];anchor=by[index];followups.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],tool_id=match['tool_id'],primary_paragraph=index,paragraph_sha256=hashlib.sha256(anchor['text'].encode()).hexdigest(),section=anchor['section'],evidence_text=anchor['text'],registry_applied=True,role_review_applied=True,implementation_credit=match['tool_id']=='palantir'))
    assert len(papers)==250 and observations==2465 and len(reviews)==988
    with current_catalog_review_context():
        import cellphonedb_catalog
        cellphonedb_catalog.verify_review_extension(ROOT,data,reviews)
    by_pmid={p['pmcid']:p for p in papers}
    for review in reviews:
        paper=by_pmid[review['pmcid']];tool=next(t for t in paper['tools'] if t['tool_id']==review['tool_id']);assert paper['doi'].lower()==review['doi'].lower();validate_supporting_evidence(sources[paper['pmcid']],[review],registry)
        for key in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','usage_context','version_reported_near_name','supporting_evidence']:
            if key in review:assert tool[key]==review[key]
    save(out/'named-followup-scan.json',dict(status='registered-primary-full-cohort-followup-scan',definitions=DEFINITIONS,observations=followups,whole_goal_complete=False));save(out/'COMPLETED.json',dict(status='passed-250-primary-XML-DOI-and-988-role-audit-with-complete-followup-scan',source_records=250,observations_verified=observations,reviewed_uses_verified=len(reviews),supporting_pointers_verified=sum(len(r.get('supporting_evidence',[])) for r in reviews),followup_observations=len(followups),named_followup_scan=pin(out/'named-followup-scan.json'),source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/palantir_catalog.py',ROOT/'tools/literature/collect_omics_review.py',ROOT/'tools/literature/collect_htseq_omics.py']},papers_pin=pin(data/'papers.json'),reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),registry_or_reviews_changed=True,source_roles_applied=True,whole_goal_complete=False));print('Audited250 original XML/DOIs,2465 observations and988 roles; found',len(followups),'registered and reviewed observations; implementation credit is Palantir only')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-source checks')
    main()
