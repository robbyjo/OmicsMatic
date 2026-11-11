# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify frozen exact-edition Sniffles execution evidence; launch no scientific work."""
import argparse,io,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from sniffles_native_evidence import verify as native_verify
from sniffles_installation_evidence import verify as installation_verify
from sniffles_execution_development_evidence import verify as development_verify

HOME=ROOT/'docs/literature/sniffles-workflows'
BASE='build/literature-review/sniffles-runtime/'
STATUS='PASS_SOURCE_BOUND_ORIGINAL_SNIFFLES_CALLING_WORKFLOWS_WITH_EXPLICIT_LIMITATIONS'


def verify(archive_only=False,home=HOME):
    home=Path(home);proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']==STATUS
    assert proof['declared_calling_workflow_available'] and not any(proof[key] for key in ['implementation_credit','canonical_adapter_available',
        'independent_SV_calling','whole_package_implemented','whole_paper_reproduced','whole_goal_complete'])
    assert (proof['complete_workflow_batches'],proof['complete_cases'],proof['complete_records'])==(4,35,86)
    assert proof['portable_frozen_verifications']==12 and proof['rehash_false_value_rejections']==2
    assert pin(home/'controls.zip')==proof['archive']
    assert all(pin(home/name)==value for name,value in proof['artifacts'].items())
    if not archive_only:
        assert all(pin(ROOT/name)==value for name,value in proof['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    for name,value in proof['parent_proofs'].items():assert pin(ROOT/name)==value
    _,native=native_verify(True);_,installation=installation_verify(True);development_verify(True)
    files=unpack(home/'controls.zip',proof)
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
    def document(name):return json.loads(files[BASE+name])
    builds=[('execution-final-build-21','sniffles-workflow.jar','javac21','sniffles-vcf-development.jar'),
        ('execution-final-build-25','sniffles-workflow-javac25.jar','javac25','sniffles-vcf-javac25-development.jar')]
    candidate=document('diagnostic-interface-preview-1/COMPLETED.json')
    assert candidate['status']=='PASS_ISOLATED_SNIFFLES_DIAGNOSTIC_INTERFACE_BUILD_ONLY'
    for build,name,compiler,native_name in builds:
        receipt=document(build+'/COMPLETED.json');assert receipt['status']=='PASS_ISOLATED_JAVA17_SNIFFLES_EXECUTION_BUILD_ONLY'
        wanted={key:candidate['artifacts'][compiler][key] for key in ['bytes','sha256']}
        assert byte_pin(files[receipt['artifact']['path']])==pin(home/name)==wanted
        for path,value in receipt['source_pins'].items():
            assert byte_pin(files[BASE+build+'/source-snapshot/'+path])==value
            assert byte_pin(files['current-source/'+path])==value
        with zipfile.ZipFile(home/name) as jar,zipfile.ZipFile(ROOT/'docs/literature/sniffles-vcf-development'/native_name) as old:
            for path in old.namelist():
                if path.endswith('.class'):assert jar.read(path)==old.read(path)
            for path in ['profiles.json','bootstrap.py','worker.py']:
                assert jar.read('org/jlinalg/omics/sniffles-execution/'+path)==files['current-source/src/main/resources/org/jlinalg/omics/sniffles-execution/'+path]
    adopted=document('interface-adoption-1/COMPLETED.json')
    assert adopted['status']=='PASS_EXACT_TESTED_SNIFFLES_INTERFACE_ADOPTION_AND_FULL_REBUILDS_ONLY' and adopted['both_full_builds_byte_identical_to_tested_candidates']
    for relative,value in candidate['candidate_sources'].items():
        assert relative.startswith('source-candidate/');path=relative.removeprefix('source-candidate/')
        assert byte_pin(files['current-source/'+path])==value
    binding=document('runtime-binding-4/COMPLETED.json')
    assert binding['status']=='PASS_BOUND_ORIGINAL_SNIFFLES_RUNTIME_ONLY' and not binding['analysis_admitted'] and not binding['owned_live_after_cleanup']
    assert len(binding['binding_files'])==400 and set(binding['versions'])=={'1.0.7','2.0.2','2.0.7','2.3.2','2.4'}
    for name,value in binding['binding_files'].items():assert byte_pin(files[BASE+'runtime-binding-4/'+name])==value
    assert not binding['analysis_installed_software'] and not binding['source_namespace_patched'] and not binding['startup_hooks_executed']
    assert binding['python_hash_seed_effective']=='0'
    profile=json.loads(files['current-source/src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json'])
    health=document('runtime-binding-4/health/HEALTH.json');assert health['packages']==profile['packages']
    for version in binding['versions']:
        process=document('runtime-binding-4/version-'+version+'/process/process.json');assert process['exit_code']==0 and not process['owned_live_after_cleanup']
    complete=0;cases=0
    for name,count,records in [('workflow-current-2',14,39),('option-interface-validation-1/workflow',10,20),('workflow-extended-2',10,25),('public-example-1/analysis',1,2)]:
        prefix=BASE+name+'/';master=document(name+'/COMPLETED.json')
        assert master['status']=='PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW' and master['analysis_admitted']
        assert master['raw_inputs_unchanged'] and not master['owned_live_after_cleanup'] and not master['source_namespace_patched'] and not master['analysis_installed_software']
        assert len(master['analyses'])==count and sum(row['records'] for row in master['analyses'])==records
        assert byte_pin(files[prefix+'result-files.json'])==master['result_files_pin'] and byte_pin(files[prefix+'WARNINGS.md'])==master['warnings_pin']
        index=json.loads(files[prefix+'result-files.json'])
        for path,value in index.items():assert byte_pin(files[prefix+path])==value
        request=json.loads(files[prefix+'request.original.json']);assert [row['id'] for row in master['analyses']]==[row['id'] for row in request['analyses']]
        for row in master['analyses']:
            assert not row['analysis_admitted'] and row['status']=='PROVISIONAL_COMPLETE_ORIGINAL_OUTPUT_AND_NATIVE_VIEW_ONLY'
            analysis=prefix+'analyses/'+row['id']+'/';raw=files[analysis+'original/calls.vcf'].decode('utf8').splitlines()
            native_rows=[json.loads(line) for line in files[analysis+'native/variants.jsonl'].splitlines()]
            header=json.loads(files[analysis+'native/header.json'])
            assert [line for line in raw if not line.startswith('#')]==[item['raw_line'] for item in native_rows]
            assert len(native_rows)==row['records']
            assert [line for line in raw if line.startswith('##')]==header['metadata_lines']
            assert [line for line in raw if line.startswith('#CHROM\t')]==[header['column_line']]
        for path,raw in files.items():
            if path.startswith(prefix) and path.endswith('/process.json'):
                process=json.loads(raw);assert 'owned_live_after_cleanup' in process and not process['owned_live_after_cleanup']
        complete+=records;cases+=count
    assert (cases,complete)==(35,86)
    for name,count,records in [('workflow-current-reference-comparison-2',14,39),('option-complete-reference-comparison-1',10,20),('extended-complete-reference-comparison-1',10,25)]:
        comparison=document(name+'/COMPLETED.json')
        assert comparison['status']=='PASS_COMPLETE_SNIFFLES_ORIGINAL_RECORD_AND_HEADER_COMPARISONS_ONLY' and comparison['comparison_passed']
        assert comparison['all_declared_workflow_cases_compared'] and (comparison['complete_cases'],comparison['complete_records'])==(count,records)
        assert all(row['every_raw_column_INFO_FORMAT_sample_value_matches'] and row['full_header_compared'] for row in comparison['cases'])
        assert all(byte_pin(files['current-source/'+path])==value for path,value in comparison['source_pins'].items())
    example=document('public-example-audit-1/COMPLETED.json')
    assert example['status']=='PASS_ACTUAL_RELOCATED_SNIFFLES_PUBLIC_EXAMPLE_AND_EXISTING_EXPORT_REFUSAL_ONLY'
    assert example['complete_records']==example['phased_genotypes']==2 and example['published_fixture_bytes_unchanged'] and example['existing_export_refused_without_changes']
    for name,count,records in [('default-frozen-controls-1',14,39),('option-frozen-controls-1',10,20)]:
        checked=document(name+'/COMPLETED.json');assert checked['status']=='PASS_SIX_SNIFFLES_PORTABLE_FROZEN_CHECKS_AND_REHASHED_FALSE_VALUE_REJECTION_ONLY'
        assert (checked['complete_cases'],checked['complete_records'])==(count,records) and len(checked['verifications'])==6
        assert {(row['compiler'],row['platform']) for row in checked['verifications']}=={(compiler,platform) for compiler in ['javac21','javac25'] for platform in ['windows21','windows25','linux21']}
        assert all(row['original_warnings_retained'] for row in checked['verifications'])
        failure=document(name+'/false-view-verification/COMPLETED.json')
        assert not failure['verification_passed'] and 'Frozen native full-record view differs from original VCF' in failure['error']
    controls=document('combined-failure-controls-1/COMPLETED.json')
    assert controls['status']=='PASS_EXPLICIT_SNIFFLES_FAILURE_AND_SUPERSESSION_RECEIPT_AUDIT_ONLY'
    assert len(controls['five_actual_preflight_controls'])==5 and len(controls['two_wrong_edition_preflight_controls'])==2
    assert controls['prior_boundary_suite_status_unchanged'].startswith('FAIL2_') and controls['prior_option_suite_status_unchanged'].startswith('FAIL2_')
    assert len(controls['corrected_actual_cancellations'])==2 and len(controls['ten_superseded_original_VCF_pins_unchanged'])==10
    for name,status in [('option-interface-method-comparison-3','PASS_BOTH_OPTION_INTERFACE_EXECUTION_ADMISSION_PATH_COMPARISONS_ONLY'),
        ('diagnostic-interface-method-comparison-1','PASS_BOTH_CAUSAL_DIAGNOSTIC_ONLY_BYTECODE_COMPARISONS')]:
        row=document(name+'/COMPLETED.json');assert row['status']==status and len(row['comparisons'])==2
    causal=document('cancellation-diagnostic-controls-1/COMPLETED.json')
    assert causal['status']=='PASS_BOTH_REAL_SNIFFLES_CANCELLATION_DIAGNOSTIC_CONTROLS_ONLY'
    for row in causal['controls']:
        assert row['exit_code']==2 and 'Cancellation requested' in row['actual_error'] and row['all_owned_process_cleanup_fields_present_and_empty']
    # Earlier immutable source/format records remain in their original evidence
    # bundles. Check their parent archives; never rewrite them for current source.
    assert native and installation
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete execution-evidence checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--archive-only',action='store_true');a=p.parse_args()
    try:
        proof,files=verify(a.archive_only)
        print(proof['status']+';35 complete cases/86 original records; frozen consistency only; goal remains active')
    except Exception as failure:
        raise SystemExit('FAIL2_SNIFFLES_EXECUTION_EVIDENCE: '+repr(failure)+'\nACTION: preserve evidence and all failed outputs, correct the named mismatch, and repeat validation. No dependent calling/catalog credit from a failed evidence check.')
