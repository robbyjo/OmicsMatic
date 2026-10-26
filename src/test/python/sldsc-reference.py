"""Reproducible synthetic S-LDSC references: NumPy QR plus pinned upstream LDSC.

First fetch the four upstream sources listed by PROVENANCE.json into
build/sldsc-upstream/ldscore. The upstream estimator is executed unchanged except
for Python 2 xrange/print syntax and import-path compatibility, in memory. Neither
the test fixture nor its small synthetic LD scores establish GWAS calibration.
"""
import hashlib
import json
import pathlib
import re
import sys
import types
import numpy as np
import scipy
from scipy.stats import t

ROOT=pathlib.Path(__file__).resolve().parents[1]/'resources'/'sldsc'
UPSTREAM=pathlib.Path('build/sldsc-upstream')
COMMIT='2fdeeb3b44379408794154993dbd6101b8946b7e'

def upstream():
    provenance=json.loads((UPSTREAM/'PROVENANCE.json').read_text(encoding='utf-8'))
    assert provenance['commit']==COMMIT
    for name in ['jackknife','irwls','regressions']:
        raw=(UPSTREAM/'ldscore'/f'{name}.py').read_bytes()
        assert hashlib.sha256(raw).hexdigest()==provenance['sources'][f'ldscore/{name}.py']['sha256']
        module=types.ModuleType(name);module.__dict__['xrange']=range
        sys.modules[name]=module
        code=re.sub(r'^(\s*)print (.*)$',r'\1print(\2)',raw.decode('utf-8'),flags=re.MULTILINE)
        exec(compile(code,str(UPSTREAM/'ldscore'/f'{name}.py'),'exec'),module.__dict__)
    return sys.modules['regressions'],provenance

def jack_cov(deletes):
    return np.atleast_2d(np.cov(deletes,rowvar=False,ddof=1))*(len(deletes)-1)**2/len(deletes)

def reference(chi,ld,wld,ns,m,overlap,blocks,free):
    b=blocks.max()+1;c=len(m);nbar=ns.mean()
    aggregate=m.sum()*(chi.mean()-1)/(ns*ld.sum(axis=1)).mean()
    expected=1+np.clip(aggregate,0,1)*ns*np.maximum(1,ld.sum(axis=1))/m.sum()
    w=1/(2*expected**2*np.maximum(1,wld))
    x=ld*ns[:,None]/nbar;y=chi.copy()
    if free:x=np.column_stack((x,np.ones(len(x))))
    else:y-=1
    def solve(mask):
        a=x[mask]*np.sqrt(w[mask,None]);r=y[mask]*np.sqrt(w[mask])
        q,rr=np.linalg.qr(a,mode='reduced');coef=np.linalg.solve(rr,q.T@r)
        coef[:c]/=nbar
        return coef
    fitted=solve(np.ones(len(x),dtype=bool));deleted=np.array([solve(blocks!=i) for i in range(b)])
    co=fitted[:c];dc=deleted[:,:c];cov=jack_cov(dc);total=m@co;dt=dc@m
    category=overlap@co;prop=category/total;pr_se=np.sqrt(np.diag(jack_cov((dc@overlap.T)/dt[:,None])))
    enrich=prop/(m/m[0]);ese=pr_se/(m/m[0]);contrast=np.full(c,np.nan);cse=contrast.copy();p=contrast.copy()
    for i in range(1,c):
        a=overlap[i]/m[i]-(m-overlap[i])/(m[0]-m[i]);contrast[i]=a@co;cse[i]=np.sqrt(a@cov@a);p[i]=2*t.sf(abs(contrast[i]/cse[i]),b)
    return {'coefficients':co,'coefficient_covariance':cov.ravel(),'intercept':fitted[c] if free else 1.,'intercept_se':np.sqrt(jack_cov(deleted[:,c:c+1])[0,0]) if free else 0.,'total':total,'total_se':np.sqrt(m@cov@m),'category':category,'proportions':prop,'proportion_se':pr_se,'enrichments':enrich,'enrichment_se':ese,'contrasts':contrast,'contrast_se':cse,'p_values':p,'delete_coefficients':dc,'delete_intercepts':deleted[:,c] if free else np.ones(b),'delete_totals':dt,'weights':w,'mean_chi_square':chi.mean(),'aggregate_weight_heritability':aggregate}

def clean(obj):
    if isinstance(obj,np.ndarray):return clean(obj.tolist())
    if isinstance(obj,list):return [clean(x) for x in obj]
    if isinstance(obj,dict):return {k:clean(v) for k,v in obj.items()}
    if isinstance(obj,(float,np.floating)):return float(obj) if np.isfinite(obj) else None
    return obj

