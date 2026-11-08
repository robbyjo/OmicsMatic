# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete original CPU tree/root/pseudotime calls; retain primitive observations and source failures."""
import csv,importlib,inspect,json,os,sys,traceback,warnings
from pathlib import Path

def startup(home,request):
    seed=request['python_hash_seed'];assert type(seed) is int and 0<=seed<=4294967295
    flags={name:getattr(sys.flags,name) for name in ['ignore_environment','isolated','safe_path','no_user_site','hash_randomization','optimize','dont_write_bytecode']};assert flags==dict(ignore_environment=0,isolated=0,safe_path=True,no_user_site=1,hash_randomization=0 if seed==0 else 1,optimize=0,dont_write_bytecode=1) and os.environ.get('PYTHONHASHSEED')==str(seed)
    assert sys.version_info[:3]==(3,11,17) and (sys.hash_info.algorithm,sys.hash_info.width,sys.hash_info.hash_bits,sys.hash_info.seed_bits)==('siphash13',64,64,128)
    value=dict(schema_version=1,runner_revision=2,requested_seed=seed,environment_seed=str(seed),flags=flags,python_executable=sys.executable,python_version='3.11.17',hash_algorithm='siphash13',hash_width=64,hash_bits=64,seed_bits=128,hashes=[dict(text=text,hash=hash(text)) for text in ['','Spyrrow ligand','caf\u00e9','ligand\u0100','cell \U0001f9ec','#123']],before_scientific_imports=True)
    with (home/'python-startup.json').open('xb') as stream:stream.write((json.dumps(value,indent=2)+'\n').encode())

