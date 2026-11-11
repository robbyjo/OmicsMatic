# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete scoped MTAG workflow evidence and optionally replay saved bundles without Python or fitting."""
import argparse
import csv
import io
import json
import math
import os
import subprocess
import sys
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE,linux
from signac_scan_evidence import unpack,byte_pin,safe
from catalog_source_integrity import verify_source,reverse_delta

HOME=ROOT/'docs/literature/mtag-workflow-development'
STATUS='PASS_SCOPED_ORIGINAL_MTAG108_TYPED_WORKFLOW_AND_STAGED_ROOT'
PREFIX='build/literature-review/mtag-runtime/'
SOURCE_FILES=[
    *[p.relative_to(ROOT).as_posix() for p in sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('Mtag*.java'))],
    'src/main/java/org/jlinalg/genetics/MtagScipy121NormalTail.java',
    'src/test/java/org/jlinalg/cli/MtagWorkflowReferenceProbe.java','src/test/java/org/jlinalg/genetics/MtagTailReferenceProbe.java',
    *[p.relative_to(ROOT).as_posix() for p in sorted((ROOT/'src/main/resources/org/jlinalg/omics/mtag').iterdir()) if p.is_file()],
    'docs/literature/MTAG_WORKFLOW_DEVELOPMENT.md','src/main/java/org/jlinalg/cli/JLinAlgCli.java',
    *['tools/literature/'+name+'.py' for name in ['build_mtag_workflow_development','build_mtag_dispatch','intake_mtag_workflow_lock',
      'validate_mtag_scipy_tail','validate_mtag_workflow_contracts','validate_mtag_tools_workflows','validate_mtag_tools_safety',
      'validate_mtag_runtime_controls','validate_mtag_dispatch','seal_mtag_workflow_evidence','verify_mtag_workflow_evidence']]]


def j(files,name):return json.loads(files[PREFIX+name])


def bundle_inventory(files,relative):
    prefix=PREFIX+relative.rstrip('/')+'/';raw={n[len(prefix):]:v for n,v in files.items() if n.startswith(prefix)}
    done=json.loads(raw['COMPLETED.json']);assert done['status']=='COMPLETE_SCOPED_MTAG_BUNDLE' and done['result_admitted']
    assert done['manifest']==byte_pin(raw['manifest.json']);manifest=json.loads(raw['manifest.json'])
    assert manifest['schema_version']==1 and manifest['files']=={n:byte_pin(v) for n,v in raw.items() if n not in ('manifest.json','COMPLETED.json')}
    session=json.loads(raw['session.json']);assert session['result_admitted'] and not session['analysis_refitted_for_verification'] and not session['producer_authenticated']
    audit=json.loads(raw['scientific-audit.json']);assert audit['status']=='PASS_COMPLETE_ORIGINAL_MTAG_RESULTS_AND_STATED_NATIVE_CONTRACTS'
    assert audit['trait_rows_checked']==audit['variants']*audit['traits'] and not audit['full_source_qc_independently_reimplemented'] and not audit['original_effective_values_repaired']
    assert 'MTAG complete. Time elapsed:' in raw['original/mtag.log'].decode() and 'Analysis terminated from error at' not in raw['original/mtag.log'].decode()
    for phase in ('original','capture'):
        process=json.loads(raw[phase+'-process.json']);assert process['exit_code']==0 and process['source_process_finished']
        cleanup=json.loads(raw[phase+'-process-cleanup.json']);assert not cleanup['parent_still_alive'] and not cleanup['remaining_owned_pids']
    return audit


