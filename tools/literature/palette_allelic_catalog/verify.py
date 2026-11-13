#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify cumulative catalog admission, complete saved controls and literal source versions."""
from pathlib import Path
import argparse, json, sys, traceback
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from palette_allelic_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,EVIDENCE,PROOF,COMPATIBILITY,CURATION,encoded,pin,rawpin,reviews,check_transition
from palette_allelic_catalog.bridge import load_before,metadata_view,prior_admission
from palette_allelic_catalog.source import definitions,scan,validate
from palette_allelic_catalog.qualification import qualification

ACTION = ('Required action: retain original sources, complete evidence and predecessor metadata. SKIP3 skips dependent work when permitted primary XML, Java/R runtime, source, provider, installation or execution is unavailable. Supply only permitted prerequisites and retry NEW paths. FAIL2 admits no complete result; retain original objects, conditions, indexes, diagnostics and all failed entries, correct the stated cause and retry NEW paths. Review all original warnings. Native scalar palette colors require Java; original R calls require their documented private R4.5.3 provider. Never infer the P054 palette/MFOD1 mapping, cover viridisLite by wesanderson, normalize P204 reported1.1.2 to candidate0.1.1.2, or replace full original COAST/SKAT-O with native burden components. This gate checks saved original controls and source-role metadata; it executes no new original analysis or installation. The full semantic-review and implementation goal stays open.')

def load_catalog_registry(root=ROOT):
    from imagej_catalog.verify import load_catalog_registry as previous
    old=previous(metadata_view(root,load_before(root)));new=definitions(root)
    if len(old)!=468 or {r['id'] for r in old}&{r['id'] for r in new}:raise ValueError('Unexpected cumulative registry extension')
    return old+new

def verify(root=ROOT,rescan=False):
    proof=json.loads((root/PROOF).read_bytes())
    if proof['status']!='PASS_NATIVE_PALETTE_ADMISSION_AND_VERSION_SEPARATE_ALLELIC_CANDIDATE' or proof['new_scoped_integration_credit']!=1 or proof['allelicseries_credit']!=0 or proof['whole_goal_complete']:
        raise ValueError('Unsupported catalog admission or candidate credit')
    for key in ['source_pins','evidence_pins','after_files']:
        for name,wanted in proof[key].items():
            if pin(root/name)!=wanted:raise ValueError('Current source/evidence differs: '+name)
    before=load_before(root)
    if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive']:raise ValueError('Exact predecessor archive differs')
    if {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact predecessor metadata differs')
    result=check_transition(root,before,{n:(root/n).read_bytes() for n in META})
    census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes())
    validate(census,json.loads(before[PAPERS]),reviews(root))
    if (root/EVIDENCE/'curation-intake-before.json').read_bytes()!=(root/CURATION).read_bytes():raise ValueError('Individual reading intake changed')
    previous=prior_admission(root,before);components=qualification(root)
    from catalog_source_integrity import reverse_delta,verify_source
    compatibility=json.loads((root/COMPATIBILITY).read_bytes())
    if len(compatibility['paths'])!=1 or compatibility['paths'][0]['path']!=REVIEWS or reverse_delta((root/REVIEWS).read_bytes(),compatibility['paths'][0])!=before[REVIEWS]:
        raise ValueError('Exact two-review append inverse differs')
    verify_source(root,root/DATA,REVIEWS,rawpin(before[REVIEWS])['sha256'])
    if rescan:
        fresh=scan(root,json.loads(before[PAPERS]));validate(fresh,json.loads(before[PAPERS]),reviews(root),root)
        if fresh!=census:raise ValueError('Fresh250-source census differs')
    return dict(status=proof['status'],**result,components=components,registry_definitions=len(load_catalog_registry(root)),
        primary_sources_rescanned=rescan,previous_admission=previous,scientific_sources_overridden=False,
        scientific_verifiers_changed=False,original_analysis_executed=False,paper_wide_semantic_review_open=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--rescan-sources',action='store_true');args=parser.parse_args();out=args.out.resolve();owned=False
    try:
        if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
        out.mkdir(parents=True,exist_ok=False);owned=True
        result=verify(rescan=args.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_PALETTE_ALLELIC_CATALOG',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
        if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
        print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
