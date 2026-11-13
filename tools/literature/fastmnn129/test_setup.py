#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual setup refusals before any compiler or analysis is launched."""
from pathlib import Path
import argparse,json,shutil,subprocess,sys
HERE=Path(__file__).resolve().parent
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',required=True,type=Path);p.add_argument('--source-root',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
 wrong=out/'wrong-base';wrong.mkdir();shutil.copyfile(a.base/'pixi.toml',wrong/'pixi.toml');(wrong/'pixi.lock').write_text('invalid dependency lock\n')
 corrupt=out/'corrupt-source';shutil.copytree(a.source_root.resolve(),corrupt);path=corrupt/'R/fastMNN.R';path.write_bytes(path.read_bytes()+b'\n# Deliberately corrupted source-control donor\n')
 crlf=out/'CRLF-source';shutil.copytree(a.source_root.resolve(),crlf)
 for member in crlf.rglob('*'):
  if member.is_file():member.write_bytes(member.read_bytes().replace(b'\n',b'\r\n'))
 cases=[('CRLF-source-refused',['--base',a.base,'--source-root',crlf,'--allow-install'],2),('installation-forbidden',['--base',a.base,'--source-root',a.source_root],3),('download-forbidden',['--base',a.base,'--allow-install'],3),('base-unavailable',['--base',out/'absent-base','--source-root',a.source_root,'--allow-install'],3),('wrong-dependency-lock',['--base',wrong,'--source-root',a.source_root,'--allow-install'],2),('source-corruption',['--base',a.base,'--source-root',corrupt,'--allow-install'],2),('base-download-permission-absent',['--provision-base','--allow-install'],3)]
 rows=[]
 for name,args,expected in cases:
  target=out/name;command=list(map(str,[sys.executable,HERE/'setup.py','--out',target,*args]));result=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=120);(out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr)
  if result.returncode!=expected or b'Required action:' not in result.stdout+result.stderr:raise ValueError('Setup refusal differs: '+name)
  failure=json.loads((target/'FAILURE.json').read_bytes())
  if failure['analysis_admitted'] or (target/'original-install.process.json').exists() or (target/'R-lib').exists():raise ValueError('Refused setup launched compilation/analysis: '+name)
  if not (target/'WARNINGS_FAILURE.md').is_file():raise ValueError('Prominent setup recovery instructions absent')
  rows.append(dict(label=name,command=command,exit_code=result.returncode,expected_exit=expected,compiler_launched=False,analysis_launched=False))
 save(out/'COMPLETED.json',dict(status='PASS_SEVEN_ACTUAL_FASTMNN_SETUP_REFUSALS',calls=rows,analysis_admitted=False,whole_goal_complete=False));print('PASS_SEVEN_ACTUAL_FASTMNN_SETUP_REFUSALS',flush=True)
if __name__=='__main__':main()
