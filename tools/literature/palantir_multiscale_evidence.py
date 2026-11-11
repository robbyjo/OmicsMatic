# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the native coordinate component, complete source references and explicitly unfinished trajectory adapter."""
import json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from prepare_palantir_workflow_reference_runner import verify as verify_runner

def verify(emit=True):
    folder=ROOT/'docs/literature/palantir-multiscale-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-native-Palantir-multiscale-component-and-original-workflow-discovery-only' and not p['implementation_credit'] and not p['full_palantir_adapter_admitted'] and not p['whole_goal_complete'];assert pin(folder/'controls.zip')==p['archive'];files=unpack(folder/'controls.zip',p);verify_runner()
    for name,want in p['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==want,name
    for name,want in p['parent_proofs'].items():assert pin(ROOT/name)==want,name
    base='build/literature-review/palantir-runtime/';get=lambda n:json.loads(files[base+n]);source=get('source-intake-3/SOURCE.json');assert source['status']=='PASS_EXACT_OFFICIAL_PALANTIR_SOURCE_ONLY' and set(source['versions'])=={'1.4.4','1.4.6'} and source['current_version']=='1.4.6'
    for edition,d in source['versions'].items():
        for name,want in d['wheel_members'].items():assert byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/palantir-multiscale/original/'+edition+'/'+name])==want
        for name,want in d['wheel_members'].items():
            if name.startswith('palantir/') and name.endswith('.py'):assert want['sha256']==d['git_source_members']['src/'+name]['sha256'],'Official wheel/tag scientific source differs'
    assert get('source-intake-1/COMPLETED.json')['status']=='FAIL2_PALANTIR_OFFICIAL_SOURCE_INTAKE' and get('source-intake-2/COMPLETED.json')['status']=='FAIL2_PALANTIR_OFFICIAL_SOURCE_INTAKE';first=get('reference-setup-1/COMPLETED.json');assert first['status']=='FAIL2_PALANTIR_REFERENCE_SETUP' and first['shared_runtime_unchanged'] and 'RECORD' in first['error'];setup=get('reference-setup-2/COMPLETED.json');assert setup['status']=='PASS_COMPLETE_PRIVATE_ORIGINAL_PALANTIR_IMPORTS_ONLY' and setup['shared_runtime_unchanged'] and not setup['namespace_functions_patched'] and not setup['analysis_admitted'];locks=get('reference-setup-2/dependency-locks.json');assert len(locks['archives'])==48 and locks['total_archive_bytes']==309947450;assert not get('reference-setup-2/reused-downloads.json')['software_prefix_reused'] and not get('reference-setup-2/reused-downloads.json')['network_used']
    for edition in ['1.4.4','1.4.6']:
        assert setup['editions'][edition]['health']['edition']==edition and setup['editions'][edition]['health']['status']=='PASS_COMPLETE_UNCHANGED_ORIGINAL_PALANTIR_IMPORT_ONLY';record=get('reference-setup-2/installer-record-'+edition+'.json');assert len(record)==1 and next(iter(record)).endswith('.dist-info/RECORD')
    skips=get('setup-permissions-1/COMPLETED.json');assert skips['status']=='PASS_FOUR_PALANTIR_PERMISSION_AND_CONNECTED_SKIP_CONTROLS_ONLY' and len(skips['processes'])==4 and all(v['exit_code']==3 for v in skips['processes']) and not skips['software_installed'] and not skips['original_analysis_executed']
    original=get('multiscale-original-1/COMPLETED.json');assert original['status']=='PASS_TWO_FULL_ORIGINAL_PALANTIR_MULTISCALE_REFERENCE_FAMILIES_ONLY' and original['all_runtime_trees_unchanged'] and len(original['processes'])==2 and all(v['exit_code']==0 and not v['owned_live_after_cleanup'] for v in original['processes'])
    for edition,report in original['reports'].items():assert (report['cases'],report['native_numeric_cases'],report['native_tie_deferrals'],report['original_errors'],report['source_only_profiles'])==(52,42,2,6,2) and not report['namespace_functions_patched'] and not report['pickle_loaded'];assert byte_pin(files[base+'multiscale-original-1/'+edition+'/cases.json'])==dict(bytes=report['case_bytes'],sha256=report['case_sha256'])
    build=get('multiscale-build-1/COMPLETED.json');assert build['status']=='PASS_COMPILED_NATIVE_PALANTIR_MULTISCALE_COMPONENT_ONLY' and build['artifact']==p['artifact'] and build['unchanged_parent_entries']==21083 and len(build['added_entries'])==50;native=get('multiscale-native-1/COMPLETED.json');assert native['status']=='PASS_THREE_JVM_TWO_EDITION_NATIVE_PALANTIR_MULTISCALE_COMPONENT_ONLY' and len(native['runs'])==6 and {v['label'] for v in native['runs']}=={'windows21','windows25','linux21'}
    for run in native['runs']:
        r=run['audit'];assert run['exit_code']==0 and (r['numeric_cases'],r['numeric_state_comparisons'],r['source_errors_rejected'],r['ambiguous_ties_deferred'],r['source_only_profiles_retained'],r['native_rejections'])==(42,17627,6,2,2,10)
    adversarial=get('multiscale-adversarial-1/COMPLETED.json');assert adversarial['status']=='PASS_SEVEN_PALANTIR_MULTISCALE_COMPARISON_CORRUPTIONS_REJECTED_ONLY' and len(adversarial['checks'])==7 and all(c['complete_comparison_rejected'] and c['exit_code']!=0 for c in adversarial['checks']);assert get('multiscale-example-1/COMPLETED.json')['status']=='FAIL2_NATIVE_PALANTIR_EXAMPLE';assert get('multiscale-example-2/COMPLETED.json')['status']=='PASS_NATIVE_ONLY_PALANTIR_DOCUMENTED_EXAMPLE'
    assert get('workflow-original-1/COMPLETED.json')['status']=='FAIL2_PALANTIR_FULL_WORKFLOW_ORIGINAL_REFERENCES';workflow=get('workflow-original-2/COMPLETED.json');assert workflow['status']=='PASS_TWO_FULL_ORIGINAL_PALANTIR_WORKFLOW_DISCOVERY_FAMILIES_ONLY' and workflow['all_runtime_trees_unchanged'] and all(not v['owned_live_after_cleanup'] and v['exit_code']==0 for v in workflow['processes'])
    for edition,report in workflow['reports'].items():
        assert report['successful_original_cases']==(7 if edition=='1.4.4' else 8) and report['expected_original_errors']==3 and report['partial_identifier_dispositions']==1 and report['source_warning_filters_preserved'] and not report['analysis_admitted'] and not report['namespace_functions_patched'] and not report['pickle_loaded']
        for case in report['cases']:
            home='workflow-original-2/'+edition+'/'+case['id']+'/';assert get(home+'global-rng-before.json')['kind']=='MT19937' and get(home+'global-rng-after.json')['kind']=='MT19937'
            if case['status']=='complete-original-result-only':assert home+'observed-before-result-clipping.json' in {n.removeprefix(base) for n in files if n.startswith(base)}
    observations=get('workflow-observations-1/COMPLETED.json');assert observations['status']=='PASS_COMPLETE_PALANTIR_PRE_POST_CLIPPING_AND_ERROR_OBSERVATIONS_ONLY' and len(observations['cases'])==15 and len(observations['original_errors'])==6 and (observations['numeric_probability_states'],observations['clipped_probability_states'],observations['returned_rows_below_unit_mass'])==(2688,503,185) and not observations['independent_trajectory_refit'];assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged'];artifact=ROOT/p['artifact']['path']
    if artifact.exists():assert pin(artifact)=={k:p['artifact'][k] for k in ['bytes','sha256']}
    if emit:print('Verified native Palantir coordinate component:42 complete cases/17627 exact states per edition/JVM, three JVMs, source errors/deferrals, four setup skips, seven corruptions and15 original full-workflow observations. Full adapter/catalog application/goal remain open.')
    return p

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/component evidence checks')
    verify()
