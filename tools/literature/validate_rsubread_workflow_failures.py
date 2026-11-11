# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual public skip, late-original/native failure, cancellation and closed-batch verification controls."""
import argparse,copy,json,os,shutil,subprocess,sys,time,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_rsubread_workflow import cases


def main():
    sys.stdout.reconfigure(encoding='utf8')
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','bindings','positive','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    source=Path(__file__).resolve();(out/'source-snapshot/tools/literature').mkdir(parents=True);(out/'source-snapshot/tools/literature'/source.name).write_bytes(source.read_bytes())
    state=dict(status='FAIL2_RSUBREAD_PUBLIC_FAILURE_VALIDATION',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,controls={},source_pins={source.relative_to(ROOT).as_posix():pin(source)})
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';base=ROOT/'build/literature-review/rsubread-runtime';allcases=cases(base/'original-controls-3/inputs',base/'result-edges-1/inputs');by={r['id']:r for r in allcases}
        template=dict(schema_version=1,mode='ANALYZE',binding=str((a.bindings/'2260-bound/COMPLETED.json').resolve()),cases=[by['single-meta']])
        def execute(name,request,expected,started=False,first_provisional=False):
            request=copy.deepcopy(request);request['out']=str(out/name);path=out/(name+'.request.json');save(path,request);command=[java,'-Xmx256m','-jar',str(jar),str(path)]
            run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode))
            assert run.returncode==expected,(name,run.returncode,expected,run.stderr.decode(errors='replace'))
            home=out/name
            if home.exists():
                receipt=json.loads((home/'COMPLETED.json').read_bytes());assert not receipt['analysis_admitted'] and receipt['status'].startswith('SKIP3' if expected==3 else 'FAIL2')
                if started:assert any(home.glob('cases/*/worker/process.json'))
                if first_provisional:
                    first=json.loads((home/'cases'/request['cases'][0]['id']/'PROVISIONAL.json').read_bytes());assert not first['analysis_admitted'];assert (home/'cases'/request['cases'][0]['id']/'worker/complete-original-result.rds').is_file()
                assert not any(json.loads(q.read_bytes()).get('analysis_admitted',False) for q in home.glob('cases/*/PROVISIONAL.json'))
            state['controls'][name]=dict(exit_code=run.returncode,original_process_started=started,prior_original_result_retained_not_admitted=first_provisional)
        execute('forbidden',dict(template,allow_execution=False),3)
        execute('missing-binding',dict(template,binding=str(out/'not-available/COMPLETED.json')),3)
        bad=out/'malformed.gtf';bad.write_bytes(b'not\ta\tGTF\n')
        late=copy.deepcopy(by['single-meta']);late['id']='bad-annotation';late['annotation']['path']=str(bad)
        execute('late-original-annotation-error',dict(template,cases=[by['single-meta'],late]),2,True,True)
        late=copy.deepcopy(by['single-meta']);late['id']='later-capacity-error'
        execute('late-native-capacity-error',dict(template,cases=[by['fresh-numeric-ids'],late],limits=dict(maximum_cells=28)),2,True,True)
        badsam=out/'broken.sam';badsam.write_bytes(b'not an alignment file\n');late=copy.deepcopy(by['single-meta']);late['id']='bad-alignment';late['files'][0]['path']=str(badsam)
        execute('late-original-alignment-error',dict(template,cases=[by['single-meta'],late]),2,True,True)
        decimal=out/'double-gene-id.saf';decimal.write_bytes(b'GeneID\tChr\tStart\tEnd\tStrand\n123.5\tchr1\t101\t150\t+\n');late=copy.deepcopy(by['single-meta']);late['id']='unsupported-double-gene-id';late['annotation'].update(path=str(decimal),format='SAF')
        execute('unsupported-original-GeneID-storage',dict(template,cases=[late]),2,True)
        execute('whole-batch-output-cap',dict(template,limits=dict(maximum_output_bytes=1)),2)
        name='cancel-after-provisional';request=dict(template,out=str(out/name),cases=allcases);path=out/(name+'.request.json');save(path,request);command=[java,'-Xmx256m','-jar',str(jar),str(path)]
        with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:
            process=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0);deadline=time.monotonic()+120
            while time.monotonic()<deadline:
                if process.poll() is not None:raise AssertionError('Expected live batch ended before cancellation observation')
                if (out/name/'cases/rna-paper-options/PROVISIONAL.json').exists():break
                time.sleep(.01)
            else:raise AssertionError('No provisional original result appeared in the live public batch')
            assert process.poll() is None;(out/name/'CANCEL').write_bytes(b'cancel observed live public Java batch after an original result\n');code=process.wait(timeout=30)
        save(out/(name+'.process.json'),dict(command=command,exit_code=code,observed_live_public_Java_PID=process.pid));assert code==2;receipt=json.loads((out/name/'COMPLETED.json').read_bytes());assert not receipt['analysis_admitted'];assert not json.loads((out/name/'cases/rna-paper-options/PROVISIONAL.json').read_bytes())['analysis_admitted'];state['controls'][name]=dict(exit_code=2,earlier_original_result_retained_not_admitted=True)
        # Retain complete independent copies for corruption experiments; never change the passing analysis.
        original=a.positive/'2260-analysis';before={q.relative_to(original).as_posix():pin(q) for q in original.rglob('*') if q.is_file()}
        for name,kind in [('changed-annotation-length','length'),('extra-result-member','extra'),('false-completed-population','summary')]:
            home=out/name;shutil.copytree(original,home)
            if kind=='length':
                path=home/'cases/single-meta/worker/values/annotation.frame';raw=path.read_bytes();assert b'V\t0000006e\n' in raw;path.write_bytes(raw.replace(b'V\t0000006e\n',b'V\t0000006f\n',1))
            elif kind=='extra':(home/'cases/single-meta/worker/values/unrecognized.txt').write_bytes(b'must not be accepted\n')
            else:
                path=home/'COMPLETED.json';receipt=json.loads(path.read_bytes());receipt['summaries'][0]['rows']+=1;save(path,receipt)
            if kind!='summary':
                seal=json.loads((home/'capture-files.json').read_bytes());seal['files']={q.relative_to(home).as_posix():pin(q) for q in home.rglob('*') if q.is_file() and q.name not in ['capture-files.json','COMPLETED.json']};save(home/'capture-files.json',seal)
            path=out/(name+'.verify.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=str(home)));command=[java,'-Xmx256m','-jar',str(jar),str(path)];run=subprocess.run(command,capture_output=True,timeout=120);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==2,(name,run.stderr.decode(errors='replace'));state['controls'][name]=dict(exit_code=2,outer_hashes_recomputed=kind!='summary',scientific_original_analysis_unchanged=True)
        assert all(pin(original/n)==v for n,v in before.items())
        assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());state.update(status='passed-public-Rsubread-real-skips-late-original-native-errors-cancellation-and-deeper-verification-rejections',artifact=build['artifact'],source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),ROOT/'tools/literature/validate_rsubread_workflow.py']},prior_scientific_reference_unchanged=True)
    except Exception as e:state['error']=repr(e);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual public batch/admission and unchanged-reference controls')
    raise SystemExit(main())
