#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Restore complete saved Monocle1.3.1 controls and replay native statistics; repeats no R fitting."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3];DATA=ROOT/'docs/literature/monocle131-models-development'
ACTION='Required action: retain all diagnostics. Supply permitted Java21/25 and exact JDistlib0.10.2 with --jdistlib-jar or explicitly permitted --download-jdistlib, and NEW output. Missing/forbidden prerequisites are SKIP3; resolve FAIL2 archive/source/numeric contradictions without altering original controls. This replays saved coefficient/Pearson comparisons and packaged refusals, not original R model fitting, selection on study data, RDS reload or producer authentication. Original analysis separately requires the qualified private Linux R4.3.3/Monocle1.3.1 runtime.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);g=p.add_mutually_exclusive_group(required=True);g.add_argument('--jdistlib-jar',type=Path);g.add_argument('--download-jdistlib',action='store_true');a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Require canonical NEW output')
  out.mkdir(parents=True,exist_ok=False);owned=True;v=json.loads((DATA/'VALIDATION.json').read_bytes())
  for n,wanted in v['sources'].items():
   if pin(ROOT/n)!=wanted:raise ValueError('Qualified source differs: '+n)
  if pin(DATA/'controls.zip')!=v['archive']:raise ValueError('Saved complete archive integrity differs')
  evidence=out/'evidence';evidence.mkdir();objects=out/'objects';objects.mkdir()
  with zipfile.ZipFile(DATA/'controls.zip') as z:
   members=z.infolist()
   if len({m.filename for m in members})!=len(members) or len(members)>10000 or sum(m.file_size for m in members)>536870912:raise ValueError('Complete archive capacity/population differs')
   if z.getinfo('LAYOUT.json').file_size>8388608:raise ValueError('Complete logical layout exceeds capacity')
   layout=json.loads(z.read('LAYOUT.json'))
   if set(layout)!={'schema','files'} or layout['schema']!='MONOCLE131_COMPLETE_MODELS_OBJECTS_V1' or layout['files']!=v['archive_members']:raise ValueError('Complete logical archive layout differs')
   files=layout['files'];expected={}
   if len(files)>20000 or sum(r['bytes'] for r in files.values())>536870912:raise ValueError('Restored complete control capacity exceeded')
   for name,row in files.items():
    sha=row['sha256'];size=row['bytes']
    if len(sha)!=64 or any(c not in '0123456789abcdef' for c in sha) or type(size) is not int or not 0<=size<=67108864:raise ValueError('Invalid complete object fingerprint')
    if sha in expected and expected[sha]!=size:raise ValueError('Conflicting complete object identity')
    expected[sha]=size
   if set(z.namelist())!={'LAYOUT.json',*('objects/'+sha for sha in expected)}:raise ValueError('Missing/extra complete archive object')
   for sha,size in expected.items():
    m=z.getinfo('objects/'+sha)
    if m.is_dir() or m.file_size!=size or (m.external_attr>>16)&0o170000==0o120000:raise ValueError('Unsafe object')
    with z.open(m) as f:raw=f.read(67108865)
    if len(raw)!=size or hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Complete object content differs')
    (objects/sha).write_bytes(raw)
   for name,row in files.items():
    if '\\' in name or name.startswith('/') or any(x in ['','.','..'] for x in name.split('/')) or any(ord(c)<32 or ord(c)==127 for c in name):raise ValueError('Unsafe restored member')
    target=(evidence/name).resolve()
    if not target.is_relative_to(evidence):raise ValueError('Archive path escapes owned output')
    target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(objects/row['sha256'],target)
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=300);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);calls.append(dict(label=label,command=command,exit_code=r.returncode));save(out/(label+'.process.json'),calls[-1])
   if r.returncode==3:raise OSError('Required replay prerequisite unavailable at '+label+'; read retained diagnostics')
   if r.returncode:raise ValueError('Saved-control replay failed at '+label+'; read retained diagnostics')
  dependency=['--jdistlib-jar',a.jdistlib_jar.resolve()] if a.jdistlib_jar else ['--download-jdistlib']
  run('build',[sys.executable,ROOT/'tools/literature/monocle131_models/build.py','--java-bin',a.java_bin.resolve(),'--out',out/'build',*dependency]);java=a.java_bin.resolve()/('java.exe' if os.name=='nt' else 'java');jar=out/'build/monocle131-models-development.jar'
  run('packaged-controls',[sys.executable,ROOT/'tools/literature/monocle131_models/test_protocol.py','--java',java,'--jar',jar,'--models',evidence/'reference/model-cases','--pearson',evidence/'reference/pearson/PEARSON-CONTROLS.json','--request',evidence/'reference/request.json','--out',out/'packaged-controls'])
  tested=json.loads((out/'packaged-controls/COMPLETED.json').read_bytes())
  if len(tested['calls'])!=46 or tested['complete_original_model_analyses']!=10 or tested['original_pearson_cases']!=16:raise ValueError('Incomplete saved-model/Pearson/refusal replay population')
  save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_SAVED_MONOCLE131_STATISTICS_AND_PEARSON_COMPARISONS',calls=calls,complete_original_model_analyses=10,original_pearson_cases=16,packaged_checks=46,original_fitting_repeated=False,RDS_reloaded=False,producer_authenticated=False,new_tool_credit=0,whole_goal_complete=False));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('PASS_COMPLETE_SAVED_MONOCLE131_STATISTICS_AND_PEARSON_COMPARISONS',flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2
  if owned:save(out/'FAILURE.json',dict(status='SKIP3_REPLAY_UNAVAILABLE' if code==3 else 'FAIL2_MONOCLE131_SAVED_CONTROL_COMPARISON',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION));(out/'WARNINGS_FAILURE.md').write_text(repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print('SKIP3' if code==3 else 'FAIL2',repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
