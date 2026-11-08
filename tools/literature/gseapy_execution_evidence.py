# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete declared GSEApy execution evidence while retaining the immutable foundation proof."""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

STATUS='passed-four-edition-complete-declared-GSEApy-workflows-and-atomic-native-admission'
BASE='build/literature-review/gseapy-runtime/'


def verify(emit=True,folder=None):
    folder=ROOT/'docs/literature/gseapy-execution-development' if folder is None else Path(folder);p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']==STATUS and p['full_gseapy_adapter_admitted'] and p['analysis_command_available']
    assert not any(p[k] for k in ['implementation_credit','whole_package_implemented','whole_paper_reproduced','whole_goal_complete','scientific_source_exception_added','independent_permutation_generation','independent_gsva_refit'])
    assert pin(folder/'controls.zip')==p['archive'];files=unpack(folder/'controls.zip',p)
    for n,v in p['source_pins'].items():assert pin(ROOT/n)==byte_pin(files['current-source/'+n])==v,n
    for n,v in p['parent_proofs'].items():assert pin(ROOT/n)==v,n
    from gseapy_workflow_foundation_evidence import verify as parent
    prior=parent(False);assert p['parent_artifact']==prior['artifact'] and p['canonical_executable_unchanged']==prior['canonical_executable_unchanged'];assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged']
    get=lambda n:json.loads(files[BASE+n]);built=get('execution-build-4/COMPLETED.json');assert built['artifact']==p['artifact'] and built['parent_artifact']==p['parent_artifact'] and built['unchanged_parent_entries']==21400 and built['status']=='passed-compiled-additive-GSEApy-analysis-entry-sources-only'
    assert get('execution-build-4/compile.json')['exit_code']==0 and '-Werror' in get('execution-build-4/compile.json')['command']
    for n,v in built['source_pins'].items():assert p['source_pins'][n]==v
    profile=json.loads(files['current-source/src/main/resources/org/jlinalg/omics/gseapy-execution/worker-profile.json'])
    for n,v in profile['files'].items():assert byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/gseapy-execution/'+n])==v
    original=get('execution-cli-extended-2/COMPLETED.json');assert original['status']=='passed-ninety-complete-original-GSEApy-class-workflows-through-Java' and original['artifact']==p['artifact'] and set(original['runs'])=={'1.0.4','1.1.2','1.1.3','1.1.8'}
    totals=dict(cases=0,numeric_states=0,identity_states=0,tsv_states=0,native_walks=0,native_null_families=0)
    def admitted(home,count):
        status=get(home+'/analysis-status.json');audit=get(home+'/native-audit.json');assert status['status']=='PASS_ORIGINAL_GSEAPY_COMPLETE_BATCH' and status['result_admitted'] and status['atomic_batch'] and not status['paper_reproduced'] and len(status['cases'])==count and status['cases']==audit['cases'];manifest=get(home+'/manifest.json');assert byte_pin(files[BASE+home+'/manifest.json'])['sha256']==status['manifest_sha256']
        for n,v in manifest['files'].items():assert byte_pin(files[BASE+home+'/'+n])==v,(home,n)
        before=get(home+'/worker-before-native-audit.json')
        for n,v in before['files'].items():assert manifest['files']['worker/'+n]==v
        report=get(home+'/worker/COMPLETED.json');assert report['status']=='PASS_COMPLETE_ORIGINAL_GSEAPY_WORKFLOWS_ONLY' and not report['result_admitted'] and not report['namespace_patched'] and not report['analysis_installed_software'] and not report['pickle_deserialized'] and len(report['cases'])==count
        for n in ['original.process.json','java-process.json']:
            receipt=get(home+'/worker/'+n);assert receipt['exit_code']==0 and not receipt['owned_live_after_cleanup']
        request=get(home+'/request.original.json')
        for case,actual in zip(request['analyses'],status['cases']):
            assert case['interface']=='original-class' and case['id']==actual['id'] and case['method']==actual['method'];effective=get(home+'/worker/'+case['id']+'/effective-options-before-run.json');assert effective['requested']==case['options'] and effective['interface']=='original-class'
        assert not audit['independent_permutations'] and not audit['independent_gsva_kernel'];return audit
    for edition,report in original['runs'].items():
        count=18 if edition=='1.0.4' else 24;assert report['exit_code']==0 and report['status']==get('execution-cli-extended-2/'+edition+'/analysis-status.json');audit=admitted('execution-cli-extended-2/'+edition,count);totals['cases']+=count
        for key in ['numeric_states','identity_states','tsv_states']:totals[key]+=audit[key]
        for c in audit['cases']:
            for key in ['native_walks','native_null_families']:totals[key]+=c.get(key,0)
    assert totals==p['positive_totals'] and totals['cases']==90
    replay=get('execution-extended-replay-2/COMPLETED.json');assert replay['status']=='passed-three-JVM-90-complete-retained-GSEApy-native-audits-only' and replay['artifact']==p['artifact'] and not replay['original_batch_relabelled'] and {r['jvm'] for r in replay['runs']}=={'windows21','windows25','linux21'} and all(r['cases']==90 for r in replay['runs'])
    for r in replay['runs']:
        report=get('execution-extended-replay-2/'+r['jvm']+'/replay/COMPLETED.json');assert report['failed_cases']==0 and len(report['cases'])==90 and not report['original_execution_admitted']
    contracts=get('execution-contracts-2/COMPLETED.json');assert contracts['artifact']==p['artifact'] and contracts['status']=='passed-three-platform-export-skip-and-invalid-interface-contracts' and len(contracts['checks'])==18
    inputs=get('execution-input-rejections-1/COMPLETED.json');assert inputs['artifact']==p['artifact'] and inputs['status']=='passed-three-JVM-twelve-scientific-input-rejections-before-worker' and len(inputs['checks'])==36
    corrupt=get('execution-corruptions-2/COMPLETED.json');assert corrupt['artifact']==p['artifact'] and corrupt['status']=='passed-four-original-baselines-and-fifteen-altered-native-families' and len(corrupt['checks'])==19 and sum(c['exit_code']==2 for c in corrupt['checks'])==15
    failure=get('execution-failures-1/COMPLETED.json');assert failure['artifact']==p['artifact'] and failure['status']=='passed-requested-real-GSEApy-source-policy-and-supervision-controls' and len(failure['checks'])==7
    for name,r in failure['checks'].items():
        assert r['exit_code']==2 and not r['status']['result_admitted'] and not r['owned_live_after_cleanup'];status=get('execution-failures-1/'+name+'/analysis-status.json');assert status==r['status'] and status['status']=='FAIL2_NO_RESULT_ADMITTED'
        if name.startswith('late-'):assert r['earlier_case_preserved_unadmitted'] and files[BASE+'execution-failures-1/'+name+'/worker/earlier-complete/full-result.tsv']
        for n in ['original.process.json','java-process.json']:assert not get('execution-failures-1/'+name+'/worker/'+n)['owned_live_after_cleanup']
    assert get('execution-failures-1/cancellation-live-confirmation.json')['confirmed_live_before_cancellation']
    example=get('execution-example-2/COMPLETED.json');assert example['artifact']==p['artifact'] and example['status']=='passed-exact-six-gene-local-Enrichr-guide-example' and example['two_positive_overlap_terms'] and example['zero_overlap_term_excluded_before_BH'];admitted('execution-example-2/example/result',1)
    for r in [original,replay,contracts,inputs,corrupt,failure,example]:
        for n,v in r['source_pins'].items():assert p['source_pins'][n]==v,n
    # Original failures and the test-controller timeout remain historical failures.
    assert get('execution-cli-positive-1/COMPLETED.json')['status'].startswith('FAIL2') and get('execution-cli-extended-1/COMPLETED.json')['status'].startswith('FAIL2') and get('execution-extended-replay-1/COMPLETED.json')['status'].startswith('FAIL2')
    assert files[BASE+'execution-example-1/DIAGNOSTIC_FILENAME_COLLISION.md']
    if emit:print('Verified four-edition90-workflow atomic GSEApy execution, three-JVM90-case native replay,18 portable contracts,36 scientific input rejections,15 altered families, seven real source/policy/supervision failures and exact guide example. No catalog credit or whole-package/paper claim.')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable execution evidence gates')
    verify()
