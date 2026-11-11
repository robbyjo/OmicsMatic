# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Typed original CellPhoneDB5.0.1 execution with data-only return snapshots. Debug pickles remain opaque and are never deserialized."""
import gc,hashlib,importlib.metadata as metadata,inspect,json,os,sys,traceback
from pathlib import Path

# BEGIN additive effective-interpreter-startup gate, before scientific imports.
def _write_python_startup():
    request=json.loads(Path(sys.argv[2]).read_bytes());seed=request['python_hash_seed']
    if type(seed) is not int or not 0<=seed<=4294967295:raise RuntimeError('Invalid declared Python hash seed')
    flags={name:getattr(sys.flags,name) for name in ['ignore_environment','isolated','safe_path','no_user_site','hash_randomization','optimize','dont_write_bytecode']}
    expected=dict(ignore_environment=0,isolated=0,safe_path=True,no_user_site=1,hash_randomization=0 if seed==0 else 1,optimize=0,dont_write_bytecode=1)
    if flags!=expected or os.environ.get('PYTHONHASHSEED')!=str(seed):raise RuntimeError('Effective Python startup does not honor the explicit hash seed and safe launch flags; no analysis admitted')
    if sys.version_info[:3]!=(3,11,17) or (sys.hash_info.algorithm,sys.hash_info.width,sys.hash_info.hash_bits,sys.hash_info.seed_bits)!=('siphash13',64,64,128):raise RuntimeError('Unexpected bound Python hash implementation')
    probes=['', 'Spyrrow ligand', 'café', 'ligandĀ', 'cell 🧬', '#123']
    receipt=dict(schema_version=1,runner_revision=2,requested_seed=seed,environment_seed=os.environ['PYTHONHASHSEED'],flags=flags,python_executable=sys.executable,python_version='.'.join(map(str,sys.version_info[:3])),hash_algorithm=sys.hash_info.algorithm,hash_width=sys.hash_info.width,hash_bits=sys.hash_info.hash_bits,seed_bits=sys.hash_info.seed_bits,hashes=[dict(text=text,hash=hash(text)) for text in probes],before_scientific_imports=True)
    with (Path(sys.argv[1])/'python-startup.json').open('xb') as stream:stream.write((json.dumps(receipt,indent=2)+'\n').encode('utf8'))
if __name__=='__main__':_write_python_startup()
# END additive effective-interpreter-startup gate.
sys.path.insert(0,str(Path(__file__).resolve().parent))
from codec import clean,save
import numpy as np
import pandas as pd
from cellphonedb.src.core.methods import cpdb_analysis_method as simple
from cellphonedb.src.core.methods import cpdb_statistical_analysis_method as statistical
from cellphonedb.src.core.methods import cpdb_statistical_analysis_complex_method as statistical_core
from cellphonedb.src.core.methods import cpdb_degs_analysis_method as degs
from cellphonedb.src.core.methods import cpdb_statistical_analysis_helper as helper
from cellphonedb.src.core.models.complex import complex_helper
from cellphonedb.src.core.utils import subsampler
from cellphonedb.utils import db_utils,file_utils,scoring_utils


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def label(value):return isinstance(value,str) and bool(value) and not any(ord(c)<32 or ord(c)==127 or 0xd800<=ord(c)<=0xdfff for c in value)


