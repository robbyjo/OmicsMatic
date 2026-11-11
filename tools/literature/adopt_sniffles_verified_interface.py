# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adopt the exact tested two-file candidate and require byte-identical full builds."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/sniffles-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNIFFLES_INTERFACE_ADOPTION',implementation_credit=False,whole_goal_complete=False)
    try:
        for name in ['workflow-current-2','option-interface-validation-1/workflow','workflow-extended-2','public-example-1/analysis']:
            value=json.loads((BASE/name/'COMPLETED.json').read_bytes())
            assert value['status']=='PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW' and value['analysis_admitted']
        for name in ['workflow-current-reference-comparison-2','option-complete-reference-comparison-1','extended-complete-reference-comparison-1']:
            value=json.loads((BASE/name/'COMPLETED.json').read_bytes());assert value['comparison_passed']
        assert json.loads((BASE/'combined-failure-controls-1/COMPLETED.json').read_bytes())['status']=='PASS_EXPLICIT_SNIFFLES_FAILURE_AND_SUPERSESSION_RECEIPT_AUDIT_ONLY'
        old=json.loads((BASE/'option-interface-preview-1/COMPLETED.json').read_bytes())
        new=json.loads((BASE/'diagnostic-interface-preview-1/COMPLETED.json').read_bytes())
        assert new['status']=='PASS_ISOLATED_SNIFFLES_DIAGNOSTIC_INTERFACE_BUILD_ONLY'
        before=old['current_workspace_sources_unchanged'];assert len(before)==2 and all(pin(ROOT/name)==value for name,value in before.items())
        selected=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-sniffles-installation-1/selection.json').read_bytes())
        held=selected['preserved'];assert len(held)==18 and all(pin(ROOT/name)==value for name,value in held.items())
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';assert pin(canonical)==selected['canonical']
        candidates={}
        for name,value in new['candidate_sources'].items():
            source=BASE/'diagnostic-interface-preview-1'/name;assert pin(source)==value
            relative=name.removeprefix('source-candidate/');assert relative in before
            target=out/'source-before'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/relative).read_bytes())
            candidates[relative]=source.read_bytes()
        assert set(candidates)==set(before)
        for name,raw in candidates.items():(ROOT/name).write_bytes(raw)
        builds={}
        for label,folder,javac,native in [('javac21','execution-final-build-21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','sniffles-vcf-development.jar'),
            ('javac25','execution-final-build-25','C:/Program Files/Common Files/Oracle/Java/javapath/javac.exe','sniffles-vcf-javac25-development.jar')]:
            command=[sys.executable,'-B',str(ROOT/'tools/literature/build_sniffles_execution.py'),'--out',str(BASE/folder),'--javac',javac,
                '--native-reference',str(ROOT/'docs/literature/sniffles-vcf-development'/native)]
            run=subprocess.run(command,capture_output=True,timeout=180);(out/(label+'.stdout')).write_bytes(run.stdout);(out/(label+'.stderr')).write_bytes(run.stderr)
            save(out/(label+'-process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace')
            built=json.loads((BASE/folder/'COMPLETED.json').read_bytes())
            assert built['status']=='PASS_ISOLATED_JAVA17_SNIFFLES_EXECUTION_BUILD_ONLY'
            artifact=ROOT/built['artifact']['path'];expected=ROOT/new['artifacts'][label]['path']
            assert artifact.read_bytes()==expected.read_bytes(),'Full build differs from tested candidate; preserve both and validate the discrepancy, never repin old evidence'
            builds[label]=built['artifact']
        assert all((ROOT/name).read_bytes()==raw for name,raw in candidates.items())
        assert all(pin(ROOT/name)==value for name,value in held.items()) and pin(canonical)==selected['canonical']
        state.update(status='PASS_EXACT_TESTED_SNIFFLES_INTERFACE_ADOPTION_AND_FULL_REBUILDS_ONLY',source_before=before,
            adopted_sources={name:pin(ROOT/name) for name in candidates},both_full_builds_byte_identical_to_tested_candidates=True,
            artifacts=builds,preserved_unrelated_files=held,canonical_executable_unchanged=selected['canonical'],
            source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},catalog_credit_not_yet_applied=True)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable candidate identity or unrelated-work preservation checks')
    raise SystemExit(main())
