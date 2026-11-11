# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify/apply ten Nextflow use reviews while preserving the cohort and prior reviews."""
import argparse
import copy
import gzip
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
HERE=ROOT/'tools/literature'
sys.path.insert(0,str(HERE))
from curate_shapeit_uses import digest,serialize,inverse
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    output=args.out.resolve();assert output.is_relative_to((ROOT/'build/literature-review/nextflow-runtime').resolve()) and not output.exists();output.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';pp=data/'papers.json';vp=HERE/'reviewed-uses.json';ep=data/'catalog-enrichment.json'
    original={p:p.read_bytes() for p in [pp,vp,ep]};papers=json.loads(original[pp]);reviews=json.loads(original[vp]);prior=json.loads(original[ep]);registry=json.loads((HERE/'tool-registry.json').read_bytes());drafts=json.loads((HERE/'nextflow-use-reviews.json').read_bytes())
    assert len(papers)==250 and len(registry)==418 and len(reviews['reviews'])==775 and len(drafts)==10
    assert {d['paper_id'] for d in drafts}=={'P002','P008','P013','P016','P027','P036','P143','P156','P169','P227'}
    assert not any(r['tool_id']=='nextflow' for r in reviews['reviews'])
    by_pmc={d['review']['pmcid']:d for d in drafts};new_reviews=copy.deepcopy(reviews);new_reviews['reviews'] += [d['review'] for d in drafts]
    assert len(new_reviews['reviews'])==785 and new_reviews['reviews'][:775]==reviews['reviews']
    preferred={(r['pmcid'],r['tool_id']):r['evidence_paragraph_sha256'] for r in new_reviews['reviews']};updated=copy.deepcopy(papers);proofs=[];updates={};changes=[]
    for old,paper in zip(papers,updated):
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert digest(raw)==paper['xml_sha256']
        ps=paragraphs(ET.fromstring(raw));prefs={tool:h for (pmc,tool),h in preferred.items() if pmc==paper['pmcid']};matched=tool_matches(ps,registry,prefs)
        old_by={t['tool_id']:t for t in old['tools']};by={t['tool_id']:t for t in matched};assert set(old_by)==set(by)
        for key,t in old_by.items():
            if key!='nextflow':assert (t['evidence_paragraph_index'],t['evidence_paragraph_sha256'])==(by[key]['evidence_paragraph_index'],digest(by[key]['evidence_text'].encode())),(paper['paper_id'],key)
        if 'nextflow' not in by:changes.append(dict(paper_id=paper['paper_id'],reviewed=[]));continue
        draft=by_pmc[paper['pmcid']];review=draft['review'];assert (draft['paper_id'],review['doi'],review['tool_id'])==(paper['paper_id'],paper['doi'],'nextflow')
        validate_supporting_evidence(ps,[review],{t['id']:t for t in registry});candidate=by['nextflow']
        assert candidate['evidence_paragraph_index']==draft['primary_paragraph'] and digest(candidate['evidence_text'].encode())==review['evidence_paragraph_sha256']
        published=publication_record(dict(paper,tools=[candidate]),[review])['tools'][0];paper['tools']=[published if t['tool_id']=='nextflow' else t for t in paper['tools']]
        assert [t for t in paper['tools'] if t['tool_id']!='nextflow']==[t for t in old['tools'] if t['tool_id']!='nextflow'];changes.append(dict(paper_id=paper['paper_id'],reviewed=['nextflow']))
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');before=cache.read_bytes();cached=json.loads(before);assert sum(t['tool_id']=='nextflow' for t in cached['tools'])==1
        cached['tools']=[candidate if t['tool_id']=='nextflow' else t for t in cached['tools']];updates[cache]=(before,serialize(cached,before))
        proofs.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],paragraph_index=draft['primary_paragraph'],paragraph_sha256=review['evidence_paragraph_sha256'],reported_version=draft['reported_version']))
    assert len(proofs)==len(updates)==10 and sum(len(p['tools']) for p in updated)==2430
    assert all({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'} for a,b in zip(papers,updated))
    papers_after=serialize(updated,original[pp]);reviews_after=serialize(new_reviews,original[vp])
    compatibility=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='nextflow',scope='Append ten source-reviewed Nextflow roles with five supporting paragraphs. All775 prior reviews are preserved; inverse reconstructs exact historical review bytes. This changes catalog usage descriptions only and does not credit a new implementation.',paths=[inverse(original[vp],reviews_after)])
    history=prior.get('history',[])+[{k:v for k,v in prior.items() if k!='history'}]
    enrichment=dict(previous_catalog_sha256=digest(original[pp]),registry_sha256=digest((HERE/'tool-registry.json').read_bytes()),source_cohort_unchanged=True,paper_count=250,history=history,changes=changes,extension_method='Full250-source/full418-registry verification preserves all2430 observations and unrelated primary pointers; add ten reviewed Nextflow uses with five supporting method paragraphs.',unrelated_observations_preserved=2420)
    report=dict(status='passed-ten-Nextflow-primary-source-role-reviews',applied=False,source_papers_scanned=250,registry_rows=418,total_observations=2430,prior_reviews=775,unrelated_prior_reviews_preserved=775,new_reviews_appended=10,total_reviews=785,reviewed_roles=10,supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in new_reviews['reviews']),source_proof=proofs,curator_sha256=digest(Path(__file__).read_bytes()),draft_sha256=digest((HERE/'nextflow-use-reviews.json').read_bytes()),whole_paper_reproduced=False,implementation_promoted=False)
    for path,raw in original.items():(output/('before-'+path.name+'.gz')).write_bytes(gzip.compress(raw,mtime=0))
    for path,(before,_) in updates.items():(output/('before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(before,mtime=0))
    if args.apply:
        assert all(p.read_bytes()==raw for p,raw in original.items()) and all(p.read_bytes()==pair[0] for p,pair in updates.items())
        target=data/'nextflow-use-shared-source-compatibility.json';assert not target.exists();target.write_text(json.dumps(compatibility,indent=2)+'\n',encoding='utf-8',newline='\n')
        for p,raw in [(pp,papers_after),(vp,reviews_after),(ep,serialize(enrichment,original[ep])),*[(p,pair[1]) for p,pair in updates.items()]]:p.write_bytes(raw)
        report.update(applied=True,papers_sha256=digest(papers_after),reviews_sha256=digest(reviews_after))
    (output/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps({k:v for k,v in report.items() if k!='source_proof'}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source review assertions')
    main()