def inspect(home=HOME):
    proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']==STATUS and proof['workflow_ready_for_catalog'] and proof['staged_root_command_available']
    assert not proof['new_catalog_credit'] and not proof['live_root_modified'] and not proof['whole_goal_complete']
    data=ROOT/'docs/literature/omics-2023-2026';files={}
    for name,wanted in proof['source_pins'].items():verify_source(ROOT,data,name,wanted['sha256'])
    for name,record in proof['archives'].items():
        assert pin(home/name)==record['archive'];content=unpack(home/name,record);assert not set(files)&set(content);files.update(content)
    for name,wanted in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==wanted
    for name,wanted in proof['artifacts'].items():assert pin(home/name)==wanted
    for name,wanted in proof['predecessor_evidence_files'].items():assert pin(ROOT/name)==wanted,('Changed predecessor scientific evidence',name)
    from verify_mtag_native_evidence import inspect as native
    native()
    matrix=j(files,'workflow-options-2/COMPLETED.json');assert matrix['status']=='PASS_TYPED_ORIGINAL_MTAG_OPTION_MATRIX_AND_NATIVE_REFUSALS'
    rows=matrix['results'];assert len(rows)==25 and all(r['passed'] for r in rows)
    assert [sum(r['expected']==n for r in rows) for n in (0,2,3)]==[20,4,1]
    admitted=[];checked=0
    for row in rows:
        relative='workflow-options-2/'+row['case']+'/analysis'
        if row['expected']==0:
            audit=bundle_inventory(files,relative);assert audit==row['audit'] and audit['variants']==row['retained'];checked+=audit['trait_rows_checked'];admitted.append(relative)
        else:
            session=j(files,relative+'/session.json');assert not session['result_admitted']
            assert PREFIX+relative+'/COMPLETED.json' not in files
    assert checked==172625
    roots=j(files,'dispatch-validation-2/COMPLETED.json');assert roots['status']=='PASS_MTAG_STAGED_ROOT_WORKFLOWS_AND60_PORTABLE_OPTION_REPLAYS'
    assert (roots['option_saved_replays'],roots['root_admitted_original_analyses'],roots['root_expected_source_errors'],roots['root_expected_skips'],roots['root_analysis_saved_replays'])==(60,3,1,3,9)
    assert len(roots['protocols'])==88 and all(r['passed'] for r in roots['protocols'])
    for name in ['root-full-ldsc-maxfdr','root-explicit-maf-qc','root-original-p-zero']:
        relative='dispatch-validation-2/'+name+'/analysis';audit=bundle_inventory(files,relative);admitted.append(relative)
        if name=='root-full-ldsc-maxfdr':assert audit['variants']==20000 and audit['fdr']['calculated']
        if name=='root-original-p-zero':assert audit['original_p_underflow_rows']==2000
    assert admitted==proof['saved_bundle_paths']
    safety=j(files,'workflow-safety-2/COMPLETED.json');assert safety['status']=='PASS_MTAG_TOOLS_SAFETY_AND_PORTABLE_SAVED_AUDIT' and safety['portable_saved_protocols']==24
    assert len(safety['refusal_cases'])==15 and all(r['passed'] for r in safety['refusal_cases']) and safety['original_bundle_unchanged']
    setup=j(files,'runtime-refusal-controls-3/COMPLETED.json');assert setup['status']=='PASS5_MTAG_RUNTIME_REFUSAL_AND_PRESERVATION_CONTROLS'
    assert not setup['downloads_permitted'] and not setup['installation_executed'] and not setup['analysis_executed'] and setup['forbidden_environments_not_created']
    profile=j(files,'workflow-runtime-profile-2/runtime.json');lock=json.loads(files['current-source/src/main/resources/org/jlinalg/omics/mtag/mtag-workflow-lock.json'])
    assert profile['source_lock']==byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/mtag/mtag-workflow-lock.json'])
    assert profile['preparer']==proof['source_pins']['src/main/resources/org/jlinalg/omics/mtag/prepare_mtag_runtime.py']
    assert len(lock['packages'])==33 and profile['source_commit']==lock['source_commit'] and profile['setup_only']
    closure=profile['closure']['source']['files'];assert set(closure)==set(lock['source']['files']) and len(closure)==1096
    for n,wanted in lock['source']['files'].items():assert {k:closure[n][k] for k in ('bytes','sha256')}==wanted
    installed=j(files,'workflow-runtime-setup-1/setup.json');assert installed['status']=='PASS_PRIVATE_MTAG108_RUNTIME_PROFILE_ONLY' and not installed['analysis_executed']
    assert profile['versions']['versions']==lock['versions']
    tail=j(files,'scipy-tail-validation-1/COMPLETED.json');assert tail['status']=='PASS_SOURCE_BOUND_SCIPY121_MTAG_TAIL_COMPONENT_ONLY' and tail['cases']==128699
    original=list(csv.DictReader(io.StringIO(files[PREFIX+'scipy-tail-validation-1/original.tsv'].decode()),delimiter='\t'))
    finite=0;zeros=0
    for platform in ['windows21','windows25','linux21']:
        native_rows=list(csv.DictReader(io.StringIO(files[PREFIX+'scipy-tail-validation-1/'+platform+'.tsv'].decode()),delimiter='\t'));assert len(native_rows)==len(original)==128699
        for src,java in zip(original,native_rows,strict=True):
            assert (src['row'],src['z_hex'])==(java['row'],java['z_hex']);z=float.fromhex(src['z_hex'])
            if math.isfinite(z):
                assert java['status']=='NATIVE_RETURNED';a=float.fromhex(src['p_hex']);b=float.fromhex(java['p_hex']);finite+=1
                if a==0:assert b==0;zeros+=1
                else:assert b>0 and abs(a-b)/a<=2e-12
            else:assert java['status']=='NATIVE_FINITE_PROFILE_REFUSAL' and java['p_hex']==''
    assert finite==386088 and zeros==35853
    # Every predecessor root entry remains exact, apart from the explicitly staged Root CLI.
    from build_mtag_dispatch import without_line_numbers
    for compiler in ('21','25'):
        root_artifact=home/('mtag-root-javac'+compiler+'.jar');standalone=home/('mtag-workflow-javac'+compiler+'.jar')
        prior=ROOT/('docs/literature/genesis-tools-development/genesis-root-javac'+compiler+'.jar')
        with zipfile.ZipFile(root_artifact) as z,zipfile.ZipFile(prior) as old,zipfile.ZipFile(standalone) as worker:
            for n in old.namelist():
                if n!='org/jlinalg/cli/JLinAlgCli.class':assert z.read(n)==old.read(n),n
            for n in worker.namelist():
                if n.endswith('.class') or n.startswith('org/jlinalg/omics/mtag/'):
                    if n=='org/jlinalg/cli/SimpleJson.class':assert without_line_numbers(z.read(n))==without_line_numbers(worker.read(n))
                    else:assert z.read(n)==worker.read(n),n
        group='dispatch-build'+compiler+'-3';delta=j(files,group+'/staged-root-delta.json')['paths'][0]
        before=files[PREFIX+group+'/prior-root-source.java'];after=files[PREFIX+group+'/source-snapshot/src/main/java/org/jlinalg/cli/JLinAlgCli.java']
        assert reverse_delta(after,delta)==before and len(delta['edits'])==2
    return proof,files,dict(status=STATUS,option_cases=25,admitted_option_workflows=20,option_trait_rows=checked,root_admitted_workflows=3,
                           portable_option_replays=60,root_saved_replays=9,portable_safety_protocols=24,source_tail_finite_comparisons=finite,
                           source_tail_zeros_preserved=zeros,new_catalog_credit=False,mtag_catalog_promotion_pending=True,whole_goal_complete=False)


