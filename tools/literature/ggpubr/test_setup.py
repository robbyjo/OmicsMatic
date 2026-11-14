#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check version-specific installation refusals without changing the protected dependency base."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,time
HERE=Path(__file__).resolve().parent
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def pin(p):
 raw=p.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',choices=['0.4.0','0.6.0'],required=True);p.add_argument('--base',type=Path,required=True);p.add_argument('--source-cache',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=a.base.resolve();cache=a.source_cache.resolve()
 for protected in [base,cache,HERE]:
  if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected base/cache/source')
 out.mkdir(parents=True,exist_ok=False);protected_files=[HERE/'provider.py']+([base/'pixi.toml',base/'pixi.lock'] if sys.platform=='linux' else []);before={str(p):pin(p) for p in protected_files};calls=[]
 def run(name,flags,expected,target=None):
  target=target or out/name;command=list(map(str,[sys.executable,HERE/'provider.py','--version',a.version,'--base',base,'--out',target,*flags]));start=time.time()
  r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
  (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
  row=dict(case=name,command=command,exit_code=r.returncode,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(name+'.process.json'),row)
  assert r.returncode==expected,(name,row,r.stdout[-1600:],r.stderr[-1600:])
  assert not (target/'READY.json').exists(),name
  assert not list(target.glob('install-*.process.json')),name
  if (target/'FAILURE.json').exists():assert 'Required action:' in (target/'WARNINGS_FAILURE.md').read_text()
  print(name,'expected exit',expected,flush=True)
 if sys.platform!='linux':run('unsupported-native-platform',['--allow-install','--allow-download'],3)
 else:
  run('installation-forbidden',['--source-cache',cache],3)
  run('download-forbidden',['--allow-install'],3)
  run('missing-runtime-base',['--allow-install','--source-cache',cache,'--base',out/'absent-base'],3)
  empty=out/'empty-cache';empty.mkdir();run('missing-source-archives',['--allow-install','--source-cache',empty],3)
  fake=out/'changed-lock-base';fake.mkdir()
  for name in ['pixi.toml','pixi.lock']:shutil.copyfile(base/name,fake/name)
  with (fake/'pixi.lock').open('ab') as f:f.write(b'\n# isolated deliberate lock mismatch\n')
  run('changed-locked-profile',['--allow-install','--source-cache',cache,'--base',fake],2)
  damaged=out/'damaged-source-cache';damaged.mkdir();profile=json.loads((HERE/'source-profiles.json').read_bytes())
  for key in ['ggplot2-3.4.3','rstatix-0.7.2','ggpubr-'+a.version]:
   name=profile['profiles'][key]['archive_name'];shutil.copyfile(cache/name,damaged/name)
  first=profile['profiles']['ggplot2-3.4.3']['archive_name']
  with (damaged/first).open('ab') as f:f.write(b'isolated deliberate archive mismatch')
  run('changed-original-archive',['--allow-install','--source-cache',damaged],2)
  overlap=base/'refused-provider-child';assert not overlap.exists();run('protected-runtime-overlap',['--allow-install','--source-cache',cache],2,overlap);assert not overlap.exists()
  run('unsupported-literal-version',['--version','9.9.9','--allow-install','--source-cache',cache],2)
 assert all(pin(Path(p))==wanted for p,wanted in before.items())
 result=dict(status='PASS_GGPUBR_SETUP_PERMISSION_PREREQUISITE_SOURCE_AND_VERSION_REFUSALS',version=a.version,controls=len(calls),calls=calls,
  protected_base_and_sources_unchanged=True,original_package_installation_performed=False,dependent_analysis_skipped_or_unadmitted=True,new_tool_credit=0,whole_goal_complete=False)
 save(out/'COMPLETED.json',result);print(result['status'],flush=True)
if __name__=='__main__':main()
