# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Unchanged original Palantir workflows with data-only observations; never load a pickle or install software."""
import hashlib,inspect,json,os,sys,traceback
from pathlib import Path

def startup(home,request):
    seed=request['python_hash_seed'];assert type(seed) is int and 0<=seed<=4294967295
    flags={name:getattr(sys.flags,name) for name in ['ignore_environment','isolated','safe_path','no_user_site','hash_randomization','optimize','dont_write_bytecode']};assert flags==dict(ignore_environment=0,isolated=0,safe_path=True,no_user_site=1,hash_randomization=0 if seed==0 else 1,optimize=0,dont_write_bytecode=1) and os.environ.get('PYTHONHASHSEED')==str(seed)
    assert sys.version_info[:3]==(3,11,17) and (sys.hash_info.algorithm,sys.hash_info.width,sys.hash_info.hash_bits,sys.hash_info.seed_bits)==('siphash13',64,64,128)
    value=dict(schema_version=1,runner_revision=2,requested_seed=seed,environment_seed=str(seed),flags=flags,python_executable=sys.executable,python_version='3.11.17',hash_algorithm='siphash13',hash_width=64,hash_bits=64,seed_bits=128,hashes=[dict(text=text,hash=hash(text)) for text in ['','Spyrrow ligand','caf\u00e9','ligand\u0100','cell \U0001f9ec','#123']],before_scientific_imports=True)
    with (home/'python-startup.json').open('xb') as stream:stream.write((json.dumps(value,indent=2)+'\n').encode())

