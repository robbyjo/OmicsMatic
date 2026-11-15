#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual Windows public entry points skip Linux-only execution without children."""
from pathlib import Path
import argparse,subprocess,sys
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'spotlight103';sys.path.insert(0,str(MODULE))
from Runtime import *
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['runtime','request','out']:p.add_argument('--'+n,type=Path,required=True)
 a=p.parse_args()
 if sys.platform!='win32':raise ValueError('These guards require actual Windows Python')
 out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);rows=[]
 commands={
  'run-linux-required':[sys.executable,'-X','utf8','-B',str(MODULE/'run.py'),'--runtime',str(a.runtime),'--request',str(a.request),'--out',str(out/'run')],
  'install-linux-required':[sys.executable,'-X','utf8','-B',str(MODULE/'install_runtime.py'),'--manager',str(out/'unused-manager'),'--allow-install','--out',str(out/'install')]}
 for name,command in commands.items():
  with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:result=subprocess.run(command,stdout=so,stderr=se,timeout=60)
  target=out/('run' if name.startswith('run') else 'install');failure=load(target/'FAILURE.json')
  if result.returncode!=3 or (target/'COMPLETED.json').exists() or list(target.glob('*LIVE*')) or 'Linux' not in failure['cause']:
   raise ValueError('Actual Windows prerequisite skip failed: '+name)
  rows.append(dict(name=name,exit_code=result.returncode,no_analysis_or_installer_child=True))
 save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_WINDOWS_SPOTLIGHT103_PREREQUISITE_GUARDS',guards=rows,new_family_credit=0))
 print('PASS_ACTUAL_WINDOWS_SPOTLIGHT103_PREREQUISITE_GUARDS',len(rows),flush=True)
if __name__=='__main__':main()
