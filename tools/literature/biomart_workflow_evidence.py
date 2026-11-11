# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline structural verification of retained actual-original biomaRt comparisons; this does not refit or query a server."""
import json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

BASE='build/literature-review/biomart-runtime/'

def verify():
    folder=ROOT/'docs/literature/biomart-workflow-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['schema_version']==1 and p['status']=='passed-scoped-original-biomaRt-workflow-with-native-query-planning' and p['adapter_admitted'] and p['implementation_credit'] and p['admitted_named_tools']==['biomart'] and not p['whole_goal_complete'];assert pin(folder/'controls.zip')==p['archive'];files=unpack(folder/'controls.zip',p)
    for name,value in p['source_pins'].items():assert byte_pin(files['current-source/'+name])==value and pin(ROOT/name)==value,('Current scientific/source file differs',name)
    get=lambda name:json.loads(files[BASE+name]);build=get('workflow-build-6/COMPLETED.json');reference=get('workflow-build-5/COMPLETED.json');assert build['artifact']==p['artifact'] and reference['artifact']==p['original_full_matrix_artifact'];assert build['canonical_executable_unchanged']==p['canonical_executable_unchanged']==reference['canonical_executable_unchanged']==pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    evolution=get('diagnostic-evolution-1/COMPLETED.json');path=evolution['path'];old=files[BASE+'diagnostic-evolution-1/BiomartRuntime-before.java'];current=files['current-source/'+path];assert byte_pin(old)==evolution['before'] and byte_pin(current)==evolution['after'];assert current.decode('utf8').count(evolution['new_fragment'])==1 and current.decode('utf8').replace(evolution['new_fragment'],evolution['old_fragment']).encode('utf8')==old;assert not evolution['query_or_result_algorithm_changed'] and not evolution['original_R_worker_changed'] and not evolution['native_query_planner_changed'] and not evolution['frozen_scientific_proof_rewritten'];assert p['changed_final_JAR_entries']==['org/jlinalg/cli/BiomartRuntime.class']
    for name,value in reference['source_pins'].items():assert byte_pin(files[BASE+'workflow-build-5/source-snapshot/'+name])==value and (name==path or p['source_pins'][name]==value)
    matrix=get('workflow-validation-5/COMPLETED.json');assert matrix['status']=='passed-complete-biomaRt-workflow-controls-and-independent-original-comparisons' and matrix['artifact']==reference['artifact'] and matrix['admitted_live_analysis_profiles']==6 and matrix['protocol_control_count']==13
    assert len(matrix['protocol'])==13 and sum(r['exit_code']==0 for r in matrix['protocol'])==4 and sum(r['exit_code']==2 for r in matrix['protocol'])==5 and sum(r['exit_code']==3 for r in matrix['protocol'])==4;assert all(r['admitted']==(r['exit_code']==0) for r in matrix['protocol']);assert len(matrix['live'])==6 and all(r['exit_code']==0 and r['admitted'] and r['complete_independent_comparison'] and r['queries']==5 for r in matrix['live'])
    extra=get('workflow-additional-2/COMPLETED.json');assert extra['status']=='passed-additional-biomaRt-runtime-preservation-capacity-and-Java25-original-comparisons' and extra['artifact']==build['artifact'] and extra['original_runtime_files_unchanged'];assert len(extra['checks'])==9 and sum(c['exit_code']==0 for c in extra['checks'])==1 and sum(c['exit_code']==2 for c in extra['checks'])==4 and sum(c['exit_code']==3 for c in extra['checks'])==4
    assert get('workflow-additional-2/runtime-before.json')==get('workflow-additional-2/runtime-after.json');assert 'Complete raw HTTP diagnostic capacity exceeded' in files[BASE+'workflow-additional-2/complete-HTTP-capacity/analysis/WARNINGS.md'].decode('utf8') and 'Increase maximum_http_bytes' in files[BASE+'workflow-additional-2/complete-HTTP-capacity/analysis/WARNINGS.md'].decode('utf8')
    native=get('query-validation-4/COMPLETED.json');assert native['status']=='passed-native-biomaRt-query-planning-original-public-operation-comparisons' and native['artifact']==build['artifact'];assert {r['name'] for r in native['JVMs']}=={'windows-java21','windows-java25','linux-java21'} and all((r['complete_original_public_cases'],r['matching_original_failures'],r['complete_query_batches'],r['complete_original_HTTP_submissions'])==(44,10,76,74) for r in native['JVMs']);assert get('query-reference-2/COMPLETED.json')['status']=='passed-original-public-getBM-protocol-reference-capture-only'
    independent=0;rows=0
    for group in ['workflow-validation-5','workflow-additional-2']:
        prefix=BASE+group+'/'
        for name,raw in files.items():
            if not name.startswith(prefix) or not name.endswith('/independent-reference/COMPLETED.json'):continue
            report=json.loads(raw);assert report['status']=='PASS_COMPLETE_INDEPENDENT_ORIGINAL_DATAFRAMES_AND_TYPED_STATES';home=name.removesuffix('/independent-reference/COMPLETED.json')
            for case in report['cases']:
                assert case['complete_original_dataframe_identical'] and case['typed_states_complete'];id=case['id'];typed=json.loads(files[home+'/analysis/results/'+id+'/typed-result.json']);assert typed['rows']==case['rows'] and len(typed['row_names'])==case['rows'] and [c['name'] for c in typed['columns']]==case['columns'];assert all(len(c['values'])==case['rows'] and all(v['state'] in ['value','NA','NaN','+Inf','-Inf'] for v in c['values']) for c in typed['columns']);assert files[home+'/analysis/results/'+id+'/original-result.rds']==files[home+'/analysis/worker/'+id+'/original-result.rds'];assert files[home+'/independent-reference/'+id+'/independent-original.rds'];independent+=1;rows+=case['rows']
    assert independent==p['complete_independent_original_dataframes']==51 and rows==p['complete_compared_original_rows']
    for name,raw in files.items():
        if name.startswith(BASE) and name.endswith('/process.json'):
            report=json.loads(raw)
            if 'owned_live_after_cleanup' in report:assert not report['owned_live_after_cleanup'],name
        if name.startswith(BASE+'workflow-validation-5/') and name.endswith('/analysis/analysis-status.json'):
            report=json.loads(raw)
            if not report['result_admitted']:assert not any(n.startswith(name.removesuffix('/analysis-status.json')+'/results/') for n in files)
    assert get('exported-runtime-268-1/COMPLETED.json')['status']=='PASS_EXACT_PRIVATE_BIOMART_RUNTIME_EXPORTED_ONLY';assert get('reference-setup-1/COMPLETED.json')['preexisting_libraries_unchanged'];assert p['native_only_named_tool_credit_added']==0 and p['original_getBM_table_conversion_retained'] and not p['historical_R44_image_reproduced'] and not p['whole_paper_reproduced'];return p

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/output evidence verification')
    p=verify();print('Verified scoped biomaRt workflow evidence:',p['complete_independent_original_dataframes'],'complete original dataframes; native planner on3 JVMs; failure/skip/preservation protocols. Offline structural verification only; no refitting or server retrieval.')
