#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify setup permissions, missing prerequisites and protected-path/source refusals."""
from pathlib import Path
import argparse,json,os,subprocess,sys,traceback
HERE=Path(__file__).resolve().parent
ACTION='Required action: provide permitted JDK21/25, the complete pristine original source tree and NEW output. This runs setup refusal controls; it does not launch image analysis. Missing/forbidden installation skips dependent work with SKIP3. Keep all diagnostics; never overwrite original compiler, source or prior evidence.'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',type=Path,required=True);p.add_argument('--source-root',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,flags,expected):
   command=list(map(str,[sys.executable,HERE/'provider.py',*flags]));r=subprocess.run(command,env=env,capture_output=True,stdin=subprocess.DEVNULL,timeout=120);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode!=expected or b'Required action:' not in r.stdout+r.stderr:raise ValueError('Setup refusal differs: '+label)
  base=['--java-bin',a.java_bin.resolve()];source=['--source-root',a.source_root.resolve()]
  run('build-forbidden',[*base,*source,'--out',out/'build-forbidden'],3)
  run('download-forbidden',[*base,'--allow-build','--out',out/'download-forbidden'],3)
  run('missing-jdk',['--java-bin',out/'absent-jdk',*source,'--allow-build','--out',out/'missing-jdk'],3)
  run('missing-source',[*base,'--source-root',out/'absent-source','--allow-build','--out',out/'missing-source'],3)
  corrupt=out/'incomplete-source';corrupt.mkdir();(corrupt/'README.txt').write_text('incomplete source fixture\n',encoding='utf8');run('incomplete-source',[*base,'--source-root',corrupt,'--allow-build','--out',out/'incomplete-source-output'],2)
  run('source-overlap',[*base,*source,'--allow-build','--out',a.source_root.resolve()/'not-created-by-refusal'],2)
  run('compiler-overlap',[*base,*source,'--allow-build','--out',a.java_bin.resolve()/'not-created-by-refusal'],2)
  for name in ['build-forbidden','download-forbidden','missing-jdk','missing-source','incomplete-source-output']:
   v=json.loads((out/name/'FAILURE.json').read_bytes())
   if any(c['label'] in ['original-javac','original-jar','readiness'] for c in v['calls']):raise ValueError('Setup refusal launched dependent compiler/readiness')
  save(out/'COMPLETED.json',dict(status='PASS_IMAGEJ154T_SETUP_POLICY_SOURCE_AND_PATH_REFUSALS',calls=calls,setup_refusals=7,image_analysis_executed=False,original_scientific_compiler_launched=False));print('PASS_IMAGEJ154T_SETUP_POLICY_SOURCE_AND_PATH_REFUSALS',flush=True);return 0
 except Exception as e:
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_IMAGEJ154T_SETUP_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION))
  print('FAIL2',repr(e),'\n'+ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