class ReturnObserver:
    """Read exact parent-process return values without replacing functions, altering locals or loading a pickle."""
    def __init__(self,out):
        self.out=out;self.pid=os.getpid();self.records={};self.entered=False;self.sampling_errors=[];self.sampling_result=None;self.modules=[simple,statistical,statistical_core,degs,helper,scoring_utils];self.originals=[(module,name,value,value.__code__) for module in self.modules for name,value in inspect.getmembers(module,inspect.isfunction) if value.__module__==module.__name__];self.originals.extend((subsampler.Subsampler,name,getattr(subsampler.Subsampler,name),getattr(subsampler.Subsampler,name).__code__) for name in ['__init__','subsample']);self.top_codes={function.__code__ for function in [simple.call,statistical.call,statistical_core.call,degs.call]};self.targets={function.__code__:name for function,name in [(helper.build_clusters,'clusters'),(helper.mean_analysis,'means'),(helper.percent_analysis,'eligibility'),(helper.shuffled_analysis,'permutation-greater-packed'),(helper.build_percent_result,'pvalues'),(degs.build_degs_matrix,'degs'),(degs.degs_analysis,'deg-unmasked'),(subsampler.Subsampler.__init__,'subsample-initialization'),(subsampler.Subsampler.subsample,'subsample'),(simple.call,'simple-final-state'),(statistical.call,'statistical-final-state'),(statistical_core.call,'statistical-core-state'),(degs.call,'degs-final-state'),(scoring_utils.filter_genes_per_cell_type,'scoring-filtered'),(scoring_utils.mean_expression_per_cell_type,'scoring-means'),(scoring_utils.heteromer_geometric_expression_per_cell_type,'scoring-geometric'),(scoring_utils.scale_expression,'scoring-scaled')]}
    def enter(self):assert sys.gettrace() is None,'An existing trace must not be replaced';sys.settrace(self.global_trace);self.entered=True
    def global_trace(self,frame,event,arg):
        if event=='call' and os.getpid()==self.pid and frame.f_code in self.targets:frame.f_trace_lines=False;frame.f_trace_opcodes=False;return self.local_trace
        return None
    def local_trace(self,frame,event,value):
        name=self.targets[frame.f_code]
        if event=='exception' and name=='subsample':self.sampling_errors.append(dict(type=type(value[1]).__name__,message=str(value[1])));return self.local_trace
        if event!='return' or value is None and name!='subsample-initialization':return self.local_trace
        if name=='clusters' and frame.f_locals.get('skip_percent') is not False:return self.local_trace
        if name=='means' and (frame.f_back is None or frame.f_back.f_code not in self.top_codes):return self.local_trace
        assert name not in self.records,'An original observed family was unexpectedly repeated: '+name
        record=dict(returned=clean(value))
        if name=='subsample-initialization':record['source_debug_seed']=clean(frame.f_locals['debug_seed']);record['rng_after_source_initialization']=clean(np.random.get_state())
        if name=='subsample':
            self.sampling_result=dict(input_cells=frame.f_locals['counts'].shape[1],returned_cells=value.shape[1],requested_cells=frame.f_locals['self'].num_cells,source_errors=list(self.sampling_errors));record['subsampling_state']=self.sampling_result
        if name.endswith('-state'):
            keys=['counts','counts_filtered','meta','counts_relations','interactions_filtered','complex_composition_filtered','complex_compositions','complex_to_protein_row_ids','cluster_combinations','cluster_interactions','real_degs_analysis','relevant_interactions'];record['locals']={key:clean(frame.f_locals[key]) for key in keys if key in frame.f_locals}
        save(self.out/('observed-'+name+'.json'),record);self.records[name]=pin(self.out/('observed-'+name+'.json'));return self.local_trace
    def close(self):
        if self.entered:sys.settrace(None)
        for module,name,function,code in self.originals:assert module.__dict__[name] is function and function.__code__ is code,'Original function namespace/code object changed'
        save(self.out/'return-observer.json',dict(method='data-only sys.settrace return observation; line/opcode events disabled; no function replacement, local assignment, RNG draw or pickle decoding',original_namespace_unchanged=True,original_code_objects_unchanged=True,parent_process_only=True,records=self.records))


