# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review all five converter roles, append four new reviews and preserve every earlier review/cohort object."""
import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
HERE=ROOT/'tools/literature'
sys.path.insert(0,str(HERE))
from curate_shapeit_uses import inverse,serialize
from catalog_fixed_cohort import verify_fixed_cohort
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence


def sha(raw):return hashlib.sha256(raw).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--apply',action='store_true');args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to((ROOT/'build/literature-review/bigwig-native').resolve()) and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';paths=dict(papers=data/'papers.json',reviews=HERE/'reviewed-uses.json',enrichment=data/'catalog-enrichment.json');before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);old_reviews=json.loads(before['reviews']);registry=json.loads((HERE/'tool-registry.json').read_bytes());draft_path=HERE/'bigwig-use-reviews.json';drafts=json.loads(draft_path.read_bytes());assert len(papers)==250 and len(registry)==425 and len(old_reviews['reviews'])==815 and len(drafts)==5;verify_fixed_cohort(ROOT,data,papers)
    native=json.loads((ROOT/'docs/literature/bigwig-native-development/VALIDATION.json').read_bytes());assert sha(draft_path.read_bytes())==native['source_pins'][draft_path.relative_to(ROOT).as_posix()]['sha256'];assert {r['paper_id'] for r in drafts}=={'P013','P050','P110','P138','P163'};reviews=copy.deepcopy(old_reviews);old_by={(r['pmcid'],r['tool_id']):r for r in old_reviews['reviews']};added=[]
    for draft in drafts:
        key=(draft['review']['pmcid'],draft['review']['tool_id']);assert key[1]=='ucsc-bedgraphtobigwig'
        if key in old_by:assert draft['paper_id']=='P163' and old_by[key]==draft['review']
        else:reviews['reviews'].append(draft['review']);added.append(draft['paper_id'])
    assert added==['P013','P050','P110','P138'] and len(reviews['reviews'])==819 and reviews['reviews'][:815]==old_reviews['reviews'];preferred={(r['pmcid'],r['tool_id']):r['evidence_paragraph_sha256'] for r in reviews['reviews']};draft_by={r['review']['pmcid']:r for r in drafts};updated=copy.deepcopy(papers);proofs=[];cache_updates={};changes=[]
    for old,paper in zip(papers,updated):
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert sha(raw)==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));prefs={tool:h for (pmc,tool),h in preferred.items() if pmc==paper['pmcid']};found=tool_matches(ps,registry,prefs);by={t['tool_id']:t for t in found};old_tools={t['tool_id']:t for t in old['tools']};assert set(by)==set(old_tools),(paper['paper_id'],'tool identities')
        for name,tool in old_tools.items():assert (tool['evidence_paragraph_index'],tool['evidence_paragraph_sha256'])==(by[name]['evidence_paragraph_index'],sha(by[name]['evidence_text'].encode())),(paper['paper_id'],name,'anchor')
        if paper['pmcid'] not in draft_by:continue
        draft=draft_by[paper['pmcid']];review=draft['review'];candidate=by['ucsc-bedgraphtobigwig'];assert (draft['paper_id'],review['doi'])==(paper['paper_id'],paper['doi']) and sha(candidate['evidence_text'].encode())==review['evidence_paragraph_sha256'];validate_supporting_evidence(ps,[review],{r['id']:r for r in registry});published=publication_record(dict(paper,tools=[candidate]),[review])['tools'][0]
        if draft['paper_id']=='P163':assert published==old_tools['ucsc-bedgraphtobigwig']
        else:
            paper['tools']=[published if t['tool_id']=='ucsc-bedgraphtobigwig' else t for t in paper['tools']];cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');raw_cache=cache.read_bytes();cached=json.loads(raw_cache);assert sum(t['tool_id']=='ucsc-bedgraphtobigwig' for t in cached['tools'])==1;cached['tools']=[candidate if t['tool_id']=='ucsc-bedgraphtobigwig' else t for t in cached['tools']];cache_updates[cache]=(raw_cache,serialize(cached,raw_cache));changes.append(dict(paper_id=paper['paper_id'],reviewed_ids=['ucsc-bedgraphtobigwig']))
        assert [t for t in old['tools'] if t['tool_id']!='ucsc-bedgraphtobigwig']==[t for t in paper['tools'] if t['tool_id']!='ucsc-bedgraphtobigwig'];proofs.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],paragraph_index=candidate['evidence_paragraph_index'],paragraph_sha256=review['evidence_paragraph_sha256'],reported_version=published['version_reported_near_name'],version_interpretation_unresolved=paper['paper_id']=='P110'))
    assert len(proofs)==5 and len(changes)==len(cache_updates)==4 and sum(len(p['tools']) for p in updated)==2442 and len({t['tool_id'] for p in updated for t in p['tools']})==387;verify_fixed_cohort(ROOT,data,updated);prior=json.loads(before['enrichment']);enrichment=dict(previous_catalog_sha256=sha(before['papers']),registry_sha256=sha((HERE/'tool-registry.json').read_bytes()),source_cohort_unchanged=True,paper_count=250,history=prior.get('history',[])+[{k:v for k,v in prior.items() if k!='history'}],changes=changes,extension_method='Complete250-source/425-registry anchor verification preserves all2442 observations; append four converter-specific reviews while retaining P163 and every815 earlier review. Literal P110v4 remains ambiguous; upstream processing/normalization/discovery stays separate. No implementation promotion by curation.')
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviews,before['reviews']),enrichment=serialize(enrichment,before['enrichment']));bridge=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='ucsc-bedgraphtobigwig',scope='Append only four primary-source converter reviews. All815 earlier reviews and their exact bytes reconstruct. No scientific source waiver or tool implementation credit.',paths=[inverse(before['reviews'],after['reviews'])]);report=dict(schema_version=1,status='passed-five-primary-bedGraphToBigWig-role-reviews',applied=False,source_papers_scanned=250,registry_rows=425,distinct_named_tools=387,total_observations=2442,prior_reviews_preserved=815,new_reviews=4,total_reviews=819,supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews']),reviewed_roles=5,existing_role_P163_unchanged=True,changes=changes,source_proof=proofs,curator_sha256=sha(Path(__file__).read_bytes()),draft_sha256=sha(draft_path.read_bytes()),implementation_promoted=False,whole_paper_reproduced=False)
    for name,path in paths.items():(out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[name],mtime=0))
    for path,(raw,_) in cache_updates.items():(out/('before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(raw,mtime=0))
    if args.apply:
        target=data/'bigwig-use-shared-source-compatibility.json';roles=data/'bigwig-role-coverage.json';assert not target.exists() and not roles.exists() and all(path.read_bytes()==before[name] for name,path in paths.items()) and all(path.read_bytes()==pair[0] for path,pair in cache_updates.items());target.write_text(json.dumps(bridge,indent=2)+'\n',encoding='utf-8',newline='\n')
        for name,path in paths.items():path.write_bytes(after[name])
        for path,(_,raw) in cache_updates.items():path.write_bytes(raw)
        report.update(applied=True,papers_sha256=sha(after['papers']),reviews_sha256=sha(after['reviews']));roles.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps({k:v for k,v in report.items() if k not in ['source_proof','changes']}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source-role checks')
    main()