def main():
    home=Path(sys.argv[1]);request_path=Path(sys.argv[2]);request=json.loads(request_path.read_bytes());startup(home,request);runtime=json.loads((home/'runtime.original.json').read_bytes());prefix=Path(runtime['runtime_environment']);edition=request['edition'];assert edition in ['1.4.4','1.4.6'];sys.path[:0]=[str(prefix/'editions'/edition),str(prefix/'dependencies'),str(home)]
    import numpy as np
    import pandas as pd
    import palantir
    import anndata as ad
    from scipy import sparse
    from codec import clean,save,array
    assert palantir.__version__==edition and np.__version__=='1.26.4' and pd.__version__=='2.2.3';assert Path(palantir.__file__).resolve().is_relative_to(prefix/'editions'/edition)
    def typed(value):
        if isinstance(value,ad.AnnData):return dict(kind='anndata-coordinate-container',cells=list(value.obs_names),obsm={k:typed(value.obsm[k]) for k in ['X_pca','DM_EigenVectors','DM_EigenVectors_multiscaled'] if k in value.obsm},uns={k:typed(value.uns[k]) for k in ['DM_EigenValues'] if k in value.uns})
        if sparse.issparse(value):
            csr=value.tocsr();return dict(kind='csr',shape=list(csr.shape),data=array(csr.data),indices=array(csr.indices),indptr=array(csr.indptr))
        if isinstance(value,dict):return {str(k):typed(v) for k,v in value.items()}
        if isinstance(value,(tuple,list)):return [typed(v) for v in value]
        return clean(value)
    def rng_state():return typed(dict(numpy_global=np.random.get_state()))
    functions=[(module,name,value,value.__code__) for module in [palantir.core,palantir.utils] for name,value in inspect.getmembers(module,inspect.isfunction) if value.__module__==module.__name__];constructor=palantir.presults.PResults.__init__;constructor_code=constructor.__code__
    def unchanged():assert all(module.__dict__[name] is function and function.__code__ is code for module,name,function,code in functions) and palantir.presults.PResults.__init__ is constructor and constructor.__code__ is constructor_code
    targets={getattr(palantir.utils,name).__code__:name for name in ['compute_kernel','diffusion_maps_from_kernel','determine_multiscale_space']};targets.update({getattr(palantir.core,name).__code__:name for name in ['_compute_pseudotime','_construct_markov_chain','_differentiation_entropy','_max_min_sampling']})
    class Observer:
        def __init__(self,out):self.out=out;self.files=[]
        def global_trace(self,frame,event,arg):
            if event=='call' and frame.f_code==constructor_code:save(self.out/'before-result-clipping.json',{k:typed(frame.f_locals[k]) for k in ['pseudotime','entropy','branch_probs','waypoints']});return None
            if event=='call' and frame.f_code in targets:frame.f_trace_lines=False;frame.f_trace_opcodes=False;return self.local_trace
            return None
        def local_trace(self,frame,event,value):
            if event=='return' and value is not None:
                name=targets[frame.f_code];file=self.out/('observed-'+name+'.json');assert not file.exists(),'Original stage repeated unexpectedly: '+name;keys=['data','data_df','wp_data','knn','alpha','backend','use_sklearn','kNN','W','kernel','adaptive_std','adaptive_k','adpative_k','dist','ind','start_cell','start_cells','waypoints','D_vals','sdv','iteration','corr','converged','T','abs_states','trans_states','terminal_states','pseudotime','n_eigs','use_eigs'];record=dict(method=name,returned=typed(value),locals={k:typed(frame.f_locals[k]) for k in keys if k in frame.f_locals});save(file,record);self.files.append(file.name)
            return self.local_trace
        def start(self):assert sys.gettrace() is None;sys.settrace(self.global_trace)
        def close(self):sys.settrace(None);unchanged()
    state=dict(status='FAIL2_ORIGINAL_PALANTIR_BATCH_UNADMITTED',namespace_patched=False,analysis_installed_software=False,pickle_deserialized=False,result_admitted=False);records=[]
    try:
        for specification in request['analyses']:
            name=specification['id'];assert Path(name).name==name and name not in ['.','..'];out=home/name;out.mkdir();observer=Observer(out);path=Path(specification['input']['prepared_json']);assert path==path.resolve() and path.is_file() and not path.is_symlink();raw=json.loads(path.read_bytes());assert raw['schema_version']==1 and raw['numeric_dtype']=='float64';cells=raw['cells'];columns=raw['columns'];values=np.asarray(raw['values'],dtype=np.float64);assert values.shape==(len(cells),len(columns)) and len(cells)<=request['maximum_cells'] and len(columns)<=request['maximum_components'] and values.size<=request['maximum_matrix_cells'] and len(set(cells))==len(cells) and len(set(columns))==len(columns) and all(isinstance(v,str) and v for v in [*cells,*columns]) and np.isfinite(values).all();data=pd.DataFrame(values,index=cells,columns=columns);save(out/'source-input.json',typed(data));np.random.seed(specification['global_seed']);save(out/'rng-before.json',rng_state());print('START',name,specification['method'],flush=True)
            try:
                observer.start();method=specification['method'];ms=data;container=None
                if specification['input']['interface']=='anndata':
                    container=ad.AnnData(np.empty((len(cells),0)),obs=pd.DataFrame(index=cells));container.obsm['DM_EigenVectors_multiscaled' if method=='trajectory' else 'X_pca']=values.copy();ms=container
                if method!='trajectory':
                    d=specification['diffusion'];diffusion=palantir.utils.run_diffusion_maps(container if container is not None else data,n_components=d['n_components'],knn=d['knn'],alpha=d['alpha'],seed=d['seed'],kernel_backend=d['kernel_backend']);save(out/'diffusion-return.json',typed(diffusion));coordinates=palantir.utils.determine_multiscale_space(container if container is not None else diffusion,n_eigs=d['n_eigs']);save(out/'multiscale-return.json',typed(coordinates));coordinates.to_csv(out/'multiscale.tsv',sep='\t',index_label='cell_id',float_format=lambda v:format(float(v),'.17g'));ms=container if container is not None else coordinates
                if method!='diffusion':
                    t=specification['trajectory'];starts=t['early_cells'];assert all(v in cells for v in starts);early=starts if edition=='1.4.6' and len(starts)>1 else starts[0];terminals=t['terminal_states']
                    if terminals is not None:
                        assert all(v['cell'] in cells for v in terminals),'Missing requested terminal IDs must not be partially matched';terminals=pd.Series({v['cell']:v['annotation'] for v in terminals}) if 'annotation' in terminals[0] else [v['cell'] for v in terminals]
                    wp=t['waypoints'];waypoints=wp['count'] if 'count' in wp else pd.Index(wp['cells']);result=palantir.core.run_palantir(ms,early_cell=early,terminal_states=terminals,knn=t['knn'],num_waypoints=waypoints,n_jobs=specification['threads'],scale_components=t['scale_components'],use_early_cell_as_start=t['use_early_cell_as_start'],max_iterations=t['max_iterations'],seed=t['seed'],save_as_df=True);save(out/'returned.json',{k:typed(getattr(result,k)) for k in ['pseudotime','entropy','branch_probs','waypoints']});pd.DataFrame({'pseudotime':result.pseudotime,'entropy':result.entropy}).to_csv(out/'pseudotime.tsv',sep='\t',index_label='cell_id',float_format=lambda v:format(float(v),'.17g'));result.branch_probs.to_csv(out/'fate-probabilities.tsv',sep='\t',index_label='cell_id',float_format=lambda v:format(float(v),'.17g'))
            finally:observer.close()
            save(out/'rng-after.json',rng_state());save(out/'observer.json',dict(namespace_functions_unchanged=True,code_objects_unchanged=True,method='data-only call/return observation; no function replacement, local assignment, RNG draw or pickle decoding; original warning filters unchanged',files=observer.files));records.append(dict(id=name,method=method,namespace_patched=False,pickle_deserialized=False));print('COMPLETE PROVISIONAL',name,flush=True)
        state.update(status='PASS_COMPLETE_ORIGINAL_PALANTIR_WORKFLOWS_ONLY',cases=records)
    except Exception as failure:state.update(error=repr(failure),earlier_cases_provisional=records);(home/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    unchanged();save(home/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/startup checks')
    raise SystemExit(main())
