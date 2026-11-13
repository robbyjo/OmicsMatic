#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Restore all complete original FastMNN controls and replay native matrix checks; fits no R models."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3];DATA=ROOT/'docs/literature/fastmnn129-workflow-development'
ACTION='Required action: retain diagnostics; supply permitted JDK21/25 and NEW output. Missing/forbidden compiler/runtime prerequisites are SKIP3; resolve FAIL2 source/archive/numeric contradictions without changing original controls. Replay restores all17 successful and two original failed FastMNN controls, compares complete matrices and saved model bytes, and checks native reconstruction. It does not repeat original R fitting, reload RDS or authenticate producers. Original analysis requires the documented exact Linux R4.5.3/Batchelor1.29.0 provider. Optional --runtime-request tests five invalid-input refusals on that existing Linux provider; installs and analyses are never launched by replay.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def compare_complete(evidence):
 import csv
 def table(path):
  with path.open(encoding='utf8',newline='') as f:rows=list(csv.reader(f,delimiter='\t',quoting=csv.QUOTE_NONE))
  return [r[1:] for r in rows[1:]]
 bank=json.loads((evidence/'bank/COMPLETED.json').read_bytes())
 if bank['status']!='PASS_NINETEEN_COMPLETE_ORIGINAL_FASTMNN129_STREAM_CONTROLS' or bank['failures'] or len(bank['cases'])!=19:raise ValueError('Incomplete19-case qualification')
 success=failed=0
 for name,row in bank['cases'].items():
  directory=evidence/'bank'/name/'adapter';reference=evidence/'independent'/name/'reference/cases'/name;original=json.loads((reference/'RESULT.json').read_bytes());state=json.loads((directory/'analysis-status.json').read_bytes())
  if original['status']=='PASS_COMPLETE_ORIGINAL_FASTMNN129':
   success+=1
   if state['exit_code']!=0 or not state['result_admitted']:raise ValueError('Successful original model not admitted: '+name)
   actual=json.loads((directory/'worker-result.json').read_bytes());expected=json.loads((reference/'complete-matrices.json').read_bytes())
   for key in ['cell_ids','feature_ids','batches']:
    if actual[key]!=expected[key]:raise ValueError('Complete independent axes differ: '+name+' '+key)
   for key,file in [('corrected','corrected.tsv'),('rotation','rotation.tsv'),('reconstructed','reconstructed-cells.tsv')]:
    values=expected[key] if key!='reconstructed' else list(map(list,zip(*expected[key])))
    if table(directory/file)!=values:raise ValueError('Complete independent numeric values differ: '+name+' '+key)
   for file in ['original-fit.rds','metadata.rds']:
    if pin(directory/file)!=pin(reference/file):raise ValueError('Full original model/metadata differs: '+name+' '+file)
  else:
   failed+=1
   if state['exit_code']!=2 or state['result_admitted'] or row['status']!='PASS_ORIGINAL_UNNAMED_WEIGHT_FAILURE_PRESERVED':raise ValueError('Original failure was substituted or admitted: '+name)
   if original['error'] not in (directory/'worker-error.txt').read_text(encoding='utf8'):raise ValueError('Original error differs: '+name)
   warnings=(directory/'WARNINGS.md').read_text(encoding='utf8')
   if 'unnamed numeric weights' not in warnings or 'batch_weights' not in warnings:raise ValueError('Original failure recovery missing: '+name)
 if success!=17 or failed!=2:raise ValueError('Original success/failure census differs')
 for file in ['original-fit.rds','metadata.rds']:
  if pin(evidence/'factors'/file)!=pin(evidence/'independent/default/reference/cases/default'/file):raise ValueError('Complete lazy-factor original model differs')

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--runtime-request',type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;v=json.loads((DATA/'VALIDATION.json').read_bytes())
  if v['status']!='PASS_ORIGINAL_FASTMNN129_COMPLETE_PREPARED_CORRECTION_AND_NATIVE_AUDITS' or v['original_source_patched'] or v['paper_patch_recovered'] or v['whole_tool_qualified'] or v['whole_goal_complete'] or v['new_tool_credit']:raise ValueError('Unsupported qualification scope')
  for n,wanted in v['sources'].items():
   if pin(ROOT/n)!=wanted:raise ValueError('Qualified source differs: '+n)
  if pin(DATA/'controls.zip')!=v['archive']:raise ValueError('Complete archive integrity differs')
  evidence=out/'evidence';evidence.mkdir();objects=out/'objects';objects.mkdir()
  with zipfile.ZipFile(DATA/'controls.zip') as z:
   members=z.infolist()
   if len({m.filename for m in members})!=len(members) or len(members)>10000 or sum(m.file_size for m in members)>536870912:raise ValueError('Complete archive capacity/population differs')
   if z.getinfo('LAYOUT.json').file_size>8388608:raise ValueError('Complete logical layout exceeds capacity')
   layout=json.loads(z.read('LAYOUT.json'))
   if set(layout)!={'schema','files'} or layout['schema']!='FASTMNN129_COMPLETE_MODEL_OBJECTS_V1' or layout['files']!=v['archive_members']:raise ValueError('Complete logical archive layout differs')
   files=layout['files'];expected={}
   if len(files)>20000 or sum(r['bytes'] for r in files.values())>536870912:raise ValueError('Restored complete population exceeds capacity')
   for name,row in files.items():
    sha=row['sha256'];size=row['bytes']
    if len(sha)!=64 or any(c not in '0123456789abcdef' for c in sha) or type(size) is not int or not 0<=size<=67108864:raise ValueError('Invalid complete object fingerprint')
    if sha in expected and expected[sha]!=size:raise ValueError('Conflicting complete object identity')
    expected[sha]=size
   if set(z.namelist())!={'LAYOUT.json',*('objects/'+sha for sha in expected)}:raise ValueError('Missing/extra complete archive object')
   for sha,size in expected.items():
    m=z.getinfo('objects/'+sha)
    if m.is_dir() or m.file_size!=size or (m.external_attr>>16)&0o170000==0o120000:raise ValueError('Unsafe archive object')
    with z.open(m) as f:raw=f.read(67108865)
    if len(raw)!=size or hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Complete object content differs')
    (objects/sha).write_bytes(raw)
   for name,row in files.items():
    if '\\' in name or name.startswith('/') or any(p in ['','.','..'] for p in name.split('/')) or any(ord(c)<32 or ord(c)==127 for c in name):raise ValueError('Unsafe restored logical member')
    target=(evidence/name).resolve()
    if not target.is_relative_to(evidence):raise ValueError('Archive escapes owned output')
    target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(objects/row['sha256'],target)
  compare_complete(evidence)
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=300);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);calls.append(dict(label=label,command=command,exit_code=r.returncode));save(out/(label+'.process.json'),calls[-1])
   if r.returncode==3:raise OSError('Required replay prerequisite unavailable at '+label+'; read diagnostics')
   if r.returncode:raise ValueError('Saved FastMNN matrix/protocol replay failed at '+label+'; read diagnostics')
  run('build',[sys.executable,ROOT/'tools/literature/fastmnn129/build.py','--java-bin',a.java_bin.resolve(),'--out',out/'build']);java=a.java_bin.resolve()/('java.exe' if os.name=='nt' else 'java');jar=out/'build/fastmnn129-workflow-development.jar'
  args=[sys.executable,ROOT/'tools/literature/fastmnn129/test_protocol.py','--java',java,'--jar',jar,'--reference',evidence/'bank','--factors',evidence/'factors/worker-result.json','--request',evidence/'bank/default/request.json','--legacy-result',evidence/'legacy/worker-result.json','--out',out/'packaged-controls']
  if a.runtime_request:args.extend(['--runtime-request',a.runtime_request.resolve()])
  run('packaged-controls',args)
  tested=json.loads((out/'packaged-controls/COMPLETED.json').read_bytes())
  count=42+(1 if os.name=='nt' else 0)+(5 if a.runtime_request else 0)
  if tested['packaged_checks']!=count or tested['complete_successful_original_cases']!=17 or tested['complete_lazy_factor_cases']!=1 or not tested['legacy_original_checked']:raise ValueError('Incomplete complete-model/refusal population')
  if any(pin(ROOT/n)!=wanted for n,wanted in v['sources'].items()) or pin(DATA/'controls.zip')!=v['archive']:raise ValueError('Qualified source/archive changed during replay')
  save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_SAVED_FASTMNN129_MODEL_AND_REFUSAL_COMPARISONS',calls=calls,complete_successful_original_models=17,original_failure_cases=2,packaged_checks=count,original_fitting_repeated=False,RDS_reloaded=False,producer_authenticated=False,new_tool_credit=0,whole_goal_complete=False));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('PASS_COMPLETE_SAVED_FASTMNN129_MODEL_AND_REFUSAL_COMPARISONS',flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2
  if owned:save(out/'FAILURE.json',dict(status='SKIP3_REPLAY_UNAVAILABLE' if code==3 else 'FAIL2_FASTMNN129_SAVED_MODEL_COMPARISON',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION));(out/'WARNINGS_FAILURE.md').write_text(repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print('SKIP3' if code==3 else 'FAIL2',repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
