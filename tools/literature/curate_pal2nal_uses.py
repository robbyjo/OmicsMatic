#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append PAL2NAL after full frozen-cohort extraction; preserve all unrelated data."""
import argparse,copy,gzip,json,os
from pathlib import Path
import xml.etree.ElementTree as ET
from curate_shapeit_uses import digest,serialize,inverse
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence

ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent;DATA=ROOT/'docs/literature/omics-2023-2026'
ENTRY=dict(id='pal2nal',name='PAL2NAL',pattern=r'(?<![A-Za-z0-9_])PAL2NAL(?![A-Za-z0-9_])',role='Protein-guided codon back-translation of aligned peptides and corresponding nucleotide sequences',current_coverage='native-subset-validated',existing_source='src/main/java/org/jlinalg/sequence/Pal2Nal.java',coverage_note='Scoped native v14/v14.1-matching codon back-translation with complete input, identity and filtering ledgers and a typed portable workflow. Full original CLI/web behavior, omitted paper settings and whole-paper parity are not claimed.')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--apply',action='store_true');args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    rp=HERE/'tool-registry.json';pp=DATA/'papers.json';vp=HERE/'reviewed-uses.json';ep=DATA/'catalog-enrichment.json'
    original={p:p.read_bytes() for p in [rp,pp,vp,ep]};registry=json.loads(original[rp]);papers=json.loads(original[pp]);reviews=json.loads(original[vp]);prior=json.loads(original[ep])
    assert len(registry)==417 and not any(t['id']=='pal2nal' for t in registry);assert len(reviews['reviews'])==759 and len(papers)==250
    drafts=json.loads((HERE/'pal2nal-use-reviews.json').read_bytes());assert {r['paper_id'] for r in drafts}=={'P110','P248'}
    expanded=registry+[ENTRY];new_papers=copy.deepcopy(papers);new_reviews=copy.deepcopy(reviews);new_reviews['reviews'] += [row['review'] for row in drafts]
    preferred={(r['pmcid'],r['tool_id']):r['evidence_paragraph_sha256'] for r in new_reviews['reviews']}
    proofs=[];cache_updates={};changes=[]
    for old,paper in zip(papers,new_papers):
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert digest(raw)==paper['xml_sha256']
        ps=paragraphs(ET.fromstring(raw));prefs={tool:hash_ for (pmc,tool),hash_ in preferred.items() if pmc==paper['pmcid']};matched=tool_matches(ps,expanded,prefs)
        old_by={t['tool_id']:t for t in old['tools']};by={t['tool_id']:t for t in matched};assert set(by)-set(old_by) in [set(),{'pal2nal'}] and set(old_by)<=set(by)
        for key,t in old_by.items():assert (t['evidence_paragraph_index'],t['evidence_paragraph_sha256'])==(by[key]['evidence_paragraph_index'],digest(by[key]['evidence_text'].encode())),(paper['paper_id'],key)
        added='pal2nal' in by
        changes.append(dict(paper_id=paper['paper_id'],added=['pal2nal'] if added else [],removed=[]))
        if not added:continue
        draft=next(row for row in drafts if row['paper_id']==paper['paper_id']);review=draft['review'];assert (review['pmcid'],review['doi'],review['tool_id'])==(paper['pmcid'],paper['doi'],'pal2nal')
        validate_supporting_evidence(ps,[review],{t['id']:t for t in expanded});candidate=by['pal2nal'];assert candidate['evidence_paragraph_index']==draft['primary_paragraph'] and digest(candidate['evidence_text'].encode())==review['evidence_paragraph_sha256']
        published=publication_record(dict(paper,tools=[candidate]),[review])['tools'][0];paper['tools'].append(published);paper['tools'].sort(key=lambda t:t['tool_id'])
        assert [t for t in paper['tools'] if t['tool_id']!='pal2nal']==old['tools']
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');before=cache.read_bytes();cached=json.loads(before);assert not any(t['tool_id']=='pal2nal' for t in cached['tools']);cached['tools'].append(candidate);cached['tools'].sort(key=lambda t:t['tool_id']);cache_updates[cache]=(before,serialize(cached,before))
        proofs.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],paragraph_index=draft['primary_paragraph'],paragraph_sha256=review['evidence_paragraph_sha256'],reported_version=published['version_reported_near_name']))
    assert len(proofs)==len(cache_updates)==2 and sum(len(p['tools']) for p in new_papers)==2430
    assert new_reviews['reviews'][:759]==reviews['reviews'] and all({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'} for a,b in zip(papers,new_papers))
    registry_after=serialize(expanded,original[rp]);papers_after=serialize(new_papers,original[pp]);reviews_after=serialize(new_reviews,original[vp]);compat=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='pal2nal',scope='Adds only two reviewed PAL2NAL objects. Every prior759 review and unrelated scientific implementation is unchanged; exact inverse reconstructs the entire historical review file.',paths=[inverse(original[vp],reviews_after)])
    history=prior.get('history',[])+[{k:v for k,v in prior.items() if k!='history'}]
    enrichment=dict(previous_catalog_sha256=digest(original[pp]),registry_sha256=digest(registry_after),source_cohort_unchanged=True,paper_count=250,history=history,changes=changes,extension_method='Full250-source/full418-registry extraction verified all prior2428 primary pointers. Append-only PAL2NAL extension preserves every unrelated published/cache object and every prior registry object.',unrelated_registry_objects_preserved=417,unrelated_observations_preserved=2428)
    for path,raw in original.items():(args.out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(raw,mtime=0))
    for path,(before,after) in cache_updates.items():(args.out/('before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(before,mtime=0))
    report=dict(status='passed-two-PAL2NAL-primary-source-role-reviews-and-full-cohort-extension',applied=False,source_papers_scanned=250,registry_rows=418,unrelated_registry_objects_preserved=417,unrelated_observations_preserved=2428,prior_reviews_preserved=759,total_observations=2430,total_reviews=761,supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in new_reviews['reviews']),reviewed_roles=2,source_proof=proofs,curator_sha256=digest(Path(__file__).read_bytes()),draft_sha256=digest((HERE/'pal2nal-use-reviews.json').read_bytes()),whole_paper_reproduced=False)
    if args.apply:
        assert all(path.read_bytes()==raw for path,raw in original.items()) and all(path.read_bytes()==pair[0] for path,pair in cache_updates.items())
        target=DATA/'pal2nal-use-shared-source-compatibility.json';assert not target.exists();target.write_text(json.dumps(compat,indent=2)+'\n',encoding='utf-8',newline='\n')
        for path,raw in [(rp,registry_after),(pp,papers_after),(vp,reviews_after),(ep,serialize(enrichment,original[ep])),*[(path,pair[1]) for path,pair in cache_updates.items()]]:path.write_bytes(raw)
        report.update(applied=True,registry_sha256=digest(registry_after),papers_sha256=digest(papers_after),reviews_sha256=digest(reviews_after))
    (args.out/'verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='source_proof'}))


if __name__=='__main__':main()
