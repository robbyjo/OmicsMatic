# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate actual MTAG CLI refusal, ownership, process cleanup and portable saved-audit contracts."""
import argparse
import copy
import concurrent.futures
import hashlib
import json
import os
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE,linux
from validate_mtag_workflow_contracts import baseline


def census(root):
    return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('out','build','build25','runtime-profile','bundle'):p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_MTAG_TOOLS_SAFETY',whole_paper_reproduced=False,new_catalog_credit=False)
    (out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        jar=a.build.resolve()/'mtag-workflow-development.jar';jar25=a.build25.resolve()/'mtag-workflow-development.jar'
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';profile=a.runtime_profile.resolve();runtime=json.loads(profile.read_bytes())
        for build in (a.build.resolve(),a.build25.resolve()):
            receipt=json.loads((build/'COMPLETED.json').read_bytes());assert pin(build/'mtag-workflow-development.jar')==receipt['artifact']
            for name,wanted in receipt['source_pins'].items():assert pin(ROOT/name)==wanted,name
        protected={str(path):pin(path) for path in (jar,jar25,canonical,profile)}
        prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx2g','-cp',linux(jar)+':'+linux(canonical),'org.jlinalg.cli.MtagToolsCli']
        cases=[]
        def add(name,change=None,expected=2,phrase=None,original=False):
            home=out/name;home.mkdir();r=baseline();r['runtime_profile']=linux(profile);r['output_dir']='analysis'
            for t,trait in enumerate(r['traits']):trait['input']=linux(BASE/'original-workflow-probe-1/inputs'/('trait%d.tsv'%(t+1)))
            if change:change(r,home)
            save(home/'request.json',r);cases.append(dict(case=name,request=home/'request.json',expected=expected,phrase=phrase,original=original))
        add('unknown-choice',lambda r,h:r.update(arbitrary_command='ignored'),phrase='field')
        def occupied(r,h):
            target=h/'analysis';target.mkdir();(target/'KEEP.txt').write_bytes(b'existing-user-output\r\n');save(h/'prior-output-census.json',census(target))
        add('existing-output',occupied,phrase='already exists')
        add('source-root-overlap',lambda r,h:r.update(output_dir=runtime['source']+'/NEVER_CREATE_BY_OVERLAP_TEST'),phrase='overlaps')
        add('environment-root-overlap',lambda r,h:r.update(output_dir=runtime['environment']+'/NEVER_CREATE_BY_OVERLAP_TEST'),phrase='overlaps')
        add('no-allele-flipping',lambda r,h:r.update(no_allele_flipping=True),3,'does not use')
        add('maximum-n-source-bug',lambda r,h:r['filters'].update(n_max=1e6),3,'scalar boolean')
        add('union-not-admitted',lambda r,h:r.update(meta_format=True),3,'not yet admitted')
        def custom(r,h):
            path=h/'grid.txt';path.write_text('0 0 0 0 0 0 0 1\n',encoding='ascii');r['fdr'].update(mode='custom',grid_file=linux(path))
        add('custom-grid-source-bug',custom,3,'bitwise-and')
        add('missing-runtime',lambda r,h:r.update(runtime_profile='not-installed.json'),3,'unavailable')
        def corrupted(r,h):
            d=copy.deepcopy(runtime);d['preparer']['sha256']='0'*64;save(h/'changed-profile.json',d);r['runtime_profile']=linux(h/'changed-profile.json')
        add('corrupt-runtime-profile',corrupted,phrase='exact helper')
        add('complete-grid-capacity',lambda r,h:r['limits'].update(maximum_fdr_grid_points=1),phrase='grid')
        add('row-capacity',lambda r,h:r['limits'].update(rows_per_trait=1),phrase='rows_per_trait')
        add('working-memory-capacity',lambda r,h:r['limits'].update(working_bytes=1048576),phrase='capacity')
        add('original-wall-timeout',lambda r,h:r['limits'].update(wall_seconds=1),phrase='wall_seconds',original=True)
        # The synthetic inputs have a median signed Z near zero; the source
        # raises when its deviation from0 exceeds the explicit cutoff0.
        add('original-zero-exit-scientific-error',lambda r,h:r.update(median_z_cutoff=0),phrase='Python2 exception handler',original=True)
        def execute(c):
            home=out/c['case'];cmd=prefix+['--request',linux(c['request'])];before=census(home);save(home/'command.json',dict(command=cmd))
            with (home/'stdout').open('xb') as so,(home/'stderr').open('xb') as se:r=subprocess.run(cmd,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
            save(home/'process.json',dict(exit_code=r.returncode));assert r.returncode==c['expected'],(c['case'],r.returncode)
            message=(home/'stderr').read_text(encoding='utf8');assert c['phrase'].lower() in message.lower(),message
            bundle=home/'analysis';session=None
            if c['case']=='existing-output':assert census(bundle)==json.loads((home/'prior-output-census.json').read_bytes())
            elif (bundle/'session.json').exists():
                session=json.loads((bundle/'session.json').read_bytes());assert not session['result_admitted'] and session['original_analysis_executed']==c['original'];assert not (bundle/'COMPLETED.json').exists()
            if c['original']:
                process=json.loads((bundle/'original-process.json').read_bytes());assert process['source_process_finished']
                cleanup=json.loads((bundle/'original-process-cleanup.json').read_bytes());assert not cleanup['parent_still_alive'] and not cleanup['remaining_owned_pids']
                if c['case']=='original-zero-exit-scientific-error':assert process['exit_code']==0
            for name,wanted in before.items():assert pin(home/name)==wanted
            return dict(case=c['case'],exit_code=r.returncode,passed=True,original_executed=c['original'])
        results=[]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            futures={pool.submit(execute,c):c['case'] for c in cases}
            for f in concurrent.futures.as_completed(futures):
                try:r=f.result()
                except Exception as error:r=dict(case=futures[f],passed=False,cause=repr(error),traceback=traceback.format_exc())
                results.append(r);save(out/'progress.json',results);print(r['case'],r['passed'],flush=True)
        assert all(r['passed'] for r in results),[r for r in results if not r['passed']]
        # Helper export never installs anything and never overwrites a directory.
        for expected in (0,2):
            cmd=prefix+['--export-runtime-helper',linux(out/'exported-helper')]
            snapshot=census(out/'exported-helper') if expected else None
            r=subprocess.run(cmd,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=60);(out/('export-%d.stdout'%expected)).write_bytes(r.stdout);(out/('export-%d.stderr'%expected)).write_bytes(r.stderr);assert r.returncode==expected
            if snapshot is not None:assert census(out/'exported-helper')==snapshot
        for name in ('prepare_mtag_runtime.py','mtag-workflow-lock.json','scipy121-LICENSE.txt'):assert (out/'exported-helper'/name).read_bytes()==(ROOT/'src/main/resources/org/jlinalg/omics/mtag'/name).read_bytes()
        # Saved replay must remain completely read-only on all three JVMs.
        bundle=a.bundle.resolve();pristine=census(bundle)
        jvms={
            'windows21':[r'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx2g','-cp',str(jar)+os.pathsep+str(canonical),'org.jlinalg.cli.MtagToolsCli'],
            'windows25':[r'C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx2g','-cp',str(jar25)+os.pathsep+str(canonical),'org.jlinalg.cli.MtagToolsCli'],
            'linux21':prefix}
        def invoke(label,arguments,expected=0):
            for jvm,command in jvms.items():
                args=[linux(p) if isinstance(p,Path) and jvm.startswith('linux') else str(p) for p in arguments]
                r=subprocess.run(command+args,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
                (out/(label+'-'+jvm+'.stdout')).write_bytes(r.stdout);(out/(label+'-'+jvm+'.stderr')).write_bytes(r.stderr)
                save(out/(label+'-'+jvm+'.process.json'),dict(exit_code=r.returncode,expected=expected,command=command+args));assert r.returncode==expected,(label,jvm,r.stderr.decode(errors='replace'))
        invoke('pristine-replay',['--verify',bundle]);assert census(bundle)==pristine
        target=out/'tampered-copy';shutil.copytree(bundle,target);original=census(target)
        def reseal():
            manifest=json.loads((target/'manifest.json').read_bytes());manifest['files']={n:v for n,v in census(target).items() if n not in ('manifest.json','COMPLETED.json')};save(target/'manifest.json',manifest)
            done=json.loads((target/'COMPLETED.json').read_bytes());done['manifest']=pin(target/'manifest.json');save(target/'COMPLETED.json',done)
        association=target/'original/mtag_trait_1.txt';saved=association.read_bytes();lines=saved.decode().splitlines();header=lines[0].split('\t');fields=lines[1].split('\t');fields[header.index('mtag_z')]='999';lines[1]='\t'.join(fields);association.write_text('\n'.join(lines)+'\n',encoding='utf8')
        invoke('changed-result-byte',['--verify',target],2)
        reseal();invoke('resealed-invalid-algebra',['--verify',target],2)
        for label,member in [('parser-ledger','native-audit/input-parser-differences.tsv'),('disposition-ledger','native-audit/variant-dispositions.tsv'),('association-ledger','native-audit/association-audit.tsv'),('capture-path','captured/capture.json')]:
            target=out/('tampered-'+label);shutil.copytree(bundle,target);path=target/member
            if label=='capture-path':
                data=json.loads(path.read_bytes());data['effective_file']='../captured/effective-inputs.tsv';save(path,data)
            else:
                lines=path.read_text(encoding='utf8').splitlines();assert len(lines)>1
                cells=lines[1].split('\t');cells[0]+='X';lines[1]='\t'.join(cells);path.write_text('\n'.join(lines)+'\n',encoding='utf8')
            reseal();invoke('resealed-'+label,['--verify',target],2)
        # The untouched original remains valid; hashes do not authenticate a producer.
        invoke('original-after-tamper-controls',['--verify',bundle]);assert census(bundle)==pristine
        for name,wanted in protected.items():assert pin(Path(name))==wanted,name
        state.update(status='PASS_MTAG_TOOLS_SAFETY_AND_PORTABLE_SAVED_AUDIT',refusal_cases=results,helper_export_and_overwrite_checked=True,portable_saved_protocols=24,
                     scientific_tamper_resealed_without_source_refit=True,original_bundle_unchanged=True,canonical_executable_unchanged=True)
        print(state['status'])
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');print(state['status']+': '+repr(error),flush=True)
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete refusal and saved-audit checks')
    raise SystemExit(main())
