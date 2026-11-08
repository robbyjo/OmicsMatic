# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete retained Hail sample-QC component evidence/current source; execute no genomic runtime."""
import json,re,sys,zipfile
from pathlib import Path
from signac_scan_evidence import byte_pin,strict_json,unpack
from validate_hail_sample_qc import ABSOLUTE_TOLERANCE,RELATIVE_TOLERANCE,compare

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/hail-sample-qc-development'

def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Never disable complete retained evidence checks')
    proof=strict_json((data/'VALIDATION.json').read_bytes());assert proof['status']=='passed-native-Hail139-sample-QC-component-only' and proof['implementation_credit'] is False and proof['full_Hail_workflow_admitted'] is False and proof['whole_goal_complete'] is False;assert byte_pin((data/'controls.zip').read_bytes())==proof['archive']
    with zipfile.ZipFile(data/'controls.zip') as archive:assert len(archive.namelist())<20000 and sum(i.file_size for i in archive.infolist())<134217728
    files=unpack(data/'controls.zip',proof['packing'])
    for name,want in proof['source_pins'].items():assert byte_pin((root/name).read_bytes())==byte_pin(files['current-source/'+name])==want,name
    def obj(name):return strict_json(files[name])
    build=obj('build/COMPLETED.json');reference=obj('reference/COMPLETED.json');validation=obj('validation/COMPLETED.json');intake=obj('upstream/source-intake.json');setup=obj('setup/COMPLETED.json')
    assert reference['status']=='PASS_COMPLETE_ORIGINAL_HAIL_SAMPLE_QC_AND_ALLELE_TYPES_ONLY' and reference['version']=='0.2.139-5e5eedb87ff8' and reference['namespace_patched'] is False and reference['genomic_or_cloud_paper_reproduced'] is False
    assert validation['status']=='PASS_COMPLETE_ORIGINAL_NATIVE_HAIL_SAMPLE_QC_THREE_JVM_COMPONENT_ONLY' and build['status']=='compiled-native-Hail-sample-QC-not-original-runtime-validation'
    assert setup['status']=='PASS_PRIVATE_ORIGINAL_HAIL139_SPARK353_REFERENCE_ONLY' and setup['preexisting_python_and_JDK_unchanged'] is True and setup['cloud_batch_submitted'] is False
    for process in setup['processes']:assert process['exit_code']==0 and process['owned_live_after_cleanup']==[]
    health=setup['health'];assert health['hail']==reference['version'] and health['spark']=='3.5.3' and health['python'].startswith('3.12.')
    assert obj('setup/shared-before.json')==obj('setup/shared-after.json') and byte_pin(files['setup/pip-report.json'])==setup['resolved_packages'] and byte_pin(files['setup/downloaded-archives.json'])==setup['runtime_file_inventory']
    original=intake['versions']['0.2.139'];assert intake['status']=='PASS_TWO_ORIGINAL_HAIL_EDITIONS_SOURCE_CAPTURE_ONLY' and original['revision']=='5e5eedb87ff814916ef08037199fec7b158f999b' and original['wheel']['sha256']=='77068dcda0dfa027a01c1127cc30f9d18714c09050728d5baf75d82e3c756137'
    tag=obj('upstream/official-git-tag.json');annotated=obj('upstream/official-annotated-tag.json');assert tag['ref']=='refs/tags/0.2.139' and tag['object']['type']=='tag' and tag['object']['sha']==annotated['sha'] and annotated['object']['sha']==original['revision'] and annotated['object']['type']=='commit'
    wheel=[item for item in obj('upstream/pypi-metadata.json')['urls'] if item['filename']=='hail-0.2.139-py3-none-any.whl'];assert len(wheel)==1 and wheel[0]['digests']['sha256']==original['wheel']['sha256'] and wheel[0]['size']==original['wheel']['bytes']
    assert byte_pin(files['upstream/LICENSE'])=={k:original['license'][k] for k in ['bytes','sha256']}
    for member in proof['original_members']:assert byte_pin(files['upstream/source/'+member])==original['members'][member],member
    assert files['upstream/tagged/upstream-hail_methods_qc.py']==files['upstream/source/hail/methods/qc.py']
    for name,want in build['source_pins'].items():assert proof['source_pins'][name]==want and byte_pin(files['build/source-snapshot/'+name])==want
    for name,want in validation['source_pins'].items():assert proof['source_pins'][name]==want,name
    for name,want in validation['reference_file_pins'].items():assert byte_pin(files['reference/'+name])==want,name
    assert obj('reference/original.process.json')['owned_live_after_cleanup']==[] and obj('reference/original.process.json')['exit_code']==0
    assert set(build['added_entries'])==set(proof['packaged_entries'])
    for name,want in proof['packaged_entries'].items():assert byte_pin(files['candidate-added-entries/'+name])==want,name
    assert validation['artifact']==build['artifact']==proof['artifact'] and validation['canonical_executable_unchanged']==build['canonical_executable_unchanged']==proof['canonical_executable_unchanged']
    assert validation['relative_tolerance']==RELATIVE_TOLERANCE==2e-12 and validation['absolute_tolerance']==ABSOLUTE_TOLERANCE==2e-12
    fixtures=obj('reference/cases.json');assert len(fixtures['cases'])==12 and len(fixtures['allele_pairs'])==56 and len(reference['cases'])==12 and reference['sample_outputs']==67 and reference['allele_pairs']==56
    assert obj('validation/complete-mismatches.json')==[];totals={};errors=[]
    for jvm in ['windows21','windows25','linux21']:
        major='25' if jvm=='windows25' else '21';text=files['validation/'+jvm+'.version.stdout']+files['validation/'+jvm+'.version.stderr'];assert text.decode('utf8')==validation['jvm_versions'][jvm] and re.search(r'version "'+major+r'(?:\.|\")',text.decode('utf8'))
        process=obj('validation/'+jvm+'.process.json');assert process['exit_code']==0 and 'org.jlinalg.cli.HailSampleQcMatrix' in process['command']
        result=obj('validation/'+jvm+'/COMPLETED.json');assert result['status']=='PASS_COMPLETE_NATIVE_SAMPLE_QC_CANDIDATE_ONLY' and result['cases']==12 and result['allele_pairs']==56 and len(set(result['expected_native_rejections']))==9
        counts=dict(numeric_states=0,exact_scalar_values=0,cases=12,sample_outputs=0,expected_native_rejections=9)
        for case in fixtures['cases']:
            expected=obj('reference/'+case['id']+'/original-result.json');actual=obj('validation/'+jvm+'/'+case['id']+'.json');assert len(expected)==len(case['samples'])
            for i,row in enumerate(expected):assert row['ordinal']==i and row['sample']==case['samples'][i]
            counts['sample_outputs']+=len(expected);compare(expected,actual,jvm+'/'+case['id'],errors,counts)
        compare(obj('reference/original-allele-types.json'),obj('validation/'+jvm+'/allele-types.json'),jvm+'/allele-types',errors,counts);assert counts==validation['checks'][jvm]==dict(numeric_states=660,exact_scalar_values=1296,cases=12,sample_outputs=67,expected_native_rejections=9);totals[jvm]=counts
    assert not errors,errors[:3]
    assert obj('history/reference-1/COMPLETED.json')['status']=='FAIL2_ORIGINAL_HAIL_QC_REFERENCE_INCOMPLETE'
    assert obj('history/validation-1/COMPLETED.json')['status']=='FAIL2_NATIVE_HAIL_QC_VALIDATION_INCOMPLETE' and len(obj('history/validation-1/complete-mismatches.json'))==420
    assert obj('history/validation-3/COMPLETED.json')['status']=='FAIL2_NATIVE_HAIL_QC_VALIDATION_INCOMPLETE'
    summary=dict(status='passed-retained-native-Hail-QC-component-evidence',checks=totals,logical_files=len(files),implementation_credit=False,full_Hail_workflow_admitted=False,whole_goal_complete=False,scope='Every retained QC field/state and current source; no runtime execution, cloud submission, cohort-scale validation or whole-paper certification.')
    if emit:print(json.dumps(summary,indent=2))
    return summary

if __name__=='__main__':
    try:verify()
    except Exception as failure:
        print('HAIL QC EVIDENCE FAIL2: '+repr(failure)+'\nRequired action: preserve the archive and failed receipts; correct the named evidence/source mismatch and repeat into a NEW proof. Do not refresh a hash to admit scientific drift or count Hail as implemented.',file=sys.stderr);raise SystemExit(2)
