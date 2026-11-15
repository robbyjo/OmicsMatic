#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check pinned public software receipts and optionally every retained private literature record."""
from pathlib import Path
import argparse,json,sys,traceback
sys.dont_write_bytecode=True
from qualify import pin,save,ACTION
ROOT=Path(__file__).resolve().parents[3];EVIDENCE=ROOT/'docs/literature/melodi-service-validation'
def verify(private=False):
 proof=json.loads((EVIDENCE/'QUALIFICATION.json').read_bytes())
 if proof['status']!='PASS_JAVA_MELODI_COMPLETE_LIVE_SERVICE_TRANSPORT_AND_SOURCE_IDENTITY_BOUNDARY' or proof['actual_fixture_tests']!=28:raise ValueError('Complete service client qualification required')
 for n,v in proof['source_files'].items():
  if pin(ROOT/n)!=v:raise ValueError('Qualified Java/client source differs: '+n)
 for n,v in proof['evidence_files'].items():
  if pin(EVIDENCE/n)!=v:raise ValueError('Preserved service receipt/index differs: '+n)
 index=json.loads((EVIDENCE/'PRIVATE-RECORDS.json').read_bytes());checked=0
 if private:
  for name,row in index['files'].items():
   p=Path(row['path'])
   if pin(p)!=dict(bytes=row['bytes'],sha256=row['sha256']):raise ValueError('Retained original private bytes differ: '+name)
   checked+=1
  if checked!=proof['complete_private_file_entries']:raise ValueError('Complete private population differs')
 return dict(status='PASS_MELODI_SERVICE_COMPLETE_SAVED_SOURCE_AND_RECORD_IDENTITIES',public_only=not private,private_files_checked=checked,
  qualification_identity=pin(EVIDENCE/'QUALIFICATION.json'),private_index_identity=pin(EVIDENCE/'PRIVATE-RECORDS.json'),
  fresh_service_query=False,author_snapshot_recovered=False,cryptographic_server_authorship_established=False,whole_goal_complete=False)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--private-records',action='store_true');a=p.parse_args();owned=False
 try:
  out=a.out.absolute()
  if out!=out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out) for p in [ROOT/'tools',ROOT/'docs']):raise ValueError('Use a canonical NEW output outside protected source/evidence')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(a.private_records);save(out/'COMPLETED.json',result);print(result['status'],result['private_files_checked'],flush=True);return 0
 except BaseException as e:
  print('FAIL2_MELODI_SERVICE_RECORDS_UNADMITTED',repr(e),ACTION,flush=True)
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_MELODI_SERVICE_RECORDS_UNADMITTED',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION))
  return 2
if __name__=='__main__':raise SystemExit(main())
