# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the source-bound runtime/original-workflow/native foundation without crediting an unfinished adapter."""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

STATUS='passed-four-edition-GSEApy-runtime-original-class-workflows-and-native-foundation-only'
BASE='build/literature-review/gseapy-runtime/'

def verify(emit=True,folder=None):
    folder=ROOT/'docs/literature/gseapy-workflow-foundation' if folder is None else Path(folder);p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']==STATUS
    assert not any(p[k] for k in ['implementation_credit','full_gseapy_adapter_admitted','analysis_command_available','whole_package_implemented','whole_paper_reproduced','whole_goal_complete','scientific_source_exception_added'])
    assert pin(folder/'controls.zip')==p['archive'];files=unpack(folder/'controls.zip',p)
    for name,want in p['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==want,name
    for name,want in p['parent_proofs'].items():assert pin(ROOT/name)==want,name
    from gseapy_statistics_evidence import verify as parent
    prior=parent(False);assert p['parent_artifact']==prior['artifact'] and p['unchanged_parent_entries']==21366
    get=lambda n:json.loads(files[BASE+n]);built=get('workflow-build-7/COMPLETED.json');assert built['status']=='passed-compiled-GSEApy-runtime-transport-and-export-foundation-only' and built['artifact']==p['artifact'] and built['parent_artifact']==p['parent_artifact']
    assert get('workflow-build-7/compile.json')['exit_code']==0 and '-Werror' in get('workflow-build-7/compile.json')['command']
    source=get('source-intake-1/SOURCE.json');backend=get('backend-source-1/SOURCE.json');assert set(source['versions'])==set(backend['versions'])=={'1.0.4','1.1.2','1.1.3','1.1.8'}
    for edition,profile in backend['versions'].items():
        archive=files[BASE+'backend-source-1/'+edition+'/'+profile['archive']['filename']];assert byte_pin(archive)=={k:profile['archive'][k] for k in ['bytes','sha256']}
        for name,want in profile['members'].items():assert byte_pin(files[BASE+'backend-source-1/'+edition+'/source/'+name])==want,name
    resources='src/main/resources/org/jlinalg/omics/gseapy-workflow/';runtime_profile=json.loads(files['current-source/'+resources+'profiles.json']);worker_profile=json.loads(files['current-source/'+resources+'worker-profile.json'])
    for profile in [runtime_profile,worker_profile]:
        for name,want in profile['files'].items():assert byte_pin(files['current-source/'+resources+name])==want,name
    setup=get('workflow-setup-1/COMPLETED.json');assert setup['status']=='PASS_BOUND_ORIGINAL_GSEAPY_WORKFLOW_RUNTIME_ONLY' and setup['runtime_trees_unchanged'] and setup['shared_inputs_unchanged'] and not setup['namespace_patched'] and not setup['analysis_admitted']
    assert setup['profile']==byte_pin(files['current-source/'+resources+'profiles.json'])
    assert get('workflow-setup-1/base-before.json')==get('workflow-setup-1/base-after.json') and get('workflow-setup-1/runtime-before.json')==get('workflow-setup-1/runtime-after.json')
    assert set(setup['health']['editions'])==set(source['versions']) and all(h['status']=='PASS_COMPLETE_ORIGINAL_GSEAPY_EDITION_ONLY' and not h['namespace_patched'] for h in setup['health']['editions'].values())
    original=get('workflow-original-4/COMPLETED.json');assert original['status']=='passed-33-complete-original-GSEApy-workflow-controls-only' and original['total_original_cases']==33 and original['complete_runtime_trees_unchanged']
    assert get('workflow-original-4/base-before.json')==get('workflow-original-4/base-after.json')==get('workflow-setup-1/base-before.json')
    assert get('workflow-original-4/runtime-before.json')==get('workflow-original-4/runtime-after.json')==get('workflow-setup-1/runtime-before.json')
    for report in [built,original]:
        for name,want in report['source_pins'].items():assert pin(ROOT/name)==want,name
    for edition,report in original['editions'].items():
        assert report['exit_code']==0 and report['report']['status']=='PASS_COMPLETE_ORIGINAL_GSEAPY_WORKFLOWS_ONLY' and not report['report']['result_admitted'] and not report['report']['namespace_patched'] and not report['process']['owned_live_after_cleanup']
        request=get('workflow-original-4/'+edition+'/request.json')
        for case in request['analyses']:
            assert case['interface']=='original-class';home='workflow-original-4/'+edition+'/'+case['id']+'/'
            effective=get(home+'effective-options-before-run.json');assert effective['interface']=='original-class' and effective['requested']==case['options']
            if case['method']=='ssgsea':assert get(home+'original-return.json')['permutation_num']==0
            assert files[BASE+'workflow-original-4/'+edition+'/worker.py']==files['current-source/'+resources+'worker.py']
    assert get('workflow-original-2/COMPLETED.json')['status']=='FAIL2_ORIGINAL_GSEAPY_CONTROL_BATCH' and get('workflow-original-3/COMPLETED.json')['status']=='FAIL2_ORIGINAL_GSEAPY_CONTROL_BATCH'
    assert b'not proof' in files[BASE+'workflow-original-1/LEGACY_WRAPPER_BEHAVIOR.md'] and get('workflow-pilot-1/COMPLETED.json')['diagnostic_pilot_without_scientific_admission']
    native=get('workflow-ranked-native-4/COMPLETED.json');assert native['status']=='passed-three-JVM-original-ranked-and-null-arithmetic-only' and native['artifact']==p['artifact'] and len(native['runs'])==3
    relations=get('workflow-ranked-native-4/original-function-relationships.json');assert relations['status']=='passed-shared-original-arithmetic-bodies-after-comment-whitespace-normalization'
    for name in relations['functions']['1.0.4']:assert len({v[name]['comment_whitespace_normalized_sha256'] for v in relations['functions'].values()})==1
    assert relations['edition_specific_callers']['1.1.8']['phenotype_dense_metric']=='absolute-power-before-walk'
    assert all(relations['edition_specific_callers'][v]['phenotype_dense_metric']=='raw-signed-walk-with-separately-weighted-ES' for v in ['1.0.4','1.1.2','1.1.3'])
    for run in native['runs']:
        audit=run['audit'];assert (audit['ranked_cases'],audit['walks'],audit['null_families'],audit['single_sample_families'],audit['numeric_states'])==(29,228,12,8,3444)
        assert run['contracts']['invalid_inputs_rejected']==13 and run['contracts']['missing_sign_FWER_NaN_retained'] and not audit['full_adapter_admitted']
    assert {r['jvm'] for r in native['runs']}=={'windows21','windows25','linux21'}
    contracts=get('workflow-setup-contracts-1/COMPLETED.json');assert contracts['status']=='passed-three-JVM-exact-export-and-forbidden-install-skip-only' and len(contracts['checks'])==10 and contracts['artifact']==p['artifact']
    skip=get('workflow-setup-contracts-1/forbidden-install/receipts/COMPLETED.json');assert skip['status']=='SKIP3_GSEAPY_RUNTIME_UNAVAILABLE' and not skip['analysis_admitted'] and 'forbidden' in skip['error']
    for report in [native,contracts]:
        for name,want in report['source_pins'].items():assert pin(ROOT/name)==want,name
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged']
    if emit:print('Verified four-edition runtime,33 original-class controls,228 native walks/12 null/8 ssGSEA families and3444 numeric states/JVM;13 native rejections and10 setup contracts. Analysis CLI and complete adapter remain unfinished; no catalog credit.')
    return p

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable foundation evidence gates')
    verify()