def run(home,request_path):
    request=json.loads(request_path.read_bytes());assert request['schema_version']==1 and request['edition']=='5.0.1' and metadata.version('cellphonedb')=='5.0.1' and pd.__version__=='2.2.3' and np.__version__=='1.26.4';records=[];state=dict(status='FAIL2_ORIGINAL_CELLPHONEDB_BATCH_UNADMITTED',namespace_patched=False,analysis_installed_software=False,result_admitted=False,pickle_deserialized=False)
    try:
        for case in request['analyses']:
            name=case['id'];assert name and name not in ['.','..'] and Path(name).name==name;out=home/name;out.mkdir();original=out/'original';original.mkdir();assert case['method'] in ['simple','statistical','degs'];print('START',name,case['method'],flush=True)
            for field in ['counts','metadata','microenvironments','degs','active_tfs']:
                if field not in case:continue
                declared=case[field]['format'];path=Path(case[field]['path']);allowed=['csv','tsv','h5ad'] if field=='counts' else ['csv','tsv'];assert declared in allowed and path.suffix.lower()=='.'+declared and path.is_absolute() and path==path.resolve() and path.is_file(),'Require a canonical file with its declared safe format; pickle/arbitrary-object formats and suffix guessing are forbidden'
            database_path=Path(case['database']['path']);assert database_path.is_absolute() and database_path==database_path.resolve() and database_path.is_file() and database_path.suffix.lower()=='.zip','Require the declared canonical data-only database ZIP'
            if case['counts']['format']=='h5ad':
                import h5py
                with h5py.File(case['counts']['path'],'r') as h5:
                    x=h5['X'];shape=x.shape if isinstance(x,h5py.Dataset) else tuple(x.attrs['shape']);assert len(shape)==2 and shape[0]<=request['maximum_cells'] and shape[1]<=request['maximum_genes'] and shape[0]*shape[1]<=request['maximum_matrix_cells'],'Complete H5AD X dimensions exceed declared limits before densification'
            raw,_=file_utils.read_counts(case['counts']['path']);assert 0<len(raw.index)<=request['maximum_genes'] and 0<len(raw.columns)<=request['maximum_cells'] and len(raw.index)*len(raw.columns)<=request['maximum_matrix_cells'],'Complete raw matrix dimensions exceed declared limits';assert all(label(v) for v in raw.index) and all(label(v) for v in raw.columns) and raw.columns.is_unique,'Require unchanged string gene/cell identities; parser coercion/missing labels are not repaired';raw.to_csv(out/'source-raw-counts.tsv.gz',sep='\t',index=True,float_format=lambda value:format(float(value),'.17g'),compression=dict(method='gzip',mtime=0));save(out/'source-input-interpretation.json',dict(raw_gene_rows=len(raw.index),raw_cells=len(raw.columns),raw_dtypes=[str(v) for v in raw.dtypes],source_conversion='Unchanged original read_counts interpretation, then17-digit round-tripping numeric-text export; original bytes remain separately captured.',gene_identity_policy='Duplicate gene rows retained; no synonym substitution or species conversion.'))
            for i in range(len(raw.columns)):
                values=raw.iloc[:,i].to_numpy();assert values.dtype.kind in 'iuf' and np.isfinite(values).all() and (values>=0).all(),'Raw expression must be finite and nonnegative; no repair'
                if values.dtype.kind in 'iu':assert (values<=9007199254740992).all(),'Integer source counts exceed exact-double native eligibility; originals retained without rounding'
                assert np.isfinite(values.astype(np.float32)).all(),'Original float32 cast would overflow; preserve the source and skip dependent analysis without replacing Inf'
            del raw;gc.collect();database=case['database']['path'];interactions,genes,composition,complexes,synonyms,receptor2tfs=db_utils.get_interactions_genes_complex(database);kwargs=dict(cpdb_file_path=database,meta_file_path=case['metadata']['path'],counts_file_path=case['counts']['path'],counts_data=case['counts_data'],output_path=str(original),separator=case['separator'],threshold=case['threshold'],result_precision=case['result_precision'],debug=True,output_suffix=name,score_interactions=case['score_interactions'],threads=case['threads'])
            for field,argument in [('microenvironments','microenvs_file_path'),('degs','degs_file_path'),('active_tfs','active_tfs_file_path')]:
                if field in case:kwargs[argument]=case[field]['path']
            if case['method']=='statistical':
                options=case['statistical'];sampling=options['subsampling'];kwargs.update(iterations=options['iterations'],debug_seed=options['debug_seed'],pvalue=options['pvalue'],subsampling=sampling['enabled'],subsampling_log=sampling['log'],subsampling_num_pc=sampling['pcs'],subsampling_num_cells=sampling['cells'])
            save(out/'effective-original-parameters.json',kwargs);counts,meta,microenvs,marks,active=file_utils.get_user_files(counts=kwargs['counts_file_path'],meta_fp=kwargs['meta_file_path'],microenvs_fp=kwargs.get('microenvs_file_path'),degs_fp=kwargs.get('degs_file_path'),active_tfs_fp=kwargs.get('active_tfs_file_path'),gene_synonym2gene_name=synonyms,counts_data=case['counts_data']);mapped,relations=helper.add_multidata_and_means_to_counts(counts,genes,case['counts_data']);assert not mapped.empty,'All raw genes were filtered by original identifier mapping';reduced=interactions[['multidata_1_id','multidata_2_id']].drop_duplicates();filtered,prepared,kept_composition=helper.prefilters(reduced,mapped,complexes,composition);assert not filtered.empty,'No original interaction remains; empty execution is not admission';types=set(meta['cell_type'].map(str));pair_bound=len(types)**2
            if not microenvs.empty:pair_bound=sum(len(group)**2 for unused,group in microenvs.groupby('microenvironment',sort=False))
            assert pair_bound<=request['maximum_matrix_cells'] and pair_bound*len(filtered)<=request['maximum_matrix_cells'],'Complete original interaction/pair matrices exceed declared limits'
            save(out/'preparation.json',clean(dict(genes=genes[['id_multidata','ensembl','gene_name','hgnc_symbol']],composition=composition,interactions=interactions[['multidata_1_id','multidata_2_id']],mapped=mapped,relations=relations,metadata_original_parsed=meta,metadata=meta.assign(cell_type=meta['cell_type'].map(str)),metadata_interpretation='String conversion matches the unchanged original full methods. Original parsed dtypes and raw metadata bytes remain retained; Java must reject any identity/label change from the declared raw metadata.',microenvironments=microenvs,degs=marks,active_tf2cell_types=active,source_filtered_counts=prepared,source_filtered_interactions=filtered,source_filtered_composition=kept_composition,complex_row_map=complex_helper.map_complex_to_protein_row_ids(kept_composition,prepared))))
            del counts,mapped,prepared;gc.collect();np.random.seed(case['global_seed']);save(out/'rng-before.json',clean(np.random.get_state()));call={'simple':simple.call,'statistical':statistical.call,'degs':degs.call}[case['method']];observer=ReturnObserver(out)
            try:observer.enter();result=call(**kwargs)
            finally:observer.close()
            save(out/'rng-after.json',clean(np.random.get_state()));assert isinstance(result,dict) and result,'Original returned no complete analysis';save(out/'returned.json',clean(result));sampling_state=observer.sampling_result
            if sampling_state is not None:
                save(out/'subsampling-state.json',sampling_state)
                if sampling_state['source_errors']:assert case['statistical']['subsampling']['failure_policy']=='retain-original-full-counts','Original subsampling failed and returned full counts. The requested fail policy admits no result; inspect subsampling-state.json, preserve inputs, resolve the named source error or explicitly authorize the source fallback in a NEW request'
            tables={};states={};prefix={'simple':'simple_analysis','statistical':'statistical_analysis','degs':'degs_analysis'}[case['method']]
            for key,value in result.items():
                if value is pd.DataFrame:assert key in ['CellSign_active_interactions','CellSign_active_interactions_deconvoluted'];states[key]='original-pandas-DataFrame-class-sentinel-not-a-result';continue
                assert isinstance(value,pd.DataFrame),'Uncaptured original return family: '+key
                if value.empty:states[key]='original-empty-dataframe';continue
                path=original/(prefix+'_'+key+'_'+name+'.txt');assert path.is_file() and path.read_bytes()==value.to_csv(sep='\t',index=False).encode('utf8'),'Original TSV/returned frame mismatch';tables[key]=dict(file=path.name,rows=len(value.index),columns=len(value.columns),pin=pin(path))
            record=dict(id=name,method=case['method'],status='PASS_ORIGINAL_PROCESS_OUTPUTS_ONLY_NO_ADMISSION',tables=tables,non_table_states=states,subsampling_state=sampling_state,original_namespace_patched=False,analysis_installed_software=False,pickle_deserialized=False,result_admitted=False,diagnostic_scope='Original UserWarning suppression is unchanged. Original debug pickle bytes remain opaque; data-only return snapshots are the audit input. Internal Subsampler(debug_seed=None) reseeds from entropy; a global/permutation seed does not make that full-method sampling deterministic.');save(out/'CASE-COMPLETED.json',record);records.append(record);print('PASS_ORIGINAL_ONLY',name,len(tables),'tables',flush=True)
        state.update(status='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_WORKFLOWS_ONLY',cases=records)
    except Exception as error:state['error']=repr(error);state['earlier_cases_provisional']=records;(home/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');raise
    finally:save(home/'COMPLETED.json',state)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original input/output checks')
    run(Path(sys.argv[1]),Path(sys.argv[2]))
