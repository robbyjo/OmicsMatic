#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Strictly compile native burden methods and compare every retained original reference value."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,traceback
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent

def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--jdistlib',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  for protected in [ROOT/'src',HERE,a.reference.resolve(),a.java_bin.resolve(),a.jdistlib.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps source/reference/runtime')
  out.mkdir(parents=True,exist_ok=False);owned=True
  sources=[ROOT/'src/main/java/org/jlinalg/genetics/AllelicSeries0112.java',HERE/'NativeControl.java',HERE/'test_native.py',HERE/'OriginalControl.R',HERE/'source-profile.json']
  before={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
  for src in sources:
   dest=out/'source-snapshot'/src.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dest)
  save(out/'BEFORE.json',dict(sources=before,jdistlib=pin(a.jdistlib),reference={n:pin(a.reference/n) for n in ['native-inputs.tsv','native-original-results.tsv','complete-native-cases-and-original-objects.rds','complete-namespace-reference.rds','namespace-results.tsv']}))
  if pin(a.jdistlib)!=dict(bytes=43416685,sha256='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'):raise ValueError('Project JDistlib0.10.2 dependency differs')
  suffix='.exe' if os.name=='nt' else '';classes=out/'classes';classes.mkdir()
  def run(label,command):
   command=list(map(str,command));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=300);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);calls.append(dict(label=label,command=command,exit_code=r.returncode));save(out/(label+'.process.json'),calls[-1])
   if r.returncode:raise ValueError('Actual native/reference comparison failed at '+label+'; preserve full mismatch logs')
  run('compiler-version',[a.java_bin/('javac'+suffix),'-version']);run('runtime-version',[a.java_bin/('java'+suffix),'-version'])
  run('compile',[a.java_bin/('javac'+suffix),'--release','17','-Xlint:all','-Werror','-cp',a.jdistlib,'-d',classes,sources[0],sources[1]])
  run('complete-native',[a.java_bin/('java'+suffix),'-cp',str(classes)+os.pathsep+str(a.jdistlib),'NativeControl',a.reference/'native-inputs.tsv',a.reference/'native-original-results.tsv'])
  if any(pin(ROOT/n)!=value for n,value in before.items()):raise ValueError('Native/control source changed during comparison')
  result=dict(status='PASS_COMPLETE_ORIGINAL_ALLELICSERIES0112_NATIVE_BURDEN_VALUES',original_case_population=539,sources=before,calls=calls,original_full_COAST_or_SKATO_ported=False,whole_goal_complete=False,new_tool_credit=0);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_NATIVE_ALLELICSERIES_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_NATIVE_ALLELICSERIES_ORIGINAL_COMPARISON',cause=repr(e),traceback=traceback.format_exc(),calls=calls,analysis_admitted=False,required_action='Retain source snapshots, complete original objects and all mismatches; resolve the named source behavior or prerequisite and retry NEW paths. Do not shrink the test population, alter original inputs or replace original SKAT-O with generic semantics.')
  if owned:save(out/'FAILURE.json',v)
  print(v['status'],repr(e),flush=True);return code
if __name__=='__main__':raise SystemExit(main())
