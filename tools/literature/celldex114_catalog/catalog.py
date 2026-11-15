#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one source-qualified celldex reference workflow without changing primary facts."""
from pathlib import Path
import argparse,json,subprocess,sys,traceback,uuid,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from celldex114_catalog.model import *
from hicat109_catalog.bridge import metadata_view
from singler_catalog.catalog import verify as previous_verify,load_catalog_registry as previous_registry
from singler_catalog.source import census
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta
ACTION=('Read CELLDEX114_CATALOG_INTEGRATION.md. Explicit original LOCAL Hub options are needed for cached Hub success; unknown author reference choices remain unknown. '
 'SKIP3 skips missing/forbidden prerequisites; FAIL2 admits no complete result, including recording failure. Preserve complete objects/conditions/RNG, reference assets and prior evidence. '
 'Private reference records and fresh scientific computation are separate from public receipt integrity. Cell Ranger/ARC remain excluded; the full 250-paper goal remains open.')
def load_before(root):
 with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(META):raise ValueError('Eight exact preceding metadata files required')
  return {n:z.read(n) for n in META}
def compatibility(before,after):
 row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
 if reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Complete preceding review bytes cannot be reconstructed')
 return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='celldex114-workflow-coverage',
  scope='Only one existing P051 celldex implementation coverage note changes. Exact reviewed-use metadata inverse only; scientific overlays forbidden.',paths=[row])
def qualification(root):
 proof=json.loads((root/WORKFLOW/'QUALIFICATION.json').read_bytes())
 if proof['status']!='PASS_ORIGINAL_CELLDEX114_REFERENCE_WORKFLOW_SOURCE_RECORDING_AND_PUBLIC_EXECUTION' or (proof['original_controls'],proof['complete_original_returns'],proof['original_failures_without_result'])!=(16,12,4):raise ValueError('Required original positive/error population differs')
 for name,identity in proof['source_files'].items():
  if pin(root/name)!=identity:raise ValueError('Qualified original-call source differs: '+name)
 for name,identity in proof['evidence_files'].items():
  if pin(root/WORKFLOW/name)!=identity:raise ValueError('Source-bound workflow receipt/index differs: '+name)
 results=json.loads((root/WORKFLOW/'receipts/COMPARISONS.json').read_bytes())
 if len(results)!=16 or sum(r['returned'] for r in results.values())!=12 or any(not all(r['checks'].values()) for r in results.values()):raise ValueError('Complete source comparisons or actual successful retrievals differ')
 private=json.loads((root/WORKFLOW/'ACTUAL-PRIVATE-READBACK.json').read_bytes())
 if private['status']!='PASS_CELLDEX114_COMPLETE_RETAINED_PRIVATE_RECORD_CONTENT' or private['private_file_entries_checked']!=4469 or private['qualification_identity']!=pin(root/WORKFLOW/'QUALIFICATION.json') or private['private_index_identity']!=pin(root/WORKFLOW/'PRIVATE-REFERENCE-RECORDS.json'):raise ValueError('Actual complete private record readback does not match this qualification')
 return dict(status=proof['status'],original_controls=16,complete_returns=12,original_errors=4,public_Linux_guards=12,Windows_prerequisite_guards=2,
  complete_retained_private_file_entries=4469,original_software_sources_redistributed=True,reference_matrices_redistributed=False,
  source_algorithms_patched=False,author_reference_snapshots_established=False,whole_tool_qualified=False,whole_paper_reproduced=False)
def load_catalog_registry(root=ROOT):
 before=load_before(root) if (root/PROOF).exists() else {n:(root/n).read_bytes() for n in META}
 rows=previous_registry(metadata_view(root,before))
 if len(rows)!=479:raise ValueError('Complete479 cumulative definitions required')
 current=entry(root,next(r for r in json.loads(before[META[0]]) if r['id']=='celldex'))
 target=next(r for r in rows if r['id']=='celldex')
 for key in ['current_coverage','existing_source','coverage_note']:target[key]=current[key]
 return rows
