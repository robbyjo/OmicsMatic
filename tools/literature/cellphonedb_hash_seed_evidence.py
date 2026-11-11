# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify additive effective-startup evidence without changing first-generation proofs or credit."""
import json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from prepare_cellphonedb_hash_seed import verify as verify_derivation
from prepare_cellphonedb_hash_seed_validators import verify as verify_validators

def verify(emit=True):
    folder=ROOT/'docs/literature/cellphonedb-hash-seed-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-additive-CellPhoneDB-effective-python-startup-runner-only' and p['runner_revision']==2 and not p['new_catalog_integration_credit'] and not p['whole_goal_complete'] and not p['first_generation_runs_relabelled'];assert pin(folder/'controls.zip')==p['archive'];files=unpack(folder/'controls.zip',p);verify_derivation();verify_validators()
    for name,want in p['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==want,name
    for name,want in p['parent_proofs'].items():assert pin(ROOT/name)==want,name
    base='build/literature-review/cellphonedb-runtime/';get=lambda name:json.loads(files[base+name]);built=get('hash-seed-build-3/COMPLETED.json');assert built['status']=='PASS_COMPILED_CELLPHONEDB_HASH_SEED_DEVELOPMENT_ONLY' and built['artifact']==p['artifact'] and built['unchanged_parent_entries']==21068;assert get('hash-seed-build-2/COMPLETED.json')['status']=='FAIL2_CELLPHONEDB_HASH_SEED_BUILD'
    original=get('hash-seed-controls-1/COMPLETED.json');assert original['status']=='PASS_FRESH_EFFECTIVE_SEED_CELLPHONEDB_CONTROLS_ONLY' and original['cases']==19 and original['serial_complete_byte_and_rng_comparisons']==17 and original['runtime_unchanged'] and original['source_inputs_unchanged'] and not original['pickle_deserialized'] and not original['original_namespace_patched'];comparison=get('hash-seed-controls-1/comparison.json');assert len(comparison['checks'])==19 and sum(c['all_returned_fields_and_TSV_bytes_exact'] for c in comparison['checks'])==17 and comparison['complete_original_pvalue_equation_cells']>0
    receipts=original['startup_receipts'];assert len(receipts)==6
    for i,seed in [(0,0),(2,123),(4,4294967295)]:
        assert receipts[i]['receipt']==receipts[i+1]['receipt'] and receipts[i]['receipt']['requested_seed']==seed;flags=receipts[i]['receipt']['flags'];assert flags==dict(ignore_environment=0,isolated=0,safe_path=True,no_user_site=1,hash_randomization=0 if seed==0 else 1,optimize=0,dont_write_bytecode=1)
    native=get('hash-seed-native-1/COMPLETED.json');assert native['status']=='PASS_THREE_JVM_EFFECTIVE_SEED_AND_FRESH_WORKFLOW_AUDITS_ONLY' and native['artifact']==p['artifact'] and len(native['processes'])==12 and all(r['exit_code']==0 for r in native['processes'])
    for jvm in ['windows21','windows25','linux21']:
        assert len(get('hash-seed-native-1/'+jvm+'/native-audit.json')['cases'])==19
        for seed in [0,123,4294967295]:
            audit=get('hash-seed-native-1/'+jvm+'/startup-'+str(seed)+'.json');assert len(audit['rejections'])==22 and all(r['rejected'] for r in audit['rejections']) and audit['missing_receipt_rejected'] and audit['valid']['requested_seed']==seed and audit['valid']['independent_native_seed0_hashes']==(seed==0)
    startup=get('hash-seed-startup-2/COMPLETED.json');assert startup['status']=='PASS_INDEPENDENT_EFFECTIVE_STARTUP_BASELINES_AND_CANCELLATION_ONLY' and startup['source_inputs_unchanged'] and startup['private_bytecode_control_files']>0 and startup['startup_modes_rejected_before_scientific_imports']==8 and not startup['owned_cancellation']['owned_live_after_cleanup']
    invalid=get('hash-seed-startup-1/INVALIDATED.json');assert invalid['status']=='INVALIDATED_STARTUP_HARNESS_RUNTIME_SIDE_EFFECT' and not invalid['analysis_admitted'];assert byte_pin(files[base+'hash-seed-startup-1/collector-before-private-pycache.py'])==invalid['source_before'];assert get('hash-seed-packaged-1/COMPLETED.json')['status']=='FAIL2_PACKAGED_CONTROL_RUNTIME_CHANGED';assert get('hash-seed-protocols-1/COMPLETED.json')['status']=='FAIL2_SAFE_CELLPHONEDB_PROTOCOL_CONTROLS'
    recovery=get('hash-seed-cache-quarantine-1/COMPLETED.json');assert recovery['status']=='PASS_QUARANTINED_PROVED_NEW_CACHE_AND_RESTORED_EXACT_BOUND_RUNTIME' and recovery['restored_inventory_exact'] and not recovery['receipt_refreshed'] and not recovery['original_members_modified_or_removed'];assert byte_pin(files[base+'hash-seed-cache-quarantine-1/quarantined-bytecode.raw'])==recovery['quarantined_bytes'];assert get('hash-seed-cache-quarantine-1/after.json')==get('hash-seed-controls-1/runtime-before.json')
    packaged=get('hash-seed-packaged-2/COMPLETED.json');assert packaged['status']=='PASS_EIGHT_PACKAGED_CELLPHONEDB_HASH_SEED_CONTROLS_ONLY' and packaged['runtime_unchanged'] and packaged['source_inputs_unchanged'] and len(packaged['processes'])==8;public_files=0
    for process in packaged['processes']:
        name=process['name'];assert process['exit_code']==process['expected_exit'] and not process['java_alive_after_cleanup'];root=base+'hash-seed-packaged-2/'+name+'/analysis/';status=json.loads(files[root+'analysis-status.json']);assert status['result_admitted']==(process['exit_code']==0) and root+'WARNINGS.md' in files
        if process['exit_code']==0:
            assert status['runner_revision']==2 and status['python_startup_audit']['status']=='PASS_EFFECTIVE_PYTHON_STARTUP_ONLY';manifest=json.loads(files[root+'manifest.json']);assert byte_pin(files[root+'worker/python-startup.json'])==status['python_startup_pin'];observed=json.loads(files[root+'worker/python-startup.json']);expected=next(r['receipt'] for r in receipts if r['receipt']['requested_seed']==observed['requested_seed']);assert observed==expected,'Public worker startup differs from repeated interpreter baselines'
            for member,want in manifest['files'].items():assert byte_pin(files[root+member])==want;public_files+=1
            if name=='complete19':assert len(status['cases'])==19
            else:assert status['python_startup_audit']['requested_seed']==int(name.removeprefix('effective-seed-'))
        elif name=='atomic-later-case-failure':assert len(json.loads(files[root+'worker/COMPLETED.json'])['earlier_cases_provisional'])==1
    assert public_files==p['public_manifest_files_verified']
    protocols=get('hash-seed-protocols-2/COMPLETED.json');assert protocols['status']=='PASS_TWELVE_SAFE_CELLPHONEDB_PROTOCOL_CONTROLS_ONLY' and protocols['runtime_unchanged'] and protocols['source_inputs_unchanged'] and len(protocols['protocols'])==12 and all(not r['original_process']['owned_live_after_cleanup'] for r in protocols['protocols'])
    windows=get('hash-seed-windows-1/COMPLETED.json');assert windows['status']=='PASS_SIX_CELLPHONEDB_EXPORT_PLATFORM_SETUP_PROTOCOLS_ONLY' and len(windows['processes'])==6
    requests=get('hash-seed-request-1/COMPLETED.json');assert requests['status']=='PASS_SEVEN_PUBLIC_HASH_SEED_REQUEST_REJECTIONS_ONLY' and len(requests['processes'])==7 and all(r['exit_code']==2 and not r['analysis_directory_created'] for r in requests['processes'])
    gates=get('hash-seed-predecessor-gates-1/COMPLETED.json');assert gates['status']=='passed-nineteen-current-and-predecessor-scientific-gates' and len(gates['checks'])==19 and not gates['frozen_scientific_sources_or_verifiers_changed'];assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged'];artifact=ROOT/p['artifact']['path']
    if artifact.exists():assert pin(artifact)=={k:p['artifact'][k] for k in ['bytes','sha256']}
    if emit:print('Verified revision2 effective hash startup,19 fresh paired workflows, three JVMs,22 receipt corruptions per seed,8 public runs,12 producer protocols, preserved cache-harness failures/recovery and19 predecessor gates. No new tool or whole-goal credit.')
    return p

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact effective-startup evidence checks')
    verify()