def replay(platform,out,proof,files):
    assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_MTAG_SAVED_WORKFLOW_REPLAY',original_fits_executed=False,new_catalog_credit=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';assert pin(canonical)==proof['canonical_executable_unchanged']
        compiler='25' if platform=='windows25' else '21';jar=HOME/('mtag-root-javac'+compiler+'.jar')
        java=Path('C:/Program Files')/('Common Files/Oracle/Java/javapath/java.exe' if compiler=='25' else 'Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')
        prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'] if platform=='linux21' else [str(java)]
        classpath=linux(jar)+':'+linux(canonical) if platform=='linux21' else str(jar)+os.pathsep+str(canonical)
        version=subprocess.run(prefix+['-version'],cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=30)
        (out/'java-version.stdout').write_bytes(version.stdout);(out/'java-version.stderr').write_bytes(version.stderr)
        if version.returncode!=0:raise FileNotFoundError('Selected permitted Java/WSL prerequisite is unavailable; skip saved verification')
        records=[]
        for relative in proof['saved_bundle_paths']:
            home=out/'bundles'/relative;home.mkdir(parents=True);stem=PREFIX+relative+'/'
            for name,raw in files.items():
                if name.startswith(stem):
                    tail=name[len(stem):];safe(tail);path=home/tail;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
            from validate_mtag_tools_safety import census
            before=census(home);label=relative.replace('/','_');command=prefix+['-Xmx2g','-cp',classpath,'org.jlinalg.cli.JLinAlgCli','mtag-tools','--verify',linux(home) if platform=='linux21' else str(home)]
            with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
            save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0 and census(home)==before,relative
            records.append(dict(bundle=relative,passed=True))
        state.update(status='PASS23_MTAG_SAVED_WORKFLOW_REPLAYS_WITHOUT_PYTHON',platform=platform,cases=records,bundles_unchanged=True)
    except FileNotFoundError as error:state.update(status='SKIP3_MTAG_SAVED_JAVA_PREREQUISITE_UNAVAILABLE',cause=repr(error))
    except Exception as error:state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    state['required_action']='SKIP verification and dependent interpretation when exact permitted Java prerequisites are unavailable or forbidden. Supply the named prerequisite and retry NEW paths. FAIL2 admits no verified result: preserve the complete attempt and resolve its named source/archive/scientific cause. This helper installs nothing and performs no Python or model fit.'
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','Complete saved native replay only.')+'\n\nRequired action: '+state['required_action']+'\n',encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],platform,state.get('cause',''))
    return 0 if state['status'].startswith('PASS') else 3 if state['status'].startswith('SKIP3_') else 2


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--replay',choices=['windows21','windows25','linux21']);p.add_argument('--out',type=Path);a=p.parse_args()
    try:
        proof,files,summary=inspect()
        if a.replay:
            assert a.out is not None;return replay(a.replay,a.out.resolve(),proof,files)
        assert a.out is None;print(json.dumps(summary));return 0
    except Exception as error:
        print('FAIL2_MTAG_WORKFLOW_EVIDENCE: '+repr(error)+'\nRequired action: retain all evidence and resolve the named source/archive/contract mismatch; admit no repaired or partial result.',file=sys.stderr);return 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/evidence/scientific and predecessor byte checks')
    raise SystemExit(main())
