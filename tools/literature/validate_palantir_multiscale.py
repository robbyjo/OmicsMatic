# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate complete original Palantir coordinate families on three actual JVMs, without upstream execution by Java."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();a.build=a.build.resolve();a.reference=a.reference.resolve();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_PALANTIR_MULTISCALE_VALIDATION',runs=[],implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_COMPILED_NATIVE_PALANTIR_MULTISCALE_COMPONENT_ONLY';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};reference=json.loads((a.reference/'COMPLETED.json').read_bytes());assert reference['status']=='PASS_TWO_FULL_ORIGINAL_PALANTIR_MULTISCALE_REFERENCE_FAMILIES_ONLY' and reference['all_runtime_trees_unchanged'];files=[Path(__file__).resolve(),a.reference/'COMPLETED.json',*[a.reference/v/'cases.json' for v in ['1.4.4','1.4.6']]];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        for label,java,linux,major in [('windows21',str(JAVA21/'java.exe'),False,'21'),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False,'25'),('linux21','/usr/bin/java',True,'21')]:
            home=out/label;home.mkdir()
            def path(p):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if linux else str(p)
            def command(arguments):return ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java,*arguments] if linux else [java,*arguments]
            version=subprocess.run(command(['-version']),capture_output=True,timeout=60);(home/'java-version.stdout').write_bytes(version.stdout);(home/'java-version.stderr').write_bytes(version.stderr);text=(version.stdout+version.stderr).decode('utf8',errors='replace');assert version.returncode==0 and ('"'+major+'.' in text or '"'+major+'"' in text)
            for edition in ['1.4.4','1.4.6']:
                expected=reference['reports'][edition];cases=a.reference/edition/'cases.json';assert pin(cases)==dict(bytes=expected['case_bytes'],sha256=expected['case_sha256']);actual_command=command(['-Xmx2g','-cp',path(jar),'org.jlinalg.cli.PalantirMultiscaleMatrix',path(cases),path(home/(edition+'.json'))]);run=subprocess.run(actual_command,cwd=ROOT,capture_output=True,timeout=180);(home/(edition+'.stdout')).write_bytes(run.stdout);(home/(edition+'.stderr')).write_bytes(run.stderr);record=dict(label=label,edition=edition,command=actual_command,exit_code=run.returncode);save(home/(edition+'.process.json'),record);assert run.returncode==0,run.stderr.decode('utf8',errors='replace');report=json.loads((home/(edition+'.json')).read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_PALANTIR_MULTISCALE_COMPONENT_ONLY' and report['numeric_cases']==expected['native_numeric_cases'] and report['source_errors_rejected']==expected['original_errors'] and report['ambiguous_ties_deferred']==expected['native_tie_deferrals'] and report['source_only_profiles_retained']==expected['source_only_profiles'] and report['native_rejections']==10;record.update(audit=report);state['runs'].append(record)
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_THREE_JVM_TWO_EDITION_NATIVE_PALANTIR_MULTISCALE_COMPONENT_ONLY',artifact=built['artifact'],source_pins=pins,canonical_executable_unchanged=canonical)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
