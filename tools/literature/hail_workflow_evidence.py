# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete retained original Hail workflows, independent equations, native families and atomic failure receipts. No runtime execution."""
import io,json,sys,zipfile
from pathlib import Path
from types import SimpleNamespace
from signac_scan_evidence import byte_pin,strict_json,unpack
from audit_hail_workflow_controls import audit

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/hail-workflow-development'
EXPECTED=dict(complete_original_cases=10,raw_genotype_entries=72,independent_sample_qc_counts=128,independent_variant_af_values=19,independent_ld_values=64,pca_equations=38,liftover_loci=9,batch_outputs=4,axis_filtered_entries=42,bgzf_roundtrip_entries=72,original_dag_edges=3)

class RetainedPath:
    """Read-only logical archive path; the auditor reads the same complete bytes without extracting executable files or links."""
    def __init__(self,files,name):self.files=files;self.name=name
    def __truediv__(self,name):return RetainedPath(self.files,self.name+'/'+str(name))
    def read_bytes(self):return self.files[self.name]
    def read_text(self,encoding='utf8'):return self.read_bytes().decode(encoding)
    def open(self,mode):assert mode=='rb';return io.BytesIO(self.read_bytes())
    def stat(self):return SimpleNamespace(st_size=len(self.read_bytes()))

