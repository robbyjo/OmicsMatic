#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read complete celldex source/evidence identities; optionally read every retained private file."""
from pathlib import Path
import argparse,os,sys,traceback
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'celldex114';sys.path.insert(0,str(MODULE))
from Runtime import *
DATA=ROOT/'docs/literature/celldex114-reference-validation'
def verify(evidence,private=False):
 proof=load(evidence/'QUALIFICATION.json')
 if proof['status']!='PASS_ORIGINAL_CELLDEX114_REFERENCE_WORKFLOW_SOURCE_RECORDING_AND_PUBLIC_EXECUTION' or (proof['original_controls'],proof['complete_original_returns'],proof['original_failures_without_result'])!=(16,12,4):raise ValueError('Complete scoped original reference qualification required')
 for name,identity in proof['source_files'].items():
  if pin(ROOT/name)!=identity:raise ValueError('Qualified original-call source differs: '+name)
 for name,identity in proof['evidence_files'].items():
  if pin(evidence/name)!=identity:raise ValueError('Complete source-bound evidence differs: '+name)
 comparisons=load(evidence/'receipts/COMPARISONS.json')
 if len(comparisons)!=16 or any(r['status']!='PASS_COMPLETE_ORIGINAL_CELLDEX_REFERENCE_COMPARISON' or not all(r['checks'].values()) for r in comparisons.values()):raise ValueError('Complete original comparisons differ')
 if sum(r['returned'] for r in comparisons.values())!=12:raise ValueError('Matching original errors cannot be counted as successful retrieval')
 count=0
 if private:
  if sys.platform!='linux':raise Unavailable('Complete private-record readback requires permitted Linux/WSL containing the retained reference records')
  for name,entry in load(evidence/'PRIVATE-REFERENCE-RECORDS.json')['files'].items():
   p=Path(entry['path'])
   if entry['kind']=='link':
    if not p.is_symlink() or os.readlink(p)!=entry['target']:raise ValueError('Retained private link differs: '+name)
   elif not p.is_file() or pin(p)!={k:entry[k] for k in ['bytes','sha256']}:raise ValueError('Complete retained private file differs or is absent: '+name)
   count+=1
 return dict(status='PASS_CELLDEX114_COMPLETE_RETAINED_PRIVATE_RECORD_CONTENT' if private else 'PASS_CELLDEX114_PUBLIC_SOURCE_AND_RECEIPT_INTEGRITY_NOT_FRESH_ANALYSIS',
  original_controls=16,complete_returns=12,original_errors=4,qualified_source_files=len(proof['source_files']),
  private_file_entries_checked=count,private_records_checked=private,fresh_original_computation_performed=False,whole_paper_reproduced=False,new_family_credit=0)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--evidence',type=Path,default=DATA);p.add_argument('--out',type=Path,required=True);p.add_argument('--private-records',action='store_true')
 a=p.parse_args();out=a.out.absolute();owned=False
 try:
  out=check_output(out,[MODULE,VALIDATION,a.evidence]);out.mkdir(parents=True,exist_ok=False);owned=True
  result=verify(a.evidence,a.private_records);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,Unavailable) or (isinstance(error,OSError) and not isinstance(error,FileExistsError)) else 2
  label='SKIP3_CELLDEX114_RECORDS_UNAVAILABLE' if code==3 else 'FAIL2_CELLDEX114_RECORDS_UNADMITTED';print(label,repr(error),ACTION,flush=True)
  if owned:save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
  return code
if __name__=='__main__':raise SystemExit(main())
