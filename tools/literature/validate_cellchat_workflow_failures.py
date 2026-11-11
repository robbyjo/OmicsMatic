# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual-JAR input/runtime dispositions and rehashed-bundle rejection."""
import argparse,copy,csv,io,json,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/cellchat-runtime'
JAVA='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='workflow-build21-7');p.add_argument('--positive',default='typed-controls-7');a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True);state=dict(status='FAIL2_CELLCHAT_WORKFLOW_DISPOSITION_CONTROLS',implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((BASE/a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']}
        for name,wanted in built['source_pins'].items():assert pin(ROOT/name)==wanted
        fixture=BASE/'original-workflow-controls-1/inputs';profile=BASE/'runtime-profile-1/runtime-profile.json';runtime=json.loads(profile.read_bytes())
        science=json.loads((fixture/'request.json').read_bytes());baseline=dict(schema_version=1,out='',runtime_profile=str(profile),analysis=science,limits=dict(input_bytes=268435456,output_bytes=1073741824,wall_seconds=1800,max_genes=100000,max_cells=100000,max_files=200000,runtime_bytes=34359738368))
        originalMembers=json.loads((ROOT/'tools/literature/cellchat-workflow-source-lock.json').read_bytes())['sources']['2.2.0-2025-release']['files']
        originalRoot=Path(runtime['source_root']);assert all(pin(originalRoot/n)==v for n,v in originalMembers.items())
        helper=Path(__file__).resolve();snapshot=out/'source-snapshot'/helper.relative_to(ROOT);snapshot.parent.mkdir(parents=True);snapshot.write_bytes(helper.read_bytes());cases=[]
        def execute(name,arguments,expected):
            command=[JAVA,'-Xmx512m','-jar',str(jar),*map(str,arguments)]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
            save(out/(name+'-process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==expected,(name,run.returncode,'Read retained stderr')
            return (out/(name+'.stderr')).read_text(encoding='utf8')
        def request(name,change,expected=2):
            r=copy.deepcopy(baseline);r['out']=str(out/(name+'-analysis'));change(r);path=out/(name+'-request.json');save(path,r)
            stderr=execute(name,['--request',path],expected);assert ('REQUIRED ACTION' in stderr or 'REQUIRED ACTIONS' in stderr) and ('SKIP3' if expected==3 else 'FAIL2') in stderr
            target=Path(r['out']);completion=target/'COMPLETED.json'
            if completion.exists():
                receipt=json.loads(completion.read_bytes());assert receipt['result_admitted'] is False and not (target/'results').exists() and (target/'WARNINGS.md').is_file()
            assert not (target/'process-started.json').exists() or name in {'timeout','changed-original-source'}
            cases.append(dict(case=name,exit_code=expected,result_admitted=False));return target,stderr
        request('unknown-field',lambda r:r['analysis'].update(arbitrary_R_code='stop(1)'))
        request('raw-count-units',lambda r:r['analysis'].update(expression_units='raw-counts'))
        request('unused-group-level',lambda r:r['analysis']['group_order'].append('not-observed'))
        request('too-many-biological-samples-required',lambda r:r['analysis']['condition_filter'].update(min_samples=4))
        request('input-byte-capacity',lambda r:r['limits'].update(input_bytes=1))
        request('output-byte-capacity',lambda r:r['limits'].update(output_bytes=1024))
        request('missing-runtime-profile',lambda r:r.update(runtime_profile=str(out/'not-a-profile.json')),3)
        missing=copy.deepcopy(runtime);missing['rscript']=str(out/'not-Rscript.exe');save(out/'missing-executable-profile.json',missing)
        request('missing-executable',lambda r:r.update(runtime_profile=str(out/'missing-executable-profile.json')),3)
        changed=copy.deepcopy(runtime);changed['files']={changed['rscript']:dict(bytes=runtime['files'][changed['rscript']]['bytes'],sha256='0'*64)};save(out/'wrong-executable-hash-profile.json',changed)
        _,error=request('changed-runtime-binding',lambda r:r.update(runtime_profile=str(out/'wrong-executable-hash-profile.json')));assert 'Changed original runtime file' in error
        incomplete=copy.deepcopy(runtime);incomplete['files']={incomplete['rscript']:incomplete['files'][incomplete['rscript']]};save(out/'incomplete-library-profile.json',incomplete)
        _,error=request('unbound-library-members',lambda r:r.update(runtime_profile=str(out/'incomplete-library-profile.json')));assert 'Unbound extra R library file' in error
        for name,token in [('nan-expression','NaN'),('negative-expression','-1')]:
            text=(fixture/'expression.tsv').read_text(encoding='utf8').splitlines();row=text[1].split('\t');row[1]=token;text[1]='\t'.join(row);path=out/(name+'.tsv');path.write_text('\n'.join(text)+'\n',encoding='utf8')
            request(name,lambda r,p=path:r['analysis'].update(expression=str(p)))
        meta=(fixture/'metadata.tsv').read_text(encoding='utf8').splitlines();meta[1],meta[2]=meta[2],meta[1];badmeta=out/'reordered-cells.tsv';badmeta.write_text('\n'.join(meta)+'\n',encoding='utf8')
        request('phenotype-order-mismatch',lambda r:r['analysis'].update(metadata=str(badmeta)))
        sentinel=out/'existing-output';sentinel.mkdir();(sentinel/'sentinel.txt').write_text('retain unchanged');old=pin(sentinel/'sentinel.txt')
        request('existing-output',lambda r:r.update(out=str(sentinel)));assert pin(sentinel/'sentinel.txt')==old and len(list(sentinel.iterdir()))==1
        duplicate=copy.deepcopy(baseline);duplicate['out']=str(out/'duplicate-analysis');raw=json.dumps(duplicate);duplicatePath=out/'duplicate-request.json';duplicatePath.write_text(raw[:-1]+',"schema_version":1}',encoding='utf8')
        execute('duplicate-json',['--request',duplicatePath],2);cases.append(dict(case='duplicate-json',exit_code=2,result_admitted=False))
        original=Path(runtime['source_root']);changedSource=out/'changed-source';assert changedSource.resolve().is_relative_to(out);shutil.copytree(original,changedSource)
        file=changedSource/'R/modeling.R';file.write_bytes(file.read_bytes()+b'\n# changed source bytes\n')
        changedProfile=copy.deepcopy(runtime);changedProfile['source_root']=str(changedSource);save(out/'changed-source-profile.json',changedProfile)
        _,error=request('changed-original-source',lambda r:r.update(runtime_profile=str(out/'changed-source-profile.json')))
        assert 'Original CellChat source size mismatch: R/modeling.R' in error
        target,_=request('timeout',lambda r:r['limits'].update(wall_seconds=1))
        started=json.loads((target/'process-started.json').read_bytes());pid=started['pid']
        probe=subprocess.run(['tasklist','/FI','PID eq '+str(pid),'/FO','CSV','/NH'],capture_output=True,text=True,timeout=30)
        (out/'timeout-owned-process-query.txt').write_text(probe.stdout+probe.stderr,encoding='utf8');assert not any(len(row)>1 and row[1]==str(pid) for row in csv.reader(io.StringIO(probe.stdout)))
        positive=BASE/a.positive/'analysis';completed=json.loads((positive/'COMPLETED.json').read_bytes());assert completed['result_admitted'] is True
        tamper=[]
        def tamperCase(name,relative,edit,baselineBundle=positive):
            target=out/(name+'-copy');assert target.resolve().is_relative_to(out) and not target.exists();shutil.copytree(baselineBundle,target);file=target/relative;edit(file)
            receipt=json.loads((target/'COMPLETED.json').read_bytes());receipt['files'][relative]=pin(file);save(target/'COMPLETED.json',receipt)
            execute(name,['--verify',target],2);tamper.append(name)
        tamperCase('rehashed-failed-exit','process.json',lambda p:save(p,dict(json.loads(p.read_bytes()),exit_code=1)))
        tamperCase('rehashed-unadmitted-status','analysis-status.json',lambda p:save(p,dict(json.loads(p.read_bytes()),result_admitted=False)))
        tamperCase('rehashed-hidden-source-warning','WARNINGS.md',lambda p:p.write_text(p.read_text(encoding='utf8').replace('ORIGINAL CENTRALITY FALLBACK WARNING:','hidden fallback:'),encoding='utf8'))
        def badCount(file):
            with file.open(encoding='utf8',newline='') as stream:rows=list(csv.reader(stream,delimiter='\t'))
            column=rows[0].index('value');rows[1][column]=str(float(rows[1][column])+1)
            with file.open('w',encoding='utf8',newline='') as stream:csv.writer(stream,delimiter='\t',lineterminator='\n').writerows(rows)
        tamperCase('rehashed-wrong-network-count','results/cases/condition-1/network-counts.tsv',badCount)
        tamperCase('rehashed-worker-edit','worker-source.R',lambda p:p.write_bytes(p.read_bytes()+b'\n# changed worker\n'))
        rare=BASE/'workflow-edges-2/replicate-zero-and-rare/analysis';constant=BASE/'workflow-edges-2/constant-comparison/analysis'
        tamperCase('rehashed-hidden-pooled-sample-warning','WARNINGS.md',lambda p:p.write_text(p.read_text(encoding='utf8').replace('POOLED VERSUS SAMPLE WARNING:','hidden discrepancy:'),encoding='utf8'),rare)
        tamperCase('rehashed-hidden-nonfinite-test-warning','WARNINGS.md',lambda p:p.write_text(p.read_text(encoding='utf8').replace('ORIGINAL NONFINITE SAMPLE TEST WARNING:','hidden nonfinite:'),encoding='utf8'),constant)
        tamperCase('rehashed-hidden-coverage-warning','WARNINGS.md',lambda p:p.write_text(p.read_text(encoding='utf8').replace('COMPARISON COVERAGE WARNING:','hidden coverage:'),encoding='utf8'),rare)
        assert helper.read_bytes()==snapshot.read_bytes() and all(pin(ROOT/n)==v for n,v in built['source_pins'].items()) and all(pin(originalRoot/n)==v for n,v in originalMembers.items())
        state.update(status='PASS17_ACTUAL_JAR_DISPOSITIONS_EIGHT_REHASHED_BUNDLE_REFUSALS_ONLY',dispositions=cases,rehashed_refusals=tamper,timeout_owned_process_terminated=True,source_pins=built['source_pins'],harness=pin(helper),original_source_preserved=True)
        assert len(cases)==17 and len(tamper)==8
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific input/process/refusal assertions')
    raise SystemExit(main())
