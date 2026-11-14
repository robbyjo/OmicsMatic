#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare six complete synthetic requests without installing or executing analysis software."""
from pathlib import Path
import argparse,hashlib,json,shutil,sys,zipfile
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent


def main():
 parser=argparse.ArgumentParser(description=__doc__)
 for name in ['provider','plot-provider','out']:parser.add_argument('--'+name,type=Path,required=True)
 args=parser.parse_args();out=args.out.absolute();owned=False
 try:
  if out!=out.resolve():raise ValueError('Use a canonical NEW preparation output')
  protected=[HERE,args.provider.resolve(),args.plot_provider.resolve()]
  if any(out.is_relative_to(p) or p.is_relative_to(out) for p in protected):raise ValueError('Preparation output overlaps source or runtime')
  pins=json.loads((HERE/'FILES.json').read_bytes())['files']
  for name,wanted in pins.items():
   raw=(HERE/name).read_bytes()
   if {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}!=wanted:raise ValueError('Example source/data changed: '+name)
  out.mkdir(parents=True,exist_ok=False);owned=True;data=out/'inputs';data.mkdir()
  with zipfile.ZipFile(HERE/'synthetic-inputs.zip') as archive:
   expected=json.loads(archive.read('MEMBERS.json'))['files']
   if len(archive.namelist())!=len(expected)+1 or set(archive.namelist())!=set(expected)|{'MEMBERS.json'}:raise ValueError('Complete synthetic archive population differs')
   for name,wanted in expected.items():
    if name.startswith('/') or '\\' in name or '..' in Path(name).parts:raise ValueError('Nonportable archive member')
    raw=archive.read(name)
    if {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}!=wanted:raise ValueError('Synthetic input differs: '+name)
    target=data/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
  def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf-8',newline='\n')
  requests=[]
  base=dict(schema_version=1,version='1.20.0',mode='R_SCRIPT',provider_directory=str(args.provider.resolve()),expected_outputs={},required_packages={},input_provenance='Original scvelo0.3.3 normal-noise synthetic160-cell/12-gene fixture; generator, exact parameters and full inputs retained. Literal NA and Greek identifiers are preserved. Selected environment is not an author environment.',analysis_population='All160 synthetic cells and12 genes in the original order. Caller seed882341; original R normalization and declared parameters. A finite fitting budget is not convergence, biological validation or paper reproduction.',execution_policy='ALLOW',trusted_code_policy='ALLOW',limits=dict(timeout_seconds=1200,maximum_files=20000,maximum_workspace_bytes=2147483648,maximum_file_bytes=536870912,maximum_R_nodes=100000,maximum_R_value_bytes=536870912))
  for mode in ['stochastic','dynamical']:
   doc=dict(base,script=str(HERE/('fit-'+mode+'.R')),inputs={'inputs.rds':str(data/'inputs.rds'),'parameters.rds':str(data/(mode+'-parameters.rds'))},out=str(out/(mode+'-analysis')),required_exports=['scvelo'],expected_outputs={'complete-caller-parameters.rds':1},expected_return='COMPLETE_EXAMPLE_'+mode.upper()+'_160_CELLS_12_GENES');path=out/(mode+'-request.json');save(path,doc);requests.append(path.name)
  doc=dict(base,script=str(HERE/'project-grid.R'),inputs={'fit.rds':str(data/'fit.rds')},out=str(out/'project-grid-analysis'),required_exports=['embedVelocity','gridVectors'],expected_return='COMPLETE_EXAMPLE_PROJECTION_GRID_160_CELLS');path=out/'project-grid-request.json';save(path,doc);requests.append(path.name)
  for scenario in ['plotVelocity-all-panels','plotVelocityStream-plain','plotVelocityStream-colored']:
   doc=dict(base,mode='FIGURES',provider_directory=str(args.plot_provider.resolve()),script=str(HERE/'plot.R'),inputs={'call.rds':str(data/(scenario+'-call.rds'))},out=str(out/(scenario+'-analysis')),required_exports=['plotVelocity' if scenario=='plotVelocity-all-panels' else 'plotVelocityStream'],expected_return='COMPLETE_ORIGINAL_FIGURE_'+scenario,limits={k:v for k,v in base['limits'].items() if not k.startswith('maximum_R_')},render=dict(width_inches=6,height_inches=4,png_dpi=120,seed=882341,background='white'));path=out/(scenario+'-request.json');save(path,doc);requests.append(path.name)
  save(out/'PREPARED.json',dict(status='PREPARED_SIX_SYNTHETIC_REQUESTS_NO_ANALYSIS_EXECUTED',requests=requests,original_data_members=len(expected),downloaded=False,installed=False,analysis_executed=False,paper_reproduced=False,new_family_credit=0,whole_goal_complete=False));print('PREPARED_SIX_SYNTHETIC_REQUESTS_NO_ANALYSIS_EXECUTED');return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;message='SKIP3_EXAMPLE_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_EXAMPLE_PREPARATION_UNADMITTED'
  print(message,repr(error),'Preserve all previous/partial files. Resolve the named cause and retry a NEW path. Missing or forbidden runtimes skip dependent analysis; preparation installs and executes nothing.',flush=True)
  if owned:
   try:(out/'FAILURE.txt').write_text(message+'\n'+repr(error)+'\n',encoding='utf-8')
   except OSError as recording:print('FAIL2_EXAMPLE_RECORDING_UNAVAILABLE',repr(recording),flush=True);return 2
  return code
if __name__=='__main__':raise SystemExit(main())
