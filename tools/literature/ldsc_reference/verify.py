#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit current reference-preparation source and all saved qualification payloads."""
import argparse,hashlib,json,subprocess,sys,traceback,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/ldsc-reference-workflow'
HISTORICAL='a39ac83c4720be26cf94a06f0e069770af7d3ff9'
ACTION=('SKIP3: dependent audit skipped; supply the named permitted source/evidence, Python 3 '
 '3.9+ and Git history before rechecking. FAIL2: no qualification admitted; preserve changed source '
 'and failed evidence, inspect the mismatch and qualify changed components separately. Never '
 'refresh historical hashes, patch the original oracle, or relabel failed upstream subset runs '
 'as results. Read the workflow guide and WARNINGS.md. Native analysis needs Java 17+ only.')

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def filepin(path):
 digest=hashlib.sha256()
 with path.open('rb') as stream:
  for block in iter(lambda:stream.read(1048576),b''):digest.update(block)
 return dict(bytes=path.stat().st_size,sha256=digest.hexdigest())
def require(ok,why):
 if not ok:raise ValueError(why)
def archive(path,expected,number):
 require(filepin(path)==expected,'Archive bytes changed: '+str(path))
 with zipfile.ZipFile(path) as z:
  names=z.namelist();entries=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads'];require(len(names)==len(set(names)) and set(names)==set(entries)|{'ENTRY-MANIFEST.json'} and len(entries)==number,'Archive inventory differs: '+str(path))
  for name,value in entries.items():require(pin(z.read(name))==value,'Archived payload changed: '+name)
 return number

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=None
 try:
  if sys.version_info<(3,9):raise FileNotFoundError('Python 3.9+ is required for this developer audit; native analysis needs Java 17+ only')
  target=a.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Choose NEW workspace build evidence output');target.mkdir(parents=True,exist_ok=False);out=target
  v=json.loads((EVIDENCE/'VALIDATION.json').read_bytes());require(v['status']=='PASS_NATIVE_REFERENCE_LDSCORE_WORKFLOW','Missing current workflow qualification')
  for name,value in v['source_guards'].items():require(filepin(ROOT/name)==value,'Qualified current source/fixture changed: '+name)
  require(v['original_models_each_boundary']==37 and v['reference_fields_each_boundary']==75226,'Original reference population differs')
  require(v['windows_junit']==v['linux_same_junit']==dict(tests=20,errors=0,failures=0,skipped=0),'Packaged focused test population differs')
  require(v['actual_native_main_counts']==dict(total=31,success=20,skip3=4,fail2=7),'Actual main-launch population differs')
  require(v['byte_identical_api_payloads']==37 and v['byte_identical_workflow_payloads']==182 and v['successful_original_cli_cases']==5 and v['retained_failed_original_cli_cases']==2,'Cross-platform/source comparison population differs')
  require(v['historical_fingerprints_refreshed'] is False and v['new_family_credit']==0 and v['whole_goal_complete'] is False,'Scope or historical-admission flags differ')
  count=archive(EVIDENCE/'checks.zip',v['archive'],v['indexed_payloads'])
  parent=ROOT/'docs/literature/ldsc-scores-development';old=json.loads((parent/'VALIDATION.json').read_bytes());require(old['status']=='PASS_NATIVE_REFERENCE_LD_SCORE_API','Missing earlier qualification')
  parents=archive(parent/'checks.zip',old['archive'],old['indexed_payloads'])
  try:
   for name,value in old['source_guards'].items():
    result=subprocess.run(['git','--no-replace-objects','show',HISTORICAL+':'+name],cwd=ROOT,capture_output=True)
    if result.returncode:raise FileNotFoundError('Required earlier Git source snapshot unavailable: '+HISTORICAL+':'+name)
    require(pin(result.stdout)==value,'Earlier qualification does not bind actual Git source: '+name)
    if name not in v['historical_changed_sources']:require(filepin(ROOT/name)==value,'Unchanged earlier source unexpectedly differs: '+name)
  except FileNotFoundError:raise
  with zipfile.ZipFile(EVIDENCE/'checks.zip') as z:
   for name in v['historical_changed_sources']:
    saved=z.read('historical-source/'+name);require(pin(saved)==old['source_guards'][name],'Saved earlier source changed: '+name);require(filepin(ROOT/name)==v['source_guards'][name],'Changed SDK has no new source qualification: '+name)
  strat=ROOT/'docs/literature/ldsc-stratified-workflow';s=json.loads((strat/'VALIDATION.json').read_bytes());strat_count=archive(strat/'checks.zip',s['archive'],s['indexed_payloads'])
  for name,value in s['source_guards'].items():require(filepin(ROOT/name)==value,'Earlier stratified workflow source changed: '+name)
  result=dict(status=v['status'],original_models_each_boundary=37,reference_fields_each_boundary=75226,windows_tests=20,linux_same_tests=20,actual_native_main_counts=v['actual_native_main_counts'],byte_identical_api_payloads=37,byte_identical_workflow_payloads=182,successful_original_cli_cases=5,retained_failed_original_cli_cases=2,indexed_payloads=count,historical_api_payloads=parents,historical_stratified_payloads=strat_count,historical_source_bound_to_actual_git=HISTORICAL,original_runtime_reexecuted=False,junit_reexecuted=False,new_family_credit=0,whole_goal_complete=False)
  (out/'COMPLETED.json').write_bytes((json.dumps(result,indent=2)+'\n').encode());print(json.dumps(result),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,(FileNotFoundError,PermissionError)) else 2;result=dict(status='SKIP3_DEPENDENT_AUDIT_SKIPPED' if code==3 else 'FAIL2_NO_QUALIFICATION_ADMITTED',error=repr(e),detail=traceback.format_exc(),required_action=ACTION)
  if out is not None:(out/'FAILURE.json').write_bytes((json.dumps(result,indent=2)+'\n').encode())
  print(result['status']+': '+repr(e)+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
