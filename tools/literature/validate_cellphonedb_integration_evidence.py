# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit the scoped5.0.1 integration from exact prior execution/native-audit evidence; never changes that checkpoint or grants paper parity."""
import contextlib,io,json,zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import byte_pin


def verify(emit=True):
    import cellphonedb_workflow_evidence as original
    with contextlib.redirect_stdout(io.StringIO()):original.main()
    folder=ROOT/'docs/literature/cellphonedb-workflow-development';proof=json.loads((folder/'VALIDATION.json').read_bytes());assert proof['implementation_credit'] is False and proof['catalog_applied'] is False and proof['whole_goal_complete'] is False and proof['pickle_deserialized'] is False;assert proof['unchanged_parent_entries']==20934;assert proof['controls']['paired_cases']==19 and proof['controls']['deterministic_serial_complete_bytes_and_rng']==17 and proof['controls']['coherent_corruptions']==7 and proof['controls']['producer_protocols']==12 and proof['controls']['public_command_protocols']==6 and proof['controls']['export_platform_setup_protocols']==6
    prefix='build/literature-review/cellphonedb-runtime/'
    with zipfile.ZipFile(ROOT/proof['archive']['path']) as archive:
        names=set(archive.namelist())
        def raw(name):return archive.read(prefix+name)
        def get(name):return json.loads(raw(name))
        native=get('workflow-native-audit-6/COMPLETED.json');assert native['status']=='PASS_THREE_JVM_NATIVE_CELLPHONEDB_WORKFLOW_CONTROL_AUDITS_ONLY' and len(native['processes'])==3
        for process in native['processes']:
            audit=process['audit'];assert process['exit_code']==0 and len(audit['cases'])==19 and audit['numeric_cells']>0 and audit['identity_states']>0 and audit['tsv_cells']>0 and all(c['status']=='PASS_NATIVE_PREPARATION_OBSERVED_KERNELS_AND_COMPLETE_SOURCE_TABLES' for c in audit['cases'])
        paired=get('safe-worker-controls-3/COMPLETED.json');assert paired['runtime_unchanged'] and paired['source_inputs_unchanged'] and not paired['original_namespace_patched'] and not paired['pickle_deserialized'];assert all(p['exit_code']==0 and not p['owned_live_after_cleanup'] for p in paired['processes'])
        protocols=get('safe-protocol-controls-2/COMPLETED.json');assert protocols['status']=='PASS_TWELVE_SAFE_CELLPHONEDB_PROTOCOL_CONTROLS_ONLY' and protocols['runtime_unchanged'] and protocols['source_inputs_unchanged'] and len(protocols['protocols'])==12;assert all(not p['owned_live_after_cleanup'] and not p['original_process']['owned_live_after_cleanup'] for p in protocols['protocols']);fallback=next(p for p in protocols['protocols'] if p['name']=='subsampling-fallback-explicit')['explicit_full_count_fallback'];assert fallback['source_errors'] and fallback['input_cells']==fallback['returned_cells']
        packaged=get('workflow-packaged-validation-2/COMPLETED.json');assert packaged['status']=='PASS_SIX_PACKAGED_CELLPHONEDB_WORKFLOW_CONTROLS_ONLY' and packaged['runtime_unchanged'] and packaged['source_inputs_unchanged'];assert {p['name']:p['exit_code'] for p in packaged['processes']}=={'complete19':0,'missing-runtime-skip3':3,'reported-edition-unavailable-skip3':3,'atomic-later-case-failure':2,'runtime-profile-corruption-fail2':2,'changed-leading-zero-labels-fail2':2}
        for process in packaged['processes']:
            assert not process['java_alive_after_cleanup'];status=process['analysis_status'];assert status['result_admitted']==(process['exit_code']==0)
        complete=get('workflow-packaged-validation-2/complete19/analysis/analysis-status.json');assert complete['result_admitted'] and complete['atomic_batch'] and complete['edition']=='5.0.1' and len(complete['cases'])==19
        manifest=get('workflow-packaged-validation-2/complete19/analysis/manifest.json');root=prefix+'workflow-packaged-validation-2/complete19/analysis/';verified=0;omitted=[]
        for name,want in manifest['files'].items():
            member=root+name
            if member not in names:
                assert any(part in ['private-cache','private-temp','private-home','private-config'] for part in name.split('/')) or name.endswith('.pyc'),('Uncaptured scientific output',name);omitted.append(name);continue
            assert byte_pin(archive.read(member))==want,name;verified+=1
        assert verified>100
        atomic=get('workflow-packaged-validation-2/atomic-later-case-failure/analysis/worker/COMPLETED.json');assert atomic['status']=='FAIL2_ORIGINAL_CELLPHONEDB_BATCH_UNADMITTED' and not atomic['result_admitted'] and len(atomic['earlier_cases_provisional'])==1
        for name in ['workflow-packaged-validation-1','safe-protocol-controls-1','workflow-windows-protocols-1','workflow-native-audit-1','workflow-native-audit-2','workflow-native-audit-3','workflow-native-audit-4']:
            assert get(name+'/COMPLETED.json')['status'].startswith('FAIL2'),name
        build=get('workflow-build-6/COMPLETED.json');assert build['artifact']==proof['artifact'] and build['parent_artifact']==proof['parent_artifact'] and build['unchanged_parent_entries']==20934
    result=dict(status='PASS_SCOPED_CELLPHONEDB_501_INTEGRATION_EVIDENCE',implementation_credit=True,admitted_named_tools=['cellphonedb'],artifact=proof['artifact'],scientific_checkpoint_unchanged=pin(folder/'VALIDATION.json'),public_cases=19,native_audit_cases_per_jvm=19,native_jvms=3,paired_complete_serial_cases=17,verified_public_manifest_files=verified,omitted_private_cache_files=len(omitted),catalog_roles_applied=False,whole_paper_reproduced=False,whole_goal_complete=False)
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/atomic/native evidence checks')
    verify()
