# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run explicit unchanged original APIs; observe primitive backend/results without patching scientific functions."""
import importlib,inspect,json,os,sys,traceback,warnings
from pathlib import Path

def startup(home,request):
    seed=request['python_hash_seed'];assert type(seed) is int and 0<=seed<=4294967295
    flags={name:getattr(sys.flags,name) for name in ['ignore_environment','isolated','safe_path','no_user_site','hash_randomization','optimize','dont_write_bytecode']};assert flags==dict(ignore_environment=0,isolated=0,safe_path=True,no_user_site=1,hash_randomization=0 if seed==0 else 1,optimize=0,dont_write_bytecode=1) and os.environ.get('PYTHONHASHSEED')==str(seed)
    assert sys.version_info[:3]==(3,11,17) and (sys.hash_info.algorithm,sys.hash_info.width,sys.hash_info.hash_bits,sys.hash_info.seed_bits)==('siphash13',64,64,128)
    value=dict(schema_version=1,runner_revision=2,requested_seed=seed,environment_seed=str(seed),flags=flags,python_executable=sys.executable,python_version='3.11.17',hash_algorithm='siphash13',hash_width=64,hash_bits=64,seed_bits=128,hashes=[dict(text=text,hash=hash(text)) for text in ['','Spyrrow ligand','caf\u00e9','ligand\u0100','cell \U0001f9ec','#123']],before_scientific_imports=True)
    with (home/'python-startup.json').open('xb') as stream:stream.write((json.dumps(value,indent=2)+'\n').encode())

