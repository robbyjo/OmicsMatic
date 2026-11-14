#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit an already provisioned original runtime. Installs/downloads nothing."""
from pathlib import Path
import argparse,os,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,PROFILE,ACTION,Unavailable,environment,inventory,load,pin,save,same,verify_source

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for name in ['r-base','r-library','r-source','python-base','out']:parser.add_argument('--'+name,required=True,type=Path)
 args=parser.parse_args();out=args.out.absolute();owned=False;calls=[]
 try:
  if sys.platform!='linux':raise Unavailable('Use the original Linux/WSL runtime')
  paths=[args.r_base,args.r_library,args.r_source,args.python_base]
  if any(p.absolute()!=p.resolve() for p in [out,*paths]):raise ValueError('Canonical NEW output and runtime paths required')
  if any(out.is_relative_to(p) or p.is_relative_to(out) for p in [HERE,*paths]):raise ValueError('Readiness output overlaps protected source/runtime')
  out.mkdir(parents=True,exist_ok=False);owned=True
  for name in ['owned-home','tmp','matplotlib-cache','numba-cache','unused-external-cache']:(out/name).mkdir()
  rprefix=args.r_base/'.pixi/envs/default'
  for name in ['pixi.toml','pixi.lock']:same(args.r_base/name,pin(HERE/('reference-'+name)))
  verify_source(args.r_source)
  ready=dict(r_prefix=str(rprefix),library=str(args.r_library),source_root=str(args.r_source),backend=str(args.r_library/'velociraptor/basilisk/env'),
   standalone_python=str(args.python_base/'bin/python3.12'),rscript=str(rprefix/'bin/Rscript'),base_library=str(rprefix/'lib/R/library'),
   runtime_roots=dict(R_prefix=str(rprefix),private_library=str(args.r_library),standalone_Python=str(args.python_base)))
  for name in [ready['rscript'],ready['standalone_python'],str(Path(ready['backend'])/'bin/python')]:
   if not Path(name).is_file() or not os.access(name,os.X_OK):raise Unavailable('Required original runtime executable missing: '+name)
  sources=['Runtime.py','AuditRuntime.R','CheckRuntime.R','CheckPython.py','adopt_runtime.py','source-profile.json','reference-pixi.toml','reference-pixi.lock','reference-python-constraints.txt']
  source_pins={name:pin(HERE/name) for name in sources};env=environment(ready,out)
  def run(label,command):
   command=list(map(str,command));start=time.monotonic()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,env=env,cwd=out,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    save(out/'LIVE.json',dict(label=label,pid=child.pid,command=command))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_ORIGINAL_READINESS_CHILD_RUNNING',label,child.pid,round(time.monotonic()-start),flush=True)
   row=dict(label=label,command=command,exit_code=code,elapsed_seconds=time.monotonic()-start);calls.append(row);save(out/'PROCESSES.json',calls)
   if code:raise ValueError('Original readiness failed: '+label+'; retain complete original logs and objects')
  run('original-R-readiness',[ready['rscript'],'--vanilla',HERE/'CheckRuntime.R',HERE/'AuditRuntime.R',ready['library'],ready['source_root'],ready['backend'],out])
  run('original-Python-readiness',[Path(ready['backend'])/'bin/python','-B',HERE/'CheckPython.py',out])
  run('original-pip-check',[Path(ready['backend'])/'bin/python','-B','-m','pip','check'])
  run('original-pip-freeze',[Path(ready['backend'])/'bin/python','-B','-m','pip','freeze','--all'])
  if (out/'original-pip-freeze.stdout').read_bytes()!=(HERE/'reference-python-constraints.txt').read_bytes():raise ValueError('Complete original Python graph differs')
  print('HASHING_COMPLETE_PRIVATE_RUNTIME_FILE_AND_LINK_POPULATIONS',flush=True)
  first=inventory(ready['runtime_roots']);second=inventory(ready['runtime_roots'])
  if first!=second:raise ValueError('Original runtime changed during readiness capture; retain both inventories')
  for name,wanted in source_pins.items():same(HERE/name,wanted)
  verify_source(ready['source_root'])
  ready.update(status='READY_PRIVATE_ORIGINAL_VELOCIRAPTOR120_NOT_ANALYSIS',source_profile=pin(PROFILE),preparation_sources=source_pins,
   runtime_inventory=first,readiness=pin(out/'readiness.json'),python_readiness=pin(out/'python-readiness.json'),
   original_source_functions=11,registered_methods=7,Python_version='3.12.10',R_version='4.5.3',version='1.20.0',scvelo_version='0.3.3',
   analysis_executed=False,installation_executed=False,download_executed=False,new_family_credit=0,whole_goal_complete=False)
  save(out/'READY.json',ready);(out/'WARNINGS.md').write_text('# Original runtime readiness only\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
  print(ready['status'],sum(map(len,first.values())),'file/link identities',flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
  result=dict(status='SKIP3_ORIGINAL_RUNTIME_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_RUNTIME_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),calls=calls,result_admitted=False,required_action=ACTION)
  if owned:
   for name,value in [('FAILURE.json',result)]:
    try:save(out/name,value)
    except OSError as recording:print('FAIL2_STATUS_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
   try:(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf-8',newline='\n')
   except OSError as recording:print('FAIL2_WARNING_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
  print(result['status'],repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
