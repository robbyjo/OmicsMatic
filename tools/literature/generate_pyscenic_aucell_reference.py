# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Call unchanged pinned pySCENIC/ctxcore APIs on artificial data; retain every outcome."""
from pathlib import Path
import argparse,contextlib,hashlib,importlib.metadata as md,inspect,json,logging,math,os,sys,time,traceback,warnings,zipfile
import numpy as np
import pandas as pd
from ctxcore.genesig import GeneSignature
from pyscenic.aucell import create_rankings,aucell4r,aucell
import ctxcore.recovery,pyscenic.aucell as source_aucell

def pin(path):
 raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):
 if isinstance(value,dict):return {k:encoded(v) for k,v in value.items()}
 if isinstance(value,(tuple,list)):return [encoded(v) for v in value]
 if isinstance(value,np.ndarray):return encoded(value.tolist())
 if isinstance(value,(np.integer,)):return int(value)
 if isinstance(value,(np.floating,float)):
  value=float(value)
  return 'NaN' if math.isnan(value) else 'Infinity' if value==math.inf else '-Infinity' if value==-math.inf else value
 return value
def save(path,value):path.write_text(json.dumps(encoded(value),indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf-8')
def frame(value):return dict(cells=list(value.index),columns=list(value.columns),values=value.to_numpy().tolist())

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--intake',type=Path,required=True);args=parser.parse_args();out=args.out;out.mkdir()
 versions={k:md.version(k) for k in ('pyscenic','ctxcore','numpy','pandas','numba','scikit-learn','arboreto')}
 assert versions['pyscenic']=='0.12.1' and versions['ctxcore']=='0.2.0' and versions['numpy']=='1.23.5' and versions['pandas']=='1.5.3'
 source_paths={}
 for name,func,path in [('aucell',create_rankings,'pyscenic/original-source/src/pyscenic/aucell.py'),('recovery',ctxcore.recovery.aucs,'ctxcore/original-source/src/ctxcore/recovery.py'),('genesig',GeneSignature,'ctxcore/original-source/src/ctxcore/genesig.py')]:
  installed=Path(inspect.getfile(func));original=args.intake/path;assert installed.read_bytes()==original.read_bytes(),name;source_paths[name]=dict(path=str(installed),identity=pin(installed))
 records=[];permutations=[];rank_calls=0;score_calls=0;expression_calls=0
 for n in (0,1,2,3,4,5,8,9,17,31,32,33,127,128,129,623,624,625,1000):
  for seed in (0,1,17,42,2147483648,4294967295):permutations.append(dict(n=n,seed=seed,permutation=np.random.RandomState(seed).permutation(n).tolist()))
 save(out/'PERMUTATIONS.json',permutations)
 def run(name,values,genes,cells,spec,fraction=.25,seed=17,noweights=False,normalize=False,workers=1,expected_native_refusal=False):
  nonlocal rank_calls,score_calls,expression_calls
  dest=out/name;dest.mkdir();request=dict(name=name,expression=values,genes=genes,cells=cells,signatures=[dict(name=n,weights=w) for n,w in spec],auc_fraction=fraction,seed=seed,no_weights=noweights,normalize=normalize,workers=workers,expected_native_refusal=expected_native_refusal)
  save(dest/'INPUT.json',request);data=pd.DataFrame(values,index=cells,columns=genes,dtype=np.float64);signatures=[GeneSignature(name=n,gene2weight=w) for n,w in spec]
  status='ORIGINAL_RETURNED';result={};start=time.time();caught=[]
  with (dest/'stdout').open('x',encoding='utf-8') as stdout,(dest/'stderr').open('x',encoding='utf-8') as stderr,contextlib.redirect_stdout(stdout),contextlib.redirect_stderr(stderr):
   logger=logging.getLogger('ctxcore.recovery');handler=logging.StreamHandler(stderr);logger.addHandler(handler)
   try:
    with warnings.catch_warnings(record=True) as caught:
     warnings.simplefilter('always');ranking=create_rankings(data,seed=seed);rank_calls+=1;result['ranks']=frame(ranking)
     raw=aucell4r(ranking,signatures,auc_threshold=fraction,noweights=noweights,normalize=False,num_workers=workers);score_calls+=1;result['raw']=frame(raw)
     effective=aucell4r(ranking,signatures,auc_threshold=fraction,noweights=noweights,normalize=normalize,num_workers=workers);score_calls+=1;result['scores']=frame(effective)
     direct=aucell(data,signatures,auc_threshold=fraction,noweights=noweights,normalize=normalize,seed=seed,num_workers=workers);expression_calls+=1;result['expression_call']=frame(direct)
     assert effective.equals(direct),'Original prepared/direct APIs disagree on seeded data'
     result['conditions']=[dict(category=w.category.__name__,message=str(w.message)) for w in caught]
   except Exception as error:
    status='ORIGINAL_REFUSED';result['exception']=dict(type=type(error).__name__,message=str(error));traceback.print_exc(file=stderr)
   finally:
    result['conditions']=[dict(category=w.category.__name__,message=str(w.message)) for w in caught]
    logger.removeHandler(handler)
  result.update(status=status,seconds=time.time()-start,expected_native_refusal=expected_native_refusal);save(dest/'RESULT.json',result);records.append(dict(name=name,status=status,expected_native_refusal=expected_native_refusal))
 genes=['NA','NaN','Inf']+['g'+str(j) for j in range(3,24)];cells=['z-cell','NA','a-cell','\U00010000-cell','\ue000-cell'];base=np.random.RandomState(817).randint(0,6,size=(len(cells),len(genes))).astype(float)
 base[0,:5]=[0,-0.0,np.nan,np.inf,-np.inf];base[1,:]=0;spec=[('z-weighted',{genes[0]:2.,genes[1]:.25,genes[3]:8.,genes[5]:.5}),('a-uniform',{genes[j]:1. for j in (2,4,6,8)}),('edge-80',{**{genes[j]:1. for j in range(4)},'absent':1.}),('low-coverage',{genes[0]:1.,'absentA':2.,'absentB':1.}),('Inf',{'absentA':1.,'absentB':1.}),('\U00010000-regulon',{genes[3]:2.,genes[8]:1.}),('\ue000-regulon',{genes[4]:2.,genes[9]:1.})]
 for seed in (0,17,4294967295):
  for noweights in (False,True):
   for normalize in (False,True):run(f'tied-s{seed}-w{int(noweights)}-n{int(normalize)}',base.tolist(),genes,cells,spec,seed=seed,noweights=noweights,normalize=normalize)
 for workers in (1,2):
  for fraction in (.05,.0625,.10416666666666667,.5,.9):run(f'cutoff-w{workers}-f{fraction}',base.tolist(),genes,cells,spec,fraction=fraction,workers=workers)
 for factor in (1e-200,1e200):run('weight-scale-'+str(factor),base.tolist(),genes,cells,[('scaled',{genes[0]:2*factor,genes[3]:factor,genes[5]:3*factor})])
 for normalize in (False,True):run('negative-weights-'+str(normalize),base.tolist(),genes,cells,[('negative',{genes[0]:-4.,genes[1]:5.})],normalize=normalize)
 for fraction in (0.,-.1,1.,np.nan):run('invalid-cutoff-'+str(fraction),base.tolist(),genes,cells,[('covered',{genes[0]:1.,genes[1]:1.})],fraction=fraction,expected_native_refusal=True)
 run('zero-weight',base.tolist(),genes,cells,[('zero',{genes[0]:0.,genes[1]:0.})],expected_native_refusal=True)
 run('nonfinite-weight',base.tolist(),genes,cells,[('nonfinite',{genes[0]:np.inf,genes[1]:1.})],expected_native_refusal=True)
 run('ignored-nonfinite-weight',base.tolist(),genes,cells,[('ignored',{genes[0]:np.inf,genes[1]:1.})],noweights=True)
 run('uncovered-invalid-cutoff',base.tolist(),genes,cells,[('absent',{'missing':1.})],fraction=-.1)
 run('uncovered-one-gene',[[0.],[np.nan]],['NA'],['z','a'],[('absent',{'missing':1.})],fraction=np.nan,normalize=True)
 run('covered-one-gene',[[1.]],['NA'],['a'],[('covered',{'NA':1.})],fraction=.5,expected_native_refusal=True)
 run('parallel-failed-workers',base.tolist(),genes,cells,[('covered',{genes[0]:1.,genes[1]:1.})],fraction=1.,workers=2,expected_native_refusal=True)
 for row in records:
  assert row['status']=='ORIGINAL_RETURNED' or row['expected_native_refusal'],row
 assert all(pin(Path(record['path']))==record['identity'] for record in source_paths.values())
 save(out/'COMPLETED.json',dict(status='UNCHANGED_ORIGINAL_API_OBSERVATIONS_NOT_NATIVE_QUALIFICATION',versions=versions,sources=source_paths,cases=records,permutation_cases=len(permutations),successful_explicit_rank_calls=rank_calls,successful_prepared_score_calls=score_calls,successful_expression_score_calls=expression_calls,source_modified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
 with zipfile.ZipFile(out/'original-aucell-reference.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for path in sorted(out.rglob('*')):
   if path.is_file() and path.suffix!='.zip':z.write(path,path.relative_to(out).as_posix())
 print(json.dumps(dict(status='ORIGINAL_API_REFERENCE_CAPTURED',cases=len(records),permutations=len(permutations),rank_calls=rank_calls,score_calls=score_calls,expression_calls=expression_calls)))

if __name__=='__main__':
 assert __debug__,'Run without -O';main()
