# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review/apply three omitted tools using distinct JATS and DOCX source pointers."""
from pathlib import Path
import argparse,copy,gzip,json
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin
from curate_shapeit_uses import serialize,inverse
from catalog_source_integrity import reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort
from malt_primary_sources import primary_paragraphs,supplement,observations,pointer
from malt_source_registry import IDS,ENTRIES
from collect_htseq_omics import tool_matches
import scdrs_catalog as prior


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';pp=data/'papers.json';rp=ROOT/'tools/literature/reviewed-uses.json';before=dict(papers=pp.read_bytes(),reviews=rp.read_bytes());papers=json.loads(before['papers']);reviewdoc=json.loads(before['reviews']);verify_fixed_cohort(ROOT,data,papers)
    assert len(papers)==250 and sum(len(r['tools']) for r in papers)==2476 and len(reviewdoc['reviews'])==1049
    registry=prior.load_catalog_registry(ROOT);assert len(registry)==440 and not IDS.intersection(r['id'] for r in registry)
    assert not any(t['tool_id'] in IDS for p in papers for t in p['tools'])
    sources={p['paper_id']:primary_paragraphs(p) for p in papers};census=[]
    for paper in papers:
        rows=tool_matches(sources[paper['paper_id']],ENTRIES)
        census.extend((paper['paper_id'],r['tool_id']) for r in rows)
    assert sorted(census)==[('P002','hops'),('P002','malt')]
    sp=supplement();target=next(p for p in papers if p['paper_id']=='P002');added,reviews,candidates=observations(target,sources['P002'],sp)
    updated=copy.deepcopy(papers);next(p for p in updated if p['paper_id']=='P002')['tools']=sorted(target['tools']+added,key=lambda r:r['tool_id'])
    merged=copy.deepcopy(reviewdoc);merged['reviews']+=reviews;assert len(merged['reviews'])==len({(r['pmcid'],r['tool_id']) for r in merged['reviews']})==1052
    assert sum(len(p['tools']) for p in updated)==2479 and merged['reviews'][:1049]==reviewdoc['reviews']
    for old,new in zip(papers,updated,strict=True):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [t for t in new['tools'] if t['tool_id'] not in IDS]==old['tools']
    cache=ROOT/'build/literature-review/extracted/PMC11964931.json';cachebefore=cache.read_bytes();cacheobj=json.loads(cachebefore);assert not IDS.intersection(t['tool_id'] for t in cacheobj['tools'])
    cacheobj['tools']=sorted(cacheobj['tools']+candidates,key=lambda r:r['tool_id']);cacheafter=serialize(cacheobj,cachebefore)
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(merged,before['reviews']))
    code=['tools/literature/'+n for n in ['malt_source_registry.py','malt_primary_sources.py','malt_source_catalog.py','curate_malt_source_roles.py']]
    proof=dict(status='PASS_THREE_MALT_FAMILY_ADDITIONS_WITH_DISTINCT_PRIMARY_SOURCE_KINDS',applied=a.apply,papers=250,prior_observations=2476,observations=2479,prior_reviews=1049,reviews=1052,main_jats_observation_additions=2,supplementary_docx_observation_additions=1,main_article_supporting_pointers=377,supplementary_supporting_pointers=4,supplementary_primary_pointers=1,prior_reviews_preserved=True,source_pins={n:pin(ROOT/n) for n in code},reviews_before=byte_pin(before['reviews']),papers_before=byte_pin(before['papers']),current_reviews=byte_pin(after['reviews']),current_papers=byte_pin(after['papers']),new_whole_tool_credit=0,whole_goal_complete=False)
    save(out/'DRAFT.json',dict(**proof,updates=[dict(review=r,observation=next(t for t in added if t['tool_id']==r['tool_id'])) for r in reviews],main_census=census,supplementary_primary=pointer(sp[246]),false_positive_exclusions=[dict(paper='P110',term='malt extract',reason='growth-medium ingredient'),dict(paper='P214',term='Hops/Höps',reason='author surname')]))
    if not a.apply:print('Reviewed draft: three new tools; two JATS pointers and one explicit supplementary DOCX pointer. No catalog mutation.',flush=True);return
    assert not (data/'malt-source-role-coverage.json').exists()
    for key in ['papers','reviews']:
        path=data/('malt-prior-'+key+'.json.gz');assert not path.exists();path.write_bytes(gzip.compress(before[key],mtime=0));proof['prior_'+key+'_archive']=pin(path)
    applied=data/'malt-applied-source-reviews.json';save(applied,dict(status='THREE_SOURCE_REVIEWED_ADDITIONS_NO_PREVIOUS_OBSERVATION_CHANGED',updates=[dict(paper_id='P002',review=r,observation=next(t for t in added if t['tool_id']==r['tool_id'])) for r in reviews]));proof['applied_reviews']=pin(applied)
    reg=data/'malt-registry-source-review.json';save(reg,dict(status='THREE_EXPLICIT_NAMES_ADDED_TO_DERIVED_REGISTRY_ONLY',base_registry_file=pin(ROOT/'tools/literature/tool-registry.json'),base_derived_registry_count=440,current_derived_registry_count=443,entries=ENTRIES,physical_base_registry_modified=False,named_software_census=census,exclusions='Exact uppercase software tokens distinguish the two checked non-software occurrences; case-insensitive literal census is retained separately in malt-hops-paper-roles.'));proof['registry_review']=pin(reg)
    ledger={}
    for name in ['tool-inventory.json','summary.json','README.md','WARNINGS.md']:
        raw=(data/name).read_bytes();archive=data/('malt-prior-'+name.replace('.','-')+'.gz');assert not archive.exists();archive.write_bytes(gzip.compress(raw,mtime=0));ledger[name]=dict(archive=archive.name,archive_pin=pin(archive),decoded_pin=byte_pin(raw))
    save(data/'malt-prior-metadata-view.json',dict(files=ledger,scope='Only the exact prior paper/review/inventory/summary prose inputs are virtual. Scientific paths remain live and unchanged.'))
    delta=inverse(before['reviews'],after['reviews']);assert reverse_delta(after['reviews'],delta)==before['reviews'];save(data/'malt-reviews-shared-source-compatibility.json',dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',scope='Append exactly three source-reviewed tools. Preserve all1049 prior reviews and every scientific source byte.',paths=[delta]))
    assert pp.read_bytes()==before['papers'] and rp.read_bytes()==before['reviews'] and cache.read_bytes()==cachebefore
    pp.write_bytes(after['papers']);rp.write_bytes(after['reviews']);cache.write_bytes(cacheafter)
    save(data/'malt-source-role-coverage.json',proof);save(out/'COMPLETED.json',proof);print('Applied three source-reviewed tools;2479 observations;1052 reviews. Whole-tool credit unchanged.',flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/metadata preservation checks')
    main()
