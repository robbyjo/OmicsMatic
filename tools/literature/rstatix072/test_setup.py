#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Public provider policy/source/lock refusals; never alter the selected runtime or installed package."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,sys,time
HERE=Path(__file__).resolve().parent
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',type=Path,required=True);p.add_argument('--source-archive',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);calls=[]
 before={str(p):pin(p) for p in [a.base/'pixi.toml',a.base/'pixi.lock',a.source_archive]}
 def case(name,args,expected):
  command=list(map(str,[sys.executable,HERE/'provider.py','--base',a.base,'--out',out/name,*args]));start=time.time();r=subprocess.run(command,capture_output=True,stdin=subprocess.DEVNULL,timeout=180);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
  row=dict(case=name,command=command,exit_code=r.returncode,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(name+'.process.json'),row)
  assert r.returncode==expected,row
  failure=json.loads((out/name/'FAILURE.json').read_bytes());assert failure['calls']==[],name
  assert not (out/name/'READY.json').exists() and not (out/name/'original-install.process.json').exists(),name
 if sys.platform=='linux':
  case('install-forbidden',['--source-archive',a.source_archive],3)
  case('source-and-download-forbidden',['--allow-install'],3)
  case('missing-base',['--allow-install','--source-archive',a.source_archive,'--base',out/'absent-base'],3)
  fake=out/'wrong-base';fake.mkdir();shutil.copyfile(a.base/'pixi.toml',fake/'pixi.toml');(fake/'pixi.lock').write_bytes((a.base/'pixi.lock').read_bytes()+b'\n# deliberately invalid control\n')
  case('wrong-lock',['--allow-install','--source-archive',a.source_archive,'--base',fake],2)
  bad=out/'changed-original.tar.gz';data=bytearray(a.source_archive.read_bytes());data[-1]^=1;bad.write_bytes(data)
  case('wrong-original-source',['--allow-install','--source-archive',bad],2)
  case('missing-original-source',['--allow-install','--source-archive',out/'absent-source.tar.gz'],3)
 else:case('unsupported-platform',['--allow-install','--source-archive',a.source_archive],3)
 assert all(pin(Path(n))==wanted for n,wanted in before.items())
 result=dict(status='PASS_ORIGINAL_RSTATIX_PROVIDER_REFUSALS_WITHOUT_INSTALL_OR_ANALYSIS',controls=len(calls),calls=calls,protected_runtime_and_source_unchanged=True,original_analysis_executed=False,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True)
if __name__=='__main__':main()
