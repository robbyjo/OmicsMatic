# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append twelve primary-bound reviews and three software observations without altering prior uses."""
import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,publication_record,tool_matches,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists();out.mkdir()
    data=ROOT/'docs/literature/omics-2023-2026';here=ROOT/'tools/literature';paths=dict(papers=data/'papers.json',reviews=here/'reviewed-uses.json',enrichment=data/'catalog-enrichment.json');before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);old=json.loads(before['reviews']);drafts=json.loads((here/'monocle-catalog-use-reviews.json').read_bytes());overlay=json.loads((here/'monocle-named-software-registry.json').read_bytes());registry=json.loads((here/'tool-registry.json').read_bytes())+overlay;reg={r['id']:r for r in registry}
    assert len(papers)==250 and len(old['reviews'])==905 and len(drafts)==12 and len(reg)==len(registry)==427;assert not any(r['tool_id'] in ['monocle','cicero','py-monocle'] for r in old['reviews']);verify_fixed_cohort(ROOT,data,papers)
    reviewed=copy.deepcopy(old);reviewed['reviews'].extend(d['review'] for d in drafts);assert reviewed['reviews'][:905]==old['reviews'] and len(reviewed['reviews'])==917;selected={(d['paper_id'],d['review']['tool_id']):d for d in drafts};updated=copy.deepcopy(papers);proof=[];cache_updates={}
    allowed={'use_summary','implementation_scope_note','version_reported_near_name','supporting_evidence','review_status','evidence_selection','usage_context'}
    for original,paper in zip(papers,updated):
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));relevant=[d for (pid,_),d in selected.items() if pid==paper['paper_id']]
        if not relevant:assert paper==original;continue
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');cache_raw=cache.read_bytes();cached=json.loads(cache_raw)
        for d in relevant:
            r=d['review'];tid=r['tool_id'];validate_supporting_evidence(ps,[r],reg);matches=tool_matches(ps,[reg[tid]],{tid:r['evidence_paragraph_sha256']});assert len(matches)==1 and matches[0]['evidence_paragraph_index']==d['primary_paragraph'];published=publication_record(dict(paper,tools=matches),[r])['tools'][0]
            if tid=='monocle':
                at=next(i for i,t in enumerate(paper['tools']) if t['tool_id']==tid);previous=paper['tools'][at];assert {k:v for k,v in previous.items() if k not in allowed}=={k:v for k,v in published.items() if k not in allowed};paper['tools'][at]=published
                at=next(i for i,t in enumerate(cached['tools']) if t['tool_id']==tid);cached['tools'][at]=matches[0]
            else:
                assert tid in ['cicero','py-monocle'] and not any(t['tool_id']==tid for t in paper['tools']);paper['tools'].append(published);cached['tools'].append(matches[0])
            proof.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tid,xml_sha256=paper['xml_sha256'],paragraph_index=d['primary_paragraph'],paragraph_sha256=r['evidence_paragraph_sha256'],reported_version=r['version_reported_near_name'],supporting_evidence=r['supporting_evidence']))
        cache_updates[cache]=(cache_raw,serialize(cached,cache_raw))
    for x,y in zip(papers,updated):
        assert {k:v for k,v in x.items() if k!='tools'}=={k:v for k,v in y.items() if k!='tools'};assert [t for t in x['tools'] if t['tool_id'] not in ['monocle','cicero','py-monocle']]==[t for t in y['tools'] if t['tool_id'] not in ['monocle','cicero','py-monocle']]
    assert sum(len(p['tools']) for p in updated)==2449 and len({t['tool_id'] for p in updated for t in p['tools']})==389
    enrichment=json.loads(before['enrichment']);enrichment.setdefault('history',[]).append(dict(reason='Nine Monocle generation/role reviews and three recovered Cicero/py-monocle observations',previous_catalog_sha256=hashlib.sha256(before['papers']).hexdigest(),prior_reviews=905,total_reviews=917,total_observations=2449,supporting_pointers=309))
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviewed,before['reviews']),enrichment=serialize(enrichment,before['enrichment']));row=inverse(before['reviews'],after['reviews'])
    for k,path in paths.items():(out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[k],mtime=0))
    for path,(raw,_) in cache_updates.items():(out/('PRIVATE-before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(raw,mtime=0))
    result=dict(status='passed-twelve-Monocle-related-primary-role-reviews',applied=a.apply,prior_reviews_preserved=905,new_reviews=12,total_reviews=917,source_XML_hashes_checked=250,prior_observations=2446,total_observations=2449,new_observations=3,distinct_named_tools=389,registry_base_entries=425,registry_extension_entries=2,supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in reviewed['reviews']),source_proof=proof,curator_sha256=pin(Path(__file__))['sha256'],draft_sha256=pin(here/'monocle-catalog-use-reviews.json')['sha256'],registry_extension=pin(here/'monocle-named-software-registry.json'),before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),implementation_promoted=False,whole_goal_complete=False)
    assert result['supporting_pointers']==309
    if a.apply:
        roles=data/'monocle-role-coverage.json';descriptor=data/'monocle-reviews-shared-source-compatibility.json';baseline=data/'monocle-prior-reviews.json.gz';assert not any(p.exists() for p in [roles,descriptor,baseline]);assert all(p.read_bytes()==before[k] for k,p in paths.items()) and all(p.read_bytes()==v[0] for p,v in cache_updates.items())
        baseline.write_bytes(gzip.compress(before['reviews'],mtime=0));result['prior_review_archive']=pin(baseline);save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='monocle',scope='Only twelve appended primary-bound Monocle/Cicero/py-monocle role reviews; every905 prior review remains unchanged. No numerical/runtime/scientific-source waiver.',paths=[row]))
        for k,p in paths.items():p.write_bytes(after[k])
        for p,(_,raw) in cache_updates.items():p.write_bytes(raw)
        save(roles,result)
    save(out/'COMPLETED.json',result);print(json.dumps({k:v for k,v in result.items() if k!='source_proof'}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/cohort/prior-review checks')
    main()