def verify_files(proof,files,root=ROOT):
    def obj(name):return strict_json(files[name])
    assert proof['status']=='passed-original-Hail-workflows-and-setup-with-complete-independent-controls' and proof['adapter_admitted'] is True and proof['implementation_credit'] is True and proof['admitted_named_tools']==['hail'] and proof['whole_goal_complete'] is False and proof['whole_paper_reproduced'] is False
    for name,want in proof['source_pins'].items():assert byte_pin((root/name).read_bytes())==byte_pin(files['current-source/'+name])==want,name
    index=obj('capture/index.json');assert index['status']=='PASS_EXACT_LINUX_REGULAR_BYTES_AND_LINK_METADATA_ONLY'
    for name,row in index['records'].items():
        if row['kind']=='file':assert byte_pin(files[name])=={k:row[k] for k in ['bytes','sha256']},name
        else:assert row['kind'] in ['directory','symlink']
    build=obj('workflow-build-6/COMPLETED.json');assert proof['artifact']==build['artifact'] and build['canonical_executable_unchanged']==proof['canonical_executable_unchanged'] and build['unchanged_parent_entries']>20000
    assert build['parent_artifact']==json.loads((root/'docs/literature/hail-sample-qc-development/VALIDATION.json').read_bytes())['artifact']
    for name,want in build['source_pins'].items():assert proof['source_pins'][name]==want and byte_pin(files['workflow-build-6/source-snapshot/'+name])==want
    assert set(build['added_entries'])==set(proof['packaged_entries']) and len(build['added_entries'])==24
    for name,want in proof['packaged_entries'].items():assert byte_pin(files['candidate-added-entries/'+name])==want
    additive=obj('setup-addition-proof-1/COMPLETED.json');assert additive['status']=='PASS_ADDITIVE_HAIL_SETUP_ONLY_ALL_ANALYSIS_MEMBER_BYTES_UNCHANGED' and additive['after']==proof['artifact'] and additive['before']==obj('workflow-build-5/COMPLETED.json')['artifact'] and additive['unchanged_members']==20749 and len(additive['added_entries'])==4
    profiles=obj('current-source/src/main/resources/org/jlinalg/omics/hail-workflow/profiles.json')['editions'];intake=obj('source-intake-2/COMPLETED.json');assert intake['status']=='PASS_TWO_ORIGINAL_HAIL_EDITIONS_SOURCE_CAPTURE_ONLY'
    for edition,profile in profiles.items():
        original=intake['versions'][edition];assert original['revision']==profile['revision'] and original['wheel']['sha256']==profile['wheel']['sha256'] and len(profile['wheel_members'])==302
        tag=obj('source-intake-2/'+edition+'/official-git-tag.json');annotated=obj('source-intake-2/'+edition+'/official-annotated-tag.json');assert tag['ref']=='refs/tags/'+edition and tag['object']['sha']==annotated['sha'] and annotated['object']['sha']==profile['revision']
        wheel=[r for r in obj('source-intake-2/'+edition+'/pypi-metadata.json')['urls'] if r['filename']=='hail-'+edition+'-py3-none-any.whl'];assert len(wheel)==1 and wheel[0]['digests']['sha256']==profile['wheel']['sha256'] and wheel[0]['size']==profile['wheel']['bytes']
        assert byte_pin(files['source-intake-2/'+edition+'/upstream-LICENSE'])=={k:original['license'][k] for k in ['bytes','sha256']}
        source='source-intake-2/'+edition+'/source/'
        for name,raw in files.items():
            if name.startswith(source):assert byte_pin(raw)==original['members'][name.removeprefix(source)],name
    bindings=['reference-bind-139-java11-2','reference-bind-130-java11-3','setup-install139-1/preparation','setup-capture130-1/preparation']
    for home in bindings:
        binding=obj(home+'/COMPLETED.json');profile=profiles[binding['edition']];assert binding['status']=='PASS_BOUND_ORIGINAL_HAIL_WORKFLOW_REFERENCE_ONLY' and binding['runtime_trees_unchanged'] is True and binding['cloud_batch_submitted'] is False
        assert obj(home+'/runtime-before.json')==obj(home+'/runtime-after.json') and byte_pin(files[home+'/runtime-before.json'])==binding['runtime_inventory'];assert obj(home+'/original-installed-wheel-files.json')==profile['wheel_members'] and byte_pin(files[home+'/original-installed-wheel-files.json'])==binding['original_installed_wheel_files']
        health=binding['health'];assert health['status']=='PASS_ORIGINAL_HAIL_JAVA11_LOCAL_SPARK_HEALTH_ONLY' and health['revision']==profile['revision'] and health['spark']==profile['spark'] and health['python'].startswith(profile['python_minor']+'.') and health['hail']==profile['hail_version']
        assert b'version "11.' in files[home+('/java-version.stderr' if home.startswith('setup-') else '/java.stderr')]
        if home.startswith('setup-'):
            assert binding['analysis_executed'] is False and binding['shared_runtime_unchanged'] is True and obj(home+'/shared-before.json')==obj(home+'/shared-after.json')
            for process in binding['processes']:assert process['exit_code']==0 and process['owned_live_after_cleanup']==[]
    fixture=RetainedPath(files,'workflow-fixtures-4');checks={}
    for home in ['cli-positive-139-2','cli-positive-130-2','cli-public-installed139-1']:
        result=home+'/result';marker=obj(result+'/analysis-status.json');manifest=obj(result+'/manifest.json');assert marker['status']=='PASS_ORIGINAL_HAIL_WORKFLOWS' and marker['result_admitted'] is True and byte_pin(files[result+'/manifest.json'])['sha256']==marker['manifest_sha256']
        regulars={n.removeprefix(result+'/'):r for n,r in index['records'].items() if n.startswith(result+'/') and r['kind']=='file'};assert set(regulars)==set(manifest['files'])|{'analysis-status.json','manifest.json'},'Complete original output family changed'
        for name,want in manifest['files'].items():assert byte_pin(files[result+'/'+name])==want,name
        links={n.removeprefix(result+'/'):r for n,r in index['records'].items() if n.startswith(result+'/') and r['kind']=='symlink'};assert set(links)==set(manifest['symlinks'])
        for name,want in manifest['symlinks'].items():
            actual=links[name];assert actual['target']==want['target'] and actual['resolved']==want['resolved_logical_path'] and actual['target_exists']==want['target_exists'] and actual.get('content')==want.get('content')
        for log in ['original.process.json','java-process.json']:assert obj(result+'/worker/'+log)['owned_live_after_cleanup']==[]
        assert obj(home+'/positive.process.json')['exit_code']==0;checks[home]=audit(RetainedPath(files,result+'/worker'),fixture);assert checks[home]==EXPECTED
    for home in ['cli-positive-139-2','cli-positive-130-2']:assert obj(home+'/COMPLETED.json')['status']=='FAIL2_JAVA_HAIL_WORKFLOW_VALIDATION_INCOMPLETE' and 'indices' in obj(home+'/COMPLETED.json')['error']
    for home in ['cli-output-audit139-1','cli-output-audit130-1']:assert obj(home+'/COMPLETED.json')['status']=='PASS_COMPLETE_ORIGINAL_HAIL_WORKFLOW_INDEPENDENT_AUDIT_ONLY' and obj(home+'/COMPLETED.json')['checks']==EXPECTED
    assert obj('cli-public-installed139-1/COMPLETED.json')['status']=='PASS_ACTUAL_JAVA_HAIL_WORKFLOW_POSITIVE_ONLY' and obj('cli-public-installed139-1/COMPLETED.json')['artifact']==proof['artifact']
    for home,mode in [('cli-timeout-1','TIMEOUT'),('cli-late-liftover-failure-1','LATE_LIFTOVER_FAILURE'),('cli-batch-failure-1','BATCH_FAILURE')]:
        receipt=obj(home+'/COMPLETED.json');assert receipt['status']=='PASS_ACTUAL_JAVA_HAIL_WORKFLOW_'+mode+'_ONLY' and receipt['artifact']==proof['artifact'];marker=obj(home+'/result/analysis-status.json');assert marker['result_admitted'] is False and marker['status']=='FAIL2_NO_RESULT_ADMITTED' and b'Required action:' in files[home+'/result/WARNINGS.md']
        for log in ['original.process.json','java-process.json']:assert obj(home+'/result/worker/'+log)['owned_live_after_cleanup']==[]
        if mode!='TIMEOUT':assert len(obj(home+'/result/worker/COMPLETED.json')['cases'])==1 and any(n.startswith(home+'/result/worker/raw-mixed/result.mt/') for n in files)
    skips=obj('cli-skip-controls-2/COMPLETED.json');assert skips['status']=='PASS_ACTUAL_JAVA_HAIL_WORKFLOW_SKIP_CONTROLS_ONLY' and len(skips['reports'])==4 and skips['artifact']==proof['artifact']
    for row in skips['reports']:
        result='cli-skip-controls-2/'+row['name']+'-result';assert row['exit_code']==3 and row['result_admitted'] is False and obj(result+'/analysis-status.json')['result_admitted'] is False and b'Required action:' in files[result+'/WARNINGS.md'];assert not any(n.startswith(result+'/inputs/') or n.startswith(result+'/worker/') for n in files)
    for home,mode in [('setup-install139-1','INSTALL139'),('setup-capture130-1','CAPTURE130'),('setup-denial-1','DENY_INSTALL139')]:
        setup=obj(home+'/COMPLETED.json');assert setup['status']=='PASS_HAIL_WORKFLOW_SETUP_'+mode+'_ONLY' and setup['artifact']==proof['artifact']
        for jvm in ['windows21','windows25','linux21']:
            exported=obj(home+'/'+jvm+'-export/setup-kit.json');assert exported['analysis_executed'] is False and exported['software_installed'] is False and exported['files']==setup['exported_files'] and len(exported['files'])==6 and obj(home+'/'+jvm+'.process.json')['exit_code']==0
            for name,want in exported['files'].items():assert byte_pin(files[home+'/'+jvm+'-export/'+name])==want
    denial=obj('setup-denial-1/preparation/COMPLETED.json');assert denial['status']=='SKIP3_HAIL_RUNTIME_UNAVAILABLE' and 'Installation flag absent' in denial['error'] and obj('setup-denial-1/preparation.process.json')['exit_code']==3
    install=obj('setup-install139-1/preparation.process.json');assert '--allow-install' in install['command'] and '--allow-download' not in install['command'] and install['exit_code']==0
    lock=obj('dependency-lock-intake-1/COMPLETED.json');assert lock['status']=='PASS_EXACT_OFFICIAL_HAIL_REFERENCE_DEPENDENCY_ARCHIVE_LOCKS_ONLY' and lock['distinct_package_versions']==106
    requests=obj('request-validation-3/COMPLETED.json');assert requests['status']=='PASS_THREE_JVM_TYPED_HAIL_REQUESTS_ONLY' and requests['artifact']==additive['before']
    for name,report in requests['reports'].items():assert report['positive_profiles']==3 and len(report['rejected_cases'])==len(set(report['rejected_cases']))==26 and report['request_preserved'] is True
    return dict(status='passed-retained-original-Hail-workflow-and-setup-evidence',artifact=proof['artifact'],checks=checks,logical_files=len(files),admitted_named_tools=['hail'],implementation_credit=True,whole_goal_complete=False,whole_paper_reproduced=False,scope='Complete retained synthetic original workflows and native families, source identity, independent math and failure protocols; no runtime execution, historical-version substitution, cohort/cloud calibration or whole-paper certification.')

def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Never disable retained evidence checks')
    proof=strict_json((data/'VALIDATION.json').read_bytes());assert byte_pin((data/'controls.zip').read_bytes())==proof['archive']
    with zipfile.ZipFile(data/'controls.zip') as bundle:assert len(bundle.namelist())<100000 and sum(i.file_size for i in bundle.infolist())<536870912
    result=verify_files(proof,unpack(data/'controls.zip',proof['packing']),root)
    if emit:print(json.dumps(result,indent=2))
    return result

if __name__=='__main__':
    try:verify()
    except Exception as failure:
        print('HAIL WORKFLOW EVIDENCE FAIL2: '+repr(failure)+'\nRequired action: preserve original evidence, errors and provisional outputs. Correct the named mismatch and repeat in NEW paths. Never replace a saved scientific hash or admit a partial batch. Required forbidden/unavailable runtimes keep dependent analysis skipped.',file=sys.stderr);raise SystemExit(2)
