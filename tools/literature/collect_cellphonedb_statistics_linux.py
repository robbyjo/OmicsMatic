# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture complete unmodified CellPhoneDB5.0.1 prepared-kernel controls, with owned-process supervision and immutable dependency inventories."""
import argparse,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory

ACTION='Prepared numeric components only: no database/gene mapping, normalization, DEG fitting, RNG port, full workflow or biological validation. Preserve full failed attempts. Missing/forbidden dependencies skip dependent analysis (SKIP3); any control failure admits no batch (FAIL2). Correct the named problem and retry NEW paths.'


def worker(out,fixtures):
    import importlib.metadata as metadata
    import numpy as np
    import pandas as pd
    from cellphonedb.src.core.methods import cpdb_statistical_analysis_helper as h
    from cellphonedb.src.core.methods import cpdb_degs_analysis_method as d
    assert metadata.version('cellphonedb')=='5.0.1' and np.__version__=='1.26.4'
    def clean(value):
        if isinstance(value,np.ndarray):return clean(value.tolist())
        if isinstance(value,np.generic):return clean(value.item())
        if isinstance(value,float) and not np.isfinite(value):return 'NaN' if np.isnan(value) else '+Inf' if value>0 else '-Inf'
        if isinstance(value,dict):return {str(k):clean(v) for k,v in value.items()}
        if isinstance(value,(list,tuple)):return [clean(v) for v in value]
        return value
    def significant(means,scores,cutoff=None):
        ranks,values=h.build_significant_means(means,scores,cutoff)
        return dict(means=values.values,ranks=ranks.to_list())
    results=[];native=[];known_failures=[]
    for case in json.loads(fixtures.read_bytes())['cases']:
        print('START',case['id'],flush=True)
        counts=pd.DataFrame(np.array(case['expression'],dtype=case['precision']),index=case['proteins'],columns=case['cells'])
        meta=pd.DataFrame(dict(cell_type=case['cell_types']),index=case['cells'])
        complex_rows={c['id']:[case['proteins'].index(p) for p in c['proteins']] for c in case['complexes']}
        interactions=pd.DataFrame(dict(multidata_1_id=[i['first'] for i in case['interactions']],multidata_2_id=[i['second'] for i in case['interactions']]),index=[i['id'] for i in case['interactions']])
        environments=pd.DataFrame(case['microenvironments'],columns=['cell_type','environment']).rename(columns={'environment':'microenvironment'})
        clusters=h.build_clusters(meta,counts,complex_rows,False)
        pairs=h.get_cluster_combinations(clusters['names'],environments)
        means=h.mean_analysis(interactions,clusters,pairs,case['separator'])
        eligible=h.percent_analysis(clusters,case['threshold'],interactions,pairs,case['separator'])
        result=dict(id=case['id'],cluster_features=clusters['means'].index.to_list(),cell_types=clusters['means'].columns.to_list(),cluster_means=clusters['means'].values,cluster_percents=clusters['percents'].values,pairs=pairs,columns=means.columns.to_list(),interaction_ids=means.index.to_list(),means=means.values,eligible=eligible.values,non_statistical=significant(means,eligible),permuted_means=[],packed_greater=[],pvalues=None,statistical=None,degs=None,deg_relevant=None,deg_significant=None)
        original=None
        if case.get('full_original_shuffle'):
            np.random.seed(case['seed'])
            original=h.shuffled_analysis(case['permutations'],meta,counts,interactions,pairs,complex_rows,means,1,case['separator'])
            original_state=clean(np.random.get_state())
        np.random.seed(case['seed']);labels=[]
        for unused in range(case['permutations']):
            shuffled_meta=h.shuffle_meta(meta)
            labels.append(shuffled_meta['cell_type'].to_list())
            shuffled_clusters=h.build_clusters(shuffled_meta,counts,complex_rows,True)
            shuffled_means=h.mean_analysis(interactions,shuffled_clusters,pairs,case['separator'])
            result['permuted_means'].append(shuffled_means.values)
            result['packed_greater'].append(h.shuffled_greater_than_real(shuffled_means,means))
        replay_state=clean(np.random.get_state())
        if original is not None:
            assert all(np.array_equal(a,b) for a,b in zip(original,result['packed_greater'],strict=True)) and original_state==replay_state,'Original full shuffled_analysis and unmodified-function replay disagree'
            save(out/(case['id']+'.rng-proof.json'),dict(original_state=original_state,replay_state=replay_state,all_original_packed_arrays=clean(original),replay_identical=True,patched_or_intercepted_functions=False))
        if labels:
            pvalues=h.build_percent_result(means,eligible,result['packed_greater'],interactions,pairs,h.build_result_matrix(interactions,pairs,case['separator']),case['separator'])
            result.update(pvalues=pvalues.values,statistical=significant(means,pvalues,case['pvalue_cutoff']))
        if case['degs'] is not None:
            marks=case['degs'];ids=list(dict.fromkeys([*case['proteins'],*[m['protein'] for m in marks]]))
            genes=pd.DataFrame([dict(id_multidata=i,ensembl='ens-'+str(i),gene_name='gene-'+str(i),hgnc_symbol='hgnc-'+str(i)) for i in ids])
            degs=pd.DataFrame([dict(gene='gene-'+str(m['protein']),cluster=m['cell_type']) for m in marks],columns=['gene','cluster'])
            composition=pd.DataFrame([dict(complex_multidata_id=c['id'],protein_multidata_id=p) for c in case['complexes'] for p in c['proteins']],columns=['complex_multidata_id','protein_multidata_id'])
            flags=d.build_degs_matrix(degs,genes,meta,counts,composition,'gene_name')
            relevant=eligible*d.degs_analysis(flags,interactions,pairs,case['separator'])
            ignored=[m for m in marks if m['protein'] not in case['proteins'] or m['cell_type'] not in clusters['names']]
            result.update(degs=dict(features=flags.index.to_list(),cell_types=flags.columns.to_list(),flags=flags.values,ignored_marks=ignored),deg_relevant=relevant.values,deg_significant=significant(means,relevant))
        native_case=dict(case,expression=counts.values.astype(np.float64).tolist(),permutation_labels=labels)
        native.append(native_case);results.append(clean(result))
        save(out/(case['id']+'.original.json'),dict(result=clean(result),source_dtype=dict(input=str(counts.values.dtype),cluster_means=str(clusters['means'].values.dtype),interaction_means=str(means.values.dtype))))
        print('PASS',case['id'],flush=True)
    # Independently retain actual source failures; never patch source or admit their partial results.
    small_counts=pd.DataFrame([[1.,2.],[3.,4.]],index=[1,2],columns=['x','y']);small_meta=pd.DataFrame(dict(cell_type=['A','B']),index=['x','y']);small_interactions=pd.DataFrame(dict(multidata_1_id=[1],multidata_2_id=[2]),index=['I']);small_clusters=h.build_clusters(small_meta,small_counts,{},False);small_pairs=h.get_cluster_combinations(small_clusters['names']);small_means=h.mean_analysis(small_interactions,small_clusters,small_pairs,'|')
    for name,expected,action in [
        ('empty-composition-DEG',TypeError,lambda:d.build_degs_matrix(pd.DataFrame(dict(gene=['g1'],cluster=['A'])),pd.DataFrame(dict(id_multidata=[1],ensembl=['e1'],gene_name=['g1'],hgnc_symbol=['h1'])),small_meta,small_counts,pd.DataFrame(columns=['complex_multidata_id','protein_multidata_id']),'gene_name')),
        ('single-thread-one-iteration-progress-zero',ZeroDivisionError,lambda:h.shuffled_analysis(1,small_meta,small_counts,small_interactions,small_pairs,{},small_means,1,'|'))]:
        failed=False
        try:action()
        except expected as error:
            failed=True;known_failures.append(dict(id=name,type=type(error).__name__,message=str(error),scientific_result_admitted=False));(out/(name+'.EXPECTED-ERROR.traceback')).write_text(traceback.format_exc(),encoding='utf8',newline='\n')
        assert failed,'Expected original error behavior changed: '+name
    save(out/'original-known-failures.json',known_failures)
    save(out/'native-input.json',dict(cases=native));save(out/'original-results.json',dict(cases=results))
    save(out/'ORIGINAL-COMPLETED.json',dict(status='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_PREPARED_KERNEL_CONTROLS_ONLY',cases=len(results),known_original_failures=known_failures,original_namespace_patched=False,source_user_warning_filter_retained='Original module ignores UserWarning globally; suppressed messages cannot be claimed captured.',numpy_rng_implemented_in_java=False,analysis_scope=ACTION))


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
        result=json.loads((out/'ORIGINAL-COMPLETED.json').read_bytes());assert result['status']=='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_PREPARED_KERNEL_CONTROLS_ONLY' and result['cases']==13
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
        assert all(pin(Path(p))==want for p,want in pins.items());state['required_action']=ACTION;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','All original prepared controls complete; native parity and full workflow validation remain separate.')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source, runtime or full-control checks')
    raise SystemExit(main())
