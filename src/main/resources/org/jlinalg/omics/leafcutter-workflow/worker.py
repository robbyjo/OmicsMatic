# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged original LeafCutter programs under a separate owned supervisor; preserve all complete output families and lossless post-fit metadata."""
import gzip,hashlib,importlib.util,json,math,os,shutil,subprocess,sys,time,traceback
from pathlib import Path


def save(path,value):path.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n',encoding='utf8',newline='\n')


def typed(value):
    if isinstance(value,dict):return {str(k):typed(v) for k,v in value.items()}
    if isinstance(value,(list,tuple)):return [typed(v) for v in value]
    if hasattr(value,'tolist'):return typed(value.tolist())
    if isinstance(value,float) and not math.isfinite(value):return dict(state='NaN' if math.isnan(value) else '+Inf' if value>0 else '-Inf')
    return value


def main():
    home=Path(sys.argv[1]);request=json.loads(Path(sys.argv[2]).read_bytes());source=home.parent/'source';runtime=json.loads((home/'runtime.original.json').read_bytes());assert sys.platform=='linux' and Path(sys.prefix)==Path(runtime['runtime_environment']);cases=request['analyses'];completed=[];state=dict(status='FAIL2_ORIGINAL_LEAFCUTTER_WORKFLOWS_INCOMPLETE',cases=completed,namespace_patched=False,analysis_installed_software=False)
    try:
        import numpy as np,scipy,sklearn
        from sklearn.decomposition import PCA
        from sklearn import preprocessing
        assert np.__version__=='1.26.4' and scipy.__version__=='1.11.4' and sklearn.__version__=='1.3.2'
        for case in cases:
            target=home/case['id'];target.mkdir();processes=[]
            def execute(label,command,cwd,extra=None):
                env=dict(os.environ);env.update(extra or {});command=list(map(str,command));started=time.monotonic()
                with (target/(label+'.stdout')).open('xb') as stdout,(target/(label+'.stderr')).open('xb') as stderr:
                    result=subprocess.run(command,cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
                record=dict(command=command,exit_code=result.returncode,elapsed_seconds=time.monotonic()-started);save(target/(label+'.process.json'),record);processes.append(record);assert result.returncode==0,'Original '+label+' failed; inspect complete retained diagnostics'
            ratio=None;counts=None
            if case['kind'] in ['cluster','pipeline']:
                inputs=target/'junctions';inputs.mkdir();paths=[]
                for sample in case['samples']:
                    name=sample['sample_id']+'.junc';shutil.copyfile(sample['junctions'],inputs/name);paths.append('junctions/'+name)
                (target/'junctions.txt').write_text('\n'.join(paths)+'\n',encoding='utf8',newline='\n');original=target/'original';original.mkdir();options=case['cluster'];command=[sys.executable,'-I','-B','-X','utf8',source/'original/clustering/leafcutter_cluster_regtools.py','-j','junctions.txt','-o','leaf','-r','original','-l',str(options['maximum_intron_length']),'-m',str(options['minimum_reads']),'-p',str(options['minimum_ratio'])]
                if not options['check_chromosomes']:command+=['-k','1']
                execute('clustering',command,target)
                for name in ['leaf_pooled','leaf_refined','leaf_sortedlibs','leaf_perind.counts.gz','leaf_perind_numers.counts.gz']:assert (original/name).is_file(),'Original clustering family incomplete: '+name
                ratio=original/'leaf_perind.counts.gz';counts=original/'leaf_perind_numers.counts.gz'
            if case['kind']=='phenotype':ratio=Path(case['counts_ratio'])
            if case['kind']=='differential':counts=Path(case['counts'])
            if case['kind']=='phenotype' or 'phenotype' in case:
                options=case['phenotype'];work=target/'phenotype';work.mkdir();copied=work/'input.counts.gz'
                if ratio.read_bytes()[:2]==b'\x1f\x8b':shutil.copyfile(ratio,copied)
                else:copied.write_bytes(gzip.compress(ratio.read_bytes(),mtime=0))
                (work/'blacklist.txt').write_text('\n'.join(options['blacklist'])+'\n' if options['blacklist'] else '',encoding='utf8',newline='\n');launcher=work/'launch-original.py';launcher.write_bytes((source/'phenotype-launcher.py').read_bytes());original=source/'original/scripts/prepare_phenotype_table.py';execute('phenotype',[sys.executable,'-I','-B','-X','utf8',launcher,original,options['seed'],'-p',options['pcs'],'--ChromosomeBlackList','blacklist.txt','input.counts.gz'],work)
                lines=gzip.decompress(copied.read_bytes()).decode('utf8').splitlines();samples=lines[0].split()[1:];rows=[];blacklist=set(options['blacklist']);kept=[];decisions=[]
                spec=importlib.util.spec_from_file_location('omics_original_leafcutter_phenotype',original);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
                for ordinal,line in enumerate(lines[1:]):
                    fields=line.split();identifier=fields[0];pairs=[list(map(int,v.split('/'))) for v in fields[1:]];rows.append(dict(id=identifier,counts=pairs));chrom,start,end,cluster=identifier.split(':');decision=dict(id=identifier,ordinal=ordinal,disposition='SOURCE_BLACKLISTED_CHROMOSOME',source_ratios=[],missing=None,observed_mean=None,observed_minimum=None,observed_maximum=None,population_stddev=None)
                    if chrom in blacklist:decisions.append(decision);continue
                    values=[None if den<1 else (float(num)+.5)/(float(den)+.5) for num,den in pairs];missing=values.count(None);decision.update(source_ratios=values,missing=missing,disposition='SOURCE_MISSINGNESS_FILTER')
                    if missing>len(samples)*.4:decisions.append(decision);continue
                    observed=[v for v in values if v is not None];mean=float(np.mean(observed));imputed=np.asarray([mean if v is None else v for v in values]);sd=float(np.std(imputed));decision.update(observed_mean=mean,observed_minimum=min(observed),observed_maximum=max(observed),population_stddev=sd,disposition='SOURCE_LOW_VARIATION_FILTER')
                    if sd<.005:decisions.append(decision);continue
                    decision['disposition']='KEPT';decisions.append(decision);kept.append(dict(input_ordinal=ordinal,id=identifier,chromosome=chrom,start_text=start,end_text=end,start=int(start),imputed_ratios=imputed.tolist(),standardized=preprocessing.scale(imputed).tolist(),inverse_normal_scores=[],observed_mean=mean,observed_minimum=min(observed),observed_maximum=max(observed),scaling_mean=float(np.mean(imputed)),population_stddev=sd))
                assert kept,'No retained phenotype introns';normalized=np.asarray([r['standardized'] for r in kept]);normalized=normalized.copy()
                for column in range(len(samples)):normalized[:,column]=module.qqnorm(normalized[:,column])
                for row,values in zip(kept,normalized):row['inverse_normal_scores']=values.tolist()
                chromosomes=list(dict.fromkeys(r['id'].split(':')[0] for r in rows));pca=None
                if options['pcs']>0:
                    np.random.seed(options['seed']);fit=PCA(n_components=min(options['pcs'],len(samples)));fit.fit(normalized);pca=typed(dict(components=fit.components_,mean=fit.mean_,scores=fit.transform(normalized),singular_values=fit.singular_values_,explained_variance=fit.explained_variance_,explained_variance_ratio=fit.explained_variance_ratio_,solver=fit._fit_svd_solver,observations=fit.n_samples_,features=fit.n_features_in_))
                save(work/'source-report.json',dict(samples=samples,original_rows=rows,chromosomes_seen=chromosomes,decisions=decisions,kept=kept,sorted_ids=[r['id'] for r in sorted(kept,key=lambda r:(r['chromosome'],r['start'],r['chromosome']+'\t'+r['start_text']+'\t'+r['end_text']+'\t'+r['id']))],PCA=pca,source_orientation='Retained introns are observations; original samples are features. No transpose.'))
            if case['kind']=='differential' or 'differential' in case:
                options=case['differential'];work=target/'differential';work.mkdir();count_name='counts.txt.gz' if counts.read_bytes()[:2]==b'\x1f\x8b' else 'counts.txt';shutil.copyfile(counts,work/count_name);shutil.copyfile(options['groups'],work/'groups.txt');command=[runtime['Rscript'],'--vanilla',source/'worker.R','-o','original','-s',str(options['maximum_cluster_size']),'-i',str(options['minimum_samples_per_intron']),'-g',str(options['minimum_samples_per_group']),'-c',str(options['minimum_coverage']),'-t',str(options['cluster_timeout_seconds']),'-p',str(options['threads']),'--init',options['initialization'],'--seed',str(options['seed'])]
                if options.get('exons') is not None:
                    exon_name='exons.txt.gz' if Path(options['exons']).read_bytes()[:2]==b'\x1f\x8b' else 'exons.txt';shutil.copyfile(options['exons'],work/exon_name);command+=['-e',exon_name]
                command += [count_name,'groups.txt'];execute('differential',command,work,dict(OMICS_LEAFCUTTER_SOURCE=str(source),OMICS_LEAFCUTTER_LIBRARY=runtime['R_library'],OMICS_LEAFCUTTER_CASE=str(work)))
                report=json.loads((work/'source-report.json').read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_DIFFERENTIAL_PROGRAM_ONLY' and report['original_namespace_patched'] is False and report['numeric_export_significant_digits']==17
                for name in ['original_cluster_significance.txt','original_effect_sizes.txt','original-results.rds','original-input-state.rds']:assert (work/name).is_file(),'Original differential-splicing family incomplete'
            completed.append(dict(id=case['id'],kind=case['kind'],processes=processes));save(target/'COMPLETED.json',dict(status='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_CASE_ONLY',id=case['id'],kind=case['kind']))
        state.update(status='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_WORKFLOWS_ONLY',python=sys.version,numpy=np.__version__,scipy=scipy.__version__,sklearn=sklearn.__version__)
    except Exception as error:state['error']=repr(error);(home/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(home/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original workflow checks')
    raise SystemExit(main())
