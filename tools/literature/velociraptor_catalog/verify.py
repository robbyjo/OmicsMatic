#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the exact one-wrapper catalog transition and all unchanged predecessor gates."""
from pathlib import Path
import argparse,json,sys,traceback
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from velociraptor_catalog.model import *
from velociraptor_catalog.source import scan,review
from velociraptor_catalog.bridge import load_before,metadata_view,prior_admission
from velociraptor_catalog.qualification import qualification
from catalog_source_integrity import reverse_delta
from wire_signac_catalog import delta
ACTION='Read VELOCIRAPTOR120_CATALOG_INTEGRATION.md and VELOCIRAPTOR120_WORKFLOWS.md. Use exact permitted original1.20.0 Linux/WSL providers, explicit trusted R scripts and NEW paths. SKIP3 skips dependent analysis for unavailable/forbidden prerequisites. FAIL2 admits no complete result, including recording failure. Preserve unknown author settings, literal identifiers, NA/NaN/Inf, raw consoles and every original/partial file. Original R native diagnostics remain unresolved; unknown raw-console totals remain unavailable. Resolve the named cause without changing saved hashes. This gate installs nothing and fits no model; the full250-paper goal remains open.'

def compatibility(before,after):
 row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
 if reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Exact full predecessor review collection could not be recovered')
 return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='velociraptor-existing-primary-use-implementation-only',scope='Only one implementation_scope_note changes in the complete1168-review collection. All1167 other reviews and every primary role/setting remain unchanged. Only reviewed-uses.json metadata is reversible; scientific overlays forbidden.',paths=[row])

def load_catalog_registry(root=ROOT):
 from scvelo_catalog.verify import load_catalog_registry as previous
 old=previous(metadata_view(root,load_before(root)))
 if len(old)!=471:raise ValueError('Unexpected cumulative matcher population')
 current=next(r for r in json.loads((root/META[0]).read_bytes()) if r['id']=='velociraptor')
 for row in old:
  if row['id']=='velociraptor':
   for key in ['current_coverage','existing_source','coverage_note']:row[key]=current[key]
 return old

def verify(root=ROOT,rescan=False):
 proof=json.loads((root/PROOF).read_bytes())
 if proof['status']!='PASS_ORIGINAL_VELOCIRAPTOR120_EXISTING_PRIMARY_USE_AND_SCOPED_WORKFLOW' or proof['new_scoped_integration_credit']!=1 or proof['added_reviews'] or proof['added_observations'] or proof['whole_goal_complete']:raise ValueError('Unsupported wrapper admission scope')
 for key in ['source_pins','evidence_pins','after_files']:
  for name,wanted in proof[key].items():
   if pin(root/name)!=wanted:raise ValueError('Qualified catalog source/evidence differs: '+name)
 before=load_before(root)
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive'] or {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact predecessor metadata differs')
 after={n:(root/n).read_bytes() for n in META};result=check_transition(root,before,after)
 if json.loads((root/COMPATIBILITY).read_bytes())!=compatibility(before,after):raise ValueError('Exact one-field review inverse differs')
 reading=review(root);coverage=json.loads((root/COVERAGE).read_bytes());row=next(r for r in json.loads(after[META[0]]) if r['id']=='velociraptor')
 if coverage['source_review']!=reading or coverage['paper_role_coverage']!=row['paper_role_coverage'] or row['implementation_status']!='upstream-adapter-validated' or row['selected_environment_is_author_environment'] or row['whole_tool_qualified']:raise ValueError('Primary role or qualified implementation scope differs')
 census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes())
 if len(census)!=250:raise ValueError('Incomplete source census')
 if rescan and scan(root,json.loads(before[PAPERS]))!=census:raise ValueError('Fresh complete250-source identities or anchors differ')
 old=prior_admission(root,before);component=qualification(root)
 return dict(status=proof['status'],**result,registry_definitions=len(load_catalog_registry(root)),primary_sources_rescanned=rescan,component=component,predecessor=old['status'],scientific_sources_overridden=False,scientific_verifiers_changed=False,original_analysis_executed=False)

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--rescan-sources',action='store_true');args=parser.parse_args();out=args.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=args.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_bytes((ACTION+'\n').encode());print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;status='SKIP3_VELOCIRAPTOR_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_VELOCIRAPTOR_CATALOG_UNADMITTED';result=dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_bytes((status+'\n'+repr(error)+'\n'+ACTION+'\n').encode())
  print(status,repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
