#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Refuse real isolated omissions, array/NaN/typed-ID changes and unadmitted supervisor results."""
from pathlib import Path
import argparse,ast,copy,hashlib,json,math,shutil,struct,subprocess,sys,time
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
def load(p):return json.loads(p.read_bytes())
def save(p,v):p.write_text(json.dumps(v,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',type=Path,required=True);p.add_argument('--actual',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
 for protected in [HERE,a.reference.resolve(),a.actual.resolve()]:
  if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('NEW refusal evidence must not overlap original sources/results')
 out.mkdir(exist_ok=False);cases=[]
 def fingerprints():return {str(root):{p.relative_to(root).as_posix():dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in root.rglob('*') if p.is_file()} for root in [a.reference,a.actual]}
 before=fingerprints();save(out/'ORIGINAL_INPUTS_BEFORE.json',before)
 def run(name,actual,expected,supervised=False):
  folder=out/name;folder.mkdir(exist_ok=True);cmd=[sys.executable,'-X','utf8',str(HERE/'compare_complete.py'),'--reference',str(a.reference),'--actual',str(actual),'--version','0.2.5','--mode','dynamical','--out',str(folder/'comparison')]
  if supervised:cmd.append('--supervised')
  result=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(folder/'process.stdout').write_bytes(result.stdout);(folder/'process.stderr').write_bytes(result.stderr)
  save(folder/'PROCESS.json',dict(command=cmd,exit_code=result.returncode,expected_exit=expected));assert result.returncode==expected,(name,result.returncode,result.stdout,result.stderr)
  cases.append(dict(id=name,exit_code=result.returncode,expected_exit=expected,supervised=supervised));save(out/'CASES.json',cases);print(name,'expected exit',expected,flush=True)
 run('complete-positive',a.actual,0)
 names=['missing-array','extra-array','typed-boolean-relabel','literal-NA-relabel','missing-stage','missing-record','old-shared-stage-schema','changed-array-byte','repaired-original-NaN-padding','changed-array-dtype','unadmitted-supervisor-result']
 for name in names:
  folder=out/name;folder.mkdir();actual=folder/'controller/results/workflow';actual.parent.mkdir(parents=True);shutil.copytree(a.actual,actual)
  index=actual/'complete-stages/COMPLETE-STAGES.json';graph=load(index);array=next(row for row in graph['records'] if row['kind']=='array');changed_index=False
  if name=='missing-array':(actual/'complete-stages'/array['file']).unlink()
  elif name=='extra-array':shutil.copyfile(actual/'complete-stages'/array['file'],actual/'complete-stages/arrays/999999.npy')
  elif name=='typed-boolean-relabel':graph['nonfinite_values_repaired']=0;changed_index=True
  elif name=='literal-NA-relabel':next(row for row in graph['records'] if row['kind']=='str' and row['value']=='NA')['value']='NA_REPAIRED';changed_index=True
  elif name=='missing-stage':graph['snapshot_roots'].pop();changed_index=True
  elif name=='missing-record':graph['records'].pop();changed_index=True
  elif name=='old-shared-stage-schema':graph['schema_version']=1;graph['snapshot_scope']='shared-across-stage-references';changed_index=True
  elif name=='changed-array-byte':
   path=actual/'complete-stages'/array['file'];raw=bytearray(path.read_bytes());raw[-1]^=1;path.write_bytes(raw)
  elif name=='repaired-original-NaN-padding':
   path=actual/'complete-stages'/graph['nonfinite_arrays'][0]['file'];raw=bytearray(path.read_bytes());assert raw[:8]==b'\x93NUMPY\x01\x00';length=struct.unpack_from('<H',raw,8)[0];header=ast.literal_eval(bytes(raw[10:10+length]).decode('latin1'));assert header['descr']=='<f8';start=10+length
   offset=next(i for i in range(start,len(raw),8) if math.isnan(struct.unpack_from('<d',raw,i)[0]));raw[offset:offset+8]=struct.pack('<d',0.0);path.write_bytes(raw)
  elif name=='changed-array-dtype':array['dtype']='unreported-repaired-dtype';changed_index=True
  elif name=='unadmitted-supervisor-result':
   save(actual.parent.parent/'analysis-status.json',dict(result_admitted=False,exit_code=0,original_version='0.2.5',condition_counts_complete=True,native_temporary_copy_complete=True));save(actual.parent.parent/'MANIFEST.json',{})
  if changed_index:save(index,graph)
  run(name,actual,2,supervised=name=='unadmitted-supervisor-result')
 after=fingerprints();assert before==after;save(out/'ORIGINAL_INPUTS_AFTER.json',after)
 result=dict(status='PASS_COMPLETE_ORIGINAL_SCVELO_COMPARATOR_BASELINE_AND_ELEVEN_REAL_DATA_REFUSALS',controls=len(cases),refusal_controls=len(names),cases=cases,original_inputs_unchanged=True,all_isolated_negative_inputs_retained=True,original_scientific_results_repaired=False,isolated_mutations_are_refusal_fixtures=True,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True)
if __name__=='__main__':main()
