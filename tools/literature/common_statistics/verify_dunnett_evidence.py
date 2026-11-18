#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only integrity check of a completed Dunnett result, without executing R.

This checks the recorded result, not publisher authenticity or an independent
multivariate probability calculation. Replay requires the captured provider.
"""
import argparse
import hashlib
import json
from pathlib import Path

PROFILE='dunnett-multcomp1429-oneway-v1'

def pin(path):
 h=hashlib.sha256();size=0
 with path.open('rb') as stream:
  for block in iter(lambda:stream.read(1048576),b''):size+=len(block);h.update(block)
 return dict(bytes=size,sha256=h.hexdigest())

def verify(root):
 root=root.resolve(strict=True)
 def regular(name):
  if not isinstance(name,str) or not name or '\\' in name or any(part in ('','.','..') for part in name.split('/')) or Path(name).is_absolute():raise ValueError('Unsafe result member')
  p=root/name
  if not p.is_file() or p.is_symlink() or p.resolve()!=p or not p.is_relative_to(root):raise ValueError('Missing, nonregular or linked result member: '+name)
  return p
 def load(name):
  p=regular(name)
  if p.stat().st_size>16*1024*1024:raise ValueError('Oversized result metadata')
  def pairs(items):
   result={}
   for key,value in items:
    if key in result:raise ValueError('Duplicate result metadata key')
    result[key]=value
   return result
  return json.loads(p.read_text(encoding='utf8'),object_pairs_hook=pairs,parse_constant=lambda x:(_ for _ in ()).throw(ValueError('Nonfinite metadata')))
 completed=load('COMPLETED.json');manifest=load('manifest.json');analysis=load('analysis.json')
 if completed.get('status')!='PASS_DUNNETT_COMPLETE_FAMILY' or completed.get('profile')!=PROFILE or completed.get('manifest')!=pin(regular('manifest.json')):raise ValueError('Invalid completed result/manifest pin')
 if analysis.get('profile')!=PROFILE or analysis.get('result_admitted') is not True or analysis.get('native_audit',{}).get('passed') is not True:raise ValueError('Result was not admitted by the model audit')
 if not isinstance(manifest,dict) or len(manifest)>20000:raise ValueError('Invalid/oversized payload population')
 required={'WARNINGS.md','source-request.json','source-input.tsv','native-groups.tsv','native-comparisons.tsv','provider-before.json','provider-after.json','worker-inputs.json','analysis.json','worker/original-fit.rds','worker/original-comparisons.tsv','worker/original-correlation.tsv','worker/original-diagnostics.tsv','worker/process.json'}
 if not required<=manifest.keys():raise ValueError('Incomplete result population')
 actual=set()
 for p in root.rglob('*'):
  if p.is_symlink():raise ValueError('Unexpected result link')
  if p.is_file():actual.add(p.relative_to(root).as_posix())
  elif not p.is_dir():raise ValueError('Unexpected result special file')
 if actual!=set(manifest)|{'manifest.json','COMPLETED.json'}:raise ValueError('Result contains missing or unlisted payloads')
 for name,expected in manifest.items():
  if pin(regular(name))!=expected:raise ValueError('Result payload differs: '+name)
 if load('provider-before.json')!=load('provider-after.json'):raise ValueError('Recorded providers changed during execution')
 for name,expected in load('worker-inputs.json').items():
  if pin(regular('worker/'+name))!=expected:raise ValueError('Captured fixed worker/input differs')
 return dict(status='PASS_DUNNETT_RECORDED_RESULT_INTEGRITY_ONLY',payloads=len(manifest),profile=PROFILE,provider_reexecuted=False,independent_multivariate_inference=False)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--result',required=True,type=Path);args=parser.parse_args()
 try:print(json.dumps(verify(args.result),sort_keys=True))
 except (OSError,ValueError,TypeError,KeyError) as error:print('FAIL2: result integrity not established. Preserve the result; inspect the named missing/changed payload. '+str(error));raise SystemExit(2)

if __name__=='__main__':main()
