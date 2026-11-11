# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the complete original coordinate adapter, native checks, failures and lossless evidence lineage."""
import hashlib,io,json,re,sys,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin,safe
from simpleppt_math_evidence import verify as verify_core

BASE='build/literature-review/scfates-runtime/'
FOLDER=ROOT/'docs/literature/scfates-workflow-development'

def load_files(folder,proof,core):
    files={}
    for part in proof['archive_parts']:
        path=folder/part['name'];assert pin(path)==part['pin'] and path.stat().st_size<95000000;members=unpack(path,part['packing']);assert not(set(files)&set(members));files.update(members)
    core_folder=ROOT/'docs/literature/simpleppt-core-development';locations={}
    for part in core['archive_parts']:
        for name in part['packing']['archive_file_pins']:
            if name.startswith('objects/'):locations[name.removeprefix('objects/')]=(part['name'],name)
    cached={}
    for name,reference in proof['parent_references'].items():
        safe(name);assert name not in files;target=reference['parent_logical_file'];want=reference['pin'];assert core['logical_file_pins'][target]==want and want==proof['logical_file_pins'][name];digest=want['sha256']
        if digest not in cached:
            archive,member=locations[digest]
            with zipfile.ZipFile(core_folder/archive) as z:raw=z.read(member)
            assert byte_pin(raw)==want;cached[digest]=raw
        files[name]=cached[digest]
    assert len(files)==proof['logical_files'] and {n:byte_pin(v) for n,v in files.items()}==proof['logical_file_pins'];return files

