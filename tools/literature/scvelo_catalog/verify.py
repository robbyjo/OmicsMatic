#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify one original Python workflow, six source roles and all unchanged predecessor gates."""
from pathlib import Path
import argparse,json,sys,traceback
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from scvelo_catalog.model import *
from scvelo_catalog.source import scan,validate
from scvelo_catalog.bridge import load_before,metadata_view,prior_admission
from scvelo_catalog.qualification import qualification
from catalog_source_integrity import reverse_delta
from wire_signac_catalog import delta
ACTION='Use the exact matching original0.2.4/0.2.5 Linux/WSL CPU source workflow, not a standalone JAR. Printed0.24 and the separately missing velociraptor R interface remain SKIP3 dependent analyses; no edition/interface substitution is admitted. Preserve unknown author settings, literal IDs, NaN/Inf, every available original object, condition and partial file. Four historical SVGs are irrecoverable; use saved-bundle temporary copies, not historical /tmp paths. SKIP3 skips dependent work for missing/forbidden prerequisites. FAIL2 admits no complete result; retain full console and recording errors, resolve the cause and retry NEW paths without dropping cases or changing saved hashes. Unknown counts remain unavailable, never zero. Read SCVELO_CATALOG_INTEGRATION.md. This gate installs nothing and fits no model; the full250-paper goal remains open.'

def compatibility(before,after):
 row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
 if row['path']!=REVIEWS or reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Exact predecessor review bytes could not be recovered')
 return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='scvelo-five-roles-and-separate-velociraptor-role',scope='Exactly six appended individual source reviews: five existing scVelo observations and one newly named R wrapper. All1162 previous reviews remain unchanged. Only reviewed-uses.json metadata is reversible; no scientific code, result or verifier overlay.',paths=[row])

def load_catalog_registry(root=ROOT):
 from cell2location_catalog.verify import load_catalog_registry as previous
 old=previous(metadata_view(root,load_before(root)));definition=json.loads((root/DEFINITION).read_bytes())
 if len(old)!=470 or definition['id']!='velociraptor' or any(r['id']=='velociraptor' for r in old):raise ValueError('Unexpected predecessor/new matcher definition population')
 current=next(r for r in json.loads((root/META[0]).read_bytes()) if r['id']=='scvelo')
 for row in old:
  if row['id']=='scvelo':
   for key in ['current_coverage','existing_source','coverage_note']:row[key]=current[key]
 result=old+[definition]
 if len(result)!=471 or len({r['id'] for r in result})!=471:raise ValueError('Duplicate cumulative matcher IDs')
 return result

def verify(root=ROOT,rescan=False):
 proof=json.loads((root/PROOF).read_bytes())
 if proof['status']!='PASS_ORIGINAL_SCVELO024025_FIVE_ROLES_AND_SEPARATE_MISSING_VELOCIRAPTOR' or proof['new_scoped_integration_credit']!=1 or proof['added_reviews']!=6 or proof['new_missing_wrapper_implementation_credit'] or proof['whole_goal_complete']:raise ValueError('Unsupported workflow/source-review admission scope')
 for key in ['source_pins','evidence_pins','after_files']:
  for name,wanted in proof[key].items():
   if pin(root/name)!=wanted:raise ValueError('Qualified catalog source/evidence differs: '+name)
 before=load_before(root)
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive'] or {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact predecessor metadata differs')
 after={n:(root/n).read_bytes() for n in META};result=check_transition(root,before,after)
 if json.loads((root/COMPATIBILITY).read_bytes())!=compatibility(before,after):raise ValueError('Exact six-review shared-source inverse differs')
 census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes());roles=validate(census,json.loads(before[PAPERS]),reviews(root),root)
 if rescan and scan(root,json.loads(before[PAPERS]))!=census:raise ValueError('Fresh complete250-source census differs')
 coverage=json.loads((root/COVERAGE).read_bytes());row=next(t for t in json.loads(after[META[0]]) if t['id']=='scvelo');wrapper_row=next(t for t in json.loads(after[META[0]]) if t['id']=='velociraptor')
 if coverage['source_reviews']!=reviews(root) or coverage['paper_role_coverage']!=row['paper_role_coverage'] or wrapper_row['implementation_status']!='missing' or wrapper_row['new_scoped_integration_credit'] or coverage['reported_0_24_execution_qualified'] or coverage['R_wrapper_execution_qualified']:raise ValueError('Complete role coverage or missing-wrapper scope differs')
 old=prior_admission(root,before);component=qualification(root)
 return dict(status=proof['status'],**result,source_roles=roles,component=component,predecessor=old['status'],registry_definitions=len(load_catalog_registry(root)),primary_sources_rescanned=rescan,scientific_sources_overridden=False,scientific_verifiers_changed=False,original_analysis_executed=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=a.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_bytes((ACTION+'\n').encode('utf8'));print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;result=dict(status='SKIP3_ORIGINAL_SCVELO_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_SCVELO_CATALOG_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_bytes((result['status']+'\n'+repr(error)+'\n'+ACTION+'\n').encode('utf8'))
  print(result['status'],repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
