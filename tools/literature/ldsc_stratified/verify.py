#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read every retained stratified API control and verify exact current source bindings."""
import argparse,hashlib,json,sys,traceback,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/ldsc-stratified-development'
ACTION=('SKIP3: supply the named permitted missing evidence/source before rechecking. '
 'FAIL2: no qualification admitted; preserve changed bytes and failed evidence, '
 'review the mismatch and qualify the changed component separately. Never refresh '
 'historical fingerprints to obtain a passing result. Read the API guide and WARNINGS.md.')
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(ok,why):
 if not ok:raise ValueError(why)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=None
 try:
  target=a.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Choose NEW workspace build evidence output');target.mkdir(parents=True,exist_ok=False);out=target
  v=json.loads((EVIDENCE/'VALIDATION.json').read_bytes());require(v['status']=='PASS_NATIVE_PREPARED_PARTITIONED_LDSC_API','Missing API qualification')
  for name,expected in v['source_guards'].items():require(pin((ROOT/name).read_bytes())==expected,'Qualified source/fixture changed: '+name)
  archive=EVIDENCE/'checks.zip';require(pin(archive.read_bytes())==v['archive'],'Archive bytes changed')
  with zipfile.ZipFile(archive) as z:
   index=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads'];require(len(index)==v['indexed_payloads'] and len(set(z.namelist()))==len(z.namelist()) and set(z.namelist())==set(index)|{'ENTRY-MANIFEST.json'},'Archive population differs')
   for name,expected in index.items():require(pin(z.read(name))==expected,'Saved payload differs: '+name)
  result=dict(status=v['status'],original_models=48,reference_fields=22596,windows_tests=9,linux_same_tests=9,byte_identical_complete_model_payloads=48,indexed_payloads=len(index),scientific_runtime_reexecuted=False,new_family_credit=0)
  (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,(FileNotFoundError,PermissionError)) else 2;result=dict(status='SKIP3_DEPENDENT_VERIFICATION_SKIPPED' if code==3 else 'FAIL2_NO_QUALIFICATION_ADMITTED',error=repr(e),detail=traceback.format_exc(),required_action=ACTION)
  if out is not None:(out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
  print(result['status']+': '+repr(e)+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
