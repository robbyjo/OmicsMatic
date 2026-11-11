#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review eight mashr uses, preserving the full cohort and all prior reviews."""
import argparse,copy,gzip,json,sys
from pathlib import Path
import xml.etree.ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parent))
from curate_shapeit_uses import digest,serialize,inverse
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence
ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
DATA=ROOT/'docs/literature/omics-2023-2026'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--apply',action='store_true');args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    pp=DATA/'papers.json';vp=HERE/'reviewed-uses.json';ep=DATA/'catalog-enrichment.json';original={p:p.read_bytes() for p in [pp,vp,ep]}
    papers=json.loads(original[pp]);reviews=json.loads(original[vp]);prior=json.loads(original[ep]);registry=json.loads((HERE/'tool-registry.json').read_bytes());drafts=json.loads((HERE/'mashr-use-reviews.json').read_bytes())
    assert len(papers)==250 and len(registry)==418 and len(reviews['reviews'])==767 and len(drafts)==8
    assert {d['paper_id'] for d in drafts}=={'P019','P021','P024','P073','P092','P104','P201','P213'} and not any(r['tool_id']=='mashr' for r in reviews['reviews'])
    by_pmc={d['review']['pmcid']:d for d in drafts};new_reviews=copy.deepcopy(reviews);new_reviews['reviews'] += [d['review'] for d in drafts]
    assert len(new_reviews['reviews'])==775 and new_reviews['reviews'][:767]==reviews['reviews']
    preferred={(r['pmcid'],r['tool_id']):r['evidence_paragraph_sha256'] for r in new_reviews['reviews']};updated=copy.deepcopy(papers);proofs=[];updates={};changes=[]
    for old,paper in zip(papers,updated):
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert digest(raw)==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));prefs={tool:h for (pmc,tool),h in preferred.items() if pmc==paper['pmcid']};matched=tool_matches(ps,registry,prefs)
        old_by={t['tool_id']:t for t in old['tools']};by={t['tool_id']:t for t in matched};assert set(old_by)==set(by)
        for key,t in old_by.items():
            if key!='mashr':assert (t['evidence_paragraph_index'],t['evidence_paragraph_sha256'])==(by[key]['evidence_paragraph_index'],digest(by[key]['evidence_text'].encode())),(paper['paper_id'],key)
        if 'mashr' not in by:changes.append(dict(paper_id=paper['paper_id'],reviewed=[]));continue
        draft=by_pmc[paper['pmcid']];review=draft['review'];assert (draft['paper_id'],review['doi'],review['tool_id'])==(paper['paper_id'],paper['doi'],'mashr');validate_supporting_evidence(ps,[review],{t['id']:t for t in registry});candidate=by['mashr']
        assert candidate['evidence_paragraph_index']==draft['primary_paragraph'] and digest(candidate['evidence_text'].encode())==review['evidence_paragraph_sha256']
        published=publication_record(dict(paper,tools=[candidate]),[review])['tools'][0];paper['tools']=[published if t['tool_id']=='mashr' else t for t in paper['tools']]
        assert [t for t in paper['tools'] if t['tool_id']!='mashr']==[t for t in old['tools'] if t['tool_id']!='mashr'];changes.append(dict(paper_id=paper['paper_id'],reviewed=['mashr']))
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');before=cache.read_bytes();cached=json.loads(before);assert sum(t['tool_id']=='mashr' for t in cached['tools'])==1;cached['tools']=[candidate if t['tool_id']=='mashr' else t for t in cached['tools']];updates[cache]=(before,serialize(cached,before))
        proofs.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],paragraph_index=draft['primary_paragraph'],paragraph_sha256=review['evidence_paragraph_sha256'],reported_version=draft['reported_version']))
    assert len(proofs)==len(updates)==8 and sum(len(p['tools']) for p in updated)==2430
    assert all({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'} for a,b in zip(papers,updated))
    papers_after=serialize(updated,original[pp]);reviews_after=serialize(new_reviews,original[vp]);compat=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='mashr',scope='Append eight source-reviewed mashr uses. All767 prior reviews remain unchanged; exact inverse reconstructs the historical full review file. This records usage, not numerical certification or catalog implementation promotion.',paths=[inverse(original[vp],reviews_after)])
    history=prior.get('history',[])+[{k:v for k,v in prior.items() if k!='history'}]
    enrichment=dict(previous_catalog_sha256=digest(original[pp]),registry_sha256=digest((HERE/'tool-registry.json').read_bytes()),source_cohort_unchanged=True,paper_count=250,history=history,changes=changes,extension_method='Full250-source/full418-registry extraction preserves all2430 observations and unrelated primary pointers; add eight reviewed mashr uses with12 supporting method pointers.',unrelated_observations_preserved=2422)
    report=dict(status='passed-eight-mashr-primary-source-role-reviews',applied=False,source_papers_scanned=250,registry_rows=418,total_observations=2430,prior_reviews=767,unrelated_prior_reviews_preserved=767,new_reviews_appended=8,total_reviews=775,reviewed_roles=8,supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in new_reviews['reviews']),source_proof=proofs,curator_sha256=digest(Path(__file__).read_bytes()),draft_sha256=digest((HERE/'mashr-use-reviews.json').read_bytes()),whole_paper_reproduced=False)
    for path,raw in original.items():(args.out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(raw,mtime=0))
    for path,(before,_) in updates.items():(args.out/('before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(before,mtime=0))
    if args.apply:
        assert all(p.read_bytes()==raw for p,raw in original.items()) and all(p.read_bytes()==pair[0] for p,pair in updates.items());target=DATA/'mashr-use-shared-source-compatibility.json';assert not target.exists();target.write_text(json.dumps(compat,indent=2)+'\n',encoding='utf-8',newline='\n')
        for p,raw in [(pp,papers_after),(vp,reviews_after),(ep,serialize(enrichment,original[ep])),*[(p,pair[1]) for p,pair in updates.items()]]:p.write_bytes(raw)
        report.update(applied=True,papers_sha256=digest(papers_after),reviews_sha256=digest(reviews_after))
    (args.out/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps({k:v for k,v in report.items() if k!='source_proof'}))


if __name__=='__main__':main()
