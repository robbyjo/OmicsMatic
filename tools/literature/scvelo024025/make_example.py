#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a complete declared original scVelo example; install nothing and execute no model."""
from pathlib import Path
import argparse,hashlib,json,shutil,sys
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8'))
def request(provider,script,inputs,outputs,out,version=None):
 version=version if version is not None else json.loads((Path(provider)/'READY.json').read_bytes())['version']
 return dict(schema_version=1,version=version,mode='SCRIPT',script=str(Path(script).resolve()),provider_directory=str(Path(provider).resolve()),inputs={k:str(Path(v).resolve()) for k,v in inputs.items()},expected_outputs=outputs,required_symbols=['pp.filter_and_normalize','pp.moments','tl.velocity','tl.velocity_graph','tl.paga','tl.recover_dynamics','tl.latent_time','pl.velocity_graph','pl.velocity_embedding','pl.velocity_embedding_stream'],required_distributions={'scvelo':version,'scanpy':'1.9.6','anndata':'0.8.0','numpy':'1.23.5','scipy':'1.10.1','pandas':'1.5.3'},expected_return='COMPLETE_ORIGINAL_SCVELO_EXAMPLE',input_provenance='Original scVelo0.2.5 Gillespie-generated fixed synthetic input; no author/patient data or inferred historical environment. Literal NA/Unicode identifiers retained.',analysis_population='All160 synthetic cells and12 genes in the declared original workflow; every stage/array/model/figure retained. Caller-selected integration settings, not author defaults or convergence.',execution_policy='ALLOW',trusted_code_policy='ALLOW',out=str(Path(out).resolve()),limits=dict(timeout_seconds=1800,maximum_files=20000,maximum_workspace_bytes=1073741824,maximum_file_bytes=134217728))
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--provider',type=Path,required=True);parser.add_argument('--version',choices=['0.2.4','0.2.5'],required=True);parser.add_argument('--mode',choices=['stochastic','dynamical'],required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.absolute()
 if out!=out.resolve():raise ValueError('Canonical NEW example output required')
 for protected in [HERE,a.provider.resolve()]:
  if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Example output overlaps protected source/provider')
 out.mkdir(parents=True,exist_ok=False);backing=out/'inputs';backing.mkdir();(backing/'driver').mkdir()
 profile=json.loads((HERE/'example-output-profile.json').read_bytes());selected=profile['profiles'][a.version][a.mode];fixture=HERE/profile['input_file']
 if hashlib.sha256(fixture.read_bytes()).hexdigest()!=profile['input_sha256']:raise ValueError('Complete fixed original synthetic input differs')
 inputs={}
 for logical,path in [('kinetics.h5ad',fixture),('driver/OriginalControl.py',HERE/'OriginalControl.py'),('driver/Portable.py',HERE/'Portable.py')]:
  target=backing/logical;shutil.copyfile(path,target);inputs[logical]=target
 save(backing/'options.json',dict(version=a.version,mode=a.mode));inputs['options.json']=backing/'options.json'
 save(backing/'expected-example.json',selected);inputs['expected-example.json']=backing/'expected-example.json'
 doc=request(a.provider,HERE/'ExampleAnalysis.py',inputs,{'workflow/'+n:1 for n in selected['expected_files']},out/'analysis',a.version);save(out/'request.json',doc)
 save(out/'PREPARED.json',dict(status='PREPARED_COMPLETE_ORIGINAL_SCVELO_EXAMPLE_NOT_ANALYSIS',version=a.version,mode=a.mode,expected_result_files=len(selected['expected_files']),input_sha256=profile['input_sha256'],analysis_executed=False,new_tool_credit=0));print('PREPARED_COMPLETE_ORIGINAL_SCVELO_EXAMPLE_NOT_ANALYSIS',a.version,a.mode,flush=True)
if __name__=='__main__':main()
