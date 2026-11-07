# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply eight exact HTSeq role reviews; preserve890 prior reviews and every unrelated observation."""
import argparse
import copy
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs,publication_record,validate_supporting_evidence
from collect_htseq_omics import tool_matches
from curate_shapeit_uses import inverse,serialize
from catalog_fixed_cohort import verify_fixed_cohort

ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/htseq-runtime'
HERE=ROOT/'tools/literature';DATA=ROOT/'docs/literature/omics-2023-2026'


def sha(raw):return hashlib.sha256(raw).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--apply',action='store_true')
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    paths=dict(papers=DATA/'papers.json',reviews=HERE/'reviewed-uses.json',enrichment=DATA/'catalog-enrichment.json')
    before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);previous=json.loads(before['reviews'])
    drafts=json.loads((HERE/'htseq-use-reviews.json').read_bytes());registry=json.loads((HERE/'tool-registry.json').read_bytes())
    assert (len(papers),len(previous['reviews']),len(drafts),len(registry))==(250,890,8,425)
    assert not any(r['tool_id']=='htseq' for r in previous['reviews']);verify_fixed_cohort(ROOT,DATA,papers)
    reviews=copy.deepcopy(previous);reviews['reviews'].extend(d['review'] for d in drafts)
    assert reviews['reviews'][:890]==previous['reviews'] and len(reviews['reviews'])==898
    preferences={(r['pmcid'],r['tool_id']):r['evidence_paragraph_sha256'] for r in reviews['reviews']}
    by={d['review']['pmcid']:d for d in drafts};updated=copy.deepcopy(papers);cache_updates={};proof=[];added=[]
    for original,paper in zip(papers,updated):
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert sha(raw)==paper['xml_sha256']
        if paper['pmcid'] not in by:assert paper==original;continue
        draft=by[paper['pmcid']];review=draft['review'];ps=paragraphs(ET.fromstring(raw))
        prefs={tool:digest for (pmc,tool),digest in preferences.items() if pmc==paper['pmcid']}
        found={t['tool_id']:t for t in tool_matches(ps,registry,prefs)};old={t['tool_id']:t for t in original['tools']}
        assert set(found)==set(old)|{'htseq'},paper['paper_id']
        for name,tool in old.items():assert (tool['evidence_paragraph_index'],tool['evidence_paragraph_sha256'])==(found[name]['evidence_paragraph_index'],sha(found[name]['evidence_text'].encode('utf8'))),(paper['paper_id'],name)
        candidate=found['htseq'];assert (draft['paper_id'],review['doi'],review['evidence_paragraph_sha256'])==(paper['paper_id'],paper['doi'],sha(candidate['evidence_text'].encode('utf8')))
        validate_supporting_evidence(ps,[review],{r['id']:r for r in registry})
        published=publication_record(dict(paper,tools=[candidate]),[review])['tools'][0]
        paper['tools']=sorted([t for t in paper['tools'] if t['tool_id']!='htseq']+[published],key=lambda t:t['tool_id'])
        assert [t for t in paper['tools'] if t['tool_id']!='htseq']==[t for t in original['tools'] if t['tool_id']!='htseq']
        assert {k:v for k,v in paper.items() if k!='tools'}=={k:v for k,v in original.items() if k!='tools'}
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');old_cache=cache.read_bytes();cached=json.loads(old_cache)
        isnew='htseq' not in old;assert sum(t['tool_id']=='htseq' for t in cached['tools'])==(0 if isnew else 1)
        cached['tools']=sorted([t for t in cached['tools'] if t['tool_id']!='htseq']+[candidate],key=lambda t:t['tool_id']);cache_updates[cache]=(old_cache,serialize(cached,old_cache))
        if isnew:added.append(paper['paper_id'])
        proof.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],
            paragraph_index=candidate['evidence_paragraph_index'],paragraph_sha256=review['evidence_paragraph_sha256'],
            reported_version=review['version_reported_near_name'],supporting_pointers=len(review['supporting_evidence']),recovered_named_alias=isnew))
    assert len(proof)==len(cache_updates)==8 and added==['P005']
    assert sum(len(p['tools']) for p in updated)==2446 and len({t['tool_id'] for p in updated for t in p['tools']})==387
    verify_fixed_cohort(ROOT,DATA,updated)
    prior=json.loads(before['enrichment']);enrichment=dict(previous_catalog_sha256=sha(before['papers']),registry_sha256=sha((HERE/'tool-registry.json').read_bytes()),
        source_cohort_unchanged=True,paper_count=250,history=prior.get('history',[])+[{k:v for k,v in prior.items() if k!='history'}],
        changes=[dict(paper_id=r['paper_id'],reviewed_ids=['htseq'],newly_recovered_named_alias=r['recovered_named_alias']) for r in proof],
        extension_method='All250 retained source XML hashes checked. Preserve425-row registry and seven original HTSeq anchors. Recover only P005 exact HT-seq0.6.0 Methods spelling, append8 reviewed roles, preserve890 prior reviews and all unrelated observations. Native/current versus old counting, strict MAPQ, custom binary peaks and missing settings remain explicit. No implementation credit.')
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviews,before['reviews']),enrichment=serialize(enrichment,before['enrichment']))
    bridge=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='htseq',
        scope='Append8 primary HTSeq role reviews, preserving890 prior reviews with an exact inverse. Registry/scientific sources unchanged; no tool credit.',paths=[inverse(before['reviews'],after['reviews'])])
    result=dict(schema_version=1,status='passed-eight-HTSeq-primary-role-reviews',applied=False,source_papers_XML_hashes_checked=250,
        reviewed_roles=8,registry_rows=425,prior_reviews_preserved=890,new_reviews=8,total_reviews=898,
        prior_observations=2445,total_observations=2446,supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews']),
        HTSeq_supporting_pointers=2,recovered_named_aliases=added,source_proof=proof,
        curator_sha256=sha(Path(__file__).read_bytes()),draft_sha256=sha((HERE/'htseq-use-reviews.json').read_bytes()),
        alias_guard_sha256=sha((HERE/'collect_htseq_omics.py').read_bytes()),alias_ledger_sha256=sha((HERE/'htseq-source-additions.json').read_bytes()),
        implementation_promoted=False,whole_paper_reproduced=False)
    for key,path in paths.items():(out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[key],mtime=0))
    for path,(old,_) in cache_updates.items():(out/('PRIVATE-before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(old,mtime=0))
    if args.apply:
        descriptor=DATA/'htseq-use-shared-source-compatibility.json';roles=DATA/'htseq-role-coverage.json'
        assert not descriptor.exists() and not roles.exists() and all(p.read_bytes()==before[k] for k,p in paths.items())
        assert all(p.read_bytes()==value[0] for p,value in cache_updates.items())
        descriptor.write_text(json.dumps(bridge,indent=2)+'\n',encoding='utf8',newline='\n')
        for key,path in paths.items():path.write_bytes(after[key])
        for path,(_,raw) in cache_updates.items():path.write_bytes(raw)
        result.update(applied=True,papers_sha256=sha(after['papers']),reviews_sha256=sha(after['reviews']))
        roles.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n')
    (out/'verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps({k:v for k,v in result.items() if k!='source_proof'}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary role preservation checks')
    main()
