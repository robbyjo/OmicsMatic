#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check current workflow controls and bind its unchanged scientific API to actual Git."""
import argparse,hashlib,json,subprocess,sys,traceback,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/ldsc-stratified-workflow'
API_BASE='e5dafcacaba4d7db6c36ecca52518474b8ca7df7'
API_EVIDENCE='docs/literature/ldsc-stratified-development'
ACTION=('SKIP3: supply the named permitted missing evidence/source or actual historical Git history. '
 'FAIL2: no qualification admitted; preserve changed bytes and failed evidence, review the mismatch '
 'and qualify the changed component separately. Never refresh historical fingerprints to force a pass. '
 'This developer audit needs Python/Git; native analysis needs the complete current JAR and Java 17+. '
 'Read the workflow guide and WARNINGS.md for analysis SKIP3/FAIL2 actions.')
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(ok,why):
 if not ok:raise ValueError(why)
def read_archive(path,expected):
 require(pin(path.read_bytes())==expected,'Control archive bytes changed: '+str(path))
 with zipfile.ZipFile(path) as z:
  index=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads'];require(len(set(z.namelist()))==len(z.namelist()) and set(z.namelist())==set(index)|{'ENTRY-MANIFEST.json'},'Archive population differs')
  for name,value in index.items():require(pin(z.read(name))==value,'Saved payload differs: '+name)
 return len(index)
def historical_api():
 exists=subprocess.run(['git','--no-replace-objects','cat-file','-e',API_BASE+'^{commit}'],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if exists.returncode:raise FileNotFoundError('Required actual historical API Git commit '+API_BASE+'; retrieve legitimate repository history before rechecking')
 def git(name):return subprocess.check_output(['git','--no-replace-objects','show',API_BASE+':'+name],cwd=ROOT)
 name=API_EVIDENCE+'/VALIDATION.json';raw=(ROOT/name).read_bytes();require(raw==git(name),'Historical API qualification record changed');v=json.loads(raw);require(v['status']=='PASS_NATIVE_PREPARED_PARTITIONED_LDSC_API' and v['original_models']==48 and v['reference_fields']==22596,'Missing selected numerical qualification')
 changed={};scientific={}
 for name,expected in v['source_guards'].items():
  require(pin(git(name))==expected,'Historical API Git binding differs: '+name)
  current=pin((ROOT/name).read_bytes())
  if name.startswith('src/') or name=='build.gradle':require(current==expected,'Qualified scientific API/fixture/build changed: '+name);scientific[name]=current
  elif current!=expected:changed[name]=dict(before=expected,current=current)
 payloads=read_archive(ROOT/API_EVIDENCE/'checks.zip',v['archive']);require(payloads==244,'Historical API control population differs')
 return dict(status='PASS_ACTUAL_GIT_API_BINDING_AND_UNCHANGED_CURRENT_SCIENTIFIC_SOURCES',base_commit=API_BASE,qualification=pin(raw),archive=v['archive'],retained_control_payloads=payloads,scientific_sources=scientific,metadata_changes=changed,historical_fingerprints_refreshed=False,original_runtime_reexecuted=False)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=None
 try:
  target=a.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Choose NEW workspace build evidence output');target.mkdir(parents=True,exist_ok=False);out=target
  v=json.loads((EVIDENCE/'VALIDATION.json').read_bytes());require(v['status']=='PASS_NATIVE_PREPARED_STRATIFIED_LDSC_WORKFLOW','Missing workflow qualification')
  for name,expected in v['source_guards'].items():require(pin((ROOT/name).read_bytes())==expected,'Qualified workflow source/fixture changed: '+name)
  require(historical_api()==v['native_api_binding'],'Prior API binding differs; use a separately qualified successor')
  payloads=read_archive(EVIDENCE/'checks.zip',v['archive']);require(payloads==v['indexed_payloads'],'Workflow control population differs')
  require(v['windows_junit']==v['linux_same_junit']==dict(tests=17,errors=0,failures=0,skipped=0),'Packaged tests differ');require(v['cli_process_counts']==dict(total=33,success=11,skip3=6,fail2=16) and v['byte_identical_successful_payloads']==46,'Packaged process qualification differs')
  result=dict(status=v['status'],original_models=48,reference_fields_each_api_and_file_boundary=22596,windows_tests=17,linux_same_tests=17,cli_process_counts=v['cli_process_counts'],byte_identical_successful_payloads=46,indexed_payloads=payloads,historical_api_payloads=244,original_runtime_reexecuted=False,new_family_credit=0)
  (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,(FileNotFoundError,PermissionError)) else 2;result=dict(status='SKIP3_DEPENDENT_VERIFICATION_SKIPPED' if code==3 else 'FAIL2_NO_QUALIFICATION_ADMITTED',error=repr(e),detail=traceback.format_exc(),required_action=ACTION)
  if out is not None:(out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
  print(result['status']+': '+repr(e)+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
