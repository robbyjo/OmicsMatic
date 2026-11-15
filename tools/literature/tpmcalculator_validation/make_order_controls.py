#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture 100,000 actual GNU string hashes and map iteration order, including rehashes."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,zipfile
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--archive',required=True,type=Path);a=p.parse_args()
root=Path(__file__).resolve().parents[3];out=a.out.resolve();out.mkdir(exist_ok=False);prefix=a.build.resolve()/'runtime/.pixi/envs/default'
env=dict(os.environ,LD_LIBRARY_PATH=str(prefix/'lib'),PATH=str(prefix/'bin')+':/usr/bin:/bin')
def pin(path):
 b=path.read_bytes();return dict(bytes=len(b),sha256=hashlib.sha256(b).hexdigest())
def run(name,command):
 result=subprocess.run(list(map(str,command)),cwd=out,env=env,capture_output=True)
 (out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr)
 (out/(name+'.json')).write_text(json.dumps(dict(command=list(map(str,command)),exit_code=result.returncode),indent=2)+'\n')
 if result.returncode:raise RuntimeError((name,result.returncode,result.stderr))
compiler=prefix/'bin/x86_64-conda_cos6-linux-gnu-g++';source=Path(__file__).with_name('OriginalOrder.cpp')
shutil.copyfile(source,out/source.name);shutil.copyfile(Path(__file__),out/Path(__file__).name)
run('compiler-version',[compiler,'--version']);run('compile',[compiler,'-std=c++14','-O2',source,'-o',out/'OriginalOrder'])
run('actual-order',[out/'OriginalOrder'])
profile=dict(status='PASS_ACTUAL_STANDARD_LIBRARY_COMPARISON_DATA_NOT_NATIVE_QUALIFICATION',keys=100000,source=pin(source),binary=pin(out/'OriginalOrder'),
 actual_libstdcpp=pin((prefix/'lib/libstdc++.so.6').resolve()),dependency_lock=pin(a.build/'runtime/pixi.lock'),original_algorithms_modified=False)
(out/'PROFILE.json').write_text(json.dumps(profile,indent=2)+'\n')
a.archive.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for f in sorted(out.iterdir()):
  if f.is_file() and f.name!='OriginalOrder':z.write(f,f.name)
print(json.dumps(dict(status=profile['status'],archive=pin(a.archive)),indent=2))
