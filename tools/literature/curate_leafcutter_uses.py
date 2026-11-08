# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append six LeafCutter primary roles and three supporting pointers; preserve all960 prior reviews and2450 observations."""
import argparse,copy,gzip,hashlib,json
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence
from hail_catalog import load_catalog_registry
from catalog_fixed_cohort import verify_fixed_cohort


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';here=ROOT/'tools/literature';paths=dict(papers=data/'papers.json',reviews=here/'reviewed-uses.json');before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);old=json.loads(before['reviews']);draft_path=here/'leafcutter-use-reviews.json';drafts=json.loads(draft_path.read_bytes());registry={r['id']:r for r in load_catalog_registry(ROOT)};verify_fixed_cohort(ROOT,data,papers);assert len(old['reviews'])==960 and sum(len(p['tools']) for p in papers)==2450 and sum(r['tool_id']=='leafcutter' for r in old['reviews'])==1 and len(drafts)==6 and sum(len(d['review'].get('supporting_evidence',[])) for d in drafts)==3
    if a.apply:
        proof=json.loads((ROOT/'docs/literature/leafcutter-workflow-development/VALIDATION.json').read_bytes());assert proof['adapter_admitted'] and proof['implementation_credit'],'Full workflow proof must be admitted before catalog role application'
    existing_p108=copy.deepcopy(next(r for r in old['reviews'] if r['tool_id']=='leafcutter'));assert existing_p108['pmcid']=='PMC10703692';updated=copy.deepcopy(papers);reviewed=copy.deepcopy(old);reviewed['reviews'].extend(d['review'] for d in drafts);by={};checked=0;caches=[];changed=[]
    for d in drafts:by.setdefault(d['paper_id'],[]).append(d)
    assert sorted(by)==['P073','P132','P157','P190','P201','P238']
    for paper in updated:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];root=ET.fromstring(raw);assert next(e.text for e in root.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();checked+=1
        if paper['paper_id'] not in by:continue
        ps=paragraphs(root);cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');rawcache=cache.read_bytes();cached=json.loads(rawcache)
        for draft in by[paper['paper_id']]:
            review=draft['review'];tool=review['tool_id'];assert paper['xml_sha256']==draft['xml_sha256'];validate_supporting_evidence(ps,[review],registry);matches=tool_matches(ps,[registry[tool]],{tool:review['evidence_paragraph_sha256']});assert len(matches)==1 and matches[0]['evidence_paragraph_index']==draft['primary_paragraph'];published=publication_record(dict(paper,tools=matches),[review])['tools'][0];index=next(i for i,t in enumerate(paper['tools']) if t['tool_id']==tool);paper['tools'][index]=published;index=next(i for i,t in enumerate(cached['tools']) if t['tool_id']==tool);cached['tools'][index]=matches[0]
        caches.append((cache,rawcache,serialize(cached,rawcache)));changed.append(paper['paper_id'])
    for prior,current in zip(papers,updated):assert {k:v for k,v in prior.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'} and [t for t in prior['tools'] if t['tool_id'] not in ['leafcutter']]==[t for t in current['tools'] if t['tool_id'] not in ['leafcutter']]
    assert checked==250 and len(caches)==6 and changed==sorted(by) and sum(len(p['tools']) for p in updated)==2450;assert reviewed['reviews'][:960]==old['reviews'] and len(reviewed['reviews'])==966 and sum(len(r.get('supporting_evidence',[])) for r in reviewed['reviews'])==334;assert next(r for r in reviewed['reviews'] if r['tool_id']=='leafcutter' and r['pmcid']=='PMC10703692')==existing_p108;assert next(p for p in papers if p['paper_id']=='P108')==next(p for p in updated if p['paper_id']=='P108');after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviewed,before['reviews']));row=inverse(before['reviews'],after['reviews'])
    for key,path in paths.items():(out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[key],mtime=0))
    result=dict(status='passed-six-LeafCutter-primary-role-reviews',applied=a.apply,prior_reviews_preserved=960,new_reviews=6,total_reviews=966,total_observations=2450,distinct_named_tools=390,source_XML_hashes_checked=250,supporting_pointers=334,changed_paper_roles=changed,curator_sha256=pin(Path(__file__))['sha256'],draft_sha256=pin(draft_path)['sha256'],before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),whole_paper_reproduced=False,whole_goal_complete=False)
    if a.apply:
        roles=data/'leafcutter-role-coverage.json';baseline=data/'leafcutter-prior-reviews.json.gz';prior_papers=data/'leafcutter-prior-papers.json.gz';descriptor=data/'leafcutter-reviews-shared-source-compatibility.json';assert not any(p.exists() for p in [roles,baseline,prior_papers,descriptor]);assert all(paths[k].read_bytes()==v for k,v in before.items()) and all(p.read_bytes()==raw for p,raw,_ in caches);baseline.write_bytes(gzip.compress(before['reviews'],mtime=0));prior_papers.write_bytes(gzip.compress(before['papers'],mtime=0));result['prior_review_archive']=pin(baseline);result['prior_papers_archive']=pin(prior_papers);save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='leafcutter',scope='Only6 appended primary-bound LeafCutter roles and3 supporting pointers; all960 prior reviews and2450 observations remain preserved. No scientific/runtime waiver.',paths=[row]))
        for key,path in paths.items():path.write_bytes(after[key])
        for path,raw,new in caches:path.write_bytes(new)
        save(roles,result)
    save(out/'COMPLETED.json',result);print(json.dumps(result))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-source/cohort/prior-review checks')
    main()
