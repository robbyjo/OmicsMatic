# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply seven primary-bound ChIPseeker reviews, preserving all898 prior reviews and unrelated uses."""
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
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true')
    args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    data=ROOT/'docs/literature/omics-2023-2026';here=ROOT/'tools/literature'
    paths=dict(papers=data/'papers.json',reviews=here/'reviewed-uses.json',enrichment=data/'catalog-enrichment.json')
    before={key:path.read_bytes() for key,path in paths.items()};papers=json.loads(before['papers']);prior=json.loads(before['reviews'])
    drafts=json.loads((here/'chipseeker-use-reviews.json').read_bytes());registry=json.loads((here/'tool-registry.json').read_bytes())
    assert len(papers)==250 and len(prior['reviews'])==898 and len(drafts)==7 and len(registry)==425
    assert not any(r['tool_id']=='chipseeker' for r in prior['reviews']);verify_fixed_cohort(ROOT,data,papers)
    reviews=copy.deepcopy(prior);reviews['reviews'].extend(d['review'] for d in drafts);assert reviews['reviews'][:898]==prior['reviews'] and len(reviews['reviews'])==905
    selected={d['review']['pmcid']:d for d in drafts};updated=copy.deepcopy(papers);source_proof=[];cache_updates={}
    allowed={'use_summary','implementation_scope_note','version_reported_near_name','supporting_evidence','review_status','evidence_selection','usage_context'}
    observed={(p['paper_id'],t['tool_id']) for p in papers for t in p['tools'] if t['tool_id']=='chipseeker'}
    assert observed=={(d['paper_id'],'chipseeker') for d in drafts}
    xml_checked=0
    for original,paper in zip(papers,updated):
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml_checked+=1
        if paper['pmcid'] not in selected:assert paper==original;continue
        draft=selected[paper['pmcid']];review=draft['review'];ps=paragraphs(ET.fromstring(raw));reg={r['id']:r for r in registry}
        validate_supporting_evidence(ps,[review],reg)
        matches=tool_matches(ps,[reg['chipseeker']],{'chipseeker':review['evidence_paragraph_sha256']})
        assert len(matches)==1 and matches[0]['evidence_paragraph_index']==draft['primary_paragraph']
        published=publication_record(dict(paper,tools=matches),[review])['tools'][0]
        at=next(i for i,t in enumerate(paper['tools']) if t['tool_id']=='chipseeker');old=paper['tools'][at]
        assert {k:v for k,v in old.items() if k not in allowed}=={k:v for k,v in published.items() if k not in allowed}
        paper['tools'][at]=published
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');raw_cache=cache.read_bytes();cached=json.loads(raw_cache)
        ci=next(i for i,t in enumerate(cached['tools']) if t['tool_id']=='chipseeker');cached['tools'][ci]=matches[0]
        cache_updates[cache]=(raw_cache,serialize(cached,raw_cache))
        source_proof.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],
            paragraph_index=draft['primary_paragraph'],paragraph_sha256=review['evidence_paragraph_sha256'],reported_version=review['version_reported_near_name'],
            supporting_paragraphs=review['supporting_evidence']))
    for a,b in zip(papers,updated):
        assert {k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'}
        assert [t for t in a['tools'] if t['tool_id']!='chipseeker']==[t for t in b['tools'] if t['tool_id']!='chipseeker']
    assert sum(len(p['tools']) for p in updated)==2446
    enrichment=json.loads(before['enrichment']);enrichment.setdefault('history',[]).append(dict(reason='Seven ChIPseeker source-use reviews; source cohort and2446 observations unchanged',
        previous_catalog_sha256=hashlib.sha256(before['papers']).hexdigest(),prior_reviews=898,total_reviews=905,supporting_pointers=302))
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviews,before['reviews']),enrichment=serialize(enrichment,before['enrichment']))
    row=inverse(before['reviews'],after['reviews'])
    for key,path in paths.items():(out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[key],mtime=0))
    for path,(raw,_) in cache_updates.items():(out/('PRIVATE-before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(raw,mtime=0))
    result=dict(status='passed-seven-ChIPseeker-primary-role-reviews',applied=args.apply,reviewed_roles=7,prior_reviews_preserved=898,new_reviews=7,total_reviews=905,
        source_papers_XML_hashes_checked=xml_checked,prior_observations=2446,total_observations=2446,unrelated_observations_preserved=2439,
        supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews']),ChIPseeker_supporting_pointers=1,
        source_proof=source_proof,curator_sha256=pin(Path(__file__))['sha256'],draft_sha256=pin(here/'chipseeker-use-reviews.json')['sha256'],
        before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),
        implementation_promoted=False,whole_paper_reproduced=False)
    assert result['supporting_pointers']==302
    if args.apply:
        descriptor=data/'chipseeker-use-shared-source-compatibility.json';roles=data/'chipseeker-role-coverage.json';baseline=data/'chipseeker-prior-reviews.json.gz'
        assert not any(path.exists() for path in [descriptor,roles,baseline])
        assert all(path.read_bytes()==before[key] for key,path in paths.items())
        assert all(path.read_bytes()==pair[0] for path,pair in cache_updates.items())
        baseline.write_bytes(gzip.compress(before['reviews'],mtime=0));result['prior_review_archive']=pin(baseline)
        save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='chipseeker',
            scope='Only seven appended primary-bound ChIPseeker reviews; every prior review and unrelated observation remains unchanged. No runtime/numerical source waiver.',paths=[row]))
        for key,path in paths.items():path.write_bytes(after[key])
        for path,(_,raw) in cache_updates.items():path.write_bytes(raw)
        save(roles,result)
    save(out/'COMPLETED.json',result)
    print(json.dumps({k:v for k,v in result.items() if k!='source_proof'}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/cohort/review preservation checks')
    main()
