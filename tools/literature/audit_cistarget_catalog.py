# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck250 primary sources,2466 observations and1018 reviews after the explicit correction."""
import argparse,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,validate_supporting_evidence
from collect_htseq_omics import tool_matches as htseq_matches
from catalog_fixed_cohort import verify_fixed_cohort
from cistarget_catalog import current_catalog_review_context,verify_review_extension,load_catalog_registry


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026'
    papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers);reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];registry={r['id']:r for r in load_catalog_registry(ROOT)}
    assert len(papers)==250 and len(reviews)==1018 and len(registry)==438;sources={};observations=0
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw)
        assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi').lower()==paper['doi'].lower()
        ps=paragraphs(xml);sources[paper['pmcid']]=ps;by={p['paragraph_index']:p for p in ps};cached={r['tool_id']:r for r in json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes())['tools']}
        for tool in paper['tools']:
            anchor=by[tool['evidence_paragraph_index']];assert anchor['text']==cached[tool['tool_id']]['evidence_text'] and hashlib.sha256(anchor['text'].encode()).hexdigest()==tool['evidence_paragraph_sha256']
            if not re.search(registry[tool['tool_id']]['pattern'],anchor['match_text'],re.I):
                assert (paper['paper_id'],tool['tool_id'],anchor['paragraph_index'])==('P005','htseq',54)
                assert htseq_matches([anchor],[registry['htseq']],{'htseq':tool['evidence_paragraph_sha256']})[0]['matched_text']=='HT-seq'
            observations+=1
    assert observations==2466
    with current_catalog_review_context():verify_review_extension(ROOT,data,reviews)
    by={p['pmcid']:p for p in papers}
    for review in reviews:
        paper=by[review['pmcid']];assert paper['doi'].lower()==review['doi'].lower();tool=next(t for t in paper['tools'] if t['tool_id']==review['tool_id']);validate_supporting_evidence(sources[paper['pmcid']],[review],registry)
        for key in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','usage_context','version_reported_near_name','supporting_evidence']:
            if key in review:assert tool[key]==review[key]
    prior=json.loads(gzip.decompress((data/'cistarget-prior-papers.json.gz').read_bytes()));changed=[];added=[]
    for old,new in zip(prior,papers,strict=True):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [t for t in old['tools'] if t['tool_id']!='cistarget']==[t for t in new['tools'] if t['tool_id']!='cistarget']
        old_ids={t['tool_id'] for t in old['tools']};new_ids={t['tool_id'] for t in new['tools']};assert old_ids<=new_ids
        added.extend((new['paper_id'],tool) for tool in new_ids-old_ids)
        if old!=new:changed.append(new['paper_id'])
    assert changed==['P047','P058','P061','P065','P094','P100','P145'] and added==[('P145','cistarget')]
    assert sum(len(r.get('supporting_evidence',[])) for r in reviews)==356
    save(out/'COMPLETED.json',dict(status='PASS250_PRIMARY_SOURCES_2466_OBSERVATIONS_1018_REVIEWS_AND_SEVEN_CISTARGET_ROLES',source_records=250,observations_verified=2466,reviewed_uses_verified=1018,supporting_pointers_verified=356,
        changed_paper_roles=changed,new_observations=added,unchanged_prior_reviews=1011,explicit_prior_review_supersessions=1,all_non_cistarget_source_roles_preserved=True,
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/cistarget_catalog.py',ROOT/'tools/literature/collect_omics_review.py',ROOT/'tools/literature/collect_htseq_omics.py']},
        papers_pin=pin(data/'papers.json'),reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),whole_goal_complete=False));print('PASS250 XML/DOIs;2466 observations;1018 reviews;356 pointers; only seven cisTarget roles changed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable all-source/prior-review checks')
    main()
