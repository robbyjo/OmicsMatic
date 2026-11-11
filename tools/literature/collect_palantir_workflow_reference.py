# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Discover complete original diffusion/trajectory outputs and source dispositions for the execution adapter. No pickle inputs or fitting credit."""
import argparse,json,os,sys,traceback,warnings
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--edition',required=True);p.add_argument('--dependencies',required=True,type=Path);p.add_argument('--package',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();assert not out.exists();out.mkdir();assert sys.flags.ignore_environment==0 and sys.flags.safe_path and sys.flags.no_user_site and sys.flags.hash_randomization==0 and sys.flags.dont_write_bytecode and not sys.flags.optimize and os.environ['PYTHONHASHSEED']=='0';sys.path[:0]=[str(a.package),str(a.dependencies)]
    import numpy as np
    import pandas as pd
    import palantir
    import anndata as ad
    from scipy import sparse
    assert palantir.__version__==a.edition;core=palantir.core;rng=np.random.default_rng(75420);t=np.tile(np.linspace(0,1,48),2);branch=np.repeat([-1,1],48);raw=np.column_stack([4*t,branch*t**1.2,t*t,.2*t])+rng.normal(0,.003,(96,4));ids=['cell-'+str(i).zfill(3) for i in range(len(raw))];features=pd.DataFrame(raw,index=ids,columns=['PC1','PC2','PC3','PC4']);cases=[]
    def fp(values):return dict(shape=list(np.asarray(values).shape),float64_bits=[format(int(v),'016x') for v in np.asarray(values,dtype=np.float64).view(np.uint64).ravel()])
    def clean(value):
        if value is None or isinstance(value,(str,bool,int,float)):return value
        if isinstance(value,np.generic):return value.item()
        if isinstance(value,pd.DataFrame):return dict(kind='dataframe',index=clean(list(value.index)),columns=clean(list(value.columns)),dtypes=[str(v) for v in value.dtypes],values=fp(value.to_numpy()))
        if isinstance(value,pd.Series):return dict(kind='series',index=clean(list(value.index)),name=clean(value.name),dtype=str(value.dtype),values=fp(value.to_numpy()))
        if isinstance(value,pd.Index):return dict(kind='index',values=clean(value.tolist()))
        if sparse.issparse(value):
            csr=value.tocsr();return dict(kind='csr',shape=list(csr.shape),data=fp(csr.data),indices=csr.indices.tolist(),indptr=csr.indptr.tolist())
        if isinstance(value,np.ndarray):return fp(value) if value.dtype.kind in 'fiu' else clean(value.tolist())
        if isinstance(value,(tuple,list)):return [clean(v) for v in value]
        if isinstance(value,dict):return {str(k):clean(v) for k,v in value.items()}
        raise TypeError('Unsupported source snapshot type '+str(type(value)))
    def save(path,value):path.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n',encoding='utf8')
    def rng_state():
        current=np.random.get_state();return dict(kind=current[0],keys=current[1].tolist(),position=int(current[2]),has_gaussian=int(current[3]),cached_gaussian=fp(np.array([current[4]])))
    observed=[(core,n,getattr(core,n),getattr(core,n).__code__) for n in ['run_palantir','_compute_pseudotime','_construct_markov_chain','_differentiation_entropy']];constructor=palantir.presults.PResults.__init__;constructor_code=constructor.__code__
    class Observer:
        def __init__(self,home):self.home=home;self.codes={v[3]:v[1] for v in observed[1:]};self.records=[];self.counter=0
        def global_trace(self,frame,event,arg):
            if event=='call' and frame.f_code==constructor_code:
                save(self.home/'observed-before-result-clipping.json',{k:clean(frame.f_locals[k]) for k in ['pseudotime','entropy','branch_probs','waypoints']});return None
            if event=='call' and frame.f_code in self.codes:frame.f_trace_lines=False;frame.f_trace_opcodes=False;return self.local_trace
            return None
        def local_trace(self,frame,event,value):
            if event=='return' and value is not None:
                name=self.codes[frame.f_code];self.counter+=1;file=self.home/('observed-'+str(self.counter)+'-'+name+'.json');save(file,dict(method=name,returned=clean(value),input_cell_axis=clean(frame.f_locals['wp_data'].index) if 'wp_data' in frame.f_locals else None));self.records.append(file.name)
            return self.local_trace
        def start(self):assert sys.gettrace() is None;sys.settrace(self.global_trace)
        def close(self):sys.settrace(None)
    def run(name,mode='core',backend='scanpy',terminal='explicit',root='cell-000',use_root=True,scale=True,annotations=False,expected_error=None):
        home=out/name;home.mkdir();record=dict(id=name,mode=mode,kernel_backend=backend,terminal_policy=terminal,early_cell=root,use_early_cell_as_start=use_root,scale_components=scale,knn=10,num_waypoints=24,max_iterations=25,seed=20,diffusion_seed=4,fixture_caller_numpy_seed=71651,partial_terminal_request_requires_adapter_rejection=terminal=='partial',expected_error=expected_error);data=features.copy();observer=Observer(home);warnings_seen=[];np.random.seed(71651);save(home/'global-rng-before.json',rng_state())
        try:
            # Preserve upstream warning filters; observation does not replace functions, locals, RNG or algorithms.
            with warnings.catch_warnings(record=True) as warnings_seen:
                if mode in ['pipeline','anndata']:
                    if mode=='anndata':data=ad.AnnData(np.zeros((len(ids),1)),obs=pd.DataFrame(index=ids));data.obsm['X_pca']=features.values.copy()
                    dm=palantir.utils.run_diffusion_maps(data,n_components=8,knn=10,alpha=0,seed=4,kernel_backend=backend);save(home/'diffusion-return.json',clean(dm));ms=palantir.utils.determine_multiscale_space(data if mode=='anndata' else dm,n_eigs=6);save(home/'multiscale-return.json',clean(ms));data=data if mode=='anndata' else ms
                terminals=None if terminal=='auto' else ['cell-047','cell-095'] if terminal=='explicit' else ['cell-047','absent-terminal'] if terminal=='partial' else ['absent-terminal']
                if annotations:terminals=pd.Series({'cell-047':'branch-A','cell-095':'branch-B'})
                observer.start()
                try:result=core.run_palantir(data,early_cell=root,terminal_states=terminals,knn=10,num_waypoints=24,n_jobs=1,scale_components=scale,use_early_cell_as_start=use_root,max_iterations=25,seed=20,save_as_df=True)
                finally:observer.close()
                assert expected_error is None,'Expected source error was absent';save(home/'returned.json',{name:clean(getattr(result,name)) for name in ['pseudotime','entropy','branch_probs','waypoints']});record.update(status='complete-original-result-only',cells=len(result.pseudotime),fate_columns=len(result.branch_probs.columns),waypoints=len(result.waypoints),preclip_state_recorded=(home/'observed-before-result-clipping.json').exists(),partial_terminal_request_requires_adapter_rejection=terminal=='partial',original_post_constructor_probability_threshold=.01,entropy_is_computed_before_constructor_clipping=True)
                if mode=='anndata':save(home/'anndata-written-results.json',dict(obs=clean(data.obs[['palantir_pseudotime','palantir_entropy']]),fates=clean(data.obsm['palantir_fate_probabilities']),waypoints=clean(data.uns['palantir_waypoints'])))
        except Exception as failure:
            record.update(status='original-error',error_type=type(failure).__name__,error=str(failure),traceback=traceback.format_exc());assert expected_error==type(failure).__name__,(name,expected_error,type(failure).__name__)
        finally:
            observer.close();save(home/'global-rng-after.json',rng_state());record['original_warnings']=[dict(type=v.category.__name__,message=str(v.message)) for v in warnings_seen];record['data_only_observations']=observer.records
        assert all(getattr(module,n) is function and function.__code__ is code for module,n,function,code in observed) and palantir.presults.PResults.__init__ is constructor and constructor.__code__ is constructor_code;save(home/'CASE.json',record);cases.append(record)
    state=dict(status='FAIL2_PALANTIR_FULL_WORKFLOW_SOURCE_DISCOVERY',edition=a.edition,namespace_functions_patched=False,pickle_loaded=False,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        run('core-explicit-terminals');run('core-unscaled-explicit-terminals',scale=False);run('core-boundary-root',use_root=False);run('core-auto-terminals',terminal='auto');run('pipeline-scanpy',mode='pipeline');run('pipeline-exact-sklearn',mode='pipeline',backend='sklearn');run('anndata-annotated-terminal-output',mode='anndata',annotations=True)
        if a.edition=='1.4.6':run('multiple-explicit-start-cells',root=['cell-000','cell-048'])
        run('source-partial-terminal-disposition',terminal='partial',expected_error='KeyError');run('source-missing-early-cell',root='absent-cell',expected_error='ValueError');run('source-missing-terminal-cells',terminal='missing',expected_error='KeyError');state.update(status='PASS_COMPLETE_ORIGINAL_PALANTIR_WORKFLOW_SOURCE_DISCOVERY_ONLY',cases=cases,successful_original_cases=sum(v['status']=='complete-original-result-only' for v in cases),expected_original_errors=sum(v['status']=='original-error' for v in cases),partial_identifier_dispositions=sum(v.get('partial_terminal_request_requires_adapter_rejection',False) for v in cases),source_warning_filters_preserved=True)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source workflow observations')
    raise SystemExit(main())
