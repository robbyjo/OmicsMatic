# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify full retained LeafCutter workflow/setup evidence without installing software or rerunning original analysis."""
import json,sys,zipfile
from pathlib import Path
from signac_scan_evidence import byte_pin,strict_json,unpack
from audit_leafcutter_workflow_outputs import audit

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/leafcutter-workflow-development'


class RetainedPath:
    def __init__(self,files,name):self.files=files;self.name=name
    def __truediv__(self,name):return RetainedPath(self.files,self.name+'/'+str(name))
    def read_bytes(self):return self.files[self.name]


def verify_files(proof,files,root=ROOT):
    obj=lambda name:strict_json(files[name])
    assert proof['status']=='passed-original-LeafCutter-workflows-and-private-setup-with-complete-audits' and proof['adapter_admitted'] is True and proof['implementation_credit'] is True and proof['admitted_named_tools']==['leafcutter'] and proof['whole_goal_complete'] is False and proof['whole_paper_reproduced'] is False and proof['released_or_deployed'] is False
    for name,want in proof['source_pins'].items():assert byte_pin((root/name).read_bytes())==byte_pin(files['current-source/'+name])==want,name
    build=obj('workflow-build-3/COMPLETED.json');assert build['status']=='compiled-complete-LeafCutter-workflow-not-runtime-validation' and build['artifact']==proof['artifact'] and build['unchanged_parent_entries']==20799 and build['canonical_executable_unchanged']==proof['canonical_executable_unchanged'];assert build['parent_artifact']==json.loads((root/'docs/literature/leafcutter-models-development/VALIDATION.json').read_bytes())['artifact']
    for name,want in build['source_pins'].items():assert proof['source_pins'][name]==byte_pin(files['workflow-build-3/source-snapshot/'+name])==want
    assert set(proof['packaged_entries'])==set(build['added_entries']) and len(build['added_entries'])==97
    for name,want in proof['packaged_entries'].items():assert byte_pin(files['candidate-added-entries/'+name])==want
    resource='src/main/resources/org/jlinalg/omics/leafcutter-workflow/';profile=obj('current-source/'+resource+'profiles.json');workers=obj('current-source/'+resource+'worker-profile.json');assert profile['revision']==workers['revision']=='2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb' and profile['R_package_version']=='0.2.9' and len(profile['files'])==74 and len(workers['files'])==4
    for name,want in {**profile['files'],**workers['files']}.items():assert byte_pin(files['current-source/'+resource+name])==want
    # The full original package/source provenance remains bound by its frozen
    # component proof; no old scientific hashes are refreshed for this adapter.
    from leafcutter_models_evidence import verify as verify_models
    verify_models(root=root,data=root/'docs/literature/leafcutter-models-development',emit=False)
    parent_proof=json.loads((root/'docs/literature/leafcutter-models-development/VALIDATION.json').read_bytes());parent=unpack(root/'docs/literature/leafcutter-models-development/controls.zip',parent_proof['packing'])
    original=parent['R-source/COMPLETED.json'];source=strict_json(original)['editions']['source-2c9907ef66ad'];assert profile['source_members']==source['members']
    for name,want in profile['source_members'].items():assert byte_pin(files['current-source/'+resource+'original/'+name])=={k:want[k] for k in ['bytes','sha256']}
    cluster_dir=root/'docs/literature/leafcutter-clusters-development';cluster_proof=json.loads((cluster_dir/'VALIDATION.json').read_bytes());assert byte_pin((cluster_dir/'controls.zip').read_bytes())==cluster_proof['archive'];cluster_files=unpack(cluster_dir/'controls.zip',cluster_proof['packing'])
    for name in ['scripts/leafcutter_ds.R','scripts/prepare_phenotype_table.py','clustering/leafcutter_cluster_regtools.py']:assert files['current-source/'+resource+'original/'+name]==cluster_files['source-intake/source-2c9907ef66ad/source/'+name]
    locks=obj('current-source/'+resource+'setup/dependency-locks.json')['packages'];assert len(locks)==239 and len({r['name'] for r in locks})==239
    for home,kit in [('workflow-runtime-capture-1','retained-kit-source'),('workflow-runtime-capture-2','kit-source')]:
        historical=obj(home+'/COMPLETED.json');saved_profile=obj(home+'/'+kit+'/profiles.json');assert historical['status']=='PASS_BOUND_ORIGINAL_LEAFCUTTER_WORKFLOW_RUNTIME_ONLY' and byte_pin(files[home+'/'+kit+'/profiles.json'])==historical['profile'] and saved_profile['revision']==profile['revision']
        for name,want in saved_profile['files'].items():assert byte_pin(files[home+'/'+kit+'/'+name])==want
        for name in ['runtime','library']:assert obj(home+'/'+name+'-before.json')==obj(home+'/'+name+'-after.json')
    for home in ['workflow-runtime-capture-3','installed-setup']:
        state=obj(home+'/COMPLETED.json');assert state['status']=='PASS_BOUND_ORIGINAL_LEAFCUTTER_WORKFLOW_RUNTIME_ONLY' and state['runtime_trees_unchanged'] is True and state['namespace_patched'] is False and state['analysis_executed'] is False
        local_profile=obj(home+'/kit-source/profiles.json');assert state['profile']==byte_pin(files[home+'/kit-source/profiles.json']) and local_profile['revision']==profile['revision']
        for name,want in local_profile['files'].items():assert byte_pin(files[home+'/kit-source/'+name])==want
        for name in ['runtime','library']:assert obj(home+'/'+name+'-before.json')==obj(home+'/'+name+'-after.json') and byte_pin(files[home+'/'+name+'-before.json'])==state[name+'_inventory']
        records=obj(home+'/complete-conda-package-records.json');assert {(r['name'],r['version'],r['build'],r['sha256']) for r in records}=={(r['name'],r['version'],r['build'],r['sha256']) for r in locks}
        health=state['health'];assert health['status']=='PASS_ORIGINAL_LEAFCUTTER_NAMESPACE_AND_LOSSLESS_EXPORT_ONLY' and len(health['namespace_functions_checked'])==24 and health['lossless_roundtrip'] and health['rstan']=='2.21.8' and health['jsonlite']=='1.8.8';assert b'length(get("stanmodels",ns))==5' in files[home+'/kit-source/setup/health.R']
        for process in state['processes']:assert process['exit_code']==0 and process['owned_live_after_cleanup']==[]
    installed=obj('installed-setup/COMPLETED.json');assert installed['mode']=='INSTALL' and installed['profile']==byte_pin(files['current-source/'+resource+'profiles.json']) and installed['shared_installer_unchanged'] is True and all(r['case_sensitive'] for r in installed['filesystem_checks']) and len(installed['filesystem_checks'])==2
    installed_tree=obj('installed-setup/runtime-before.json')
    for left,right in [('netfilter_ipv4/ipt_TTL.h','netfilter_ipv4/ipt_ttl.h'),('netfilter_ipv6/ip6t_HL.h','netfilter_ipv6/ip6t_hl.h')]:
        a=installed_tree['x86_64-conda-linux-gnu/sysroot/usr/include/linux/'+left];b=installed_tree['x86_64-conda-linux-gnu/sysroot/usr/include/linux/'+right];assert a['kind']==b['kind']=='file' and a['sha256']!=b['sha256']
    assert obj('workflow-filesystem-skip-1/COMPLETED.json')['status']=='PASS_PACKAGED_LEAFCUTTER_CASE_COLLISION_SKIP_BEFORE_INSTALL_ONLY';skipped=obj('workflow-filesystem-skip-1/preparation/COMPLETED.json');assert skipped['status']=='SKIP3_LEAFCUTTER_RUNTIME_UNAVAILABLE' and skipped['processes']==[] and skipped['analysis_executed'] is False and skipped['filesystem_checks'][0]['case_sensitive'] is False
    assert obj('workflow-install-1/COMPLETED.json')['status']=='SKIP3_LEAFCUTTER_RUNTIME_UNAVAILABLE' and obj('workflow-install-1/CASE-COLLISION-STOP.json')['status'].startswith('STOP')
    failed_install=files['workflow-install-1/dependencies-install.stderr'];fresh_install=files['installed-setup/dependencies-install.stderr'].lower();assert b'Invalid package cache' in failed_install and b'has incorrect size' in failed_install and b'/ipt_TTL.h' in failed_install and b'/ipt_ttl.h' in failed_install
    assert all(marker not in fresh_install for marker in [b'invalid package cache',b'incorrect size',b'corrupted',b'already present in the environment'])
    cached=obj('workflow-archives-1/COMPLETED.json');assert cached['status']=='PASS_EXACT_FLAT_CACHED_LEAFCUTTER_DEPENDENCY_ARCHIVES_ONLY' and all(cached[k] is False for k in ['software_installed','software_downloaded','original_cache_modified']) and len(cached['archives'])==239
    assert {(r['filename'],r['bytes'],r['sha256']) for r in cached['archives']}=={(r['filename'],r['bytes'],r['sha256']) for r in locks}
    assert obj('workflow-original-1/COMPLETED.json')['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_FULL_WORKFLOW_CONTROLS_ONLY' and obj('workflow-original-1/COMPLETED.json')['runtime_trees_unchanged'] is True
    checks={}
    for home in ['workflow-validation-1','workflow-validation-2']:
        state=obj(home+'/COMPLETED.json');assert state['status']=='PASS_COMPLETE_PUBLIC_LEAFCUTTER_WORKFLOW_REFERENCE_AND_NATIVE_AUDITS_ONLY' and state['checks']['cases']==8 and state['checks']['complete_original_table_comparisons']==6
        result=home+'/analysis';manifest=obj(result+'/manifest.json');assert {n.removeprefix(result+'/') for n in files if n.startswith(result+'/')}==set(manifest['files'])|{'analysis-status.json','manifest.json'}
        checks[home]=audit(RetainedPath(files,result));assert checks[home]['native_audit_operations']==206829 and checks[home]['complete_manifest_files']==306
        for case in obj(result+'/request.original.json')['analyses']:
            if case['kind']=='differential' or 'differential' in case:
                for name in ['original_cluster_significance.txt','original_effect_sizes.txt']:assert files[result+'/worker/'+case['id']+'/differential/'+name]==files['workflow-original-1/worker/'+case['id']+'/differential/'+name]
    assert obj('workflow-validation-2/COMPLETED.json')['artifact']==proof['artifact'] and obj('workflow-validation-2/analysis/worker/runtime.original.json')==installed
    for home in ['workflow-protocols-1','workflow-protocols-2']:
        state=obj(home+'/COMPLETED.json');assert state['status']=='PASS_COMPLETE_PUBLIC_LEAFCUTTER_FAILURE_SKIP_AND_ATOMIC_PROTOCOLS_ONLY' and len(state['checks'])==9
        for name,code in [('unsupported-windows21',3),('unsupported-windows25',3),('unsupported-edition-no-fallback',3),('missing-runtime',3),('late-original-error-atomic-batch',2),('owned-timeout-cleanup',2)]:
            marker=obj(home+'/'+name+'/analysis/analysis-status.json');assert marker['result_admitted'] is False and marker['status'].startswith('SKIP3' if code==3 else 'FAIL2') and home+'/'+name+'/analysis/manifest.json' not in files
        for name in ['late-original-error-atomic-batch','owned-timeout-cleanup']:
            for log in ['original.process.json','java-process.json']:assert obj(home+'/'+name+'/analysis/worker/'+log)['owned_live_after_cleanup']==[]
        assert obj(home+'/late-original-error-atomic-batch/analysis/worker/earlier-success/COMPLETED.json')['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_CASE_ONLY';denied=obj(home+'/installation-not-authorized/COMPLETED.json');assert denied['status']=='SKIP3_LEAFCUTTER_RUNTIME_UNAVAILABLE' and denied['processes']==[] and denied['analysis_executed'] is False
    assert obj('workflow-protocols-2/COMPLETED.json')['artifact']==proof['artifact']
    contracts=obj('workflow-input-contracts-1/COMPLETED.json');assert contracts['status']=='PASS_PUBLIC_LEAFCUTTER_EXPLICIT_SAMPLE_ID_AND_RATIO_HEADER_REJECTIONS_ONLY' and contracts['artifact']==proof['artifact'] and len(contracts['checks'])==3
    for check in contracts['checks']:
        home='workflow-input-contracts-1/'+check['id'];marker=obj(home+'/analysis/analysis-status.json');assert check['exit_code']==2 and check['original_program_executed'] is False and marker['result_admitted'] is False and marker['status'].startswith('FAIL2') and not any(n.startswith(home+'/analysis/worker/') for n in files)
        for name,want in check['input_pins'].items():assert byte_pin(files[home+'/'+name])==want
    chain=obj('workflow-installed-validation-chain-1/COMPLETED.json');assert chain['status']=='PASS_FRESH_PACKAGED_LEAFCUTTER_RUNTIME_SCIENCE_PROTOCOL_AND_RETAINED_EQUATIONS_ONLY' and chain['installation_restarted'] is False and chain['installation_repaired'] is False and len(chain['processes'])==3 and all(r['exit_code']==0 for r in chain['processes']) and chain['runtime_receipt']==byte_pin(files['installed-setup/COMPLETED.json'])
    for home in ['workflow-output-audit-3','workflow-output-audit-4']:assert obj(home+'/COMPLETED.json')['status']=='PASS_COMPLETE_RETAINED_LEAFCUTTER_WORKFLOW_OUTPUT_AND_EQUATIONS_ONLY'
    assert obj('workflow-output-audit-3/COMPLETED.json')['checks']==checks['workflow-validation-1'] and obj('workflow-output-audit-4/COMPLETED.json')['checks']==checks['workflow-validation-2']
    assert obj('workflow-build-1/compile.json')['exit_code']!=0 and obj('workflow-output-audit-1/COMPLETED.json')['status'].startswith('FAIL2') and obj('workflow-output-audit-2/COMPLETED.json')['status'].startswith('FAIL2')
    assert obj('workflow-seal-attempt-1/process.json')['exit_code']!=0 and obj('workflow-seal-attempt-1/SNAPSHOT-RETENTION.json')['scientific_inputs_and_results_changed'] is False and byte_pin(files['workflow-seal-attempt-1/sealer-at-attempt.py'])['sha256']==obj('workflow-seal-attempt-1/STARTED.json')['source_sha256']
    bridge=obj('setup-evolution.json');assert bridge['status']=='PASS_EXACT_TWO_SETUP_MEMBER_EVOLUTION_ALL_ANALYSIS_BYTES_PRESERVED' and set(bridge['changed_entries'])=={'org/jlinalg/omics/leafcutter-workflow/profiles.json','org/jlinalg/omics/leafcutter-workflow/setup/prepare.py'} and bridge['before']==obj('workflow-build-2/COMPLETED.json')['artifact'] and bridge['after']==proof['artifact']
    assert byte_pin(files['workflow-build-2/source-snapshot/'+resource+'profiles.json'])==obj('workflow-runtime-capture-3/COMPLETED.json')['profile']
    return dict(status='passed-retained-original-LeafCutter-workflows-and-setup',checks=checks,logical_files=len(files),implementation_credit=True,admitted_named_tools=['leafcutter'],whole_goal_complete=False,whole_paper_reproduced=False)


def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Never disable complete retained workflow checks')
    proof=strict_json((data/'VALIDATION.json').read_bytes());assert byte_pin((data/'controls.zip').read_bytes())==proof['archive']
    with zipfile.ZipFile(data/'controls.zip') as z:assert len(z.namelist())<30000 and sum(i.file_size for i in z.infolist())<536870912
    result=verify_files(proof,unpack(data/'controls.zip',proof['packing']),root)
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':
    try:verify()
    except Exception as failure:
        print('LEAFCUTTER WORKFLOW EVIDENCE FAIL2: '+repr(failure)+'\nRequired action: preserve complete original/native files, binary fits, source/runtime bindings, setup/compiler errors and failed attempts. Correct the named mismatch and retry NEW paths. Do not refresh frozen scientific hashes, change source values, omit cases or admit a partial proof.',file=sys.stderr);raise SystemExit(2)
