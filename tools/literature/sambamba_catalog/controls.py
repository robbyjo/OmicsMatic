#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Metadata-only adversarial controls using the actual science tree, never a source overlay."""
from pathlib import Path
import argparse,copy,json,sys
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from sambamba_catalog.model import *
from sambamba_catalog.catalog import compatibility,streaming_catalog_readback
from sambamba_catalog.source import census
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    before={name:(ROOT/name).read_bytes() for name in META};after=transition(ROOT,before);result=check_transition(ROOT,before,after);observations=[]
    primary=census(ROOT);assert len(primary['hits'])==3 and sum(row['raw_paragraph_occurrences'] for row in primary['hits'])==4
    assert result['new_scoped_integrations']==123 and result['added_reviews']==2 and result['added_observations']==1
    inv=json.loads(after[INVENTORY]);row=next(row for row in inv if row['id']=='sambamba')
    assert row['supported_original_editions']==['0.6.6','0.8.2'] and row['unsupported_reported_editions']==['0.6.5'] and row['paper_role_qualification']['P082']['reported_edition_supported'] is False
    assert all(isinstance(value,str) and '[object Object]' not in value for value in row['paper_role_coverage'].values())
    assert 'Unsupported declared edition' in row['paper_role_coverage']['P082']
    assert row['source_build_required'] and row['released_binary_contains_adapter'] is False and row['native_analytical_credit_added']==0
    compatibility(before,after);observations.append(dict(control='complete-scoped-populations-roles-and-exact-review-inverse',accepted=True))
    streaming=streaming_catalog_readback(ROOT,before)
    assert streaming['current_project_sources_match_snapshot'] and streaming['original_standalone_reader_passes_changed_catalog'] is False
    observations.append(dict(control='actual-streaming-sources-and-public-archives-with-exact-predecessor-metadata',accepted=True))
    wrong=dict(before);wrong[README]+=b'controlled incorrect metadata\n'
    try:streaming_catalog_readback(ROOT,wrong)
    except ValueError as error:observations.append(dict(control='streaming-consumer-refuses-altered-historical-metadata',expected_refusal=True,cause=str(error)))
    else:raise AssertionError('Incorrect historical streaming metadata accepted')
    def reject(name,changed):
        try:check_transition(ROOT,before,changed)
        except ValueError as error:observations.append(dict(control=name,expected_refusal=True,cause=str(error)))
        else:raise AssertionError('Incorrect controlled catalog was accepted: '+name)
    changed=dict(after);rows=json.loads(changed[INVENTORY]);other=next(row for row in rows if row['id']!='sambamba');other['name']='unrelated controlled change';changed[INVENTORY]=encoded(rows);reject('unrelated-tool-change',changed)
    changed=dict(after);rows=json.loads(changed[PAPERS]);next(p for p in rows if p['paper_id']=='P198')['title']='controlled wrong title';changed[PAPERS]=encoded(rows);reject('unrelated-paper-identity-change',changed)
    changed=dict(after);rows=json.loads(changed[REVIEWS]);next(r for r in rows['reviews'] if r['tool_id']!='sambamba')['use_summary']='controlled unrelated review';changed[REVIEWS]=encoded(rows);reject('unrelated-reviewed-use-change',changed)
    changed=dict(after);rows=json.loads(changed[INVENTORY]);next(r for r in rows if r['id']=='sambamba')['paper_role_qualification']['P082']['reported_edition_supported']=True;changed[INVENTORY]=encoded(rows);reject('unsupported065-cannot-be-promoted',changed)
    changed=dict(after);rows=json.loads(changed[SUMMARY]);rows['implementation_status_counts']['upstream-adapter-validated']+=1;changed[SUMMARY]=encoded(rows);reject('extra-integration-credit',changed)
    changed=dict(after);rows=json.loads(changed[PAPERS]);p198=next(p for p in rows if p['paper_id']=='P198');next(r for r in p198['tools'] if r['tool_id']=='sambamba')['use_summary']='Removed duplicates before variant calling';changed[PAPERS]=encoded(rows);reject('marking-cannot-be-relabeled-removal',changed)
    changed=dict(after);changed.pop(REVIEWS);reject('missing-complete-review-state',changed)
    if any((ROOT/name).read_bytes()!=raw for name,raw in before.items()):raise ValueError('Actual predecessor metadata changed during controls')
    (out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_METADATA_ONLY_SAMBAMBA_TRANSITION_CONTROLS',controls=observations,census=primary,source_files_overlaid=False,actual_catalog_files_modified=False,incorrect_controls_are_not_admitted_catalogs=True)))
    print(json.dumps(dict(status='PASS_METADATA_ONLY_SAMBAMBA_TRANSITION_CONTROLS',controls=len(observations),prospective_counts=result)),flush=True)
if __name__=='__main__':main()