def main():
    home=Path(sys.argv[1]);request=json.loads(Path(sys.argv[2]).read_bytes());startup(home,request);runtime=json.loads((home/'runtime.original.json').read_bytes());prefix=Path(runtime['runtime_environment']);assert request['edition']=='scfates-1.2.5_simpleppt-1.1.7';sys.path[:0]=[str(prefix/'dependencies'),str(home)]
    import numpy as np
    import pandas as pd
    import anndata as ad
    import scFates as scf
    import simpleppt as spp
    from codec import Writer
    assert scf.__version__=='1.2.5' and spp.__version__=='1.1.7' and np.__version__=='1.26.4' and pd.__version__=='2.2.3'
    for module in [scf,spp,np,pd,ad]:assert Path(module.__file__).resolve().is_relative_to(prefix/'dependencies')
    modules=[importlib.import_module(n) for n in ['simpleppt.ppt','simpleppt.utils','scFates.tools.graph_fitting','scFates.tools.root','scFates.tools.pseudotime']];functions=[(m,n,v,v.__code__) for m in modules for n,v in inspect.getmembers(m,inspect.isfunction) if v.__module__==m.__name__];original_ppt=spp.ppt
    def unchanged():assert all(m.__dict__[n] is v and v.__code__ is code for m,n,v,code in functions)
    class Observer:
        def __init__(self,writer):self.writer=writer
        def trace(self,frame,event,value):
            if event=='call' and frame.f_code is original_ppt.__code__:frame.f_trace_lines=False;frame.f_trace_opcodes=False;return self.local
            if event=='call' and frame.f_code is scf.tl.root.__code__:frame.f_trace_lines=False;frame.f_trace_opcodes=False;return self.root
            return None
        def local(self,frame,event,value):
            if event=='return' and value is not None:
                keys=['X_cpu','W','old_F','F_mat_cpu','R','B','L','M','d','score','sigma','lam','metric','nsteps','err_cut','err','i','co_nodes','Nodes'];self.writer.save('observed-final-ppt-step.json',dict(locals={k:frame.f_locals[k] for k in keys if k in frame.f_locals},returned=vars(value)))
            return self.local
        def root(self,frame,event,value):
            if event=='return' and 'to_g' in frame.f_locals:
                keys=['root','to_g','avgs','avgs_temp','nodes','tips_only','min_val'];self.writer.save('observed-root-step.json',{k:frame.f_locals[k] for k in keys if k in frame.f_locals})
            return self.root
    def prepared(path,role):
        path=Path(path);assert path==path.resolve() and path.is_file() and not path.is_symlink();v=json.loads(path.read_bytes());assert v['schema_version']==1 and v['numeric_dtype']=='float64' and v['role']==role;values=np.asarray(v['values'],dtype=np.float64);assert values.shape==(len(v['ids']),len(v['columns'])) and values.size<=request['maximum_matrix_cells'] and len(v['ids'])<=max(request['maximum_cells'],request['maximum_nodes']) and len(v['columns'])<=request['maximum_components'] and len(set(v['ids']))==len(v['ids']) and len(set(v['columns']))==len(v['columns']) and all(type(n) is str and n for n in [*v['ids'],*v['columns']]) and np.isfinite(values).all();return v,values
    state=dict(status='FAIL2_ORIGINAL_SCFATES_BATCH_UNADMITTED',namespace_patched=False,analysis_installed_software=False,pickle_deserialized=False,result_admitted=False);records=[]
    try:
        for specification in request['analyses']:
            name=specification['id'];assert Path(name).name==name and name not in ['.','..'];out=home/name;out.mkdir();w=Writer(out,request['maximum_matrix_cells']);observer=Observer(w);inputs=specification['prepared'];raw,X=prepared(inputs['coordinates'],'coordinates');cells=raw['ids'];assert len(cells)<=request['maximum_cells'] and X.shape[1]<=request['maximum_components'];adata=ad.AnnData(np.empty((len(cells),0)),obs=pd.DataFrame(index=cells));adata.obsm['coordinates']=X.copy();weight_rep=None;initialization=None
            if 'weights' in inputs:
                v,W=prepared(inputs['weights'],'weights');assert v['ids']==cells and v['columns']==raw['columns'] and W.shape==X.shape;adata.obsm['input_weights']=W;weight_rep='input_weights'
            if 'initialization' in inputs:
                v,F=prepared(inputs['initialization'],'initialization');assert v['columns']==raw['columns'] and 2<=len(v['ids'])<=request['maximum_nodes'];initialization=pd.DataFrame(F,index=v['ids'],columns=v['columns'])
            if 'root-values' in inputs:
                v,signal=prepared(inputs['root-values'],'root-values');assert v['ids']==cells and signal.shape==(len(cells),1);adata.obs['root_signal']=signal[:,0]
            w.save('source-input.json',dict(cells=cells,columns=raw['columns'],coordinates=X,weights=adata.obsm['input_weights'] if weight_rep else None,initialization=initialization,root_values=adata.obs['root_signal'] if 'root_signal' in adata.obs else None));np.random.seed(specification['global_seed']);w.save('rng-before.json',np.random.get_state());seen=[];print('START',name,'tree-root-pseudotime',flush=True)
            try:
                with warnings.catch_warnings(record=True) as seen:
                    tree=specification['tree'];assert tree['nodes']<=request['maximum_nodes'];assert sys.gettrace() is None;sys.settrace(observer.trace)
                    try:scf.tl.tree(adata,Nodes=tree['nodes'],use_rep='coordinates',ndims_rep=None,weight_rep=weight_rep,method='ppt',init=initialization,ppt_sigma=tree['sigma'],ppt_lambda=tree['lambda'],ppt_metric='euclidean',ppt_nsteps=tree['nsteps'],ppt_err_cut=tree['err_cut'],device='cpu',plot=False,seed=tree['seed'],copy=False)
                    finally:sys.settrace(None);unchanged()
                    w.save('tree-return.json',dict(ppt=adata.uns['ppt'],graph=adata.uns['graph'],assignments=adata.obsm['X_R']));root=specification['root'];sys.settrace(observer.trace)
                    try:scf.tl.root(adata,root['node'] if root['mode']=='node' else 'root_signal',tips_only=root['tips_only'],min_val=root['min_val'],copy=False)
                    finally:sys.settrace(None);unchanged()
                    w.save('root-return.json',adata.uns['graph']);pseudo=specification['pseudotime'];scf.tl.pseudotime(adata,n_jobs=pseudo['n_jobs'],n_map=pseudo['n_map'],seed=pseudo['seed'],copy=False);w.save('pseudotime-return.json',dict(obs=adata.obs,graph=adata.uns['graph'],pseudotime_list=adata.uns['pseudotime_list'],assignments=adata.obsm['X_R'],milestones_colors=adata.uns.get('milestones_colors'),seg_colors=adata.uns.get('seg_colors')))
                    # CSV quoting preserves tabs/newlines/Unicode in exact cell IDs.
                    adata.obs[['t','seg','edge','milestones']+(['t_sd'] if 't_sd' in adata.obs else [])].to_csv(out/'pseudotime.tsv',sep='\t',index_label='cell_id',float_format=lambda v:format(float(v),'.17g'),na_rep='',quoting=csv.QUOTE_ALL)
            except Exception:
                w.save('failed-current-state.json',dict(obs=adata.obs,graph=adata.uns.get('graph'),assignments=adata.obsm.get('X_R')));raise
            finally:
                sys.settrace(None);unchanged();w.save('rng-after.json',np.random.get_state());w.save('original-warnings.json',[dict(type=v.category.__name__,message=str(v.message)) for v in seen]);w.save('observer.json',dict(namespace_functions_unchanged=True,code_objects_unchanged=True,method='read-only final PPT and root return observations; no function replacement, fitting-state edit or RNG draw; original warning filters preserved',user_pickle_loaded=False))
            records.append(dict(id=name,method='tree-root-pseudotime',namespace_patched=False,pickle_deserialized=False));print('COMPLETE PROVISIONAL',name,flush=True)
        state.update(status='PASS_COMPLETE_ORIGINAL_SCFATES_WORKFLOWS_ONLY',cases=records)
    except Exception as error:state.update(error=repr(error),earlier_cases_provisional=records);(home/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    unchanged();(home/'COMPLETED.json').write_text(json.dumps(state,indent=2)+'\n',encoding='utf8');return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original workflow/startup checks')
    raise SystemExit(main())
