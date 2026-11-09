# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check current JVM25 original execution, portable frozen verification and actual Linux runtime skips."""
import argparse,copy,json,os,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save,environment


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.resolve().relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','positive','bindings','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_RSUBREAD_PORTABILITY_VALIDATION',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,runs={})
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};proof=json.loads((a.positive/'COMPLETED.json').read_bytes());assert proof['status']=='passed-fifty-public-original-Rsubread-complete-count-list-executions-and-separate-original-comparisons'
        java25='C:/Program Files/Common Files/Oracle/Java/javapath/java.exe';base=ROOT/'build/literature-review/rsubread-runtime';script=ROOT/'tools/literature/compare_rsubread_workflow_subset.R'
        def execute(name,command,expected,env=None):
            with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:run=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=240,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==expected,(name,run.returncode,expected)
        for edition,original in [('2260','original-controls-3'),('2232-R441','original2232-controls-1')]:
            source=a.positive/(edition+'-analysis');before={q.relative_to(source).as_posix():pin(q) for q in source.rglob('*') if q.is_file()}
            for runtime,prefix in [('windows25',[java25]),('linux21',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'])]:
                islinux=runtime=='linux21';path=out/(edition+'-'+runtime+'.verify.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=linux(source) if islinux else str(source.resolve())))
                execute(edition+'-'+runtime+'-verify',prefix+['-Xmx256m','-jar',linux(jar) if islinux else str(jar),linux(path) if islinux else str(path)],0)
            assert all(pin(source/n)==v for n,v in before.items())
            raw=json.loads((a.positive/(edition+'.request.json')).read_bytes());selected=['mixed-libraries','fractional-overlap','fresh-empty-junctions','duplicate-basenames'];by={r['id']:r for r in raw['cases']};raw['cases']=[by[n] for n in selected];raw['binding']=str((a.bindings/(edition+'-bound/COMPLETED.json')).resolve());raw['out']=str(out/(edition+'-windows25-analysis'));path=out/(edition+'-windows25.request.json');save(path,raw)
            execute(edition+'-windows25-analyze',[java25,'-Xmx256m','-jar',str(jar),str(path)],0);analysis=Path(raw['out']);receipt=json.loads((analysis/'COMPLETED.json').read_bytes());assert receipt['analysis_admitted'] and len(receipt['summaries'])==4
            path=out/(edition+'-windows25-fresh.verify.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=str(analysis)));execute(edition+'-windows25-fresh-verify',[java25,'-Xmx256m','-jar',str(jar),str(path)],0)
            comparison=out/(edition+'-comparison');comparison.mkdir();rscript=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe') if edition=='2260' else base/'r441-setup-1/R441/root/bin/x64/Rscript.exe';env=environment()
            if edition=='2232-R441':env['R_HOME']=rscript.parents[2].as_posix()
            execute(edition+'-separate-original-comparison',[str(rscript),'--vanilla',str(script),str(analysis),str(base/original/'original'),str(base/'result-edges-1'/edition),str(comparison)],0,env)
            assert (comparison/'R-status.txt').read_text().strip()=='PASS4_COMPLETE_JVM25_PUBLIC_ORIGINAL_COUNT_LIST_COMPARISONS';state['runs'][edition]=dict(Windows25_fresh_original_comparisons=4,Windows25_frozen_verify=True,Linux21_frozen_verify=True)
        raw=json.loads((a.positive/'2260.request.json').read_bytes());raw['cases']=raw['cases'][:1];raw['out']=linux(out/'linux-analysis-skipped');path=out/'linux-analysis-skip.request.json';save(path,raw)
        execute('linux-analysis-skip',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx256m','-jar',linux(jar),linux(path)],3)
        raw=json.loads((a.bindings/'2260.request.json').read_bytes());raw['out']=linux(out/'linux-binding-skipped');path=out/'linux-binding-skip.request.json';save(path,raw)
        execute('linux-binding-skip',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx256m','-cp',linux(jar),'org.jlinalg.cli.RsubreadRuntimeCli',linux(path)],3)
        for name,key in [('linux-analysis-skipped','analysis_admitted'),('linux-binding-skipped','runtime_bound')]:
            receipt=json.loads((out/name/'COMPLETED.json').read_bytes());assert receipt['status'].startswith('SKIP3') and not receipt[key] and not any((out/name).rglob('process.json'))
        assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());state.update(status='passed-eight-JVM25-original-comparisons-four-portable-verifications-and-two-actual-Linux-runtime-skips',artifact=build['artifact'],fresh_original_executions=8,Linux_original_R_counting_performed=False,source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),script]})
    except Exception as e:state['error']=repr(e);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual original/portable/skip checks')
    raise SystemExit(main())
