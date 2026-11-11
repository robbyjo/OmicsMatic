#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze public native mash evidence while preserving historical scientific proof."""
import argparse
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile
from validate_mashr_native import ROOT,BASE,pin
from catalog_source_integrity import verify_source

DOC=ROOT/'docs/literature'
DEST=DOC/'mashr-native-workflow'


def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')


def main():
    if not __debug__:raise RuntimeError('Never disable evidence closure checks')
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['matrix','portable','attempt']:parser.add_argument('--'+name,required=True,type=Path)
    args=parser.parse_args();matrix,portable,attempt=[getattr(args,n).resolve() for n in ['matrix','portable','attempt']]
    assert all(p.is_relative_to(BASE.resolve()) and p!=BASE.resolve() for p in [matrix,portable,attempt])
    proof_file,archive_file=DEST/'VALIDATION.json',DEST/'controls.zip';assert not proof_file.exists() and not archive_file.exists() and not attempt.exists();attempt.mkdir(parents=True)
    try:
        matrix_file=matrix/'COMPLETED.json';portable_file=portable/'COMPLETED.json';m=json.loads(matrix_file.read_text());p=json.loads(portable_file.read_text())
        assert m['status']=='passed-actual-JAR-public-native-mash-workflow-matrix' and m['tests_per_JVM']==325 and len(m['runs'])==3
        assert p['status']=='passed-cross-JVM-native-mash-public-bundle-verification' and len(p['cross_producer_checks'])==18 and len(p['learned'])==3
        assert pin(matrix_file)=={k:p['matrix'][k] for k in ['bytes','sha256']}
        for folder,record in [(matrix,m),(portable,p)]:assert all(pin(folder/n)==v for n,v in record['files'].items())
        bound=dict(m['source_pins']);bound.update(p['source_pins'])
        assert all(pin(ROOT/n)==v for n,v in bound.items())
        for file in [Path(__file__),DOC/'MASHR_NATIVE_WORKFLOW.md',DEST/'.gitattributes']:bound[file.relative_to(ROOT).as_posix()]=pin(file)
        old_proof=DOC/'MASHR_NATIVE_EXECUTION_VALIDATION.json';baseline=json.loads(old_proof.read_text());assert all(pin(ROOT/n)==v for n,v in baseline['source_pins'].items())
        baseline_archive=ROOT/baseline['archive']['path'];assert pin(baseline_archive)=={k:baseline['archive'][k] for k in ['bytes','sha256']}
        artifact=m['artifact'];assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';assert pin(canonical)==m['canonical_artifact_unchanged']==baseline['canonical_artifact_unchanged']
        development=BASE/'public-native-workflow-1';prior=development/'fastp-prior-integrity.json';fastp=json.loads(prior.read_text());assert fastp['status']=='passed-complete-fastp-frozen-public-workflow-evidence' and fastp['public_original_workflows']==58
        bridge_file=DOC/'omics-2023-2026/mashr-dispatch-shared-source-compatibility.json';bridge=json.loads(bridge_file.read_text());row=bridge['paths'][0]
        assert len(bridge['paths'])==1 and row['path']=='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
        verify_source(ROOT,DOC/'omics-2023-2026',row['path'],row['before_sha256'])
        catalog=DOC/'omics-2023-2026/summary.json';summary=json.loads(catalog.read_text());assert summary['papers']==250 and summary['implementation_status_counts']['missing']==259 and summary['implementation_status_counts']['native-subset-validated']==13 and summary['implementation_status_counts']['upstream-adapter-validated']==48
        entries={};omitted={}
        def add(name,path):assert name not in entries and path.is_file() and not path.is_symlink();entries[name]=path.read_bytes()
        with zipfile.ZipFile(baseline_archive) as z:
            for name,value in bound.items():
                if name.endswith(('.jar','.zip')):
                    raw=z.read('workspace/'+name);assert len(raw)==value['bytes'] and hashlib.sha256(raw).hexdigest()==value['sha256'];omitted[name]=value
                else:add('workspace/'+name,ROOT/name)
        entries['BASELINE-DEPENDENCY.json']=(json.dumps(dict(path=baseline['archive']['path'],**pin(baseline_archive),source_members_reused=omitted,required_with_this_archive=True),indent=2)+'\n').encode()
        for label,folder in [('matrix',matrix),('portable',portable)]:
            for file in sorted(folder.rglob('*')):
                if file.is_file():add(label+'/'+file.relative_to(folder).as_posix(),file)
        for name in ['compile-1.log','tests-2.log','package-3.log','package-4.log','matrix-1.log','matrix-2.log','matrix-3.log','portable-1.log','portable-2.log','fastp-prior-integrity.json','fastp-prior-integrity.log','dispatcher-before.java','checkpoint-before.json']:
            file=development/name
            if file.is_file():add('retained-attempts/'+name,file)
        for name in ['matrix-2/COMPLETED.json','portable-1/COMPLETED.json']:
            file=development/name
            if file.is_file():add('historical-324-test-artifact/'+name,file)
        for folder in [development/'matrix-1',development/'before-finalization-guard']:
            for file in sorted(folder.rglob('*')):
                if file.is_file():add('retained-attempts/'+folder.name+'/'+file.relative_to(folder).as_posix(),file)
        inventory={n:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for n,raw in entries.items()}
        entries['ARCHIVE-CONTENTS.json']=(json.dumps(dict(schema_version=1,files=inventory),indent=2)+'\n').encode()
        pending=attempt/'controls.zip'
        with zipfile.ZipFile(pending,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(entries.items()):info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
        with zipfile.ZipFile(pending) as z:assert set(z.namelist())==set(entries) and len(z.namelist())==len(entries) and all(z.read(n)==raw for n,raw in entries.items())
        shutil.copyfile(pending,archive_file)
        proof=dict(schema_version=1,status='passed-native-mash-public-workflow-development-validation',recorded_utc=datetime.now(timezone.utc).isoformat(),
            scope='Public native full-population fit/posterior analysis with provisional-stage distinction, complete replay and guarded finalization. Upstream preparation and paper-role completion remain open.',
            artifact=artifact,source_pins=bound,evidence_archive=dict(path=archive_file.relative_to(ROOT).as_posix(),entries=len(entries),**pin(archive_file)),
            required_baseline_archive=dict(path=baseline['archive']['path'],**pin(baseline_archive),reused_binary_source_members=omitted),
            tests=dict(per_JVM=325,platforms=[r['name'] for r in m['runs']],heap_mib=128,public_admission_cases=30,skipped=0,aborted=0,failed=0,full_project_suite_rerun=False),
            public_process_checks=21,additional_original_learned_model_processes=6,cross_producer_copied_bundle_checks=18,
            original_learned_model=dict(case=p['original_source_case'],values_per_JVM=[r['original_target_values'] for r in p['learned']],maximum_absolute_errors=[r['maximum_absolute_error'] for r in p['learned']],R_rerun=False),
            gates=dict(matrix=dict(path=matrix_file.relative_to(ROOT).as_posix(),**pin(matrix_file)),portable=dict(path=portable_file.relative_to(ROOT).as_posix(),**pin(portable_file))),
            prior_native_scientific_sources_still_exact=True,prior_native_proof=dict(path=old_proof.relative_to(ROOT).as_posix(),**pin(old_proof)),
            previous_dispatch_bytes_reconstructed=True,scientific_catalog_allowlist_expanded=False,fastp_frozen_evidence_recheck=fastp,
            finalization='A deliberately changed posterior after replay/marker rename is detected by the final byte/directory census and the completion marker is removed; all partial evidence remains.',
            historical_attempts='Resource metadata was initially mistaken for an exported runtime asset; corrected gate requires exactly eleven TSV example resources. The earlier 324-test artifact/source is retained; a finalization guard and its regression bring current coverage to325.',
            canonical_artifact_unchanged=pin(canonical),catalog=dict(papers=250,new_integrations=61,native=13,upstream=48,missing=259,method_overlap=48,cellranger='excluded-by-user',mashr='missing; native public scope available; full integration/roles pending'),
            public_native_analysis_available=True,upstream_covariance_preparation_integrated=False,study_route_integrated=False,catalog_promoted=False,whole_paper_reproduced=False,whole_goal_complete=False,changes_pushed=False,released_or_deployed=False)
        write(proof_file,proof)
        assert all(pin(ROOT/n)==v for n,v in bound.items()) and pin(canonical)==proof['canonical_artifact_unchanged'] and pin(catalog)==baseline['source_pins'][catalog.relative_to(ROOT).as_posix()]
        checkpoint=BASE/'current-checkpoint.json';shutil.copyfile(checkpoint,attempt/'previous-checkpoint.json')
        current=dict(status=proof['status'],previous_goal_turn_classification='progress',current_goal_turn_classification='progress',recorded_utc=proof['recorded_utc'],proof_path=proof_file.relative_to(ROOT).as_posix(),proof_pin=pin(proof_file),artifact=artifact,source_pins=bound,evidence_archive=proof['evidence_archive'],required_baseline_archive=proof['required_baseline_archive'],
                     tests_per_JVM=325,platforms=proof['tests']['platforms'],public_native_analysis_available=True,catalog_promoted=False,new_scoped_integrations=61,registered_named_tools_missing=259,current_changes_uncommitted=True,whole_goal_complete=False,released_or_deployed=False,
                     next_actions=['Integrate managed original PCA/ED covariance preparation with the native public execution and complete capability/dependency SKIP3 recovery gates.',
                                   'Add reviewed sharing/composite roles and study integration; resolve supplement/edition/missingness ambiguities and finish eight mashr paper-role reviews before catalog promotion.',
                                   'Continue the other259 missing tools and paper-wide semantic completeness review; retain Cell Ranger exclusion.'])
        staged=attempt/'current-checkpoint.json';write(staged,current);shutil.copyfile(staged,checkpoint)
        write(attempt/'final-audit.json',dict(status='passed-public-native-mash-checkpoint-closure',proof=pin(proof_file),archive=pin(archive_file),baseline_archive=pin(baseline_archive),source_files=len(bound),archive_entries=len(entries),catalog_unchanged=pin(catalog),canonical_unchanged=pin(canonical),whole_goal_complete=False))
        print(json.dumps(dict(status=proof['status'],artifact=artifact,proof=pin(proof_file),archive=pin(archive_file),source_files=len(bound),entries=len(entries))))
        return 0
    except Exception as failure:
        write(attempt/'FAILURE.json',dict(status='FAIL2_MASH_PUBLIC_EVIDENCE_FREEZE',message=str(failure)));(attempt/'WARNINGS.md').write_text('# FAIL2: public mash checkpoint not closed\n\n'+str(failure)+'\n\nRequired action: preserve this attempt. Correct the named mismatch before producing a new proof; do not refresh old scientific hashes to imply coverage.\n',encoding='utf-8');raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_MASH_PUBLIC_EVIDENCE_FREEZE: '+str(failure),file=sys.stderr);sys.exit(2)
