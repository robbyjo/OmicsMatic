# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute unchanged original CellPhoneDB5.0.1 full raw-file methods, scoring, CellSign and subsampling on complete synthetic controls; retain every output and source failure."""
import argparse,json,os,pickle,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory

ACTION='Original full-workflow reference controls only, not a public execution adapter, independent full-output audit, paper reproduction or biological validation. Preserve all original files, dtypes, RNG states and failed attempts. Missing/forbidden required dependencies skip dependent analysis (SKIP3); any unexpected failure admits no partial batch (FAIL2). Correct the named problem and retry NEW paths.'


def worker(out,fixtures):
    import importlib.metadata as metadata
    import numpy as np
    import pandas as pd
    from cellphonedb.src.core.methods import cpdb_analysis_method as simple
    from cellphonedb.src.core.methods import cpdb_statistical_analysis_method as statistical
    from cellphonedb.src.core.methods import cpdb_degs_analysis_method as degs
    from cellphonedb.src.core.methods import cpdb_statistical_analysis_helper as helper
    from cellphonedb.src.core.models.complex import complex_helper
    from cellphonedb.utils import db_utils,file_utils
    assert metadata.version('cellphonedb')=='5.0.1' and np.__version__=='1.26.4' and pd.__version__=='2.2.3'
    def clean(value):
        if value is pd.NA:return {'$pandas_NA':True}
        if isinstance(value,pd.DataFrame):return {'$dataframe':dict(index=clean(value.index.to_list()),index_names=clean(value.index.names),columns=clean(value.columns.to_list()),column_names=clean(value.columns.names),dtypes=[str(d) for d in value.dtypes],values=clean(value.values.tolist()))}
        if isinstance(value,pd.Series):return {'$series':dict(index=clean(value.index.to_list()),name=clean(value.name),dtype=str(value.dtype),values=clean(value.to_list()))}
        if isinstance(value,pd.Index):return {'$index':dict(dtype=str(value.dtype),name=clean(value.name),values=clean(value.to_list()))}
        if isinstance(value,np.ndarray):return {'$ndarray':dict(dtype=str(value.dtype),shape=list(value.shape),values=clean(value.tolist()))}
        if isinstance(value,np.generic):return clean(value.item())
        if isinstance(value,float) and not np.isfinite(value):return 'NaN' if np.isnan(value) else '+Inf' if value>0 else '-Inf'
        if isinstance(value,dict):
            if all(isinstance(k,str) for k in value):return {k:clean(v) for k,v in value.items()}
            return {'$mapping':[[clean(k),clean(v)] for k,v in value.items()]}
        if isinstance(value,(list,tuple)):return [clean(v) for v in value]
        if isinstance(value,type):return {'$python_class':value.__module__+'.'+value.__qualname__}
        if value is None or isinstance(value,(bool,int,float,str)):return value
        raise TypeError('Uncaptured original result/intermediate type: '+str(type(value)))
    request=json.loads(fixtures.read_bytes());database=fixtures.parent/request['database'];results=[];native=[]
    def prepare(case,source,parameters):
        interactions,genes,composition,complexes,synonyms,receptor2tfs=db_utils.get_interactions_genes_complex(parameters['cpdb_file_path']);raw_counts,_=file_utils.read_counts(str(source/'counts.tsv'));counts,meta,microenvs,marks,active=file_utils.get_user_files(counts=str(source/'counts.tsv'),meta_fp=str(source/'meta.tsv'),microenvs_fp=parameters.get('microenvs_file_path'),degs_fp=parameters.get('degs_file_path'),active_tfs_fp=parameters.get('active_tfs_file_path'),gene_synonym2gene_name=synonyms,counts_data=case['counts_data']);mapped,relations=helper.add_multidata_and_means_to_counts(counts,genes,case['counts_data']);reduced=interactions[['multidata_1_id','multidata_2_id']].drop_duplicates();filtered,prepared,kept_composition=helper.prefilters(reduced,mapped,complexes,composition);complex_map=complex_helper.map_complex_to_protein_row_ids(kept_composition,prepared)
        gene_rows=[]
        for row in genes[['id_multidata','ensembl','gene_name','hgnc_symbol']].to_dict('records'):
            gene_rows.append({k:None if pd.isna(v) else int(v) if k=='id_multidata' else str(v) for k,v in row.items()})
        native_case=dict(id=case['id'],raw_genes=raw_counts.index.to_list(),raw_cells=raw_counts.columns.to_list(),raw_expression=raw_counts.values.tolist(),meta_cells=meta.index.to_list(),meta_cell_types=meta['cell_type'].to_list(),counts_data=case['counts_data'],gene_rows=gene_rows,composition=[dict(row_id=int(index),**{k:int(v) for k,v in row.items()}) for index,row in composition.iterrows()],interactions=[dict(id=str(index),first=int(row['multidata_1_id']),second=int(row['multidata_2_id'])) for index,row in interactions.iterrows()])
        preparation=dict(preprocessed_genes=counts.index.to_list(),preprocessed_cells=counts.columns.to_list(),preprocessed_expression=counts.values.tolist(),metadata_cells=meta.index.to_list(),metadata_cell_types=meta['cell_type'].to_list(),mapped_features=mapped.index.to_list(),cells=mapped.columns.to_list(),mapped_expression=mapped.values.tolist(),relations=relations.to_dict('records'),filtered_interaction_ids=[str(i) for i in filtered.index.to_list()],filtered_features=prepared.index.to_list(),filtered_expression=prepared.values.tolist(),filtered_composition=[dict(row_id=int(index),**{k:int(v) for k,v in row.items()}) for index,row in kept_composition.iterrows()],complex_map=[dict(id=int(k),rows=v) for k,v in complex_map.items()],aligned_cell_types=meta.loc[mapped.columns]['cell_type'].astype(str).to_list())
        return native_case,clean(preparation)
    for case in request['cases']:
        print('START',case['id'],flush=True);home=out/case['id'];home.mkdir();source=fixtures.parent/case['id'];destination=home/'original-output';destination.mkdir();case_database=fixtures.parent/case.get('database',request['database']);parameters=dict(cpdb_file_path=str(case_database),meta_file_path=str(source/'meta.tsv'),counts_file_path=str(source/'counts.tsv'),counts_data=case['counts_data'],output_path=str(destination),separator=case['separator'],threshold=case['threshold'],result_precision=case['result_precision'],debug=True,output_suffix=case['id'],score_interactions=case['score_interactions'],threads=case['threads'])
        for field,argument in [('microenvironments','microenvs_file_path'),('active_tfs','active_tfs_file_path'),('degs','degs_file_path')]:
            if case[field] is not None:parameters[argument]=str(source/(field+'.tsv'))
        if case['method']=='statistical':
            for name in ['iterations','debug_seed','pvalue','subsampling','subsampling_log','subsampling_num_pc','subsampling_num_cells']:parameters[name]=case[name]
        save(home/'effective-original-parameters.json',parameters);np.random.seed(7221);save(home/'rng-before.json',clean(np.random.get_state()));call={'simple':simple.call,'statistical':statistical.call,'degs':degs.call}[case['method']];caught=None;result=None
        try:result=call(**parameters)
        except Exception as error:caught=error;(home/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
        save(home/'rng-after.json',clean(np.random.get_state()))
        if case['expected_error'] is not None:
            assert caught is not None and type(caught).__name__==case['expected_error'],('Original expected error changed',case['id'],repr(caught));record=dict(id=case['id'],status='FAIL2_EXPECTED_ORIGINAL_SOURCE_ERROR_NO_ANALYSIS_ADMITTED',exception_type=type(caught).__name__,exception_message=str(caught),scientific_result_admitted=False);save(home/'CASE-COMPLETED.json',record);results.append(record)
            if case.get('capture_preparation_after_expected_error'):
                native_case,preparation=prepare(case,source,parameters);native.append(native_case);save(home/'preparation.json',preparation)
            else:native.append(dict(id=case['id'],expected_original_error=case['expected_error']))
            print('EXPECTED_SOURCE_ERROR',case['id'],type(caught).__name__,flush=True);continue
        if caught is not None:raise RuntimeError('Unexpected original full-workflow error for '+case['id']+'; inspect retained case traceback') from caught
        assert isinstance(result,dict) and result,'Original returned no analysis; never count empty execution as a completed workflow'
        save(home/'original-returned.json',clean(result));debug_file=destination/'debug_intermediate.pkl';assert debug_file.is_file();debug=pickle.loads(debug_file.read_bytes());save(home/'original-debug.json',clean(debug));returned_tables={};returned_non_table_states={}
        for name,value in result.items():
            if value is pd.DataFrame:
                assert name in ['CellSign_active_interactions','CellSign_active_interactions_deconvoluted'],'Unknown original class-valued result state: '+name
                returned_non_table_states[name]=dict(state='original-pandas-DataFrame-class-sentinel-not-a-dataframe-or-result',active_TFs_requested=case['active_tfs'] is not None);continue
            assert isinstance(value,pd.DataFrame),'Non-dataframe returned by original method: '+name
            if value.empty:continue
            prefix={'simple':'simple_analysis','statistical':'statistical_analysis','degs':'degs_analysis'}[case['method']];path=destination/(prefix+'_'+name+'_'+case['id']+'.txt');assert path.is_file() and path.read_bytes()==value.to_csv(sep='\t',index=False).encode('utf8');returned_tables[name]=dict(file=path.name,rows=len(value),columns=value.columns.to_list(),pin=pin(path))
        native_case,preparation=prepare(case,source,parameters);native.append(native_case)
        save(home/'preparation.json',clean(preparation));files={p.relative_to(destination).as_posix():pin(p) for p in destination.rglob('*') if p.is_file()};record=dict(id=case['id'],status='PASS_COMPLETE_ORIGINAL_FULL_METHOD_REFERENCE_ONLY',method=case['method'],returned_tables=returned_tables,returned_non_table_states=returned_non_table_states,files=files,scoring_executed=case['score_interactions'],CellSign_requested=case['active_tfs'] is not None,subsampling_requested=case['subsampling'],scientific_result_admitted=False,preparation=clean(preparation));save(home/'CASE-COMPLETED.json',record);results.append(record);print('PASS',case['id'],len(returned_tables),'tables',flush=True)
    save(out/'native-input.json',dict(cases=native));save(out/'original-results.json',dict(cases=results));save(out/'ORIGINAL-COMPLETED.json',dict(status='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_FULL_WORKFLOW_CONTROLS_ONLY',cases=len(results),successful_workflows=sum(r['status'].startswith('PASS_') for r in results),expected_source_errors=sum(r['status'].startswith('FAIL2_') for r in results),original_namespace_patched=False,full_tool_implementation_credit=False,biological_claim=False,analysis_scope=ACTION))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--fixtures',type=Path,required=True);parser.add_argument('--worker',action='store_true');args=parser.parse_args();out=args.out.absolute();fixtures=args.fixtures.absolute()
    if args.worker:worker(out,fixtures);return 0
    assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True)
    state=dict(status='SKIP3_CELLPHONEDB_REFERENCE_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False);before=None;child=None;code=None;started=time.monotonic();process={}
    prefix=ROOT/'build/cellphonedb-case-sensitive-1/runtime-1';setup=ROOT/'build/cellphonedb-case-sensitive-1/setup-1';source=ROOT/'build/literature-review/cellphonedb-runtime/source-intake-2';paths=[Path(__file__).resolve(),ROOT/'tools/literature/inventory_hail_runtime_linux.py',fixtures,fixtures.parent/'COMPLETED.json',setup/'COMPLETED.json',source/'COMPLETED.json'];pins={str(p):pin(p) for p in paths}
    for i,path in enumerate(paths):(out/('bound-input-'+str(i)+'-'+path.name)).write_bytes(path.read_bytes())
    save(out/'STARTED.json',dict(source_pins=pins,started_at=time.time()))
    def members(group):
        result=[]
        for proc in Path('/proc').iterdir():
            if not proc.name.isdigit():continue
            try:
                fields=(proc/'stat').read_text().split(') ',1)[1].split()
                if int(fields[2])==group:result.append(dict(pid=int(proc.name),state=fields[0],start_ticks=int(fields[19])))
            except (OSError,ValueError,IndexError):pass
        return result
    try:
        receipt=json.loads((setup/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_PRIVATE_ORIGINAL_CELLPHONEDB_501_REFERENCE_IMPORT_ONLY' and receipt['runtime_trees_unchanged'] and receipt['shared_archive_bytes_unchanged']
        intake=json.loads((source/'COMPLETED.json').read_bytes());assert intake['versions']['5.0.1']['original_license_body_present']
        for name,want in intake['versions']['5.0.1']['members'].items():
            if name.startswith('cellphonedb/'):assert pin(prefix/'lib/python3.11/site-packages'/name)==want,'Original installed member drift: '+name
        fixture_pins=json.loads((fixtures.parent/"COMPLETED.json").read_bytes())["files"];assert all(pin(fixtures.parent/name)==want for name,want in fixture_pins.items())
        before=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',before);assert before==json.loads((setup/'runtime-after.json').read_bytes()),'Reference runtime changed since private setup'
        state['status']='FAIL2_CELLPHONEDB_ORIGINAL_CONTROLS_FAILED'
        env=dict(os.environ)
        for key in list(env):
            if key.startswith(('PYTHON','CONDA_','PIP_','R_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
        for name in ['private-home','private-cache','private-temp','private-config']:(out/name).mkdir()
        env.update(HOME=str(out/'private-home'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),TMPDIR=str(out/'private-temp'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),PYTHONDONTWRITEBYTECODE='1',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',LANG='C.UTF-8',LC_ALL='C.UTF-8',PATH=str(prefix/'bin')+':'+env.get('PATH',''))
        command=[str(prefix/'bin/python'),'-I','-B',str(Path(__file__).resolve()),'--worker','--out',str(out),'--fixtures',str(fixtures)];process=dict(command=command,started_at=time.time())
        with (out/'original.stdout').open('xb') as first,(out/'original.stderr').open('xb') as second:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);process['owned_process_group']=child.pid;save(out/'original.STARTED.json',process);code=child.wait(timeout=1200)
        assert code==0,'Unmodified original controls failed; inspect original.stderr and original.stdout'
        result=json.loads((out/'ORIGINAL-COMPLETED.json').read_bytes());assert result['status']=='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_FULL_WORKFLOW_CONTROLS_ONLY' and result['cases']==19
        state.update(status=result['status'],cases=result['cases'],source_pins=pins,original_namespace_patched=False,results=pin(out/'original-results.json'),native_input=pin(out/'native-input.json'))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if child is not None:
            process['owned_before_cleanup']=members(child.pid)
            for sig in [signal.SIGTERM,signal.SIGKILL]:
                if not any(p['state']!='Z' for p in members(child.pid)):break
                try:os.killpg(child.pid,sig)
                except ProcessLookupError:pass
                deadline=time.monotonic()+3
                while any(p['state']!='Z' for p in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
            child.wait(timeout=10);process['owned_live_after_cleanup']=[p for p in members(child.pid) if p['state']!='Z']
            if process['owned_live_after_cleanup']:state.update(status='FAIL2_CELLPHONEDB_OWNED_PROCESSES_REMAIN',error='Owned process cleanup incomplete')
        process.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(out/'original.process.json',process)
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);state['runtime_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_CELLPHONEDB_DEPENDENCY_TREE_CHANGED',error='Complete dependency inventory changed')
        if "fixture_pins" in locals():assert all(pin(fixtures.parent/name)==want for name,want in fixture_pins.items())
        assert all(pin(Path(p))==want for p,want in pins.items());state['required_action']=ACTION;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','All original prepared controls complete; native parity and full workflow validation remain separate.')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source, runtime or full-control checks')
    raise SystemExit(main())
