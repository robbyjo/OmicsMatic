# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pair safe observed/uninstrumented original methods and new file/parallel controls with complete unchanged runtime/source records. No pickle loading."""
import argparse,base64,csv,hashlib,io,json,os,shutil,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.absolute();assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);kit=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-workflow';setup=ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1';binding=json.loads((setup/'COMPLETED.json').read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_CELLPHONEDB_WORKFLOW_RUNTIME_ONLY' and binding['runtime_trees_unchanged'];prefix=Path(binding['runtime_environment']);python=prefix/'bin/python';fixture=ROOT/'build/literature-review/cellphonedb-runtime/workflow-fixtures-5';manifest=json.loads((fixture/'COMPLETED.json').read_bytes());assert all(pin(fixture/name)==want for name,want in manifest['files'].items());state=dict(status='FAIL2_SAFE_CELLPHONEDB_WORKER_CONTROLS_INCOMPLETE',implementation_credit=False,whole_goal_complete=False,processes=[]);before=None;env=dict(os.environ)
    sources=[Path(__file__).resolve(),ROOT/'tools/literature/cellphonedb_direct_reference_worker.py',ROOT/'tools/literature/compare_cellphonedb_safe_worker.py',ROOT/'tools/literature/validate_cellphonedb_seeded_sampling.py',ROOT/'tools/literature/inventory_hail_runtime_linux.py',kit/'codec.py',kit/'worker.py',kit/'supervisor.py',kit/'profiles.json',setup/'COMPLETED.json'];pins={str(path):pin(path) for path in sources}
    for i,path in enumerate(sources):(out/('bound-source-'+str(i)+'-'+path.name)).write_bytes(path.read_bytes())
    for key in list(env):
        if key.startswith(('PYTHON','CONDA_','PIP_','R_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
    for name in ['private-home','private-cache','private-config','private-temp']:(out/name).mkdir()
    env.update(HOME=str(out/'private-home'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),TMPDIR=str(out/'private-temp'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),PYTHONDONTWRITEBYTECODE='1',PYTHONHASHSEED='0',PATH=str(prefix/'bin')+':'+env.get('PATH',''),LANG='C.UTF-8',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1')
    def members(group):
        result=[]
        for path in Path('/proc').iterdir():
            if not path.name.isdigit():continue
            try:
                fields=(path/'stat').read_text().split(') ',1)[1].split()
                if int(fields[2])==group:result.append(dict(pid=int(path.name),state=fields[0],start_ticks=int(fields[19])))
            except (OSError,ValueError,IndexError):pass
        return result
    def execute(name,command,timeout=1200):
        record=dict(command=list(map(str,command)),started_at=time.time());child=None;code=None;error=None;started=time.monotonic()
        try:
            with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:
                child=subprocess.Popen(record['command'],cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);record['owned_process_group']=child.pid;save(out/(name+'.STARTED.json'),record);code=child.wait(timeout=timeout)
            assert code==0,name+' failed; inspect full retained stdout/stderr'
        except Exception as failure:error=failure;record['error']=repr(failure)
        finally:
            if child is not None:
                record['owned_before_cleanup']=members(child.pid)
                for sig in [signal.SIGTERM,signal.SIGKILL]:
                    if not any(p['state']!='Z' for p in members(child.pid)):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+3
                    while any(p['state']!='Z' for p in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);record['owned_live_after_cleanup']=[p for p in members(child.pid) if p['state']!='Z'];assert not record['owned_live_after_cleanup']
            record.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(out/(name+'.process.json'),record);state['processes'].append(record)
        if error:raise error
    try:
        before=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',before);assert before==json.loads((setup/'runtime-after.json').read_bytes()),'Admitted installed runtime changed';cases=json.loads((fixture/'cases.json').read_bytes());analyses=[]
        for case in cases['cases']:
            if case['expected_error'] is not None:continue
            original=fixture/case['id'];analysis=dict(id=case['id'],method=case['method'],counts=dict(path=str(original/'counts.tsv'),format='tsv'),counts_data=case['counts_data'],metadata=dict(path=str(original/'meta.tsv'),format='tsv'),database=dict(path=str(fixture/case.get('database',cases['database'])),edition='synthetic-v1',source='Project-authored software controls; no biological database'),threshold=case['threshold'],separator=case['separator'],result_precision=case['result_precision'],score_interactions=case['score_interactions'],threads=case['threads'],global_seed=7221)
            for key in ['microenvironments','degs','active_tfs']:
                if case[key] is not None:analysis[key]=dict(path=str(original/(key+'.tsv')),format='tsv')
            if case['method']=='statistical':analysis['statistical']=dict(iterations=case['iterations'],debug_seed=case['debug_seed'],pvalue=case['pvalue'],subsampling=dict(enabled=case['subsampling'],log=case['subsampling_log'],pcs=case['subsampling_num_pc'],cells=case['subsampling_num_cells'],failure_policy='fail'))
            analyses.append(analysis)
        assert len(analyses)==13;inputs=out/'extra-inputs';inputs.mkdir();base=analyses[0];csv_case=json.loads(json.dumps(base));csv_case['id']='simple-explicit-CSV-inputs'
        for field in ['counts','metadata']:
            source=Path(base[field]['path']);rows=list(csv.reader(io.StringIO(source.read_text(encoding='utf8')),delimiter='\t'));file=inputs/(field+'.csv')
            with file.open('w',encoding='utf8',newline='') as stream:csv.writer(stream,lineterminator='\n').writerows(rows)
            csv_case[field]=dict(path=str(file),format='csv')
        numeric=json.loads(json.dumps(base));numeric['id']='simple-canonical-numeric-cell-types';file=inputs/'numeric-metadata.csv';rows=list(csv.reader(io.StringIO(Path(base['metadata']['path']).read_text(encoding='utf8')),delimiter='\t'));mapping={'A':'0','B':'1','C':'2'}
        with file.open('w',encoding='utf8',newline='') as stream:csv.writer(stream,lineterminator='\n').writerows([rows[0],*[[row[0],mapping[row[1]]] for row in rows[1:]]])
        numeric['metadata']=dict(path=str(file),format='csv');analyses.append(numeric)
        analyses.append(csv_case);make=out/'make-h5ad-inputs.py';make.write_text('import anndata as ad,pandas as pd,numpy as np;from scipy import sparse;from pathlib import Path;root=Path('+repr(str(inputs))+');c=pd.read_csv('+repr(base['counts']['path'])+',sep="\\t",index_col=0);a=ad.AnnData(X=c.to_numpy(dtype=float).T,obs=pd.DataFrame(index=c.columns),var=pd.DataFrame(index=c.index));a.write_h5ad(root/"dense.h5ad");a.X=sparse.csr_matrix(a.X);a.write_h5ad(root/"csr.h5ad");b=ad.AnnData(X=(c.to_numpy(dtype=float).T+.1).astype(np.float32),obs=pd.DataFrame(index=c.columns),var=pd.DataFrame(index=c.index));b.write_h5ad(root/"float32.h5ad");print("PASS_THREE_SOURCE_ANNDATA_INPUTS")\n',encoding='utf8',newline='\n');execute('make-h5ad',[python,'-I','-B',make],120)
        for kind in ['dense','csr','float32']:
            case=json.loads(json.dumps(base));case['id']='simple-'+kind+'-H5AD-input';case['counts']=dict(path=str(inputs/(kind+'.h5ad')),format='h5ad');analyses.append(case)
        parallel=json.loads(json.dumps(next(case for case in analyses if case['id']=='statistical-original100-permutations')));parallel['id']='statistical-original-two-processes';parallel['threads']=2;analyses.append(parallel);assert len(analyses)==19;request=dict(schema_version=1,edition='5.0.1',analyses=analyses,maximum_cells=100000,maximum_genes=100000,maximum_matrix_cells=50000000,python_hash_seed=0);save(out/'request.json',request)
        direct=out/'direct';direct.mkdir();execute('direct',[python,'-I','-B',ROOT/'tools/literature/cellphonedb_direct_reference_worker.py',direct,out/'request.json',kit],1200);candidate=out/'candidate';candidate.mkdir()
        for name in ['codec.py','worker.py','supervisor.py']:(candidate/name).write_bytes((kit/name).read_bytes())
        (candidate/'request.json').write_bytes((out/'request.json').read_bytes());(candidate/'runtime.original.json').write_bytes((setup/'COMPLETED.json').read_bytes());execute('candidate',[python,'-I','-B',candidate/'supervisor.py','--runtime',candidate/'runtime.original.json','--out',candidate,'--worker',candidate/'worker.py','--request',candidate/'request.json','--timeout','1200','--maximum-output-bytes','2147483648','--maximum-files','100000'],1260);completed=json.loads((candidate/'COMPLETED.json').read_bytes());assert completed['status']=='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_WORKFLOWS_ONLY' and len(completed['cases'])==19 and completed['pickle_deserialized'] is False
        # Comparison uses only JSON/base64 primitive arrays; it never opens a debug pickle.
        execute('compare-data-only',[python,'-I','-B',ROOT/'tools/literature/compare_cellphonedb_safe_worker.py',out],120);execute('seeded-sampling',[python,'-I','-B',ROOT/'tools/literature/validate_cellphonedb_seeded_sampling.py',out,kit],120);state.update(status='PASS_PAIRED_SAFE_CELLPHONEDB_WORKER_CONTROLS_ONLY',cases=19,serial_complete_byte_and_rng_comparisons=17,entropy_seeded_full_method_sampling_selection_checks=1,seeded_original_sampling_kernel_observation_checks=1,parallel_real_data_comparisons=1,source_pins=pins,runtime_binding=pin(setup/'COMPLETED.json'),pickle_deserialized=False,original_namespace_patched=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);state['runtime_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_SAFE_WORKER_RUNTIME_CHANGED',error='Whole dependency prefix changed')
        state['source_inputs_unchanged']=all(pin(Path(name))==want for name,want in pins.items()) and all(pin(fixture/name)==want for name,want in manifest['files'].items())
        if not state['source_inputs_unchanged']:state.update(status='FAIL2_SAFE_WORKER_SOURCE_INPUT_CHANGED',error='Bound original/worker input changed')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete safe-worker/source comparisons')
    raise SystemExit(main())
