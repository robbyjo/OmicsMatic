#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify zero-credit current rstatix routing, unchanged source reviews and complete original evidence."""
from pathlib import Path
import argparse,json,sys,traceback
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from rstatix_revision2_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,EVIDENCE,PROOF,COVERAGE,CURATION,INTAKE,WORKFLOW,encoded,pin,rawpin,reviews,scan,validate,check_transition
from rstatix_revision2_catalog.bridge import load_before,metadata_view
from rstatix_revision2_catalog.qualification import qualification
ACTION='Required action: use rstatix072_workflow_v2/run.py and the exact permitted private Linux/WSL R4.5.3/rstatix0.7.2 provider for new analyses. Preserve all prior source roles, every scientific object/condition, raw malformed report, controller traceback, partial output, link/empty-directory identity and all failed attempts. Unknown totals are unavailable, never zero. SKIP3 skips dependent analysis/verification for missing or forbidden prerequisites; install/download only where permitted, resolve the named cause and retry NEW paths. FAIL2 admits no complete result; retain complete console logs and every writable warning/status/inventory record, correct the cause and retry NEW paths. This is zero-credit routing:109 scoped integrations and264 missing families remain; full paper review and implementation stay open. Read RSTATIX_REVISION2_CATALOG.md. This catalog gate installs nothing and executes no new original R analysis.'

def load_catalog_registry(root=ROOT):
 from ggpubr_catalog.verify import load_catalog_registry as previous
 old=previous(metadata_view(root,load_before(root)))
 if len(old)!=470 or len({r['id'] for r in old})!=470:raise ValueError('Unexpected cumulative registry population')
 current=next(r for r in json.loads((root/META[0]).read_bytes()) if r['id']=='rstatix')
 for row in old:
  if row['id']=='rstatix':
   for key in ['current_coverage','existing_source','coverage_note']:row[key]=current[key]
 return old

def verify(root=ROOT,rescan=False):
 proof=json.loads((root/PROOF).read_bytes())
 if proof['status']!='PASS_ZERO_CREDIT_RSTATIX_REVISION2_CURRENT_CATALOG_ROUTING' or proof['new_scoped_integration_credit']!=0 or proof['added_reviews']!=0 or proof['whole_goal_complete']:raise ValueError('Unsupported routing scope or extra credit')
 for key in ['source_pins','evidence_pins','after_files']:
  for n,wanted in proof[key].items():
   if pin(root/n)!=wanted:raise ValueError('Qualified catalog source/evidence differs: '+n)
 before=load_before(root)
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive'] or {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact predecessor differs')
 result=check_transition(root,before,{n:(root/n).read_bytes() for n in META})
 census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes());source=validate(census,json.loads(before[PAPERS]),reviews(root),root)
 if (root/EVIDENCE/'unchanged-source-reviews.json').read_bytes()!=(root/CURATION).read_bytes() or (root/EVIDENCE/'unchanged-intake.json').read_bytes()!=(root/INTAKE).read_bytes():raise ValueError('Earlier individual source readings changed')
 coverage=json.loads((root/COVERAGE).read_bytes());row=next(r for r in json.loads((root/META[0]).read_bytes()) if r['id']=='rstatix')
 if coverage['source_reviews']!=reviews(root) or coverage['current_paper_role_coverage']!=row['paper_role_coverage'] or coverage['supervisor_revision']!=2 or coverage['new_scoped_integration_credit']!=0:raise ValueError('Current source-role routing differs')
 if rescan and scan(root,json.loads(before[PAPERS]))!=census:raise ValueError('Fresh250-source census differs')
 component=qualification(root)
 return dict(status=proof['status'],**result,source_roles=source,components=component,registry_definitions=len(load_catalog_registry(root)),primary_sources_rescanned=rescan,all_previous_reviews_and_observations_unchanged=True,scientific_sources_overridden=False,scientific_verifiers_changed=False,original_analysis_executed=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=a.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_ROUTING_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RSTATIX_REVISION2_ROUTING',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
