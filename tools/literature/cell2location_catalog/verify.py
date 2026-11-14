#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify one original workflow and six primary-source reviews without changing scientific code."""
from pathlib import Path
import argparse,json,sys,traceback
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from cell2location_catalog.model import *
from cell2location_catalog.source import scan,validate
from cell2location_catalog.bridge import load_before,metadata_view,prior_admission
from cell2location_catalog.qualification import qualification
ACTION='Use the qualified original0.1.3 Linux/WSL CPU source workflow; not a standalone JAR. Reported0.7a0 remains a separate unqualified historical runtime, so skip its dependent analysis. Preserve exact primary roles, paper/code discrepancies, complete populations, every warning/error and all failed attempts. SKIP3 skips dependent analysis/verification when required permitted prerequisites are unavailable. FAIL2 admits no complete result; correct the named cause and retry NEW paths without rewriting saved hashes or dropping cases. Unknown condition totals are unavailable, never zero. Read CELL2LOCATION_CATALOG_INTEGRATION.md. This gate installs nothing and fits no model; the full literature review and implementation goal remains open.'

def load_catalog_registry(root=ROOT):
 from rstatix_revision2_catalog.verify import load_catalog_registry as previous
 old=previous(metadata_view(root,load_before(root)));current=next(t for t in json.loads((root/META[0]).read_bytes()) if t['id']=='cell2location')
 if len(old)!=470:raise ValueError('Unexpected preserved registry definition count')
 for row in old:
  if row['id']=='cell2location':
   for n in ['current_coverage','existing_source','coverage_note']:row[n]=current[n]
 return old

def verify(root=ROOT,rescan=False):
 proof=json.loads((root/PROOF).read_bytes())
 if proof['status']!='PASS_ORIGINAL_CELL2LOCATION013_WORKFLOW_AND_SIX_PRIMARY_SOURCE_ROLES' or proof['new_scoped_integration_credit']!=1 or proof['added_reviews']!=6 or proof['whole_goal_complete']:raise ValueError('Unsupported original workflow/source-review admission scope')
 for key in ['source_pins','evidence_pins','after_files']:
  for n,wanted in proof[key].items():
   if pin(root/n)!=wanted:raise ValueError('Qualified catalog source/evidence differs: '+n)
 before=load_before(root)
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive'] or {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact predecessor differs')
 result=check_transition(root,before,{n:(root/n).read_bytes() for n in META});census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes());roles=validate(census,json.loads(before[PAPERS]),reviews(root),root)
 if rescan and scan(root,json.loads(before[PAPERS]))!=census:raise ValueError('Fresh complete250-source census differs')
 coverage=json.loads((root/COVERAGE).read_bytes());entry_row=next(t for t in json.loads((root/META[0]).read_bytes()) if t['id']=='cell2location')
 if coverage['source_reviews']!=reviews(root) or coverage['paper_role_coverage']!=entry_row['paper_role_coverage'] or coverage['reported_0_7a0_execution_qualified']:raise ValueError('Complete source-role coverage differs')
 old=prior_admission(root,before);component=qualification(root)
 return dict(status=proof['status'],**result,source_roles=roles,component=component,predecessor=old['status'],registry_definitions=len(load_catalog_registry(root)),primary_sources_rescanned=rescan,scientific_sources_overridden=False,scientific_verifiers_changed=False,original_analysis_executed=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=a.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;result=dict(status='SKIP3_ORIGINAL_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_CELL2LOCATION_CATALOG_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
