# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply14 primary-bound roles and all7 observations for five new definitions; preserve966 prior reviews and the exact250-paper cohort."""
import argparse,copy,gzip,hashlib,json
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence
from cellphonedb_catalog import load_catalog_registry
from catalog_fixed_cohort import verify_fixed_cohort


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--apply',action='store_true');args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';here=ROOT/'tools/literature';paths=dict(papers=data/'papers.json',reviews=here/'reviewed-uses.json');before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);old=json.loads(before['reviews']);draft_names=['cellphonedb-use-reviews.json','cellphonedb-additional-use-reviews.json'];drafts=[d for name in draft_names for d in json.loads((here/name).read_bytes())];registry={r['id']:r for r in load_catalog_registry(ROOT)};extra=json.loads((here/'cellphonedb-named-software-registry.json').read_bytes());extra_ids={r['id'] for r in extra};verify_fixed_cohort(ROOT,data,papers);assert len(old['reviews'])==966 and sum(len(p['tools']) for p in papers)==2450 and len(drafts)==14 and sum(len(d['review'].get('supporting_evidence',[])) for d in drafts)==6 and not any(r['tool_id'] in extra_ids|{'cellphonedb'} for r in old['reviews'])
    if args.apply:
        from validate_cellphonedb_integration_evidence import verify
        assert verify(emit=False)['implementation_credit']
    updated=copy.deepcopy(papers);reviewed=copy.deepcopy(old);reviewed['reviews'].extend(d['review'] for d in drafts);by={};caches=[];changed=[];scanned=[]
    for draft in drafts:by.setdefault(draft['paper_id'],[]).append(draft)
    assert sorted(by)==['P008','P010','P070','P094','P100','P160','P161','P167','P169'];new_observations=0
    for paper in updated:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw);assert next(e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(xml);new_matches=tool_matches(ps,extra,{});scanned.extend((paper['paper_id'],m['tool_id']) for m in new_matches)
        if paper['paper_id'] not in by:assert not new_matches;continue
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');rawcache=cache.read_bytes();cached=json.loads(rawcache)
        for draft in by[paper['paper_id']]:
            review=draft['review'];tool=review['tool_id'];assert paper['xml_sha256']==draft['xml_sha256'];validate_supporting_evidence(ps,[review],registry);matches=tool_matches(ps,[registry[tool]],{tool:review['evidence_paragraph_sha256']});assert len(matches)==1 and matches[0]['evidence_paragraph_index']==draft['primary_paragraph'];published=publication_record(dict(paper,tools=matches),[review])['tools'][0];existing=[i for i,t in enumerate(paper['tools']) if t['tool_id']==tool];cached_existing=[i for i,t in enumerate(cached['tools']) if t['tool_id']==tool];assert len(existing)==len(cached_existing)<=1
            if existing:paper['tools'][existing[0]]=published;cached['tools'][cached_existing[0]]=matches[0];assert tool=='cellphonedb'
            else:assert tool in extra_ids;paper['tools'].append(published);cached['tools'].append(matches[0]);new_observations+=1
        assert {m['tool_id'] for m in new_matches}=={d['review']['tool_id'] for d in by[paper['paper_id']] if d['review']['tool_id'] in extra_ids};caches.append((cache,rawcache,serialize(cached,rawcache)));changed.append(paper['paper_id'])
    affected=extra_ids|{'cellphonedb'}
    for prior,current in zip(papers,updated):assert {k:v for k,v in prior.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'} and [t for t in prior['tools'] if t['tool_id'] not in affected]==[t for t in current['tools'] if t['tool_id'] not in affected]
    assert len(scanned)==new_observations==7 and len(caches)==9 and changed==sorted(by) and sum(len(p['tools']) for p in updated)==2457 and len({t['tool_id'] for p in updated for t in p['tools']})==395;assert reviewed['reviews'][:966]==old['reviews'] and len(reviewed['reviews'])==980 and sum(len(r.get('supporting_evidence',[])) for r in reviewed['reviews'])==340;assert len({(r['pmcid'],r['tool_id']) for r in reviewed['reviews']})==980;after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviewed,before['reviews']));row=inverse(before['reviews'],after['reviews']);row['path']='tools/literature/reviewed-uses.json'
    for key,path in paths.items():(out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[key],mtime=0))
    result=dict(status='passed-fourteen-CellPhoneDB-and-related-primary-role-reviews',applied=args.apply,prior_reviews_preserved=966,new_reviews=14,total_reviews=980,total_observations=2457,new_named_observations=7,distinct_named_tools=395,source_XML_hashes_checked=250,fulltext_DOIs_checked=250,supporting_pointers=340,changed_paper_roles=changed,curator_sha256=pin(Path(__file__))['sha256'],draft_pins={name:pin(here/name) for name in draft_names},registry_extension=pin(here/'cellphonedb-named-software-registry.json'),before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),whole_paper_reproduced=False,whole_goal_complete=False)
    if args.apply:
        roles=data/'cellphonedb-role-coverage.json';baseline=data/'cellphonedb-prior-reviews.json.gz';prior_papers=data/'cellphonedb-prior-papers.json.gz';descriptor=data/'cellphonedb-reviews-shared-source-compatibility.json';assert not any(p.exists() for p in [roles,baseline,prior_papers,descriptor]);assert all(paths[k].read_bytes()==raw for k,raw in before.items()) and all(p.read_bytes()==raw for p,raw,_ in caches);baseline.write_bytes(gzip.compress(before['reviews'],mtime=0));prior_papers.write_bytes(gzip.compress(before['papers'],mtime=0));result['prior_review_archive']=pin(baseline);result['prior_papers_archive']=pin(prior_papers);save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='cellphonedb',scope='Exactly14 appended primary-bound roles,7 new observations from five definitions and6 supporting pointers. All966 prior reviews and the250-paper identities are retained; no scientific/runtime exception.',paths=[row]))
        for key,path in paths.items():path.write_bytes(after[key])
        for path,raw,new in caches:path.write_bytes(new)
        save(roles,result)
    save(out/'COMPLETED.json',result);print('Validated250 XML/DOIs;14 appended roles,7 new observations,980 total reviews,340 supporting pointers; applied='+str(args.apply))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-source/prior-review/cohort checks')
    main()
