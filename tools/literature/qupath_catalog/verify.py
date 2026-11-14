#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify cumulative QuPath admission, complete saved controls and exact source roles."""
from pathlib import Path
import argparse,json,sys,traceback
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from qupath_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,EVIDENCE,PROOF,COMPATIBILITY,CURATION,INTAKE,encoded,pin,rawpin,reviews,check_transition
from qupath_catalog.bridge import load_before,metadata_view,prior_admission
from qupath_catalog.source import scan,validate
from qupath_catalog.qualification import qualification

ACTION = ('Required action: preserve exact primary sources, complete original controls, old source reviews and predecessor metadata. SKIP3 skips dependent work when required permitted downloads, installation, source, runtime, provider, extension/model or execution are unavailable. Supply only permitted prerequisites and retry NEW paths. QuPath original execution needs the complete private native-platform 0.5.1 provider and trusted caller script; a READY setup or controller JAR is not an analysis result. FAIL2 admits no complete result. Keep full inputs, images, hierarchies, tables, errors, conditions, status and logs; resolve the stated cause and retry NEW paths. Review every original warning. Do not substitute reported editions, infer unknown author ROIs/stains/thresholds, claim a stock StarDist model or extrapolate synthetic H-DAB validation to fluorescence, RNAscope or registration. Read QUPATH_CATALOG_INTEGRATION.md and QUPATH051_WORKFLOW_DEVELOPMENT.md. This gate runs no new original analysis or installation. Full semantic review and implementation remain open.')

def load_catalog_registry(root=ROOT):
    from palette_allelic_catalog.verify import load_catalog_registry as previous
    old = previous(metadata_view(root,load_before(root)))
    if len(old) != 470 or len({r['id'] for r in old}) != 470: raise ValueError('Unexpected cumulative registry population')
    inventory = json.loads((root/META[0]).read_bytes());current = next(r for r in inventory if r['id']=='qupath')
    found = 0
    for row in old:
        if row['id'] == 'qupath':
            for key in ['current_coverage','existing_source','coverage_note']: row[key] = current[key]
            found += 1
    if found != 1: raise ValueError('Missing/duplicate previous QuPath definition')
    return old

def verify(root=ROOT,rescan=False):
    proof = json.loads((root/PROOF).read_bytes())
    if proof['status'] != 'PASS_ORIGINAL_QUPATH051_CATALOG_ADMISSION_AND_TWELVE_SOURCE_ROLES' or proof['new_scoped_integration_credit'] != 1 or proof['whole_goal_complete']:
        raise ValueError('Unsupported QuPath admission or scope')
    for key in ['source_pins','evidence_pins','after_files']:
        for name,wanted in proof[key].items():
            if pin(root/name) != wanted: raise ValueError('Current source/evidence differs: ' + name)
    before = load_before(root)
    if pin(root/EVIDENCE/'catalog-before.zip') != proof['before_archive'] or {n:rawpin(v) for n,v in before.items()} != proof['before_files']:
        raise ValueError('Exact predecessor metadata/archive differs')
    result = check_transition(root,before,{n:(root/n).read_bytes() for n in META})
    census = json.loads((root/EVIDENCE/'CENSUS.json').read_bytes())
    source = validate(census,json.loads(before[PAPERS]),reviews(root))
    if (root/EVIDENCE/'qualified-intake-before.json').read_bytes() != (root/INTAKE).read_bytes() or (root/EVIDENCE/'curation-intake.json').read_bytes() != (root/CURATION).read_bytes():
        raise ValueError('Individual source reading/intake changed')
    previous = prior_admission(root,before);components = qualification(root)
    from catalog_source_integrity import reverse_delta,verify_source
    compatibility = json.loads((root/COMPATIBILITY).read_bytes())
    if len(compatibility['paths']) != 1 or compatibility['paths'][0]['path'] != REVIEWS or reverse_delta((root/REVIEWS).read_bytes(),compatibility['paths'][0]) != before[REVIEWS]:
        raise ValueError('Exact eleven-review append inverse differs')
    verify_source(root,root/DATA,REVIEWS,rawpin(before[REVIEWS])['sha256'])
    if rescan:
        fresh = scan(root,json.loads(before[PAPERS]))
        if fresh != census: raise ValueError('Fresh 250-source census differs')
    return dict(status=proof['status'],**result,components=components,primary_source_scope=source,registry_definitions=len(load_catalog_registry(root)),
        primary_sources_rescanned=rescan,previous_admission=previous,scientific_sources_overridden=False,
        scientific_verifiers_changed=False,original_analysis_executed=False,paper_wide_semantic_review_open=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--rescan-sources',action='store_true');args=parser.parse_args();out=args.out.resolve();owned=False
    try:
        if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'): raise ValueError('Use NEW monocle-runtime output')
        out.mkdir(parents=True,exist_ok=False);owned=True
        result=verify(rescan=args.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_QUPATH_CATALOG',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
        if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
        print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__': raise SystemExit(main())
