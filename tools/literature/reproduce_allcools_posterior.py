"""Generate synthetic references with the unmodified ALLCools 1.0.8 functions.

Run in a permitted, isolated ALLCools environment. Missing prerequisites skip
the dependent analysis. This generator never installs software or repairs data.
"""
from pathlib import Path
import argparse,hashlib,json,os,sys,warnings

EXPECTED={
 'ALLCools/mcds/utilities.py':'b634858842c108d2039beab0db3a4efff2ef4a33c8a2e72de280dfc0ddb223a4',
 'ALLCools/mcds/mcds.py':'368eb9d974d900acc68024dd2058a7e385a3fcd2bcffd897b2e8b245ebbd4957',
}
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source-tree',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--selected-functions-only',action='store_true',help='Execute unchanged original numerical function/class definitions without the package import orchestrator');a=p.parse_args()
 assert not a.out.exists(),'Use a NEW output path'
 for name,expected in EXPECTED.items():assert sha(a.source_tree/name)==expected,'Changed comparison source: '+name
 import numpy as np,xarray as xr
 assert np.__version__=='1.23.5' and xr.__version__=='2023.1.0','Require the declared float64 comparison context'
 sys.path.insert(0,str(a.source_tree.absolute()))
 if a.selected_functions_only:
  import ast,glob,pathlib,re,runpy,typing,anndata,dask,pandas,scipy.sparse,yaml
  from pybedtools import BedTool
  utility=runpy.run_path(str(a.source_tree/'ALLCools/mcds/utilities.py'));calculate_posterior_mc_frac=utility['calculate_posterior_mc_frac']
  path=a.source_tree/'ALLCools/mcds/mcds.py';tree=ast.parse(path.read_text(encoding='utf-8'),filename=str(path))
  definitions=[node for node in tree.body if isinstance(node,(ast.FunctionDef,ast.ClassDef))]
  namespace=dict(__name__='_selected_original_allcools_mcds',glob=glob,pathlib=pathlib,re=re,warnings=warnings,Union=typing.Union,anndata=anndata,dask=dask,np=np,pd=pandas,ss=scipy.sparse,xr=xr,yaml=yaml,BedTool=BedTool)
  namespace.update({name:utility[name]for name in ('calculate_posterior_mc_frac','determine_engine','highly_variable_methylation_feature')})
  exec(compile(ast.Module(body=definitions,type_ignores=[]),str(path),'exec'),namespace);MCDS=namespace['MCDS']
 else:
  from ALLCools.mcds.utilities import calculate_posterior_mc_frac
  from ALLCools.mcds.mcds import MCDS
 assert Path(calculate_posterior_mc_frac.__code__.co_filename).resolve()==(a.source_tree/'ALLCools/mcds/utilities.py').resolve()
 assert Path(MCDS.add_m_value.__code__.co_filename).resolve()==(a.source_tree/'ALLCools/mcds/mcds.py').resolve()
 vectors=[('zero-coverage',[0,0,0],[0,0,0]),('zero-mc',[0,0,0],[10,10,10]),('all-mc',[10,10,10],[10,10,10]),('constant-half',[5,5,5],[10,10,10]),('bernoulli',[0,10,0,10],[10,10,10,10]),('single',[1],[3]),('regular',[1,2,3,4,0],[5,5,5,5,0]),('asymmetric',[1,2,2,3,0],[7,6,10,11,0]),('near-uint32',[4294967294,4294967293,0,1],[4294967295]*4),('near-constant',[100000,100001,100000,0],[1000000,1000000,1000000,0])]
 random=np.random.RandomState(63851)
 for n in [2,7,8,9,31,64,127,128,129,255,256,257,1024,4097]:
  cov=random.randint(1,10000,size=n).astype('int64');mc=np.floor(cov*random.uniform(0,1,size=n)).astype('int64');mc[::7]=0;cov[::7]=0;vectors.append(('random-'+str(n),mc.tolist(),cov.tolist()))
 settings=[]
 for branch in ['numpy','xarray']:
  for norm,clip in [(False,None),(False,0.6),(True,None),(True,10.0),(True,0.6)]:settings.append((branch,'posterior-fraction',norm,clip,0.01))
 for norm in [False,True]:
  for alpha in [0.0,0.01,0.1]:settings.append(('xarray','m-value',norm,None,alpha))
 def number(v):return 'NaN'if np.isnan(v)else'Inf'if np.isposinf(v)else'-Inf'if np.isneginf(v)else repr(float(v))
 def array(v):return ','.join(number(x)for x in np.asarray(v).reshape(-1))
 records=[];warning_records=[];outputs=[]
 for name,mc,cov in vectors:
  m=np.array([mc],dtype='uint32');c=np.array([cov],dtype='uint32')
  for representation,operation,norm,clip,alpha in settings:
   case='-'.join([name,representation,operation,str(norm),str(clip),str(alpha)])
   with warnings.catch_warnings(record=True) as caught:
    warnings.simplefilter('always')
    raw=m/c;mean=np.nanmean(raw,axis=1);var=np.nanvar(raw,axis=1);aa=(1-mean)*(mean**2)/var-mean;bb=aa*(1/mean-1);prior=aa/(aa+bb)
    posterior=calculate_posterior_mc_frac(m,c,normalize_per_cell=False,clip_norm_value=None)
    if representation=='xarray':
     md=xr.DataArray(m,dims=['cell','region']);cd=xr.DataArray(c,dims=['cell','region'])
    else:md,cd=m,c
    if operation=='posterior-fraction':out=calculate_posterior_mc_frac(md,cd,var_dim='region',normalize_per_cell=norm,clip_norm_value=clip)
    else:
     data=xr.Dataset({'region_da':xr.DataArray(np.stack([m,c],axis=-1),dims=['cell','region','count_type'],coords={'count_type':['mc','cov']})})
     ds=MCDS(data,obs_dim='cell',var_dim='region');ds.add_m_value(var_dim='region',alpha=alpha,normalize_per_cell=norm);out=ds['region_da_mvalue']
    outputs.append(np.asarray(out).reshape(-1))
    records.append('\t'.join([case,representation,operation,str(norm).lower(),'null'if clip is None else str(clip),str(alpha),','.join(map(str,mc)),','.join(map(str,cov)),array(posterior),array(out),array([mean[0],var[0],aa[0],bb[0],prior[0]])]))
    warning_records.append(dict(case=case,warnings=[dict(category=type(w.message).__name__,message=str(w.message))for w in caught]))
 a.out.mkdir(parents=True);fixture=a.out/'cases.tsv'
 with fixture.open('w',encoding='utf-8',newline='\n')as stream:stream.write('case\trepresentation\toperation\tnormalize\tclip\tm_value_alpha\tmc\tcov\tposterior\tvalues\tprior\n'+'\n'.join(records)+'\n')
 (a.out/'WARNINGS.json').write_text(json.dumps(warning_records,indent=2)+'\n',encoding='utf-8')
 flattened=np.concatenate(outputs)
 summary=dict(status='COMPLETE_UNMODIFIED_ALLCOOLS_SYNTHETIC_REFERENCES',source_commit='2a77f30bf911726de10fe6cc4971331f023d9e68',numpy=np.__version__,xarray=xr.__version__,source_files=EXPECTED,cases=len(records),base_vectors=len(vectors),settings=len(settings),output_values=len(flattened),finite_values=int(np.isfinite(flattened).sum()),NaN=int(np.isnan(flattened).sum()),Inf=int(np.isposinf(flattened).sum()),negative_Inf=int(np.isneginf(flattened).sum()),warning_instances=sum(len(r['warnings'])for r in warning_records),fixture_sha256=sha(fixture),selected_function_definitions_without_package_import_orchestrator=a.selected_functions_only,whole_package_qualified=False,whole_paper_reproduced=False)
 (a.out/'COMPLETED.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8');print(json.dumps(summary,sort_keys=True));return 0
if __name__=='__main__':
 try:raise SystemExit(main())
 except (ModuleNotFoundError,FileNotFoundError,PermissionError)as e:print('SKIP3 ALLCOOLS REFERENCE ANALYSIS SKIPPED: '+str(e)+'. Required action: supply the exact permitted source/runtime in a NEW output path, or skip the dependent comparison.',file=sys.stderr);raise SystemExit(3)
 except Exception as e:print('FAIL2 NO ALLCOOLS REFERENCE ADMITTED: '+str(e)+'. Required action: preserve this attempt, correct the named source, numeric or execution problem and retry NEW paths.',file=sys.stderr);raise SystemExit(2)
