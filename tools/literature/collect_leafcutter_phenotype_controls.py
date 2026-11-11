# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute unchanged original phenotype script/PCA and capture complete typed outputs; no R differential-model or full-tool credit."""
import argparse,gzip,hashlib,importlib.util,json,math,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save,group_members
from inventory_hail_runtime_linux import inventory

def plain(value):
    if isinstance(value,dict):return {str(k):plain(v) for k,v in value.items()}
    if isinstance(value,(list,tuple)):return [plain(v) for v in value]
    if hasattr(value,'tolist'):return plain(value.tolist())
    if isinstance(value,float) and not math.isfinite(value):return dict(state='NaN' if math.isnan(value) else '+Inf' if value>0 else '-Inf')
    return value

def expected(case,original,np,preprocessing,PCA,home):
    samples=case['samples'];rows=case['rows'];kept=[];decisions=[];chromosomes=list(dict.fromkeys(r['id'].split(':')[0] for r in rows));phen={};qq={};qq_order=[]
    for chrom in sorted(chromosomes):
        for suffix,target in [('phen_',phen),('qqnorm_',qq)]:
            path=home/('input.counts.gz.'+suffix+chrom);lines=path.read_text(encoding='utf8').splitlines();assert lines[0].split()==['#Chr','start','end','ID',*samples]
            for line in lines[1:]:f=line.split();assert f[0]==chrom and len(f)==len(samples)+4 and f[3] not in target;target[f[3]]=[float(v) for v in f[4:]]
        qq_order.extend([line.split()[3] for line in (home/('input.counts.gz.qqnorm_'+chrom)).read_text(encoding='utf8').splitlines()[1:]])
    for i,row in enumerate(rows):
        chrom,start,end,cluster=row['id'].split(':');d=dict(ordinal=i,id=row['id'],disposition='SOURCE_BLACKLISTED_CHROMOSOME',source_ratios=[],missing=None,observed_mean=None,observed_minimum=None,observed_maximum=None,population_stddev=None)
        if chrom in case['blacklist']:decisions.append(d);continue
        ratios=[None if den<1 else (float(num)+.5)/(float(den)+.5) for num,den in row['counts']];missing=ratios.count(None);d.update(source_ratios=ratios,missing=missing,disposition='SOURCE_MISSINGNESS_FILTER')
        if missing>len(samples)*.4:decisions.append(d);continue
        obs=[v for v in ratios if v is not None];mean=float(np.mean(obs));minimum=min(obs);maximum=max(obs);values=np.asarray([mean if v is None else v for v in ratios],dtype='float64');sd=float(np.std(values));d.update(observed_mean=mean,observed_minimum=minimum,observed_maximum=maximum,population_stddev=sd,disposition='SOURCE_LOW_VARIATION_FILTER')
        if sd<.005:decisions.append(d);continue
        assert row['id'] in phen and phen[row['id']]==values.tolist();scaled=preprocessing.scale(values);d['disposition']='KEPT';decisions.append(d);kept.append(dict(input_ordinal=i,id=row['id'],chromosome=chrom,start_text=start,end_text=end,start=int(start),imputed_ratios=values.tolist(),standardized=scaled.tolist(),inverse_normal_scores=[],observed_mean=mean,observed_minimum=minimum,observed_maximum=maximum,scaling_mean=float(np.mean(values)),population_stddev=sd))
    assert len(kept)==len(phen)==len(qq)>0;matrix=np.asarray([r['standardized'] for r in kept]);normalized=matrix.copy()
    for j in range(len(samples)):normalized[:,j]=original.qqnorm(matrix[:,j])
    for row,values in zip(kept,normalized):assert qq[row['id']]==values.tolist();row['inverse_normal_scores']=values.tolist()
    sorted_ids=[r['id'] for r in sorted(kept,key=lambda r:(r['chromosome'],r['start'],r['chromosome']+'\t'+r['start_text']+'\t'+r['end_text']+'\t'+r['id']))];assert qq_order==sorted_ids;pca_report=None
    if case['pcs']>0:
        lines=(home/'input.counts.gz.PCs').read_text(encoding='utf8').splitlines();assert lines[0].split()==['id',*samples] and len(lines)-1==min(case['pcs'],len(samples));components=[]
        for i,line in enumerate(lines[1:],1):f=line.split();assert f[0]==str(i) and len(f)==len(samples)+1;components.append([float(v) for v in f[1:]])
        np.random.seed(case['seed']);fit=PCA(n_components=min(case['pcs'],len(samples)));fit.fit(normalized);assert np.allclose(fit.components_,components,rtol=1e-13,atol=1e-13),'Unchanged original PCA and independent public PCA replay differ';pca_report=plain(dict(components=components,solver=fit._fit_svd_solver,mean=fit.mean_,singular_values=fit.singular_values_,explained_variance=fit.explained_variance_,explained_variance_ratio=fit.explained_variance_ratio_,scores=fit.transform(normalized),observations=fit.n_samples_,features=fit.n_features_in_,seed=case['seed'],source='Original sklearn.PCA auto solver/global NumPy seed; input rows are retained introns, features are original samples. This is not sample-score PCA obtained by silently transposing the source matrix.'))
    return dict(samples=samples,original_rows=rows,chromosomes_seen=chromosomes,decisions=decisions,kept=kept,sorted_ids=sorted_ids),pca_report

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--intake',required=True,type=Path);p.add_argument('--fixtures',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert sys.platform=='linux' and out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);(out/'collector-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='SKIP3_ORIGINAL_LEAFCUTTER_PHENOTYPE_RUNTIME_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False,namespace_patched=False,cases=[]);before=None;prefix=None
    try:
        runtime=json.loads((a.runtime/'COMPLETED.json').read_bytes());assert runtime['status']=='PASS_PRIVATE_LEAFCUTTER_DEPENDENCY_CANDIDATE_ONLY';prefix=Path(runtime['runtime_environment']);assert Path(sys.prefix)==prefix and runtime['runtime_trees_unchanged'] is True;before=inventory(prefix,progress=out/'prefix-before.progress.json');save(out/'runtime-before.json',before);assert before==json.loads((a.runtime/'runtime-before.json').read_bytes()),'Complete bound runtime tree differs';intake=json.loads((a.intake/'COMPLETED.json').read_bytes());source=a.intake/'prepare_phenotype_table.py';source=source.resolve();assert pin(source)=={k:intake['original_script'][k] for k in ['bytes','sha256']};source_pin=pin(source);raw=(a.fixtures/'cases.json').read_bytes();(out/'cases.json').write_bytes(raw);fixture=json.loads(raw);os.environ.update(OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',PYTHONDONTWRITEBYTECODE='1')
        import numpy as np
        import scipy,sklearn
        from sklearn import preprocessing
        from sklearn.decomposition import PCA
        assert np.__version__=='1.26.4' and scipy.__version__=='1.11.4' and sklearn.__version__=='1.3.2';spec=importlib.util.spec_from_file_location('original_leafcutter_phenotype',source);original=importlib.util.module_from_spec(spec);spec.loader.exec_module(original);launcher=out/'launch-original.py';launcher.write_text('import runpy,sys\nimport numpy as np\nsource=sys.argv[1];np.random.seed(int(sys.argv[2]));sys.argv=[source,*sys.argv[3:]]\nrunpy.run_path(source,run_name="__main__")\n',encoding='utf8',newline='\n');state['status']='FAIL2_ORIGINAL_LEAFCUTTER_PHENOTYPE_CONTROLS_INCOMPLETE'
        for case in fixture['cases']:
            home=out/case['id'];home.mkdir();tmp=home/'owned-temp';tmp.mkdir();text='chrom '+' '.join(case['samples'])+'\n'+''.join(row['id']+' '+' '.join(str(num)+'/'+str(den) for num,den in row['counts'])+'\n' for row in case['rows']);input_path=home/'input.counts.gz';input_path.write_bytes(gzip.compress(text.encode('utf8'),mtime=0));input_pin=pin(input_path);(home/'blacklist.txt').write_text('\n'.join(case['blacklist'])+'\n' if case['blacklist'] else '',encoding='utf8',newline='\n');command=[sys.executable,'-I','-B','-X','utf8',str(launcher),str(source),str(case['seed']),'-p',str(case['pcs'])]
            if case['id']!='literal-blacklist-chromosomes':command+=['--ChromosomeBlackList','blacklist.txt']
            else:assert case['blacklist']==['X','Y']
            command+=['input.counts.gz'];env=dict(os.environ,TMPDIR=str(tmp),TEMP=str(tmp),TMP=str(tmp),PYTHONPYCACHEPREFIX=str(tmp/'pycache'));child=None;started=time.monotonic();process=dict(command=command,started_at=time.time());error=None;code=None
            try:
                with (home/'original.stdout').open('xb') as stdout,(home/'original.stderr').open('xb') as stderr:
                    child=subprocess.Popen(command,cwd=home,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);process['owned_process_group']=child.pid;code=child.wait(timeout=180)
                assert code==0,'Original full phenotype script failed; inspect retained case stderr'
            except Exception as failure:error=failure
            finally:
                if child is not None:
                    process['owned_before_cleanup']=group_members(child.pid)
                    for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                        if not any(r['state']!='Z' for r in group_members(child.pid)):break
                        try:os.killpg(child.pid,sig)
                        except ProcessLookupError:pass
                        deadline=time.monotonic()+seconds
                        while any(r['state']!='Z' for r in group_members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                    child.wait(timeout=10);process['owned_live_after_cleanup']=[r for r in group_members(child.pid) if r['state']!='Z'];assert not process['owned_live_after_cleanup']
                process.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(home/'original.process.json',process)
            if error:raise error
            assert pin(input_path)==input_pin;result,pca=expected(case,original,np,preprocessing,PCA,home);save(home/'original-result.json',plain(result));save(home/'original-PCA.json',pca);state['cases'].append(dict(id=case['id'],input=input_pin,kept=len(result['kept']),PCA_requested=case['pcs']>0,PCA_solver=pca['solver'] if pca else None));print(case['id'],'complete source rows',len(result['kept']),'PCA',pca['solver'] if pca else 'off',flush=True)
        assert pin(source)==source_pin and (a.fixtures/'cases.json').read_bytes()==raw;state.update(status='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_PHENOTYPE_AND_PCA_CONTROLS_ONLY',source_pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),ROOT/'tools/literature/setup_hail_reference_linux.py',ROOT/'tools/literature/inventory_hail_runtime_linux.py']},original_script=dict(path=source.relative_to(ROOT).as_posix(),**source_pin),python=sys.version,packages=dict(numpy=np.__version__,scipy=scipy.__version__,sklearn=sklearn.__version__),runtime_manifest=dict(path=(a.runtime/'COMPLETED.json').relative_to(ROOT).as_posix(),**pin(a.runtime/'COMPLETED.json')),fixtures=pin(a.fixtures/'cases.json'),global_seed_is_explicit=True,analysis_installed_software=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'prefix-after.progress.json');save(out/'runtime-after.json',after);state['runtime_trees_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_LEAFCUTTER_PHENOTYPE_RUNTIME_CHANGED',error='Complete runtime tree changed during original controls; no results admitted')
        action='Required action: do not admit a partial original/native phenotype matrix, PCA or full LeafCutter workflow. Preserve all source counts/outputs, logs, warnings, parameters and failure receipts. Correct the named source/runtime/input/numerical/process/capacity cause and retry NEW paths. Required missing/different/forbidden dependencies keep analyses skipped. Original undefined/NaN PCA diagnostics and source matrix orientation remain explicit; no biological interpretation is inferred.';state['required_action']=action;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Technical original controls only; no R differential-splicing or whole-paper/tool admission.')+'\n\n'+action+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/source/runtime checks')
    raise SystemExit(main())
