#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit an already installed private original runtime; do not install or analyze."""
from pathlib import Path
import argparse,os,subprocess,sys,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,pin,save,sources,inventory,source_tree,environment

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for name in ['prefix','source','out']:p.add_argument('--'+name,required=True,type=Path)
 a=p.parse_args();out=a.out.absolute();owned=False
 try:
  prefix=a.prefix.resolve();source=a.source.resolve()
  if out!=out.resolve() or any(out.is_relative_to(x) or x.is_relative_to(out) for x in [HERE,prefix,source]):raise ValueError('Use a NEW separate provider output')
  out.mkdir(exist_ok=False);owned=True
  if sys.platform!='linux':raise Unavailable('Selected original runtime requires Linux/WSL')
  source_tree(source)
  for name in ['pixi.toml','pixi.lock']:
   if pin(prefix/name)!=pin(HERE/('reference-'+name)):raise ValueError('Declared exact original dependency profile differs')
  ready=dict(schema='omicsmatic-original-doubletfinder206-runtime-v1',provider=str(out),prefix=str(prefix),r_prefix=str(prefix/'.pixi/envs/default'),library=str(prefix/'library'),base_library=str(prefix/'.pixi/envs/default/lib/R/library'),source=str(source),manifest=pin(prefix/'pixi.toml'),lock=pin(prefix/'pixi.lock'),runner_sources=sources())
  for name in ['home','tmp']:(out/name).mkdir()
  command=[str(Path(ready['r_prefix'])/'bin/Rscript'),'--vanilla',str(HERE/'AuditRuntime.R'),ready['library'],str(source),str(out/'namespace')]
  with (out/'audit.stdout').open('xb') as so,(out/'audit.stderr').open('xb') as se:child=subprocess.run(command,env=environment(ready,out),cwd=out,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
  save(out/'PROCESS.json',dict(command=command,exit_code=child.returncode))
  if child.returncode:raise Unavailable('Installed original namespace audit failed; inspect full audit logs')
  for key in ['r_prefix','library']:save(out/(key+'-files.json'),inventory(Path(ready[key])))
  ready['inventory_files']={key:pin(out/(key+'-files.json')) for key in ['r_prefix','library']}
  save(out/'READY.json',ready);save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_RUNTIME_AUDITED_NOT_ANALYSIS',provider=pin(out/'READY.json'),analysis_executed=False,new_family_credit=0,whole_goal_complete=False))
  (out/'WARNINGS.md').write_text('# Original private runtime audited; no analysis qualified\n\n'+ACTION+'\n',encoding='utf-8');print('PASS_ORIGINAL_RUNTIME_AUDITED_NOT_ANALYSIS',flush=True);return 0
 except BaseException as e:
  code=3 if isinstance(e,OSError) and not isinstance(e,FileExistsError) else 2
  if owned:save(out/'FAILURE.json',dict(status='SKIP3_RUNTIME_UNAVAILABLE' if code==3 else 'FAIL2_RUNTIME_UNADMITTED',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION))
  print('SKIP3_RUNTIME_UNAVAILABLE' if code==3 else 'FAIL2_RUNTIME_UNADMITTED',repr(e),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
