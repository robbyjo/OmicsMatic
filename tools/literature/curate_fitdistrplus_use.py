# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append one source-bound fitdistrplus observation/review; preserve every prior paper field and review."""
import argparse,copy,gzip,hashlib,json
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence
from monocle_catalog_context import load_catalog_registry
from catalog_fixed_cohort import verify_fixed_cohort


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/fitdistrplus-runtime') and not out.exists();out.mkdir()
    data=ROOT/'docs/literature/omics-2023-2026';here=ROOT/'tools/literature';paths=dict(papers=data/'papers.json',reviews=here/'reviewed-uses.json',enrichment=data/'catalog-enrichment.json');before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);old=json.loads(before['reviews']);draft=json.loads((here/'fitdistrplus-use-review.json').read_bytes());extra=json.loads((here/'fitdistrplus-named-software-registry.json').read_bytes());registry={r['id']:r for r in load_catalog_registry(ROOT)+extra};verify_fixed_cohort(ROOT,data,papers)
    assert len(old['reviews'])==917 and sum(len(p['tools']) for p in papers)==2449 and len(registry)==428 and not any(r['tool_id']=='fitdistrplus' for r in old['reviews'])
    updated=copy.deepcopy(papers);reviewed=copy.deepcopy(old);reviewed['reviews'].append(draft['review']);checked=0;cache_before=None
    for paper in updated:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];root=ET.fromstring(raw);assert next(e.text for e in root.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();checked+=1
        if paper['paper_id']!=draft['paper_id']:continue
        ps=paragraphs(root);review=draft['review'];validate_supporting_evidence(ps,[review],registry);matches=tool_matches(ps,[registry['fitdistrplus']],{'fitdistrplus':review['evidence_paragraph_sha256']});assert len(matches)==1 and matches[0]['evidence_paragraph_index']==51
        published=publication_record(dict(paper,tools=matches),[review])['tools'][0];assert not any(t['tool_id']=='fitdistrplus' for t in paper['tools']);paper['tools'].append(published)
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');cache_before=cache.read_bytes();cached=json.loads(cache_before);cached['tools'].append(matches[0]);cache_after=serialize(cached,cache_before)
    for prior,current in zip(papers,updated):
        assert {k:v for k,v in prior.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'};assert prior['tools']==[t for t in current['tools'] if t['tool_id']!='fitdistrplus']
    assert checked==250 and cache_before is not None and sum(len(p['tools']) for p in updated)==2450 and len({t['tool_id'] for p in updated for t in p['tools']})==390
    enrichment=json.loads(before['enrichment']);enrichment.setdefault('history',[]).append(dict(reason='Recover primary P043 fitdistrplus normal-background role',prior_reviews=917,total_reviews=918,prior_observations=2449,total_observations=2450,previous_catalog_sha256=hashlib.sha256(before['papers']).hexdigest()))
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviewed,before['reviews']),enrichment=serialize(enrichment,before['enrichment']));row=inverse(before['reviews'],after['reviews'])
    for key,path in paths.items():(out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[key],mtime=0))
    (out/'PRIVATE-before-extraction.json.gz').write_bytes(gzip.compress(cache_before,mtime=0))
    result=dict(status='passed-one-fitdistrplus-primary-role-review',applied=a.apply,prior_reviews_preserved=917,new_reviews=1,total_reviews=918,prior_observations=2449,total_observations=2450,distinct_named_tools=390,source_XML_hashes_checked=250,supporting_pointers=309,curator_sha256=pin(Path(__file__))['sha256'],draft_sha256=pin(here/'fitdistrplus-use-review.json')['sha256'],registry_extension=pin(here/'fitdistrplus-named-software-registry.json'),before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),whole_paper_reproduced=False,whole_goal_complete=False)
    if a.apply:
        roles=data/'fitdistrplus-role-coverage.json';baseline=data/'fitdistrplus-prior-reviews.json.gz';prior_papers=data/'fitdistrplus-prior-papers.json.gz';descriptor=data/'fitdistrplus-reviews-shared-source-compatibility.json';assert not any(p.exists() for p in [roles,baseline,prior_papers,descriptor]);assert all(paths[k].read_bytes()==v for k,v in before.items()) and cache.read_bytes()==cache_before
        baseline.write_bytes(gzip.compress(before['reviews'],mtime=0));prior_papers.write_bytes(gzip.compress(before['papers'],mtime=0));result['prior_review_archive']=pin(baseline);result['prior_papers_archive']=pin(prior_papers);save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='fitdistrplus',scope='Only one appended primary-bound fitdistrplus role; all917 prior reviews remain byte-reconstructable and unchanged. No scientific/runtime waiver.',paths=[row]))
        for key,path in paths.items():path.write_bytes(after[key])
        cache.write_bytes(cache_after);save(roles,result)
    save(out/'COMPLETED.json',result);print(json.dumps(result))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/cohort/prior-review checks')
    main()
