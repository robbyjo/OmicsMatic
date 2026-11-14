#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only differences against a saved provider; never refresh hashes or grant admission."""
from pathlib import Path
import argparse,sys,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,CORE,load,pin,save,inventory,ACTION

def difference(before,after):
 return dict(added={k:after[k] for k in sorted(after.keys()-before.keys())},removed={k:before[k] for k in sorted(before.keys()-after.keys())},changed={k:dict(before=before[k],after=after[k]) for k in sorted(before.keys()&after.keys()) if before[k]!=after[k]})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();provider=a.runtime.resolve();ready=load(provider/'READY.json')
 if any(out.is_relative_to(x) or x.is_relative_to(out) for x in [HERE,provider,Path(ready['prefix']),Path(ready['source'])]):raise ValueError('Use a NEW separate diagnostic output')
 out.mkdir(exist_ok=False)
 try:
  result=dict(status='EXACT_RUNTIME_DIFFERENCES_RECORDED_NO_ADMISSION_OR_HASH_REFRESH',provider=str(provider),source_differences=difference(ready['runner_sources'],{name:pin(HERE/name) for name in CORE}),inventories={},original_provider_modified=False,analysis_executed=False,result_admitted=False,required_action=ACTION)
  for key in ['r_prefix','library']:
   record=provider/(key+'-files.json');before=load(record);after=inventory(Path(ready[key]));result['inventories'][key]=dict(saved_inventory=pin(record),saved_inventory_identity_matches_provider='inventory_files' in ready and pin(record)==ready['inventory_files'][key],differences=difference(before,after))
  save(out/'DIFFERENCES.json',result)
  (out/'WARNINGS.md').write_text('# Inspection grants no result admission\n\nPreserve the old provider, all original/partial outputs and every saved hash. Inspect the named files and source changes in DIFFERENCES.json. Provision and audit a NEW matching permitted runtime/source rather than overwriting an old provider.\n\n'+ACTION+'\n',encoding='utf-8')
  print(result['status'],flush=True)
  for key,row in result['inventories'].items():print(key,{k:len(v) for k,v in row['differences'].items()},flush=True)
  return 0
 except BaseException as e:save(out/'FAILURE.json',dict(cause=repr(e),traceback=traceback.format_exc(),result_admitted=False,required_action=ACTION));raise
if __name__=='__main__':raise SystemExit(main())
