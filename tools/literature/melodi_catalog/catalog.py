#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Correct a software identity, admit a live Java service adapter and review companion uses."""
from pathlib import Path
import argparse,json,subprocess,sys,traceback,uuid,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from melodi_catalog.model import *
from hicat109_catalog.bridge import metadata_view
from spotlight103_catalog.catalog import verify as previous_verify,load_catalog_registry as previous_registry
from melodi_catalog.source import audit as census,R_PRESTO_PATTERN,DEFINITIONS
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta
ACTION=('Read MELODI_PRESTO_SOURCE_AND_SERVICE.md. P169 uses the literature-triple service, not the unrelated R marker-testing package. '
 'Current queries require explicit live-unversioned snapshot and network permission; no author historical data is inferred. '
 'SKIP3 skips missing/forbidden prerequisites; FAIL2 admits no complete result. Preserve complete original responses and private data provenance. '
 'Saved integrity checks are separate from fresh service queries and private-content readback. The full250-paper goal remains open.')

def load_before(root):
 with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(META):raise ValueError('Eight exact preceding metadata files required')
  return {n:z.read(n) for n in META}
def compatibility(before,after):
 row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
 if reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Complete preceding review bytes cannot be reconstructed')
 return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='melodi-identity-and-companion-source-roles',
  scope='Correct P169 Presto identity, append eight source-specific reviews and seven new observations. Exact complete review-byte inverse only; scientific-code overlays forbidden.',paths=[row])
def qualification(root):
 proof=json.loads((root/WORKFLOW/'QUALIFICATION.json').read_bytes())
 if proof['status']!='PASS_JAVA_MELODI_COMPLETE_LIVE_SERVICE_TRANSPORT_AND_SOURCE_IDENTITY_BOUNDARY' or proof['actual_fixture_tests']!=28 or not all(proof[k] for k in ['all_three_actual_packaged_Windows_operations','all_three_actual_public_Linux_operations','all_three_Windows_records_rechecked_under_Linux']):raise ValueError('Complete actual service client qualification required')
 for n,v in proof['source_files'].items():
  if pin(root/n)!=v:raise ValueError('Qualified Java/service source differs: '+n)
 for n,v in proof['evidence_files'].items():
  if pin(root/WORKFLOW/n)!=v:raise ValueError('Preserved service receipt or index differs: '+n)
 source=json.loads((root/WORKFLOW/'receipts/SOURCE.json').read_bytes())
 for p in (root/WORKFLOW/'original-contract-source').rglob('*'):
  if p.is_file():
   n=p.relative_to(root/WORKFLOW/'original-contract-source').as_posix();expected=source['members'][n]
   if pin(p)!={k:expected[k] for k in ['bytes','sha256']}:raise ValueError('Selected original source byte differs: '+n)
 actual=json.loads((root/WORKFLOW/'ACTUAL-PRIVATE-READBACK.json').read_bytes())
 if actual['status']!='PASS_MELODI_SERVICE_COMPLETE_SAVED_SOURCE_AND_RECORD_IDENTITIES' or actual['public_only'] or actual['private_files_checked']!=proof['complete_private_file_entries'] or actual['qualification_identity']!=pin(root/WORKFLOW/'QUALIFICATION.json') or actual['private_index_identity']!=pin(root/WORKFLOW/'PRIVATE-RECORDS.json'):raise ValueError('Actual full private content readback differs')
 return dict(status=proof['status'],actual_fixture_tests=28,actual_Windows_live_operations=3,actual_Linux_live_operations=3,
  Windows_records_rechecked_under_Linux=3,complete_retained_private_files=proof['complete_private_file_entries'],native_analytic_credit=0,
  author_deployed_commit_or_database_snapshot_established=False,whole_tool_qualified=False,whole_paper_reproduced=False)

def load_catalog_registry(root=ROOT):
 before=load_before(root) if (root/PROOF).exists() else {n:(root/n).read_bytes() for n in META}
 rows=previous_registry(metadata_view(root,before))
 if len(rows)!=479:raise ValueError('Complete479-definition predecessor required')
 old=next(r for r in rows if r['id']=='presto');old.update(name='Presto (single-cell R package)',pattern=R_PRESTO_PATTERN,
  coverage_note='Unrelated R single-cell package retained as a registry definition; the explicit MELODI Presto context is excluded. No explicit software use in this all250-primary-source census; prior unrelated dependency evidence remains unchanged.')
 for definition in DEFINITIONS:
  row=dict(definition)
  if row['id']=='melodi-presto':row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE)
  rows.append(row)
 if len(rows)!=484 or len({r['id'] for r in rows})!=484:raise ValueError('Exactly five new canonical registry definitions required')
 return rows

def apply(root,out):
 if any((root/n).exists() for n in [EVIDENCE,PROOF,COMPATIBILITY]):raise ValueError('Existing admission must be preserved and verified')
 before={n:(root/n).read_bytes() for n in META};previous=previous_verify(metadata_view(root,before),rescan=True)
 if (previous['new_scoped_integrations'],previous['missing'])!=(120,262):raise ValueError('Exact complete SPOTlight predecessor required')
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
 source_names=sorted(p.relative_to(root).as_posix() for p in (root/MODULE).iterdir() if p.is_file())+[GUIDE]
 evidence_names=sorted(p.relative_to(root).as_posix() for folder in [evidence,root/WORKFLOW] for p in folder.rglob('*') if p.is_file())+[COMPATIBILITY]
 proof=dict(status='PASS_MELODI_IDENTITY_SERVICE_AND_COMPANION_PRIMARY_CATALOG_ADMISSION',**result,admitted_tool_ids=['melodi-presto'],
  before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(root/n) for n in META},
  source_pins={n:pin(root/n) for n in source_names},evidence_pins={n:pin(root/n) for n in evidence_names},
  scientific_sources_overridden=False,preceding_scientific_verifiers_changed=False,fresh_scientific_calls_repeated_by_catalog_gate=False)
 (root/PROOF).write_bytes(encoded(proof));return proof
def verify(root=ROOT,rescan=False):
 proof=json.loads((root/PROOF).read_bytes())
 if proof['status']!='PASS_MELODI_IDENTITY_SERVICE_AND_COMPANION_PRIMARY_CATALOG_ADMISSION':raise ValueError('Unsupported MELODI admission')
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
  primary_sources_rescanned=rescan,fresh_scientific_calls_repeated_by_catalog_gate=False,complete_record_payloads_rechecked_by_catalog_gate=False,whole_paper_reproduced=False)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=p.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true')
 p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build'):raise ValueError('Use a NEW build output directory')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=apply(ROOT,out) if a.apply else verify(rescan=a.rescan_sources)
  (out/('APPLIED.json' if a.apply else 'COMPLETED.json')).write_bytes(encoded(result));print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;label='SKIP3_MELODI_CATALOG_UNAVAILABLE' if code==3 else 'FAIL2_MELODI_CATALOG_UNADMITTED'
  print(label,repr(error),ACTION,flush=True)
  if owned:(out/'FAILURE.json').write_bytes(encoded(dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
  return code
if __name__=='__main__':raise SystemExit(main())