def main():
    home=Path(sys.argv[1]);request=json.loads(Path(sys.argv[2]).read_bytes());startup(home,request);runtime=json.loads((home/'runtime.original.json').read_bytes());prefix=Path(runtime['runtime_environment']);edition=request['edition'];assert edition in ['1.0.4','1.1.2','1.1.3','1.1.8'];namespace=prefix/'editions'/edition;sys.path[:0]=[str(namespace),str(home)]
    import numpy as np
    import pandas as pd
    import gseapy as gp
    import gseapy.gse as backend
    from codec import Writer
    assert gp.__version__==edition and np.__version__=='1.26.4' and pd.__version__=='2.2.3'
    assert Path(gp.__file__).resolve().is_relative_to(namespace) and Path(backend.__file__).resolve().is_relative_to(namespace)
    modules=[importlib.import_module(n) for n in ['gseapy','gseapy.base','gseapy.gsea','gseapy.enrichr','gseapy.stats']]
    for name in ['gseapy.ssgsea','gseapy.gsva']:
        if (namespace/(name.replace('.','/')+'.py')).is_file():modules.append(importlib.import_module(name))
    functions=[(m,n,v,v.__code__) for m in modules for n,v in inspect.getmembers(m,inspect.isfunction) if v.__module__==m.__name__];compiled=[(n,v) for n,v in inspect.getmembers(backend) if inspect.isbuiltin(v)];original_api={n:getattr(gp,n) for n in ['enrichr','gsea','prerank','ssgsea','gsva'] if callable(getattr(gp,n,None))};classes={name:getattr(gp,cls) for name,cls in [('enrichr','Enrichr'),('gsea','GSEA'),('prerank','Prerank'),('ssgsea','SingleSampleGSEA'),('gsva','GSVA')] if hasattr(gp,cls)};class_functions=[(cls,n,v,v.__code__) for module in modules for _,cls in inspect.getmembers(module,inspect.isclass) for n,v in vars(cls).items() if inspect.isfunction(v) and v.__module__==module.__name__]
    def unchanged():
        assert all(m.__dict__[n] is v and v.__code__ is code for m,n,v,code in functions)
        assert all(getattr(backend,n) is v for n,v in compiled) and all(getattr(gp,n) is v for n,v in original_api.items()) and all(vars(cls)[n] is v and v.__code__ is code for cls,n,v,code in class_functions)
    class Observer:
        def __init__(self,writer):self.writer=writer;self.events=[]
        def trace(self,frame,event,value):
            if event=='call' and frame.f_code.co_name=='_to_df' and Path(frame.f_code.co_filename).resolve()==Path(importlib.import_module('gseapy.base').__file__).resolve():
                fields=['term','es','nes','pval','fdr','fwerp','esnull','run_es','hits','index'];summaries=[{n:getattr(gs,n) for n in fields if hasattr(gs,n)} for gs in frame.f_locals['gsea_summary']];self.writer.save('observed-backend-summary.json',dict(summaries=summaries,gmt=frame.f_locals['gmt'],metric=frame.f_locals['metric'],metric_dict=frame.f_locals['self']._metric_dict));self.events.append('complete-original-backend-summary-before-formatting')
                return None
            if event=='call' and frame.f_code.co_name=='enrich' and Path(frame.f_code.co_filename).resolve()==Path(importlib.import_module('gseapy.enrichr').__file__).resolve():
                frame.f_trace_lines=False;frame.f_trace_opcodes=False;return self.enrich
            return None
        def enrich(self,frame,event,value):
            if event=='return' and value is not None:
                local=frame.f_locals;owner=local['self'];self.writer.save('observed-enrichr-test.json',dict(query=local.get('_gls',owner._gls),background=local.get('bg',owner._bg),gene_sets=local['gmt'],returned=value));self.events.append('complete-original-local-enrichment-test')
            return self.enrich
    state=dict(status='FAIL2_ORIGINAL_GSEAPY_BATCH_UNADMITTED',namespace_patched=False,analysis_installed_software=False,pickle_deserialized=False,result_admitted=False);records=[]
    try:
        for specification in request['analyses']:
            name=specification['id'];method=specification['method'];assert specification['interface']=='original-class' and Path(name).name==name and name not in ['.','..'] and method in classes;out=home/name;out.mkdir();writer=Writer(out,request['maximum_matrix_cells']);observer=Observer(writer);prepared=specification['prepared'];raw=json.loads(Path(prepared).read_bytes());assert raw['schema_version']==1 and raw['numeric_dtype']=='float64';gene_sets=raw['gene_sets'];assert type(gene_sets) is dict and all(type(k) is str and type(v) is list and all(type(x) is str for x in v) for k,v in gene_sets.items());options=dict(specification['options']);assert 'outdir' not in options and 'gene_sets' not in options;kwargs=dict(gene_sets=gene_sets,outdir=str(out/'original'),verbose=True);kwargs.update(options)
            writer.save('source-input.json',raw);np.random.seed(specification['global_seed']);writer.save('rng-before.json',np.random.get_state());seen=[];print('START',name,edition,method,flush=True)
            try:
                if method=='enrichr':
                    assert type(raw['query']) is list and all(type(x) is str for x in raw['query']);kwargs.update(gene_list=raw['query'],background=raw['background'],no_plot=True)
                else:
                    matrix=np.asarray(raw['values'],dtype=np.float64);assert matrix.shape==(len(raw['ids']),len(raw['columns'])) and matrix.size<=request['maximum_matrix_cells'] and np.isfinite(matrix).all();frame=pd.DataFrame(matrix,index=raw['ids'],columns=raw['columns']);kwargs['rnk' if method=='prerank' else 'data']=frame.iloc[:,0] if method=='prerank' and len(raw['columns'])==1 else frame
                    if method=='gsea':kwargs['classes']=raw['classes']
                    if method!='gsva':kwargs['no_plot']=True
                    if edition=='1.0.4':assert method!='gsva'
                with warnings.catch_warnings(record=True) as seen:
                    assert sys.gettrace() is None;sys.settrace(observer.trace)
                    try:
                        result=classes[method](**kwargs)
                        for option,wanted in options.items():
                            if method=='ssgsea' and option=='permutation_num' and wanted==0:assert result.permutation_num in [None,0]
                            elif option=='correl_norm_type':assert result.correl_type==getattr(backend.CorrelType,{'rank':'Rank','symrank':'SymRank','zscore':'ZScore'}[wanted])
                            elif option=='kcdf':assert result.kernel==(wanted in ['Gaussian','Poisson']) and result.rnaseq==(wanted=='Poisson')
                            else:
                                attribute='tau' if option=='weight' and method=='gsva' else '_threads' if option=='threads' else option;assert getattr(result,attribute)==wanted,'Constructor did not bind scientific option: '+option
                        effective={key:getattr(result,key) for key in ['weight','tau','permutation_num','ascending','min_size','max_size','_threads','seed','cutoff','organism','sample_norm_method','method','permutation_type','kernel','rnaseq','mx_diff','abs_rnk'] if hasattr(result,key)}
                        if hasattr(result,'correl_type'):effective['correl_type']=str(result.correl_type)
                        writer.save('effective-options-before-run.json',dict(interface='original-class',class_name=classes[method].__module__+'.'+classes[method].__name__,requested=options,effective=effective));result.run()
                        if method=='ssgsea':assert result.permutation_num==options['permutation_num'],'Original ssGSEA did not resolve its declared permutation count'
                    finally:sys.settrace(None);unchanged()
                attrs={key:getattr(result,key) for key in ['data','ranking','gmt','res2d','results','_results','_metric_dict','_gls','_bg','_gene_isupper','_gene_toupper','_isezid','permutation_type','weight','permutation_num'] if hasattr(result,key)};writer.save('original-return.json',attrs);writer.save('rng-after.json',np.random.get_state());writer.save('warnings.json',[dict(category=v.category.__name__,message=str(v.message),filename=v.filename,line=v.lineno) for v in seen]);writer.save('trace-events.json',observer.events)
                table=result.res2d if hasattr(result,'res2d') else result.results;assert isinstance(table,pd.DataFrame);table.to_csv(out/'full-result.tsv',sep='\t',index=False,float_format='%.17g');records.append(dict(id=name,method=method,edition=edition,rows=len(table),columns=list(table.columns),original_completed=True));print('COMPLETE-PROVISIONAL',name,len(table),'rows',flush=True)
            except BaseException:
                writer.save('warnings.json',[dict(category=v.category.__name__,message=str(v.message),filename=v.filename,line=v.lineno) for v in seen]) if not(out/'warnings.json').exists() else None
                (out/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8');raise
        unchanged();state.update(status='PASS_COMPLETE_ORIGINAL_GSEAPY_WORKFLOWS_ONLY',cases=records,original_package=gp.__version__,backend=str(backend.__file__),native_statistical_refit=False)
    except BaseException as error:
        state.update(error=repr(error),earlier_complete_cases_are_provisional=records);(home/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    with (home/'COMPLETED.json').open('xb') as stream:stream.write((json.dumps(state,indent=2)+'\n').encode())
    return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source checks')
    raise SystemExit(main())
