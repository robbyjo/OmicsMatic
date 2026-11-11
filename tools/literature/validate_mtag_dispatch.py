# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run actual staged-root MTAG workflows and replay all admitted option bundles on three JVMs without refitting."""
import argparse
import concurrent.futures
import json
import os
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE,linux
from validate_mtag_tools_safety import census


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('out','root21','root25','matrix','runtime-profile'):p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_MTAG_STAGED_ROOT_PROTOCOLS',live_root_modified=False,new_catalog_credit=False,whole_paper_reproduced=False)
    (out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';jars={}
        for key,home in [('windows21',a.root21),('linux21',a.root21),('windows25',a.root25)]:
            home=home.resolve();proof=json.loads((home/'COMPLETED.json').read_bytes());assert proof['status']=='PASS_MTAG_STAGED_ROOT_PREDECESSOR_SCIENTIFIC_BYTES_UNCHANGED'
            jar=home/'mtag-root-supplement.jar';assert pin(jar)==proof['artifact'];jars[key]=jar
            assert pin(ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java')==proof['source_before'] and pin(ROOT/'tools/literature/build_mtag_dispatch.py')==proof['builder']
        protected={str(p):pin(p) for p in set(jars.values())|{canonical,a.runtime_profile.resolve(),ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java'}}
        matrix=a.matrix.resolve();m=json.loads((matrix/'COMPLETED.json').read_bytes());assert m['status']=='PASS_TYPED_ORIGINAL_MTAG_OPTION_MATRIX_AND_NATIVE_REFUSALS'
        commands={
            'windows21':[r'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx2g','-cp',str(jars['windows21'])+os.pathsep+str(canonical),'org.jlinalg.cli.JLinAlgCli'],
            'windows25':[r'C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx2g','-cp',str(jars['windows25'])+os.pathsep+str(canonical),'org.jlinalg.cli.JLinAlgCli'],
            'linux21':['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx2g','-cp',linux(jars['linux21'])+':'+linux(canonical),'org.jlinalg.cli.JLinAlgCli']}
        def invoke(jvm,label,args,expected=0):
            home=out/label/jvm;home.mkdir(parents=True)
            args=[linux(p) if isinstance(p,Path) and jvm=='linux21' else str(p) for p in args];command=commands[jvm]+args
            with (home/'stdout').open('xb') as so,(home/'stderr').open('xb') as se:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=900)
            save(home/'process.json',dict(command=command,exit_code=r.returncode,expected=expected));assert r.returncode==expected,(label,jvm,(home/'stderr').read_text(errors='replace'))
            return dict(case=label,jvm=jvm,exit_code=r.returncode,passed=True)
        records=[]
        for jvm in commands:
            for command in ['mtag-tools','genesis-tools','snprelate-tools','mafft-tools']:
                records.append(invoke(jvm,'help-'+command,[command,'--help']))
        # Repeat only native saved auditing, preserving every original bundle.
        replays=[]
        for r in m['results']:
            if r['expected']==0:
                bundle=matrix/r['case']/'analysis';replays.append((r['case'],bundle,census(bundle)))
        def replay(jvm):
            answer=[]
            for name,bundle,before in replays:
                answer.append(invoke(jvm,'saved-'+name,['mtag-tools','--verify',bundle]));assert census(bundle)==before
            return answer
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            for answer in pool.map(replay,commands):records.extend(answer);print('root saved replays',answer[0]['jvm'],len(answer),flush=True)
        # New source executions retain exactly their declared scientific choices.
        jobs=[]
        def add(name,source,jvm='linux21',expected=0,change=None):
            home=out/name;home.mkdir();recipe=json.loads(source.read_bytes());recipe['runtime_profile']=linux(a.runtime_profile.resolve()) if jvm=='linux21' else str(a.runtime_profile.resolve())
            recipe['output_dir']=linux(home/'analysis') if jvm=='linux21' else str(home/'analysis')
            if change:change(recipe)
            save(home/'request.json',recipe);jobs.append((name,home,jvm,expected))
        add('root-full-ldsc-maxfdr',BASE/'workflow-baseline-run-1/request.json')
        add('root-explicit-maf-qc',matrix/'explicit-frequency-header-quality/request.json')
        add('root-original-p-zero',matrix/'source-zero-tail-underflow/request.json')
        add('root-source-zero-exit-error',BASE/'workflow-safety-1/original-zero-exit-scientific-error/request.json',expected=2)
        add('root-unsupported-no-flip',BASE/'workflow-safety-1/no-allele-flipping/request.json',expected=3)
        for jvm in ['windows21','windows25']:add('root-platform-skip-'+jvm,matrix/'provided-covariances/request.json',jvm=jvm,expected=3)
        def execute(job):
            name,home,jvm,expected=job;result=invoke(jvm,name+'-process',['mtag-tools','--request',home/'request.json'],expected)
            bundle=home/'analysis';session=json.loads((bundle/'session.json').read_bytes());assert session['result_admitted']==(expected==0)
            assert session['original_analysis_executed']==(jvm=='linux21' and expected!=3)
            if expected==0:
                result['audit']=json.loads((bundle/'scientific-audit.json').read_bytes());assert (bundle/'COMPLETED.json').exists()
                for platform in commands:records.append(invoke(platform,name+'-saved',['mtag-tools','--verify',bundle]))
            else:assert not (bundle/'COMPLETED.json').exists()
            return result
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            for result in pool.map(execute,jobs):records.append(result);print(result['case'],result['exit_code'],flush=True)
        for name,wanted in protected.items():assert pin(Path(name))==wanted,name
        state.update(status='PASS_MTAG_STAGED_ROOT_WORKFLOWS_AND60_PORTABLE_OPTION_REPLAYS',protocols=records,option_saved_replays=len(replays)*3,
                     root_analysis_attempts=7,root_admitted_original_analyses=3,root_expected_source_errors=1,root_expected_skips=3,
                     predecessor_help_checks=9,mtag_help_checks=3,root_analysis_saved_replays=9,
                     original_option_bundles_unchanged=True,live_root_and_canonical_executable_unchanged=True)
        print(state['status'])
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');print(state['status']+': '+repr(error),flush=True)
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete root/source admission and saved-bundle preservation checks')
    raise SystemExit(main())
