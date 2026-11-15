#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Owned recording/input-failure injection; original R/package sources are unchanged."""
from pathlib import Path
import importlib.util,json,sys
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'spotlight103';sys.path.insert(0,str(MODULE))
mode=sys.argv.pop(1);out=Path(sys.argv[sys.argv.index('--out')+1]).absolute()
spec=importlib.util.spec_from_file_location('SPOTlight_owned_public_runner',MODULE/'run.py');runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
original_save=runner.save;original_popen=runner.subprocess.Popen
def receipt(action):
 with (out/'OWNED-INJECTION.json').open('x') as f:json.dump(dict(mode=mode,action=action,original_package_or_wrapper_files_modified=False),f,indent=2)
if mode=='recording-fault':
 def save(path,value):
  if Path(path).name=='MANIFEST.json':receipt('Deliberate recording failure after the real original return');raise PermissionError('Owned guard refuses final manifest recording')
  return original_save(path,value)
 runner.save=save
elif mode in ['captured-input-tamper','missing-status']:
 class Observed:
  def __init__(self,p):self.process=p;self.finished=False
  def __getattr__(self,n):return getattr(self.process,n)
  def finish(self,code):
   if code is not None and not self.finished:
    self.finished=True
    if code==0:
     if mode=='captured-input-tamper':
      target=out/'inputs/request.rds';assert target.resolve().is_relative_to(out.resolve())
      with target.open('ab') as f:f.write(b'\nOWNED_POSTRETURN_INPUT_TAMPER\n')
      receipt('Only the owned captured request copy was changed after the real original call')
     else:
      source=out/'original/R-status.json';target=out/'original/RETAINED-R-status-for-missing-marker-guard.json'
      assert source.resolve().is_relative_to(out.resolve()) and target.resolve().is_relative_to(out.resolve()) and not target.exists()
      source.rename(target);receipt('Complete real status retained under another owned filename; no original data deleted')
   return code
  def poll(self):return self.finish(self.process.poll())
  def wait(self,*args,**kwargs):return self.finish(self.process.wait(*args,**kwargs))
 def popen(command,*args,**kwargs):
  p=original_popen(command,*args,**kwargs)
  return Observed(p) if str(MODULE/'Worker.R') in list(map(str,command)) else p
 runner.subprocess.Popen=popen
else:raise ValueError('Unsupported owned failure mode')
raise SystemExit(runner.main())
