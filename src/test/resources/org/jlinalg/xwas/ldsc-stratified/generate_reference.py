"""Unchanged selected original partitioned Hsq + overlapping-annotation output."""
import argparse,hashlib,importlib,json,math,sys,types,warnings,zipfile,traceback
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
 p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
 import numpy as np,pandas,scipy
 from scipy.stats import t
 package=types.ModuleType('ldscore');package.__path__=[str(a.source/'ldscore')];sys.modules['ldscore']=package
 reg=importlib.import_module('ldscore.regressions');jk=importlib.import_module('ldscore.jackknife')
 guards={n:pin(a.source/n) for n in ['ldscore/regressions.py','ldscore/irwls.py','ldscore/jackknife.py','ldscore/sumstats.py','ldscore/__init__.py','setup.py','LICENSE']}
 rng=np.random.RandomState(19900919);cases=[];errors=[];payload={};configs=['id\trows\tannotations\tblocks\tintercept\tuniverse\tannotation_counts'];fields=0
 def table(matrix):return '\n'.join('\t'.join(str(float(v)) for v in row) for row in np.asarray(matrix))+'\n'
 for index in range(48):
  try:
   ident='S%03d'%index;n=160+index;k=2+index%8;blocks=4+index%9;universe=1200+index*5
   if index%2:
    ann=np.zeros((universe,k));groups=rng.randint(k,size=universe);ann[np.arange(universe),groups]=1
   else:
    ann=(rng.uniform(size=(universe,k))<rng.uniform(.1,.7,size=k)).astype(float);ann[:,0]=1
   m=ann.sum(axis=0)[None,:];overlap=ann.T@ann
   ld=rng.uniform(-.25 if index%7==0 else 1.,35.,(n,k));wld=rng.uniform(-2,25,n);ns=rng.uniform(25000,80000,n)
   tau=rng.uniform(-.2,1.,k)*2e-7;fixed=[1.,.8,None][index%3];intercept=1.1 if fixed is None else fixed
   if index%10==8:tau=-np.abs(tau);intercept=3. if fixed is None else 4.
   if index%10==9:tau=np.abs(tau)*50000
   y=intercept+ns*(ld@tau)+rng.uniform(-.02,.02,n);assert np.all(y>=0)
   with warnings.catch_warnings(record=True) as seen:
    warnings.simplefilter('always');fit=reg.Hsq(y[:,None],ld,wld[:,None],ns[:,None],m,n_blocks=blocks,intercept=fixed,old_weights=True)
    output=fit._overlap_output(['A%d'%j for j in range(k)],overlap,m,universe,True)
   nbar=ns.mean();initial=float(reg.Hsq.aggregate(y[:,None],ld.sum(axis=1)[:,None],ns[:,None],m.sum(),fixed));weights=reg.Hsq.weights(ld.sum(axis=1)[:,None],wld[:,None],ns[:,None],m.sum(),initial,fixed)
   estimates=np.r_[fit.coef,float(fit.intercept)];deletes=np.column_stack([fit.jknife.delete_values[:,:k]/nbar,np.full(blocks,fixed) if fixed is not None else fit.jknife.delete_values[:,k]])
   scale=np.r_[np.full(k,nbar),1.];cov=np.zeros((k+1,k+1));q=k if fixed is not None else k+1;cov[:q,:q]=fit.jknife.jknife_cov/np.outer(scale[:q],scale[:q])
   numer=m*fit.jknife.delete_values[:,:k]/nbar;denom=np.repeat(numer.sum(axis=1)[:,None],k,axis=1);ratios=jk.RatioJackknife(fit.prop,numer,denom)
   contrast=np.zeros((k,k))
   for j in range(k):
    if universe!=m[0,j]:contrast[j]=overlap[j]/m[0,j]-(m[0]-overlap[j])/(universe-m[0,j])
   diff=contrast@fit.coef;diffse=np.sqrt(np.diag(contrast@fit.coef_cov@contrast.T));logp=['NA' if se==0 else min(0.,math.log(2)+float(t.logsf(abs(d/se),blocks))) for d,se in zip(diff,diffse)]
   expected=dict(meanSampleSize=nbar,initialH2=initial,weightingH2=min(1,max(0,initial)),weights=weights.reshape(-1),separators=fit.jknife.separators,estimates=estimates,deleteValues=deletes,covariance=cov,allocations=fit.cat.reshape(-1),allocationCovariance=fit.cat_cov,totalH2=fit.tot,totalStandardError=fit.tot_se,proportions=fit.prop.reshape(-1),proportionJackknifeEstimates=ratios.jknife_est.reshape(-1),proportionCovariance=fit.prop_cov,snpProportions=fit.M_prop.reshape(-1),enrichments=fit.enrichment.reshape(-1),interceptStandardError='NA' if fixed is not None else float(fit.intercept_se),meanChiSquare=fit.mean_chisq,lambdaGc=fit.lambda_gc,ratio='NA' if fixed is not None else fit.ratio,ratioStandardError='NA' if fixed is not None else fit.ratio_se)
   ov=dict(snpProportions=output['Prop._SNPs'].values,h2Proportions=output['Prop._h2'].values,h2ProportionStandardErrors=output['Prop._h2_std_error'].values,enrichments=output['Enrichment'].values,enrichmentStandardErrors=output['Enrichment_std_error'].values,enrichmentPValues=list(output['Enrichment_p'].values),enrichmentLogPValues=logp,coefficients=output['Coefficient'].values,coefficientStandardErrors=output['Coefficient_std_error'].values,coefficientZScores=output['Coefficient_z-score'].values,contrastEstimates=diff,contrastStandardErrors=diffse)
   configs.append('\t'.join([ident,str(n),str(k),str(blocks),'free' if fixed is None else str(fixed),str(universe),','.join(map(str,m[0]))]));payload[ident+'/input.tsv']=table(np.column_stack([y,wld,ns,ld]));payload[ident+'/overlap.tsv']=table(overlap)
   lines=['field\tvalue']
   def flatten(key,value):
    nonlocal fields
    value=plain(value)
    if isinstance(value,list):
     for j,v in enumerate(value):flatten(key+'.'+str(j),v)
    else:lines.append(key+'\t'+str(value));fields+=1
   for key,value in expected.items():flatten('fit.'+key,value)
   for key,value in ov.items():flatten('overlap.'+key,value)
   payload[ident+'/expected.tsv']='\n'.join(lines)+'\n';cases.append(dict(id=ident,input=dict(chi=y,ld=ld,weight_ld=wld,n=ns,annotation_counts=m,universe=universe,blocks=blocks,intercept=fixed),expected=expected,overlap_expected=ov,original_warnings=[str(w.message) for w in seen]))
  except Exception as error:errors.append(dict(index=index,error=repr(error),traceback=traceback.format_exc()))
 assert all(pin(a.source/n)==v for n,v in guards.items());assert not errors,errors
 payload['cases.tsv']='\n'.join(configs)+'\n';payload['provenance.json']=json.dumps(dict(source_guards=guards,seed=19900919,numpy=np.__version__,scipy=scipy.__version__,pandas=pandas.__version__,old_weights=True,original_functions_replaced=False,package_initializer_executed=False,fields=fields),indent=2)+'\n'
 with zipfile.ZipFile(a.out/'reference-fixtures.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,value in payload.items():z.writestr(name,value.encode())
 result=dict(status='PASS_UNCHANGED_ORIGINAL_PARTITIONED_HSQ_AND_OVERLAP',source_guards=guards,python=sys.version,numpy=np.__version__,scipy=scipy.__version__,pandas=pandas.__version__,models=len(cases),fields=fields,cases=cases,errors=errors)
 (a.out/'reference.json').write_text(json.dumps(plain(result),indent=2,allow_nan=False)+'\n',encoding='utf8');print('PASS',len(cases),'models',fields,'fields',flush=True)
if __name__=='__main__':main()