def apply(root,out):
 if any((root/n).exists() for n in [EVIDENCE,PROOF,COMPATIBILITY]):raise ValueError('Existing admission must be preserved and verified')
 before={n:(root/n).read_bytes() for n in META};previous=previous_verify(metadata_view(root,before),rescan=True)
 if (previous['new_scoped_integrations'],previous['missing'])!=(118,264):raise ValueError('Exact complete SingleR predecessor required')
 science=qualification(root);source_census=census(root);after=transition(root,before);result=check_transition(root,before,after)
 evidence=root/EVIDENCE;evidence.mkdir(exist_ok=False);(evidence/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
 with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for n,raw in before.items():z.writestr(n,raw)
 for name,value in [('PREDECESSOR.json',previous),('QUALIFICATION.json',science),('CENSUS.json',source_census)]:
  (evidence/name).write_bytes(encoded(value))
 (evidence/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode());(root/COMPATIBILITY).write_bytes(encoded(compatibility(before,after)))
 for n,raw in before.items():
  if (root/n).read_bytes()!=raw:raise ValueError('Concurrent metadata edit; no overwrite allowed: '+n)
 (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},**result)))
 for n,raw in after.items():(root/n).write_bytes(raw)
 source_names=sorted(p.relative_to(root).as_posix() for p in (root/MODULE).iterdir() if p.is_file())+[GUIDE,'tools/literature/celldex114_validation/records.py']
 evidence_names=sorted(p.relative_to(root).as_posix() for folder in [evidence,root/WORKFLOW] for p in folder.rglob('*') if p.is_file())+[COMPATIBILITY]
 proof=dict(status='PASS_CELLDEX114_SCOPED_REFERENCE_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION',**result,admitted_tool_ids=['celldex'],
  before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(root/n) for n in META},
  source_pins={n:pin(root/n) for n in source_names},evidence_pins={n:pin(root/n) for n in evidence_names},
  scientific_sources_overridden=False,preceding_scientific_verifiers_changed=False,fresh_scientific_calls_repeated_by_catalog_gate=False)
 (root/PROOF).write_bytes(encoded(proof));return proof
def verify(root=ROOT,rescan=False):
 proof=json.loads((root/PROOF).read_bytes())
 if proof['status']!='PASS_CELLDEX114_SCOPED_REFERENCE_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION':raise ValueError('Unsupported celldex admission')
 for kind in ['source_pins','evidence_pins','after_files']:
  for n,identity in proof[kind].items():
   if pin(root/n)!=identity:raise ValueError('Qualified catalog source/evidence differs: '+n)
 before=load_before(root)
 if {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact preceding metadata differs')
 after={n:(root/n).read_bytes() for n in META};result=check_transition(root,before,after)
 if json.loads((root/COMPATIBILITY).read_bytes())!=compatibility(before,after):raise ValueError('Exact review inverse differs')
 previous=previous_verify(metadata_view(root,before),rescan=rescan)
 if rescan and census(root)!=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes()):raise ValueError('Complete primary source census differs')
 return dict(status=proof['status'],**result,registry_definitions=len(load_catalog_registry(root)),component=qualification(root),predecessor=previous['status'],
  primary_sources_rescanned=rescan,fresh_scientific_calls_repeated_by_catalog_gate=False,private_records_rechecked_by_catalog_gate=False,whole_paper_reproduced=False)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=p.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true')
 p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build'):raise ValueError('Use a NEW build output directory')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=apply(ROOT,out) if a.apply else verify(rescan=a.rescan_sources)
  (out/('APPLIED.json' if a.apply else 'COMPLETED.json')).write_bytes(encoded(result));print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;label='SKIP3_CELLDEX114_CATALOG_UNAVAILABLE' if code==3 else 'FAIL2_CELLDEX114_CATALOG_UNADMITTED'
  print(label,repr(error),ACTION,flush=True)
  if owned:(out/'FAILURE.json').write_bytes(encoded(dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
  return code
if __name__=='__main__':raise SystemExit(main())
