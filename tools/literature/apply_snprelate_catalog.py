# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply nine source-bound package roles, preserving all250 records and1029 previous reviews."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from catalog_source_integrity import reverse_delta,verify_source
from verify_snprelate_tools_evidence import verify
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    verify()
    data=ROOT/'docs/literature/omics-2023-2026';candidate=BASE/'catalog-candidate-3';metadata=json.loads((candidate/'COMPLETED.json').read_bytes())
    assert metadata['status']=='PASS250_PRIMARY_SNPRELATE_GENESIS_CATALOG_CANDIDATE_ONLY'
    assert all(pin(candidate/n)==v for n,v in metadata['candidate_files'].items())
    assert pin(data/'snprelate-genesis-primary-roles-v2.json')==metadata['primary_role_supplement']
    assert pin(ROOT/'tools/literature/snprelate-named-software-registry.json')==metadata['registry_extension']
    paths=dict(papers=data/'papers.json',reviews=ROOT/'tools/literature/reviewed-uses.json');before={k:p.read_bytes() for k,p in paths.items()}
    for k,raw in before.items():assert gzip.decompress((candidate/('prior-'+k+'.json.gz')).read_bytes())==raw
    old=json.loads(before['reviews']);reviews=json.loads((candidate/'candidate-reviews.json').read_bytes());papers=json.loads((candidate/'candidate-papers.json').read_bytes())
    old_scope='Typed original SNPRelate1.46.0 execution is under validation and earns no new implementation credit here. '
    new_scope='Scoped typed original SNPRelate1.46.0 KING-robust, LD pruning, GRM and exact/randomized PCA execution is validated; use SNPRELATE_INTEGRATION.md and require an admitted runtime/result. '
    changed=0
    for row in reviews['reviews'][1029:]:
        if row['tool_id']=='snprelate':assert row['implementation_scope_note'].startswith(old_scope);row['implementation_scope_note']=row['implementation_scope_note'].replace(old_scope,new_scope,1);changed+=1
    assert changed==4 and reviews['reviews'][:1029]==old['reviews']
    by={(r['pmcid'],r['tool_id']):r for r in reviews['reviews'][1029:]}
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
        for row in paper['tools']:
            if row['tool_id'] in ['snprelate','genesis']:row['implementation_scope_note']=by[(paper['pmcid'],row['tool_id'])]['implementation_scope_note']
    for oldpaper,newpaper in zip(json.loads(before['papers']),papers,strict=True):
        assert {k:v for k,v in oldpaper.items() if k!='tools'}=={k:v for k,v in newpaper.items() if k!='tools'}
        assert oldpaper['tools']==[r for r in newpaper['tools'] if r['tool_id'] not in ['snprelate','genesis']]
    assert len(papers)==250 and len(reviews['reviews'])==1038 and sum(len(p['tools']) for p in papers)==2475
    after=dict(papers=serialize(papers,before['papers']),reviews=serialize(reviews,before['reviews']))
    destinations=[data/n for n in ['snprelate-prior-reviews.json.gz','snprelate-prior-papers.json.gz','snprelate-role-coverage.json','snprelate-applied-role-reviews.json','snprelate-reviews-shared-source-compatibility.json']]
    assert not any(p.exists() for p in destinations)
    for n in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md']:(out/n).write_bytes((data/n).read_bytes())
    row=inverse(before['reviews'],after['reviews']);row['path']='tools/literature/reviewed-uses.json';assert reverse_delta(after['reviews'],row)==before['reviews']
    destinations[0].write_bytes(gzip.compress(before['reviews'],mtime=0));destinations[1].write_bytes(gzip.compress(before['papers'],mtime=0))
    save(destinations[3],dict(status='nine-primary-bound-SNPRelate-GENESIS-package-reviews',reviews=reviews['reviews'][1029:]))
    save(destinations[4],dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='snprelate',
        scope='Nine appended primary-bound package reviews, including the citation-normalized P170 correction. All1029 prior reviews and every scientific source remain unchanged.',paths=[row]))
    for k,path in paths.items():assert path.read_bytes()==before[k];path.write_bytes(after[k])
    proof=dict(status='PASS_NINE_CITATION_NORMALIZED_PRIMARY_PACKAGE_ROLES',applied=True,prior_reviews=1029,new_reviews=9,total_reviews=1038,observations=2475,supporting_pointers=369,
        prior_review_archive=pin(destinations[0]),prior_papers_archive=pin(destinations[1]),applied_draft_pin=pin(destinations[3]),curator_pin=pin(Path(__file__)),
        current_reviews=pin(paths['reviews']),current_papers=pin(paths['papers']),prior_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),
        source_anchors=metadata['source_anchors'],citation_normalized_cohort_census=250,unchanged_prior_reviews=1029,unchanged_prior_observations=2466,
        snprelate_papers=['P130','P170','P191','P216'],genesis_papers=['P128','P130','P142','P191','P219'],whole_goal_complete=False)
    save(destinations[2],proof);verify_source(ROOT,data,row['path'],row['before_sha256']);save(out/'COMPLETED.json',proof)
    print('PASS_NINE_CITATION_NORMALIZED_PRIMARY_PACKAGE_ROLES;250 papers;1038 reviews;2475 observations')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete cohort/source-role preservation')
    main()
