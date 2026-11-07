# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply reviewed program identities; preserve the cohort, prior reviews and implementations."""
import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / 'tools/literature'
sys.path.insert(0, str(HERE))
from collect_omics_review import paragraphs, tool_matches, publication_record, validate_supporting_evidence
from curate_shapeit_uses import inverse, serialize


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists()
    data = ROOT / 'docs/literature/omics-2023-2026'
    paths = dict(papers=data/'papers.json', reviews=HERE/'reviewed-uses.json', registry=HERE/'tool-registry.json', enrichment=data/'catalog-enrichment.json')
    before = {key:path.read_bytes() for key,path in paths.items()}
    assert gzip.decompress((args.preview/'registry-before.json.gz').read_bytes()) == before['registry']
    old_registry = json.loads(before['registry']); registry = json.loads((args.preview/'registry-proposed.json').read_bytes())
    old_by = {t['id']:t for t in old_registry}; by = {t['id']:t for t in registry}
    added_ids = {'meme-chip','dreme','centrimo','tomtom','streme','hyphy','bwa-meme'}
    assert len(old_registry) == 418 and len(registry) == 425 and set(by)-set(old_by) == added_ids
    assert all(by[key] == value for key,value in old_by.items() if key not in {'meme','bwa'})
    for key in ['meme','bwa']:
        assert {k:v for k,v in by[key].items() if k!='pattern'} == {k:v for k,v in old_by[key].items() if k!='pattern'}
    assert all(by[key]['current_coverage']=='missing' and by[key]['existing_source'] is None for key in added_ids)
    affected = added_ids | {'meme','bwa'}
    papers = json.loads(before['papers']); original_reviews = json.loads(before['reviews'])
    drafts = json.loads((HERE/'meme-program-use-reviews.json').read_bytes())
    assert len(papers)==250 and len(original_reviews['reviews'])==793 and len(drafts)==22
    reviews = copy.deepcopy(original_reviews)
    existing_keys = {(r['pmcid'],r['tool_id']) for r in reviews['reviews']}
    assert not any((d['review']['pmcid'],d['review']['tool_id']) in existing_keys for d in drafts)
    reviews['reviews'] += [d['review'] for d in drafts]
    assert len(reviews['reviews'])==815 and reviews['reviews'][:793]==original_reviews['reviews']
    preferred = {(r['pmcid'],r['tool_id']):r['evidence_paragraph_sha256'] for r in reviews['reviews']}
    draft_by = {(r['paper_id'],r['review']['tool_id']):r for r in drafts}
    updated = copy.deepcopy(papers); cache_updates={}; changes=[]; used=set(); removed=[]
    for old,paper in zip(papers,updated):
        raw = gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert sha(raw)==paper['xml_sha256']
        ps = paragraphs(ET.fromstring(raw))
        prefs = {tool:value for (pmc,tool),value in preferred.items() if pmc==paper['pmcid']}
        candidates = tool_matches(ps,[by[key] for key in affected],prefs)
        candidate_by = {t['tool_id']:t for t in candidates}
        old_affected = {t['tool_id']:t for t in old['tools'] if t['tool_id'] in affected}
        gone = set(old_affected)-set(candidate_by)
        for key in gone:
            assert (paper['paper_id'],key) in {('P066','meme'),('P113','bwa')}
            assert (paper['pmcid'],key) not in existing_keys
            removed.append((paper['paper_id'],key))
        new_tools = [t for t in paper['tools'] if t['tool_id'] not in gone]
        for key,candidate in candidate_by.items():
            draft = draft_by.get((paper['paper_id'],key))
            if draft is None:
                assert key=='bwa' and key in old_affected
                assert candidate['evidence_paragraph_index']==old_affected[key]['evidence_paragraph_index'] and sha(candidate['evidence_text'].encode())==old_affected[key]['evidence_paragraph_sha256']
                continue
            review = draft['review']; used.add((paper['paper_id'],key))
            assert (review['pmcid'],review['doi'])==(paper['pmcid'],paper['doi'])
            assert candidate['evidence_paragraph_index']==draft['primary_paragraph'] and sha(candidate['evidence_text'].encode())==review['evidence_paragraph_sha256']
            validate_supporting_evidence(ps,[review],by)
            published = publication_record(dict(paper,tools=[candidate]),[review])['tools'][0]
            new_tools = [t for t in new_tools if t['tool_id']!=key]+[published]
        paper['tools'] = sorted(new_tools,key=lambda t:t['tool_id'])
        assert {k:v for k,v in paper.items() if k!='tools'}=={k:v for k,v in old.items() if k!='tools'}
        assert [t for t in paper['tools'] if t['tool_id'] not in affected]==[t for t in old['tools'] if t['tool_id'] not in affected]
        if paper != old:
            cache = ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')
            cache_before = cache.read_bytes(); cached=json.loads(cache_before)
            cached['tools'] = sorted([t for t in cached['tools'] if t['tool_id'] not in affected]+candidates,key=lambda t:t['tool_id'])
            cache_updates[cache]=(cache_before,serialize(cached,cache_before))
            changes.append(dict(paper_id=paper['paper_id'],added_ids=sorted(set(candidate_by)-set(old_affected)),removed_ids=sorted(gone),reviewed_ids=sorted(key for pid,key in used if pid==paper['paper_id'])))
    assert used==set(draft_by) and sorted(removed)==[('P066','meme'),('P113','bwa')]
    assert len(changes)==15 and len(cache_updates)==15
    assert sum(len(p['tools']) for p in papers)==2430 and sum(len(p['tools']) for p in updated)==2442
    assert len({t['tool_id'] for p in updated for t in p['tools']})==387
    prior=json.loads(before['enrichment'])
    enrichment=dict(previous_catalog_sha256=sha(before['papers']),registry_sha256=sha((args.preview/'registry-proposed.json').read_bytes()),source_cohort_unchanged=True,paper_count=250,
                    history=prior.get('history',[])+[{k:v for k,v in prior.items() if k!='history'}],changes=changes,
                    extension_method='Whole250-primary-source review distinguishes seven new named programs, removes two homonym/implementation misassignments, and reviews22 roles. All793 earlier reviews and all unaffected paper observations remain unchanged. No new implementation is credited.')
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviews,before['reviews']),registry=(args.preview/'registry-proposed.json').read_bytes(),enrichment=serialize(enrichment,before['enrichment']))
    bridge=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='meme-program-identities',scope='Append22 primary-source reviewed program/resource roles. Preserve all793 prior reviews and reconstruct their exact bytes. No scientific source exception, implementation credit or current-host admission.',paths=[inverse(before['reviews'],after['reviews'])])
    report=dict(schema_version=1,status='passed-distinct-MEME-suite-program-and-evolutionary-roles',applied=False,source_papers_scanned=250,registry_rows=425,distinct_named_tools=387,total_observations=2442,
                prior_reviews_preserved=793,new_reviews=22,total_reviews=815,supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews']),
                added_program_ids=sorted(added_ids),removed_misassignments=[dict(paper_id=pid,tool_id=tool) for pid,tool in removed],changes=changes,
                source_proof=[dict(paper_id=d['paper_id'],tool_id=d['review']['tool_id'],primary_paragraph=d['primary_paragraph'],paragraph_sha256=d['review']['evidence_paragraph_sha256']) for d in drafts],
                curator_sha256=sha(Path(__file__).read_bytes()),draft_sha256=sha((HERE/'meme-program-use-reviews.json').read_bytes()),project_implementation_count_unchanged=True,implementation_promoted=False,whole_paper_reproduced=False)
    out.mkdir(parents=True)
    for name,raw in before.items():
        (out/('before-'+paths[name].name+'.gz')).write_bytes(gzip.compress(raw,mtime=0))
    for path,(raw,_) in cache_updates.items():
        (out/('before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(raw,mtime=0))
    if args.apply:
        target=data/'meme-program-use-shared-source-compatibility.json'; role_path=data/'meme-program-role-coverage.json'
        assert not target.exists() and not role_path.exists()
        assert all(path.read_bytes()==before[name] for name,path in paths.items()) and all(path.read_bytes()==pair[0] for path,pair in cache_updates.items())
        target.write_text(json.dumps(bridge,indent=2)+'\n',encoding='utf-8',newline='\n')
        for name,path in paths.items():
            path.write_bytes(after[name])
        for path,(_,raw) in cache_updates.items():
            path.write_bytes(raw)
        report.update(applied=True,papers_sha256=sha(after['papers']),reviews_sha256=sha(after['reviews']),registry_sha256=sha(after['registry']))
        role_path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps({k:v for k,v in report.items() if k not in {'source_proof','changes'}}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable primary-source curation checks')
    main()