def main():
    ROOT.mkdir(parents=True,exist_ok=True);rng=np.random.default_rng(327041)
    ref=np.ones((12000,4));ref[:,1]=rng.binomial(1,.2,len(ref));ref[:,2]=rng.binomial(1,.08+.45*ref[:,1]);ref[:,3]=rng.binomial(1,.33,len(ref))
    m=ref.sum(axis=0);overlap=ref.T@ref
    n=483;blocks=np.searchsorted(np.linspace(0,n,14,dtype=int)[1:-1],np.arange(n),side='right');ns=rng.integers(20000,45000,n).astype(float)
    ld=np.column_stack((rng.gamma(5,2,n),rng.gamma(3,1,n),rng.gamma(2,1,n),rng.gamma(4,1,n)))
    ld[:,0]+=ld[:,1:].sum(axis=1)*.4;wld=rng.gamma(3,3,n)
    tau=np.array([8e-6,14e-6,-3e-6,4e-6]);chi=1.12+ns*(ld@tau)+rng.uniform(-.06,.06,n)
    ids=['ALL','TARGET_A','TARGET_B','NUISANCE'];roles=['all','target','target','nuisance']
    with (ROOT/'annotations.tsv').open('w',encoding='utf-8',newline='\n') as f:
        f.write('term_id\tterm_name\trole\tcount\n')
        for name,role,count in zip(ids,roles,m):f.write(f'{name}\tSynthetic {name}\t{role}\t{count:.0f}\n')
    with (ROOT/'overlap.tsv').open('w',encoding='utf-8',newline='\n') as f:
        f.write('term_id\t'+'\t'.join(ids)+'\n')
        for name,row in zip(ids,overlap):f.write(name+'\t'+'\t'.join(f'{v:.0f}' for v in row)+'\n')
    with (ROOT/'ld-scores.tsv').open('w',encoding='utf-8',newline='\n') as f:
        f.write('variant_id\tchromosome\tposition\tref\talt\tweight_ld\t'+'\t'.join('ld_'+x for x in ids)+'\n')
        for i in range(n):f.write(f'rs{i+1}\t{1+i//23}\t{10000+(i%23)*100}\tA\tG\t'+ '\t'.join(f'{v:.17g}' for v in [wld[i],*ld[i]])+'\n')
    with (ROOT/'summary.tsv').open('w',encoding='utf-8',newline='\n') as f:
        f.write('variant_id\tchromosome\tposition\tref\talt\tchi_square\tn\n')
        for i in range(n):f.write(f'rs{i+1}\t{1+i//23}\t{10000+(i%23)*100}\tA\tG\t{chi[i]:.17g}\t{ns[i]:.0f}\n')
    reg,provenance=upstream();cases=[]
    for free in [True,False]:
        expected=reference(chi,ld,wld,ns,m,overlap,blocks,free)
        actual=reg.Hsq(chi[:,None],ld.copy(),wld[:,None],ns[:,None],m[None,:],n_blocks=13,intercept=None if free else 1.,old_weights=True)
        df=actual._overlap_output(ids,overlap,m[None,:],m[0],True)
        for name,left,right in [('coefficients',expected['coefficients'],actual.coef),('covariance',expected['coefficient_covariance'],actual.coef_cov.ravel()),('total',expected['total'],actual.tot),('total_se',expected['total_se'],actual.tot_se),('proportion',expected['proportions'],df['Prop._h2']),('enrichment',expected['enrichments'],df['Enrichment']),('enrichment_se',expected['enrichment_se'][1:],df['Enrichment_std_error'][1:]),('p',expected['p_values'][1:],np.array(df['Enrichment_p'][1:],dtype=float)),('deletes',expected['delete_coefficients'],actual.part_delete_values)]:
            assert np.allclose(left,right,rtol=1e-8,atol=1e-12),(free,name,left,right)
        cases.append({'free_intercept':free,'expected':clean(expected)})
    document={'commit':COMMIT,'seed':327041,'rows':n,'blocks':13,'annotation_counts':m.tolist(),'cases':cases,'scope':'Synthetic numerical parity only; not biological LD reference or calibration evidence'}
    (ROOT/'reference.json').write_text(json.dumps(document,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    provenance.update({'numpy':np.__version__,'scipy':scipy.__version__,'python':sys.version,'generator_sha256':hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),'files':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(ROOT.iterdir()) if p.name!='PROVENANCE.json'},'compatibility_changes':['in-memory xrange=range; Python 2 print statements parenthesized; module import path registration; no estimator edits']})
    (ROOT/'PROVENANCE.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'cases':len(cases),'rows':n,'upstream':COMMIT,'independent_qr_matches_upstream':True}))

if __name__=='__main__':main()
