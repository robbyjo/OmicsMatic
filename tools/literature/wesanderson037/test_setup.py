#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test explicit installation permissions, actual private source installs and missing-runtime skips."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,time,traceback
HERE=Path(__file__).resolve().parent
ACTION='Required action: run in permitted Linux/WSL with the qualified R4.5.3, exact original source archive and NEW output. These controls install the original package only in new private libraries; they perform no paper analysis. SKIP3 skips unavailable dependent setup; FAIL2 preserves failures and admits no provider qualification. Preserve raw logs and correct the named cause before retrying NEW paths.'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--rscript',required=True,type=Path);p.add_argument('--source-archive',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True
  if os.name=='nt':raise OSError('These private original-provider controls use the qualified Linux/WSL R profile')
  rscript=a.rscript.resolve();archive=a.source_archive.resolve();paths=[HERE/'provider.py',HERE/'InstallOriginal.R',HERE/'source-profile.json',HERE/'build.py',HERE/'test_setup.py',archive,rscript];before={str(n):pin(n) for n in paths};save(out/'BEFORE.json',before)
  def run(label,command,expected):
   command=list(map(str,command));start=time.time();r=subprocess.run(command,cwd=out,stdin=subprocess.DEVNULL,capture_output=True,timeout=360);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode!=expected:raise ValueError('Public setup control failed: '+label)
  provider=[sys.executable,HERE/'provider.py'];common=['--rscript',rscript]
  cases=[('install-forbidden',[*common,'--source-archive',archive],3),('source-unavailable',[*common,'--allow-install'],3),('runtime-missing',['--rscript',out/'missing-Rscript','--source-archive',archive,'--allow-install'],3)]
  wrong=out/'unqualified-version-Rscript';wrong.write_text('#!/bin/sh\nprintf "R scripting front-end version 4.4.0\\n"\n',encoding='utf8',newline='\n');wrong.chmod(0o755)
  cases.append(('runtime-unqualified',['--rscript',wrong,'--source-archive',archive,'--allow-install'],3))
  corrupt=out/'controlled-corrupt-source.tar.gz';raw=bytearray(archive.read_bytes());raw[-1]^=1;corrupt.write_bytes(raw);cases.append(('source-corrupt',[*common,'--source-archive',corrupt,'--allow-install'],2))
  for label,args,code in cases:
   target=out/label;run(label,[*provider,*args,'--out',target],code);failure=json.loads((target/'FAILURE.json').read_bytes())
   if any(r['label']=='install' for r in failure['calls']) or (target/'library').exists() or not (target/'WARNINGS_FAILURE.md').is_file():raise ValueError('Refused prerequisite launched dependent installation or omitted warning')
  existing=out/'existing-output';existing.mkdir();(existing/'marker.txt').write_text('preserve exact earlier bytes',encoding='utf8');marker=pin(existing/'marker.txt');run('existing-output',[*provider,*common,'--source-archive',archive,'--allow-install','--out',existing],3)
  if pin(existing/'marker.txt')!=marker or len(list(existing.iterdir()))!=1:raise ValueError('Existing output changed')
  run('source-overlap',[*provider,*common,'--source-archive',archive,'--allow-install','--out',archive],2)
  missingJdk=out/'missing-JDK';run('compiler-missing',[sys.executable,HERE/'build.py','--java-bin',missingJdk,'--out',out/'compiler-missing'],3)
  fakeJdk=out/'unqualified-JDK';fakeJdk.mkdir()
  for name,version in [('java','openjdk version "17.0.1"'),('javac','javac 17.0.1')]:
   f=fakeJdk/name;f.write_text('#!/bin/sh\nprintf \'%s\\n\' \''+version+'\'\n',encoding='utf8',newline='\n');f.chmod(0o755)
  run('compiler-unqualified',[sys.executable,HERE/'build.py','--java-bin',fakeJdk,'--out',out/'compiler-unqualified'],3)
  for label,args in [('local-install',['--source-archive',archive]),('permitted-download',['--allow-download'])]:
   target=out/label;run(label,[*provider,*common,*args,'--allow-install','--out',target],0);ready=json.loads((target/'READY.json').read_bytes())
   if ready['status']!='READY_PRIVATE_ORIGINAL_WESANDERSON037_NO_ANALYSIS' or ready['original_source_modified'] or ready['paper_analysis_executed'] or len(ready['source_files'])!=13:raise ValueError('Actual complete private installation differs')
  if any(pin(Path(n))!=wanted for n,wanted in before.items()):raise ValueError('Protected original runtime/source/helper changed during setup controls')
  result=dict(status='PASS_WESANDERSON037_PRIVATE_ORIGINAL_SETUP_AND_REFUSALS',source_pins=before,calls=calls,actual_private_original_installs=2,actual_official_source_downloads=1,setup_refusals=9,dependent_installation_launched_for_refusals=False,paper_analysis_executed=False,existing_runtime_modified=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_ORIGINAL_SETUP_CONTROL_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_WESANDERSON037_SETUP_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
