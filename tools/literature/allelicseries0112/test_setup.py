#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual setup policy/missing-runtime/corrupt-source refusals without mutating a prior runtime."""
from pathlib import Path
import argparse,hashlib,json,subprocess,sys,traceback
HERE=Path(__file__).resolve().parent
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--r-prefix',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  for protected in [HERE,a.r_prefix.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source/runtime')
  out.mkdir(parents=True,exist_ok=False);owned=True
  def run(label,flags,expected):
   dest=out/label;command=list(map(str,[sys.executable,HERE/'provider.py','--r-prefix',a.r_prefix,'--out',dest,*flags]));r=subprocess.run(command,capture_output=True,stdin=subprocess.DEVNULL,timeout=90);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);calls.append(row);save(out/(label+'.process.json'),row);assert r.returncode==expected,label
   if dest.exists():
    failure=json.loads((dest/'FAILURE.json').read_bytes());assert not failure['analysis_admitted'];assert not (dest/'READY.json').exists()
   return dest
  run('install-policy-skip',[],3)
  if sys.platform=='linux':
   run('source-permission-skip',['--allow-install'],3)
   missing=out/'absent-prefix'
   command=[sys.executable,HERE/'provider.py','--r-prefix',missing,'--allow-install','--allow-download','--out',out/'missing-runtime']
   r=subprocess.run(list(map(str,command)),capture_output=True,stdin=subprocess.DEVNULL,timeout=90);(out/'missing-runtime.stdout').write_bytes(r.stdout);(out/'missing-runtime.stderr').write_bytes(r.stderr);assert r.returncode==3;calls.append(dict(label='missing-runtime',command=list(map(str,command)),exit_code=r.returncode,expected_exit=3))
   cache=out/'bad-cache';cache.mkdir();row=json.loads((HERE/'source-profile.json').read_bytes())['private_dependency_sources'][0];(cache/row['archive']).write_bytes(b'Deliberately corrupted source archive; no installation permitted by this control.\n')
   dest=run('corrupt-first-source',['--allow-install','--source-cache',cache],2)
   assert not any((dest/'library').iterdir()),'Corrupted source was installed'
  else:run('unsupported-platform-skip',['--allow-install','--allow-download'],3)
  result=dict(status='PASS_ORIGINAL_ALLELICSERIES_SETUP_REFUSALS',calls=calls,controls=len(calls),actual_source_installs=0,paper_analysis_executed=False,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],'controls='+str(len(calls)),flush=True);return 0
 except Exception as e:
  value=dict(status='FAIL2_ORIGINAL_SETUP_REFUSALS',cause=repr(e),traceback=traceback.format_exc(),calls=calls)
  if owned:save(out/'FAILURE.json',value)
  print(value['status'],repr(e),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
