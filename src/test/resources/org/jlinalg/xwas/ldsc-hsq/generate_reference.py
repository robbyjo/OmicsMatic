"""Execute unchanged selected CBIIT Hsq/IRWLS/jackknife bodies; no package/web initializer."""
import argparse,hashlib,importlib,json,math,sys,traceback,types,warnings
from pathlib import Path

def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def plain(x):
 import numpy as np
 if isinstance(x,np.ndarray):return [plain(v) for v in x.tolist()]
 if isinstance(x,np.generic):return plain(x.item())
 if isinstance(x,(list,tuple)):return [plain(v) for v in x]
 if isinstance(x,dict):return {k:plain(v) for k,v in x.items()}
 if isinstance(x,float) and not math.isfinite(x):return str(x)
 return x
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
 import numpy as np
 import pandas as pd
 import scipy
 # Isolate original regression modules from the unrelated __init__ web/CLI
 # imports. Original module files, class bodies and all arithmetic stay unchanged.
 package=types.ModuleType('ldscore');package.__path__=[str(args.source/'ldscore')];sys.modules['ldscore']=package
 regressions=importlib.import_module('ldscore.regressions');irwls=importlib.import_module('ldscore.irwls');jackknife=importlib.import_module('ldscore.jackknife')
 guarded=['ldscore/regressions.py','ldscore/irwls.py','ldscore/jackknife.py','ldscore/__init__.py','setup.py','LICENSE']
 before={name:pin(args.source/name) for name in guarded}
 rng=np.random.RandomState(19870624);cases=[];errors=[]
 for case in range(72):
  rows=81+case;blocks=3+case%13;mode=case%6
  n=rng.uniform(10000,80000,rows);ld=rng.uniform(-2,80,rows);wld=rng.uniform(-1,50,rows);m=1e6*(1+case%3)
  h=[.3,.1,1.4,-.12,.4,.25][mode]
  y=1.1+n/m*h*ld+rng.uniform(-.06,.06,rows)
  if mode==3:y=1.5-.004*ld+rng.uniform(-.03,.03,rows)
  intercept=[None,1.0,.8,None,None,None][mode];threshold=30.0 if mode==4 else 8.0 if mode==5 else None
  if threshold is not None:
   y[0]=threshold;y[-1]=threshold+4;y[rows//3]=threshold+8;y[rows//2]=threshold
  assert np.all(y>=0)
  weights=[];stages=[];calls={}
  def observer(frame,event,arg):
   name=frame.f_code.co_name;file=Path(frame.f_code.co_filename)
   if name=='weights' and file.name=='regressions.py':
    if event=='call':calls[id(frame)]=dict(h2=float(np.asarray(frame.f_locals['hsq']).flat[0]),intercept=float(np.asarray(frame.f_locals['intercept']).flat[0]))
    elif event=='return':
     original=calls.pop(id(frame));original['weights']=np.asarray(arg).reshape(-1).copy();weights.append(original)
   if name=='irwls' and file.name=='irwls.py' and event=='return':
    stages.append(dict(weights=np.square(frame.f_locals['w']).reshape(-1).copy(),separators=np.asarray(arg.separators).tolist(),estimate=np.asarray(arg.est).tolist(),deletes=np.asarray(arg.delete_values).tolist()))
  saved=[]
  try:
   with warnings.catch_warnings(record=True) as seen:
    warnings.simplefilter('always');sys.setprofile(observer)
    try:fit=regressions.Hsq(y[:,None],ld[:,None],wld[:,None],n[:,None],np.array([[m]]),n_blocks=blocks,intercept=intercept,twostep=threshold)
    finally:sys.setprofile(None)
    saved=[dict(category=w.category.__name__,message=str(w.message)) for w in seen]
   expected={name:plain(getattr(fit,name)) for name in ['tot','tot_se','coef','coef_se','coef_cov','intercept','intercept_se','mean_chisq','lambda_gc','tot_delete_values']}
   expected['ratio']=plain(getattr(fit,'ratio',None));expected['ratio_se']=plain(getattr(fit,'ratio_se',None));expected['intercept_delete_values']=plain(getattr(fit,'intercept_delete_values',None));expected['jknife_cov']=plain(fit.jknife.jknife_cov);expected['jknife_delete_values']=plain(fit.jknife.delete_values)
   item=dict(id='C%03d'%case,chi_square=y.tolist(),ld=ld.tolist(),weight_ld=wld.tolist(),n=n.tolist(),reference_variants=m,blocks=blocks,fixed_intercept=intercept,two_step=threshold,expected=expected,weight_calls=plain(weights),stages=plain(stages),original_warnings=saved)
   json.dumps(item,allow_nan=False);cases.append(item)
  except Exception as error:
   sys.setprofile(None);errors.append(dict(id=case,error=repr(error),traceback=traceback.format_exc(),warnings=saved))
 assert all(pin(args.source/name)==value for name,value in before.items())
 report=dict(status='PASS_UNCHANGED_SELECTED_CORE_REFERENCE' if len(cases)==72 else 'FAIL_INCOMPLETE_REFERENCE',python=sys.version,numpy=np.__version__,scipy=scipy.__version__,pandas=pd.__version__,source_guards=before,package_initializer_executed=False,scope='Original CBIIT/ldsc3.0.2 Hsq numerical modules only; no full CLI, web wrapper, munging, LD-score computation or paper reproduction',cases=cases,original_errors=errors)
 (args.out/'reference.json').write_text(json.dumps(report,indent=2,allow_nan=False)+'\n',encoding='utf8')
 print('completed',len(cases),'models;',len(errors),'errors',flush=True)
 if errors:raise SystemExit(2)

if __name__=='__main__':main()
