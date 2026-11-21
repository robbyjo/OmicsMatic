from pathlib import Path
import copy,json,os,shutil,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';O=B/'original-pilot-2';O.mkdir();D=T/'src/main/resources/org/jlinalg/omics/twosamplemr-historical';V=R/'build/twosamplemr-historical-runtime-20261121-1';old=R/'build/mr-2/build/twosamplemr-workflow-1/normal-analyses-3';results={}
raw='Marker\tEffect\tStdError\tA1\tA2\tAF\tP\tTrait\tN\nRS-A\t0.1\t0.02\tA\tG\t0.2\t1e-8\tSynthetic\t1000\n rs-b \t0.2\t0.03\tC\tT\t0.4\tNA\tSynthetic\t1000\nRS-A\t0.3\t0.04\tA\tG\t0.2\t1e-9\tSynthetic\t1000\nPAL\t0.05\t0.01\tA\tT\t0.5\t0\tSynthetic\t1000\nBAD\tbad\t0.02\tA\tG\t0.1\t0.5\tSynthetic\t1000\nNEG\t0.1\t-1\tA\tG\t0.3\t0.2\tSynthetic\t1000\nMISS\tNA\t0.02\tA\tG\t0.2\tNA\tSynthetic\t1000\n'
profiles=json.loads((D/'PROFILES.json').read_bytes())
for profile,properties in profiles.items():
    version=properties['version'];reference=json.loads((D/version/'PROFILE.json').read_bytes());allowed={m['obj'] for m in reference['methods']};methods=[m for m in ['mr_wald_ratio','mr_two_sample_ml','mr_egger_regression','mr_ivw','mr_ivw_fe','mr_ivw_mre'] if m in allowed]
    for case in ['harmonise-1','harmonise-2','harmonise-3','mr-default','mr-deterministic','multivariable','pipeline','mr-modes','format-exposure','format-outcome','format-all-missing','zero-rank']:
        out=O/version/case;(out/'inputs').mkdir(parents=True);(out/'source').mkdir();(out/'worker').mkdir();worker=out/'worker';shutil.copyfile(D/version/'PROFILE.json',out/'source/historical-profile.json');shutil.copyfile(D/'worker.R',out/'source/twosamplemr_historical.R')
        r=dict(profile=profile,seed=20261121,parameters=dict(nboot=32),methods=methods,sensitivity=[])
        if case.startswith('format-'):
            text=raw if case!='format-all-missing' else raw.splitlines()[0]+'\n'+raw.splitlines()[-1]+'\n';(out/'inputs/data').write_text(text,encoding='utf-8',newline='\n');r.update(operation='FORMAT',format_options=dict(type='outcome' if case=='format-outcome' else 'exposure',phenotype_col='Trait',snp_col='Marker',beta_col='Effect',se_col='StdError',eaf_col='AF',effect_allele_col='A1',other_allele_col='A2',pval_col='P',samplesize_col='N',min_pval=1e-200))
        else:
            template='mr-deterministic' if case in ['mr-modes','zero-rank'] else case;source=old/template/'analysis';original=json.loads((source/'effective-request.json').read_bytes());r.update(operation=original['operation'])
            if 'harmonise_action' in original:r['harmonise_action']=original['harmonise_action']
            if case=='mr-default':r['methods']=[m['obj'] for m in reference['methods'] if m['use_by_default']]
            if case=='mr-modes':r['methods']=['mr_simple_mode','mr_weighted_mode']
            if case in ['mr-deterministic','pipeline']:r['sensitivity']=['HETEROGENEITY','PLEIOTROPY','SINGLE_SNP','LEAVE_ONE_OUT']
            for key in ['exposure','outcome','harmonised']:
                if key in original:shutil.copyfile(source/original[key],out/'inputs'/key)
            if case=='zero-rank':
                path=out/'inputs/harmonised';lines=path.read_text().splitlines();columns=lines[0].split('\t');at=columns.index('beta.exposure');path.write_text(lines[0]+'\n'+'\n'.join('\t'.join('0' if j==at else value for j,value in enumerate(line.split('\t'))) for line in lines[1:])+'\n',encoding='utf-8',newline='\n')
        (out/'effective-request.json').write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8',newline='\n');lib=V/'versions'/('0.4.9-documentation-only-attempt-3' if version=='0.4.9' else version)/'library';(worker/'runtime-paths.txt').write_text('\n'.join(str(p) for p in [lib,V/'library',R/'build/twosamplemr-runtime-intake-20261121-1/library'])+'\n',encoding='utf-8',newline='\n');command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla','../source/twosamplemr_historical.R']
        with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=worker,env=dict(os.environ,LANG='C',LC_ALL='C'),stdout=stdout,stderr=stderr,timeout=180)
        report=json.loads((worker/'report.json').read_bytes()) if (worker/'report.json').exists() else {};result=dict(exit_code=p.returncode,operation=r['operation'],methods=r.get('methods'),source_functions=report.get('source_functions_guarded'),population=report.get('original_population'),error=report.get('error'),calls=len(report.get('calls',[])));results[version+'/'+case]=result;print(version,case,json.dumps(result),flush=True)
(O/'RESULT.json').write_text(json.dumps(dict(results=results,unmodified_namespace_calls=True,Java_adapter=False,new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
