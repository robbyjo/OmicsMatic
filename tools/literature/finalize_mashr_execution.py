#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the verified native execution checkpoint without altering older evidence."""
import argparse
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile
from validate_mashr_native import ROOT,BASE,pin

DOC=ROOT/'docs/literature'
GUIDE=DOC/'MASHR_NATIVE_EXECUTION.md'
PROOF=DOC/'MASHR_NATIVE_EXECUTION_VALIDATION.json'
ARCHIVE=DOC/'mashr-native-execution-controls.zip'


def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')


def main():
    if not __debug__:raise RuntimeError('Never disable evidence checks')
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['matrix','reference','attempt']:parser.add_argument('--'+name,required=True,type=Path)
    args=parser.parse_args();matrix,reference,attempt=[getattr(args,n).resolve() for n in ['matrix','reference','attempt']]
    assert all(p.is_relative_to(BASE.resolve()) and p!=BASE.resolve() for p in [matrix,reference,attempt])
    assert not attempt.exists() and not PROOF.exists() and not ARCHIVE.exists()
    attempt.mkdir(parents=True)
    try:
        checkpoint=BASE/'current-checkpoint.json';shutil.copyfile(checkpoint,attempt/'previous-checkpoint.json')
        matrix_file=matrix/'COMPLETED.json';validated=json.loads(matrix_file.read_text())
        assert validated['status']=='passed-actual-JAR-native-mash-file-execution-matrix'
        assert validated['tests_per_JVM']==295 and validated['heap_mib']==128 and len(validated['runs'])==3
        artifact=validated['artifact'];assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
        for name,value in validated['files'].items():assert pin(matrix/name)==value,name
        sources=validated['source_and_dependency_pins'];assert all(pin(ROOT/n)==v for n,v in sources.items())
        for path in [GUIDE,Path(__file__),DOC/'.gitattributes',ROOT/'examples/mashr-native-execution/.gitattributes']:
            sources[path.relative_to(ROOT).as_posix()]=pin(path)
        original=json.loads((reference/'REFERENCE.json').read_text())
        assert original['status']=='captured-original-residual-and-singleton-primitives' and original['source_workflows']==17
        assert all(pin(reference/n)==v for n,v in original['files'].items())
        for name,value in original['source_pins'].items():assert pin(ROOT/name)==value
        before=json.loads((reference/'runtime-before.json').read_text());assert before==json.loads((reference/'runtime-after.json').read_text())
        runtime_files=sum(map(len,before['files'].values()));assert runtime_files==5637
        historical=[]
        for name in ['MASHR_NATIVE_DEVELOPMENT_VALIDATION.json','MASHR_WORKFLOW_DEVELOPMENT_VALIDATION.json']:
            path=DOC/name;previous=json.loads(path.read_text());bindings=previous['source_pins']
            changes=[n for n,v in bindings.items() if pin(ROOT/n)!=v]
            expected_delta=['src/main/java/org/jlinalg/shrinkage/MashGaussian.java'] if name=='MASHR_NATIVE_DEVELOPMENT_VALIDATION.json' else []
            assert changes==expected_delta,(name,changes)
            previous_artifact=previous['artifact'];assert pin(ROOT/previous_artifact['path'])=={k:previous_artifact[k] for k in ['bytes','sha256']}
            historical.append(dict(path=path.relative_to(ROOT).as_posix(),**pin(path),current_source_delta=changes,
                                   scope='Historical proof for its saved artifact; current kernel is covered by the new 295-test matrix. No previous hashes refreshed.'))
        catalog=DOC/'omics-2023-2026/summary.json';summary=json.loads(catalog.read_text());statuses=summary['implementation_status_counts']
        assert summary['papers']==250 and statuses['native-subset-validated']==13 and statuses['upstream-adapter-validated']==48 and statuses['missing']==259 and statuses['excluded-by-user']==1
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';assert pin(canonical)==validated['canonical_artifact_unchanged']
        sources[catalog.relative_to(ROOT).as_posix()]=pin(catalog)
        entries={}
        def add(name,path):
            assert name not in entries and not path.is_symlink() and path.is_file();entries[name]=path.read_bytes()
        for name in sources:add('workspace/'+name,ROOT/name)
        for path in sorted(matrix.rglob('*')):
            if path.is_file():add('matrix/'+path.relative_to(matrix).as_posix(),path)
        for name in ['MASHR_NATIVE_DEVELOPMENT_VALIDATION.json','MASHR_WORKFLOW_DEVELOPMENT_VALIDATION.json']:add('historical/'+name,DOC/name)
        development=BASE/'native-execution-1'
        for name in ['previous-kernel/MashGaussian.java','original-residual-reference-1/REFERENCE.json','original-residual-reference-1/reference.R','original-residual-reference-1/collector.py',
                     'tests-1.log','compile-2.log','tests-3.log','package-4.log','package-5.log','original-residual-reference-2.log','matrix-1.log']:
            path=development/name
            if path.is_file():add('retained-attempts/'+name,path)
        failed_freeze=development/'evidence-freeze-1'
        if failed_freeze!=attempt and failed_freeze.is_dir():
            for path in sorted(failed_freeze.rglob('*')):
                if path.is_file():add('retained-attempts/evidence-freeze-1/'+path.relative_to(failed_freeze).as_posix(),path)
            add('retained-attempts/evidence-freeze-1.log',development/'evidence-freeze-1.log')
        add('historical/previous-checkpoint.json',attempt/'previous-checkpoint.json')
        inventory={n:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for n,raw in entries.items()}
        entries['ARCHIVE-CONTENTS.json']=(json.dumps(dict(schema_version=1,files=inventory),indent=2)+'\n').encode()
        pending=attempt/'mashr-native-execution-controls.zip'
        with zipfile.ZipFile(pending,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for name,raw in sorted(entries.items()):
                entry=zipfile.ZipInfo(name,(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,raw)
        with zipfile.ZipFile(pending) as archive:
            assert set(archive.namelist())==set(entries) and len(archive.namelist())==len(entries)
            for name,raw in entries.items():assert archive.read(name)==raw,name
        shutil.copyfile(pending,ARCHIVE)
        parent=ROOT/'src/test/resources/org/jlinalg/cli/mashr-workflow/original-controls.zip'
        with zipfile.ZipFile(parent) as archive:protocols=json.loads(archive.read('ORIGINAL-CONTROLS.json'))['protocols']
        source_targets=sum(p['expected_target_rows'] for p in protocols if p['exit_code']==0)
        imported_values=sum(p['expected_target_rows']*(25+2*p['expected_components']) for p in protocols if p['exit_code']==0)
        proof=dict(schema_version=1,status='passed-native-mash-fit-and-file-execution-development-validation',recorded_utc=datetime.now(timezone.utc).isoformat(),
            scope='Native Java fixed/simple/ad-hoc residual fitting and typed file execution/model reuse; no public study/runtime admission or catalog promotion.',
            artifact=artifact,source_pins=sources,archive=dict(path=ARCHIVE.relative_to(ROOT).as_posix(),entries=len(entries),**pin(ARCHIVE)),
            matrix=dict(path=matrix_file.relative_to(ROOT).as_posix(),tests_per_JVM=295,heap_mib=128,platforms=[r['name'] for r in validated['runs']],packaged_processes=9,**pin(matrix_file)),
            original_reference=dict(path=(reference/'REFERENCE.json').relative_to(ROOT).as_posix(),**pin(reference/'REFERENCE.json'),original_workflows=17,training_rows=1700,
                                    residual_numeric_values=5253,imported_original_target_rows=source_targets,imported_original_target_numeric_values=imported_values,
                                    singleton_original_primitive_numeric_values=18,runtime_files_unchanged=runtime_files,source_to_binary_rebuild_verified=False),
            retained_numerical_departure='Large-mean original E_V cancels to zero; native triangular noise calculation agrees with the analytic identity covariance. Not source equivalence at this boundary.',
            retained_attempts='Earlier unmeasured subtraction diagnostic, compile-name error, misplaced Gradle --tests option and overly broad inherited-proof delta assertion are preserved; corrected reference and all final checks passed.',
            historical_proofs=historical,canonical_artifact_unchanged=pin(canonical),catalog=dict(papers=250,new_integrations=61,native=13,upstream=48,missing=259,method_overlap=48,cellranger='excluded-by-user',mashr='missing; development-only'),
            full_project_tests_rerun=False,public_runtime_or_bundle_admitted=False,whole_paper_reproduced=False,whole_goal_complete=False,changes_pushed=False,released_or_deployed=False)
        write(PROOF,proof)
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(canonical)==proof['canonical_artifact_unchanged']
        current=dict(status=proof['status'],recorded_utc=proof['recorded_utc'],proof_path=PROOF.relative_to(ROOT).as_posix(),proof_pin=pin(PROOF),artifact=artifact,
                     archive=proof['archive'],source_pins=sources,tests_per_JVM=295,platforms=proof['matrix']['platforms'],current_changes_uncommitted=True,
                     new_scoped_integrations=61,registered_named_tools_missing=259,mashr_catalog_status='missing; development-only',whole_goal_complete=False,released_or_deployed=False,
                     next_actions=['Wire reviewed native execution and original covariance-learning preparation into public CLI/study bundles with complete admission gates.',
                                   'Complete sharing/composite roles, supplement review and eight mashr paper-role reviews before catalog promotion.',
                                   'Continue the other missing tools and paper-wide semantic completeness review; retain Cell Ranger exclusion and dependency SKIP3 recovery policy.'])
        staged=attempt/'current-checkpoint.json';write(staged,current);shutil.copyfile(staged,checkpoint)
        write(attempt/'final-audit.json',dict(status='passed-native-mash-execution-checkpoint-closure',proof=pin(PROOF),archive=pin(ARCHIVE),source_files=len(sources),archive_entries=len(entries),
                    catalog_unchanged=pin(catalog),canonical_unchanged=pin(canonical),whole_goal_complete=False))
        print(json.dumps(dict(status=proof['status'],artifact=artifact,proof=pin(PROOF),archive=pin(ARCHIVE),source_targets=source_targets,imported_values=imported_values,source_files=len(sources))))
        return 0
    except Exception as error:
        write(attempt/'FAILURE.json',dict(status='FAIL2_MASH_NATIVE_EVIDENCE_FREEZE',message=str(error)))
        (attempt/'WARNINGS.md').write_text('# FAIL2: checkpoint not closed\n\n'+str(error)+'\n\nRequired action: preserve this attempt. Inspect the named mismatch and resolve it before producing new proof; do not refresh old hashes to imply new validation.\n',encoding='utf-8');raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as error:print('FAIL2_MASH_NATIVE_EVIDENCE_FREEZE: '+str(error),file=sys.stderr);sys.exit(2)
