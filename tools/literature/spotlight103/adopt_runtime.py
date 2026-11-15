#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit a source-built SPOTlight1.0.3 runtime without installing or fetching data."""
from pathlib import Path
import argparse,subprocess,sys,traceback
sys.dont_write_bytecode=True
from Runtime import *
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--workspace',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
 p.add_argument('--source',action='append',required=True,help='PACKAGE=/absolute/pristine/source; supply every profile package');a=p.parse_args();owned=False;out=a.out.absolute()
 try:
  paths={}
  for entry in a.source:
   name,value=entry.split('=',1)
   if name in paths:raise ValueError('Duplicate source package')
   paths[name]=str(Path(value).resolve())
  workspace=a.workspace.resolve();out=check_output(out,[HERE,workspace,*paths.values()]);out.mkdir(exist_ok=False);owned=True
  (out/'WARNINGS.md').write_text(ACTION+'\n')
  if sys.platform!='linux':raise Unavailable('Source-built SPOTlight requires permitted Linux/WSL')
  before=sources();source_tree(paths);prefix=workspace/'runtime/.pixi/envs/default';library=workspace/'library'
  for source,target in [('dependencies.toml','pixi.toml'),('dependencies.lock','pixi.lock')]:
   if pin(HERE/source)!=pin(workspace/'runtime'/target):raise ValueError('Exact selected dependency manifest/lock differs')
  inventories={'r-prefix-files':inventory(prefix),'private-library-files':inventory(library)}
  ready=dict(schema='omicsmatic-original-spotlight103-v1',workspace=str(workspace),r_prefix=str(prefix),library=str(library),source_paths=paths,provider=str(out),adapter_sources=before)
  for n in ['home','tmp']:(out/n).mkdir()
  save(out/'control.json',control(ready));command=[str(prefix/'bin/Rscript'),'--vanilla',str(HERE/'Audit.R'),str(out/'control.json'),str(out/'complete-namespace-audit.rds')]
  with (out/'audit.stdout').open('xb') as so,(out/'audit.stderr').open('xb') as se:
   result=subprocess.run(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
  save(out/'PROCESS.json',dict(command=command,exit_code=result.returncode))
  if result.returncode:raise ValueError('Complete original namespace audit failed; read full logs')
  if sources()!=before or inventory(prefix)!=inventories['r-prefix-files'] or inventory(library)!=inventories['private-library-files']:raise ValueError('Protected source/runtime changed during audit')
  source_tree(paths)
  for name,value in inventories.items():save(out/(name+'.json'),value);ready[name]=pin(out/(name+'.json'))
  save(out/'READY.json',ready);save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_SPOTLIGHT103_PROVIDER_NOT_ANALYSIS',provider=pin(out/'READY.json'),source_algorithms_patched=False,new_family_credit=0))
  print('PASS_ORIGINAL_SPOTLIGHT103_PROVIDER_NOT_ANALYSIS',flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;label='SKIP3_SPOTLIGHT103_RUNTIME_UNAVAILABLE' if code==3 else 'FAIL2_SPOTLIGHT103_RUNTIME_UNADMITTED'
  print(label,repr(error),ACTION,flush=True)
  if owned:
   try:save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
   except BaseException as recording:print('FAIL2_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
  return code
if __name__=='__main__':raise SystemExit(main())
