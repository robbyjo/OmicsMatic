#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual permission/source refusals before any original compiler or analysis starts."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,sys
HERE=Path(__file__).resolve().parent
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',required=True,type=Path);p.add_argument('--source-root',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
 before={str(p):pin(p) for p in [a.base/'pixi.toml',a.base/'pixi.lock',*[x for x in a.source_root.rglob('*') if x.is_file()]]}
 wrong=out/'wrong-base';wrong.mkdir();shutil.copyfile(a.base/'pixi.toml',wrong/'pixi.toml');(wrong/'pixi.lock').write_text('invalid lock\n')
 corrupt=out/'corrupt-source';shutil.copytree(a.source_root.resolve(),corrupt);path=corrupt/'src/jaccard_coeff.cpp';path.write_bytes(path.read_bytes()+b'\n// Deliberately corrupted source donor\n')
 cases=[('installation-forbidden',['--base',a.base,'--source-root',a.source_root],3),('download-forbidden',['--base',a.base,'--allow-install'],3),('base-unavailable',['--base',out/'absent-base','--source-root',a.source_root,'--allow-install'],3),('wrong-lock',['--base',wrong,'--source-root',a.source_root,'--allow-install'],2),('source-corruption',['--base',a.base,'--source-root',corrupt,'--allow-install'],2),('base-download-permission-absent',['--provision-base','--allow-install'],3)]
 rows=[]
 for name,args,expected in cases:
  target=out/name;command=list(map(str,[sys.executable,HERE/'setup.py','--out',target,*args]));r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=120);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
  if r.returncode!=expected or b'Required action:' not in r.stdout+r.stderr:raise ValueError('Setup refusal differs: '+name)
  failure=json.loads((target/'FAILURE.json').read_bytes())
  if failure['analysis_admitted'] or (target/'original-install.process.json').exists() or (target/'R-lib').exists():raise ValueError('Refused setup launched compiler/analysis: '+name)
  if not (target/'WARNINGS_FAILURE.md').is_file():raise ValueError('Prominent setup recovery instructions absent')
  rows.append(dict(label=name,command=command,exit_code=r.returncode,expected_exit=expected,compiler_launched=False,analysis_launched=False))
 for name,protected,args in [('source-overlap',a.source_root,['--base',a.base,'--source-root',a.source_root,'--allow-install']),('base-overlap',a.base,['--base',a.base,'--source-root',a.source_root,'--allow-install'])]:
  target=protected.resolve()/'rphenograph-refused-overlap-output';assert not target.exists();command=list(map(str,[sys.executable,HERE/'setup.py','--out',target,*args]));r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=60);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
  if r.returncode!=2 or b'overlaps' not in r.stdout+r.stderr or target.exists():raise ValueError('Protected source/base overlap was modified')
  rows.append(dict(label=name,command=command,exit_code=r.returncode,expected_exit=2,compiler_launched=False,analysis_launched=False,protected_output_created=False))
 if any(pin(Path(n))!=value for n,value in before.items()):raise ValueError('A protected input changed during refused installation')
 save(out/'COMPLETED.json',dict(status='PASS_EIGHT_ACTUAL_RPHENOGRAPH099_SETUP_REFUSALS',calls=rows,analysis_admitted=False,whole_goal_complete=False));print('PASS_EIGHT_ACTUAL_RPHENOGRAPH099_SETUP_REFUSALS',flush=True)
if __name__=='__main__':main()
