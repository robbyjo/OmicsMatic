# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Call unchanged full-package Palantir multiscale methods; retain exact float64 outputs and unsupported source cases."""
import argparse,hashlib,json,os,sys,traceback,warnings
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--edition',required=True);p.add_argument('--dependencies',required=True,type=Path);p.add_argument('--package',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();assert not out.exists() and out.parent.is_dir();out.mkdir();assert sys.flags.ignore_environment==0 and sys.flags.safe_path and sys.flags.no_user_site and sys.flags.hash_randomization==0 and sys.flags.optimize==0 and sys.flags.dont_write_bytecode and os.environ['PYTHONHASHSEED']=='0';sys.path[:0]=[str(a.package),str(a.dependencies)]
    import numpy as np
    import pandas as pd
    import palantir
    import anndata as ad
    assert palantir.__version__==a.edition and np.__version__=='1.26.4' and pd.__version__=='2.2.3';function=palantir.utils.determine_multiscale_space;code=function.__code__;rng=np.random.default_rng(660044);cases=[]
    def bits(array):return [[format(int(v),'016x') for v in row] for row in np.asarray(array,dtype=np.float64).view(np.uint64).reshape(np.asarray(array).shape)]
    def vector(array):return [format(int(v),'016x') for v in np.asarray(array,dtype=np.float64).view(np.uint64).ravel()]
    def state():
        current=np.random.get_state();return [current[0],current[1].tolist(),int(current[2]),int(current[3]),float(current[4])]
    def run(name,values,n=None,rows=5,columns=None,vectors=None,mode='dict-array',native='supported',error=None,cells=None):
        values=np.asarray(values,dtype=np.float64);width=len(values) if columns is None else columns;vectors=np.asarray(vectors,dtype=np.float64) if vectors is not None else rng.standard_normal((rows,width));cells=cells if cells is not None else ['cell-'+str(i) for i in range(len(vectors))];frame=pd.DataFrame(vectors,index=cells);eigenvalues=values.copy()
        if mode=='dict-series':eigenvalues=pd.Series(eigenvalues)
        if mode=='dict-series-reordered':eigenvalues=pd.Series(eigenvalues,index=[2,0,1]);native='source-only-series-alignment'
        if mode=='dict-float32':eigenvalues=values.astype(np.float32);native='source-only-float32-eigenvalues'
        if mode=='anndata':data=ad.AnnData(np.zeros((len(vectors),1)),obs=pd.DataFrame(index=cells));data.obsm['DM_EigenVectors']=vectors.copy();data.uns['DM_EigenValues']=values.copy()
        else:data=dict(EigenVectors=frame,EigenValues=eigenvalues)
        record=dict(id=name,n_eigs=n,rows=len(vectors),vector_columns=width,cells=cells,eigenvalues_bits=vector(values),eigenvectors_bits=bits(vectors),mode=mode,native_scope=native,expected_error=error);before=state()
        try:
            with warnings.catch_warnings(record=True) as seen:
                warnings.simplefilter('always');returned=function(data,n_eigs=n)
            record['source_warnings']=[dict(type=v.category.__name__,message=str(v.message)) for v in seen];assert error is None,'Expected original error was absent';assert isinstance(returned,pd.DataFrame) and list(returned.index)==cells and list(returned.columns)==list(range(returned.shape[1]));record.update(source_status='returned-complete-family',output_columns=returned.shape[1],coordinates_bits=bits(returned.to_numpy()),actual_n_eigs=returned.shape[1]+1 if n is None else n)
            if mode=='anndata':assert np.array_equal(data.obsm['DM_EigenVectors_multiscaled'],returned.values,equal_nan=True);record['source_anndata_output_written']=True
            if native in ['supported','requires-upstream-tie-order']:
                indices=list(range(1,record['actual_n_eigs']))
                with np.errstate(all='ignore'):weights=values[indices]/(1-values[indices])
                record['weights_bits']=vector(weights)
        except Exception as failure:
            record.update(source_status='original-error',source_error_type=type(failure).__name__,source_error=str(failure),traceback=traceback.format_exc());assert error==type(failure).__name__,(name,error,type(failure).__name__)
        assert state()==before,'Original multiscale calculation changed global RNG state';assert palantir.utils.determine_multiscale_space is function and function.__code__ is code;assert bits(frame.values)==record['eigenvectors_bits'] and vector(values)==record['eigenvalues_bits'];record['global_rng_unchanged']=True;cases.append(record)
    state_report=dict(status='FAIL2_PALANTIR_MULTISCALE_ORIGINAL_REFERENCE',edition=a.edition,namespace_functions_patched=False,pickle_loaded=False,whole_goal_complete=False,implementation_credit=False)
    try:
        for n in [-2147483648,-5,0,1,2,3,4,5]:run('explicit-bound-'+str(n),[1,.9,.7,.4,.1],n,mode='dict-series' if n%2 else 'dict-array')
        for name,vals in [('largest-later',[1,.984375,.96875,.953125,.125]),('second-later',[1,.5,.484375,.46875,.125]),('minimum-three',[1,.5,.125,.0625,.03125]),('negative-spectrum',[1,.75,.25,-.25,-.5]),('tie-low-pair',[1,.75,.5,.375]),('tie-low-high-same-bound',[1,.75,.625,.5,.25]),('unique-high-other-ties',[1,.875,.75,.625,.125])]:run('automatic-'+name,vals)
        run('automatic-ambiguous-flat',[1,1,1,1,1],native='requires-upstream-tie-order');run('automatic-ambiguous-second',[1,.5,.375,.25,.125],native='requires-upstream-tie-order')
        for mode in ['dict-array','dict-series','anndata']:run('container-'+mode,[1,.8,.5,.1],4,mode=mode)
        run('duplicate-unicode-empty-identities',[1,.8,.4],3,cells=['001','cell-\U0001f9ec','','duplicate','duplicate']);run('empty-cell-family',[1,.8,.4],3,rows=0,vectors=np.empty((0,3)));run('empty-eigenpair-family',[],0,rows=4,columns=0,vectors=np.empty((4,0)))
        run('nonfinite-source-products',[1,1,np.nan,np.inf,-np.inf],5,vectors=[[0,0,1,2,-2],[1,-0.,0,0,-0.],[2,1,-1,1,1]])
        run('near-one-and-overflow',[1,np.nextafter(1.,0),np.nextafter(1.,2),-0.],4,vectors=[[1,np.finfo(float).max,np.finfo(float).max,-0.],[0,-0.,0.,1.]])
        run('signed-zero-propagation',[1,-0.,0.,-.5],4,vectors=[[1,2,-2,-0.],[1,-2,2,0.]])
        run('nan-leading-auto',[np.nan,1,.5],None);run('large-complete-family',np.linspace(1,.05,17),17,rows=1000)
        for i in range(16):
            size=4+i%8;vals=np.r_[1.,np.sort(rng.uniform(-.6,.999,size-1))[::-1]];run('random-ordered-'+str(i),vals,None if i%2 else size,rows=7+i)
        for name,vals,n,cols in [('auto-empty',[],None,0),('auto-one',[1],None,1),('auto-two',[1,.5],None,2),('bound-too-large',[1,.8,.4],4,3),('vector-position-absent',[1,.8,.4,.2],4,2),('spectrum-position-absent',[1,.8],4,4)]:run(name,vals,n,columns=cols,error='IndexError')
        run('noncanonical-integer-series-alignment',[1,.8,.4],3,mode='dict-series-reordered');run('float32-eigenvalue-ufunc-profile',[1,.83,.42],3,mode='dict-float32')
        target=out/'cases.json';target.write_text(json.dumps(dict(schema_version=1,edition=a.edition,cases=cases),indent=2)+'\n',encoding='utf8');state_report.update(status='PASS_UNCHANGED_FULL_PACKAGE_PALANTIR_MULTISCALE_REFERENCES_ONLY',cases=len(cases),native_numeric_cases=sum(v['native_scope']=='supported' and v['source_status']=='returned-complete-family' for v in cases),native_tie_deferrals=sum(v['native_scope']=='requires-upstream-tie-order' for v in cases),original_errors=sum(v['source_status']=='original-error' for v in cases),source_only_profiles=sum(v['native_scope'].startswith('source-only') for v in cases),source_module=str(Path(palantir.utils.__file__).resolve()),source_module_sha256=hashlib.sha256(Path(palantir.utils.__file__).read_bytes()).hexdigest(),case_bytes=target.stat().st_size,case_sha256=hashlib.sha256(target.read_bytes()).hexdigest(),effective_hash_seed='0',numpy=np.__version__,pandas=pd.__version__)
    except Exception as failure:state_report['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    (out/'COMPLETED.json').write_text(json.dumps(state_report,indent=2)+'\n',encoding='utf8');print(state_report['status'],state_report.get('error',''));return 0 if state_report['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable unchanged-original reference checks')
    raise SystemExit(main())
