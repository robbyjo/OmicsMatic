"""Run unchanged selected CBIIT RG/Gencov/Hsq numerical modules, without its web initializer."""
import argparse,hashlib,importlib,json,math,sys,types,warnings,traceback
from pathlib import Path
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def plain(x):
 import numpy as np
 if isinstance(x,np.ndarray):return plain(x.tolist())
 if isinstance(x,np.generic):return plain(x.item())
 if isinstance(x,(list,tuple)):return [plain(v) for v in x]
 if isinstance(x,dict):return {k:plain(v) for k,v in x.items()}
 if isinstance(x,float) and not math.isfinite(x):return 'NaN' if math.isnan(x) else 'Inf' if x>0 else '-Inf'
 return x
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
 import numpy as np,pandas,scipy
 package=types.ModuleType('ldscore');package.__path__=[str(a.source/'ldscore')];sys.modules['ldscore']=package
 reg=importlib.import_module('ldscore.regressions');importlib.import_module('ldscore.irwls');importlib.import_module('ldscore.jackknife')
 guards={n:pin(a.source/n) for n in ['ldscore/regressions.py','ldscore/irwls.py','ldscore/jackknife.py','ldscore/__init__.py','setup.py','LICENSE']}
 rng=np.random.RandomState(19870625);cases=[];errors=[]
 for index in range(72):
  mode=index%9;rows=192+index;blocks=4+index%11;m=1e6*(1+index%3)
  n1=rng.uniform(30000,90000,rows);n2=rng.uniform(20000,70000,rows);ld=rng.uniform(-1,60,rows);wld=rng.uniform(-1,40,rows)
  y1=1.1+n1/m*.35*ld+rng.uniform(-.04,.04,rows);y2=1.05+n2/m*.28*ld+rng.uniform(-.04,.04,rows)
  signs=rng.choice([-1.,1.],rows);flips=np.where(rng.uniform(size=rows)<(.2 if mode%2 else .8),-1.,1.)
  i1=i2=ic=None;threshold=None
  if mode==1:i1=i2=1.;ic=0.
  if mode==2:i2=.8
  if mode==3:ic=.15
  if mode in [4,5]:
   threshold=36. if mode==4 else 16.;root=math.sqrt(threshold)
   y1[0]=threshold;y2[-1]=(root+1)**2;y1[rows//3]=(root+2)**2;y2[rows//2]=threshold
  if mode==6:y1=1.8-.008*ld+rng.uniform(-.02,.02,rows)
  if mode in [7,8]:
   rows=160;blocks=4;n1=np.full(rows,50000.);n2=np.full(rows,40000.);m=1e6
   ld=np.tile(np.linspace(1,40,40),4);wld=ld/2;signs=np.ones(rows);flips=np.ones(rows)
   y1=2+ld*np.repeat([.05,-.01,-.01,-.01],40)
   y2=1.7+ld*np.repeat([.04,-.008,-.008,-.008],40) if mode==8 else 1.1+.03*ld
  assert np.all(y1>=0) and np.all(y2>=0)
  z1=signs*np.sqrt(y1);z2=signs*flips*np.sqrt(y2);stages=[];weight_calls=[];calls={}
  def observer(frame,event,arg):
   name=frame.f_code.co_name;filename=Path(frame.f_code.co_filename).name
   if name=='weights' and filename=='regressions.py':
    if event=='call':
     local=frame.f_locals;kind='Gencov' if 'rho_g' in local else 'Hsq';calls[id(frame)]=dict(kind=kind,estimate=float(np.asarray(local['rho_g' if kind=='Gencov' else 'hsq']).flat[0]),intercept=plain(local.get('intercept_gencov') if kind=='Gencov' else local.get('intercept')))
    elif event=='return':record=calls.pop(id(frame));record['weights']=plain(np.asarray(arg).reshape(-1));weight_calls.append(record)
   if name=='irwls' and filename=='irwls.py' and event=='return':stages.append(dict(weights=plain(np.square(frame.f_locals['w']).reshape(-1)),separators=plain(arg.separators)))
  try:
   with warnings.catch_warnings(record=True) as seen:
    warnings.simplefilter('always');sys.setprofile(observer)
    try:fit=reg.RG(z1[:,None],z2[:,None],ld[:,None],wld[:,None],n1[:,None],n2[:,None],np.array([[m]]),intercept_hsq1=i1,intercept_hsq2=i2,intercept_gencov=ic,n_blocks=blocks,twostep=threshold)
    finally:sys.setprofile(None)
    observed=[dict(category=w.category.__name__,message=str(w.message)) for w in seen]
   def component(x):return {name:plain(getattr(x,name,None)) for name in ['tot','tot_se','coef','coef_se','intercept','intercept_se','tot_delete_values','intercept_delete_values','mean_chisq','lambda_gc','mean_z1z2','p','z']}
   expected=dict(hsq1=component(fit.hsq1),hsq2=component(fit.hsq2),gencov=component(fit.gencov),rg={name:plain(getattr(fit,name,None)) for name in ['rg_ratio','rg_se','rg_jknife','p','z','_negative_hsq']},source_summary=fit.summary(),source_summary_raw=fit.summary(silly=True))
   case=dict(id='R%03d'%index,mode=mode,z1=plain(z1),z2=plain(z2),ld=plain(ld),weight_ld=plain(wld),n1=plain(n1),n2=plain(n2),reference_variants=m,blocks=blocks,intercept_hsq1=i1,intercept_hsq2=i2,intercept_gencov=ic,two_step=threshold,expected=expected,stages=stages,weight_calls=weight_calls,original_warnings=observed);json.dumps(case,allow_nan=False);cases.append(case)
  except Exception as error:sys.setprofile(None);errors.append(dict(index=index,error=repr(error),traceback=traceback.format_exc()))
 assert all(pin(a.source/n)==v for n,v in guards.items())
 result=dict(status='PASS_UNCHANGED_ORIGINAL_RG_CORE' if not errors else 'FAIL_INCOMPLETE_REFERENCE',python=sys.version,numpy=np.__version__,scipy=scipy.__version__,pandas=pandas.__version__,source_guards=guards,package_initializer_executed=False,original_function_replaced=False,cases=cases,errors=errors)
 (a.out/'reference.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n',encoding='utf8');print('completed',len(cases),'models',len(errors),'errors',flush=True)
 if errors:raise SystemExit(2)
if __name__=='__main__':main()