def verify(emit=True,folder=None):
    folder=FOLDER if folder is None else Path(folder).resolve();assert folder==FOLDER or folder.is_relative_to(ROOT/'build/literature-review/scfates-runtime')
    p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-original-scfates-coordinate-workflow-and-native-equation-audits-only' and p['full_scfates_coordinate_adapter_admitted'] and p['implementation_credit'] and not p['whole_package_implemented'] and not p['independent_trajectory_refit'] and not p['new_catalog_credit'] and not p['whole_goal_complete'];core=verify_core(False)
    for name,want in p['parent_proofs'].items():assert pin(ROOT/name)==want,name
    files=load_files(folder,p,core)
    for name,want in p['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==want,name
    get=lambda name:json.loads(files[BASE+name]);setup=get('workflow-setup-3/COMPLETED.json');assert setup['status']=='PASS_BOUND_ORIGINAL_SCFATES_WORKFLOW_RUNTIME_ONLY' and setup['runtime_trees_unchanged'] and setup['shared_inputs_unchanged'] and not setup['namespace_patched'] and not setup['analysis_installed_software'];assert setup['health']['status']=='PASS_COMPLETE_ORIGINAL_SCFATES_NAMESPACES_ONLY' and setup['health']['edition']=='scfates-1.2.5_simpleppt-1.1.7'
    for label in ['base','runtime']:assert get('workflow-setup-3/'+label+'-before.json')==get('workflow-setup-3/'+label+'-after.json')==get('workflow-runtime-final-1/'+label+'-final.json')
    assert get('workflow-runtime-final-1/COMPLETED.json')['status']=='PASS_COMPLETE_SCFATES_BASE_AND_PRIVATE_RUNTIME_UNCHANGED_AFTER_CONTROLS_ONLY';assert get('workflow-build-5/COMPLETED.json')['artifact']==p['artifact'];build=get('workflow-build-5/COMPLETED.json');assert build['status']=='PASS_COMPILED_SCFATES_COORDINATE_WORKFLOW_ONLY' and build['unchanged_parent_entries']==21253
    for name,want in build['source_pins'].items():assert p['source_pins'][name]==want
    profiles=json.loads(files['current-source/src/main/resources/org/jlinalg/omics/scfates-workflow/profiles.json']);assert len(profiles['dependency_archives'])==50 and profiles['scfates_version']=='1.2.5' and profiles['simpleppt_version']=='1.1.7';assert byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/scfates-workflow/profiles.json'])==setup['profile']
    for package in profiles['packages'].values():
        raw=files['current-source/src/main/resources/org/jlinalg/omics/scfates-workflow/original/'+package['wheel']['filename']];assert byte_pin(raw)=={k:package['wheel'][k] for k in ['bytes','sha256']}
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
            for name,want in package['members'].items():assert byte_pin(z.read(name))==want
    positive=get('workflow-cli-positive-2/COMPLETED.json');assert positive['status']=='PASS_COMPLETE_SCFATES_PUBLIC_CLI_CONTROL_ONLY' and positive['exit_code']==0 and positive['analysis_admitted'] and positive['analysis_status']['result_admitted'] and len(positive['analysis_status']['cases'])==9
    for name in ['workflow-cli-quoted-1','workflow-example-1']:
        r=get(name+'/COMPLETED.json');assert r['status'].startswith('PASS_') and r['analysis_status']['result_admitted'] and r['exit_code']==0
    parity=get('workflow-reference-parity-2/COMPLETED.json');assert parity['status']=='PASS_NINE_SCFATES_PUBLIC_WORKFLOWS_EXACT_FROZEN_ORIGINAL_PARITY_ONLY' and len(parity['cases'])==9 and parity['numeric_array_states']==83789804 and parity['scalar_identity_states']==61200
    native=get('workflow-native-1/COMPLETED.json');assert native['status']=='PASS_THREE_JVM_SCFATES_WORKFLOW_AUDITS_ONLY' and len(native['runs'])==3 and {r['label'] for r in native['runs']}=={'windows21','windows25','linux21'}
    for run in native['runs']:
        r=run['report'];assert run['exit_code']==0 and len(r['cases'])==9 and (r['numeric_states'],r['identity_states'],r['tsv_states'],r['audit_operations'])==(27995425,9089806,22200,761441709) and not r['independent_trajectory_refit'] and not r['public_execution_adapter_batch_admitted']
    root=get('root-scan-original-1/COMPLETED.json');assert root['status']=='PASS_31_COMPILED_SCIPY_POSITIVE_TREE_SCANS_ONLY' and root['all_runtime_trees_unchanged'] and len(root['cases'])==31 and root['numeric_distance_states']==21593;root_native=get('root-scan-native-1/COMPLETED.json');assert root_native['status']=='PASS_THREE_JVM_SCFATES_ROOT_AUDITS_ONLY' and len(root_native['runs'])==3
    for run in root_native['runs']:assert run['exit_code']==0 and run['report']['cases']==31 and run['report']['numeric_states']==21593 and not run['report']['general_asymmetric_graph_support']
    source=get('root-distance-source-review-2/COMPLETED.json');assert source['status']=='PASS_OFFICIAL_DIRECTIONAL_DISTANCE_AND_UNDIRECTED_TREE_SOURCE_REVIEW_ONLY' and not source['network_used'] and files[BASE+'root-distance-source-review-1/FAILURE.md'];assert get('workflow-cli-positive-1/COMPLETED.json')['status']=='FAIL2_SCFATES_PUBLIC_CLI_CONTROL';assert not get('workflow-fixtures-1/runs/positive-batch/analysis-status.json')['result_admitted']
    for package,record in source['sources'].items():
        for name,want in record['members'].items():
            raw=files[source['intake']+'/'+package+'/'+name];assert byte_pin(raw)=={k:want[k] for k in ['bytes','sha256']};assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==want['git_blob']
    for directory,count in [('workflow-cli-late-palette-1',2),('workflow-cli-late-sampling-1',1)]:
        r=get(directory+'/COMPLETED.json');assert r['status']=='PASS_COMPLETE_SCFATES_PUBLIC_CLI_CONTROL_ONLY' and r['exit_code']==2 and r['earlier_cases_provisional']==count and not r['analysis_status']['result_admitted']
    assert get('workflow-cli-policy-1/COMPLETED.json')['exit_code']==2 and not get('workflow-cli-policy-1/COMPLETED.json')['analysis_status']['result_admitted'];assert get('workflow-cli-invalid-1/COMPLETED.json')['exit_code']==2
    corrupt=get('workflow-corruptions-1/COMPLETED.json');assert corrupt['status']=='PASS_15_SCFATES_OBSERVED_WORKFLOW_CORRUPTIONS_REJECTED_ONLY' and corrupt['original_files_unchanged'] and len(corrupt['checks'])==15 and all(r['whole_family_rejected'] and r['exit_code']!=0 for r in corrupt['checks']);inputs=get('workflow-input-contracts-2/COMPLETED.json');assert inputs['status']=='PASS_11_INVALID_SCFATES_INPUTS_AND_EXACT_GZIP_FIXTURE_ONLY' and inputs['original_files_unchanged'] and len(inputs['checks'])==12 and sum(r['exit_code']!=0 for r in inputs['checks'])==11
    contracts=get('workflow-contracts-1/COMPLETED.json');assert contracts['status']=='PASS_SEVEN_SCFATES_EXPORT_PERMISSION_AND_CONNECTED_SKIP_CONTRACTS_ONLY' and contracts['checks_completed']==7 and len(contracts['checks'])==7 and not contracts['original_analysis_executed'] and not contracts['software_installed']
    for name in ['workflow-timeout-1','workflow-output-capacity-1','workflow-cancellation-1']:
        r=get(name+'/COMPLETED.json');assert r['status'].startswith('PASS_ACTUAL_SCFATES_') and r['exit_code']==2 and not r['analysis_admitted'] and not r['analysis_status']['result_admitted'] and not r['supervision']['owned_live_after_cleanup'] and not r['java_process']['owned_live_after_cleanup']
    example=get('workflow-example-1/COMPLETED.json');assert example['status']=='PASS_SCFATES_EXACT_DOCUMENTED_SIX_POINT_EXAMPLE_ONLY' and example['scientific_parameters_unchanged_from_guide'];guide=files['current-source/docs/literature/SCFATES_WORKFLOW.md'].decode();assert byte_pin(files[BASE+'workflow-example-1/guide-source.md'])==example['guide_snapshot']
    for lang,key in [('csv','documented_csv'),('json','documented_json')]:assert byte_pin(re.search('```'+lang+r'\n(.*?)```',guide,re.S).group(1).encode())==example[key],'Documented executable/scientific sections changed after their actual run'
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged'];artifact=ROOT/p['artifact']['path']
    if artifact.exists():assert pin(artifact)=={k:p['artifact'][k] for k in ['bytes','sha256']}
    if emit:print('Verified complete scFates coordinate adapter:9 exact original workflows/83789804 array states,3-JVM native audits,31 compiled tree controls,15 corruptions,11 invalid inputs/gzip,7 dependency/export controls,late source/policy failures,timeout/output/cancellation and exact documented example. Catalog registration and250-paper goal remain open.')
    return p

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original adapter/source/evidence checks')
    verify()
