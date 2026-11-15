#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete saved source/record bytes; this does not rerun original scientific calls."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,sys,traceback,zipfile
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'spotlight103';sys.path.insert(0,str(MODULE))
from Runtime import *
WORKFLOW=ROOT/'docs/literature/spotlight103-deconvolution-validation'

def verify():
 proof=load(WORKFLOW/'QUALIFICATION.json')
 if proof['status']!='PASS_ORIGINAL_SPOTLIGHT103_FULL_DECONVOLUTION_SOURCE_RECORDING_AND_PUBLIC_EXECUTION' or (proof['original_controls'],proof['complete_original_returns'],proof['original_failures_without_result'])!=(29,20,9):raise ValueError('Full original source qualification required')
 for name,identity in {**proof['source_files'],**proof['reused_original_source_archives']}.items():
  if pin(ROOT/name)!=identity:raise ValueError('Qualified source differs: '+name)
 for name,identity in proof['evidence_files'].items():
  if pin(WORKFLOW/name)!=identity:raise ValueError('Preserved evidence differs: '+name)
 index=load(WORKFLOW/'BUNDLE-INDEX.json');checked=0
 for label,row in index['bundles'].items():
  path=WORKFLOW/row['archive']
  if pin(path)!=row['archive_identity']:raise ValueError('Complete bundle differs: '+label)
  with zipfile.ZipFile(path) as z:
   if len(z.namelist())!=len(row['files']) or set(z.namelist())!=set(row['files']):raise ValueError('Complete record population differs: '+label)
   for name,identity in row['files'].items():
    rel=PurePosixPath(name)
    if rel.is_absolute() or '..' in rel.parts:raise ValueError('Unsafe record member')
    raw=z.read(name)
    if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=identity:raise ValueError('Complete original record differs: '+label+'/'+name)
    checked+=1
 if checked!=proof['full_synthetic_record_file_entries']:raise ValueError('Incomplete full record readback')
 comparisons=load(WORKFLOW/'receipts/COMPARISONS.json')
 if len(comparisons)!=29 or sum(c['returned'] for c in comparisons.values())!=20 or any(not all(c['checks'].values()) for c in comparisons.values()):raise ValueError('Original comparisons did not all qualify')
 return dict(status='PASS_SPOTLIGHT103_COMPLETE_SAVED_SOURCE_AND_SYNTHETIC_RECORD_CONTENT',complete_record_files_checked=checked,
  original_controls=29,complete_returns=20,original_errors=9,fresh_scientific_computation=False,biological_accuracy_established=False,original_NMF_convergence_certified=False,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();owned=False
 try:
  out=check_output(a.out,[ROOT/'tools',ROOT/'docs']);out.mkdir(parents=True,exist_ok=False);owned=True
  result=verify();save(out/'COMPLETED.json',result);print(result['status'],result['complete_record_files_checked'],flush=True);return 0
 except BaseException as e:
  print('FAIL2_SPOTLIGHT103_SAVED_RECORDS_UNADMITTED',repr(e),ACTION,flush=True)
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_SPOTLIGHT103_SAVED_RECORDS_UNADMITTED',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION))
  return 2
if __name__=='__main__':raise SystemExit(main())
