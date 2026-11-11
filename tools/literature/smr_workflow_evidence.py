# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Strict portable complete original-SMR workflow/source/artifact proof; catalog review and whole-goal completion remain separate."""
import json,zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin


def verify():
    folder=ROOT/'docs/literature/smr-workflow-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-typed-original-SMR-HEIDI-workflow' and p['adapter_admitted'] and p['implementation_credit'] and p['admitted_named_tools']==['smr','heidi'] and not p['whole_goal_complete'] and not p['catalog_changed'];assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document'];files=unpack(folder/'controls.zip',p)
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==sha
    for name,value in p['source_pins'].items():assert pin(ROOT/name)==value and byte_pin(files['current-source/'+name])==value
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value
    get=lambda name:json.loads(files[name]);reports={k:get(v+'/COMPLETED.json') for k,v in p['final_attempts'].items()};build=reports['build'];assert build['artifact']==p['artifact'];assert all(reports[k]['artifact']==p['artifact'] for k in ['core','edges','native','preparation','boundaries','overlap','example']);assert all(reports[k]['status'].startswith('passed-') for k in ['core','edges','native','preparation','boundaries','overlap','example'])
    for report in reports.values():
        for name,value in report.get('source_pins',{}).items():assert pin(ROOT/name)==value
    protocols=[*reports['core']['protocols'],*reports['edges']['protocols']];assert len(protocols)==p['runtime_protocols']==38 and sum(r['exit_code']==0 for r in protocols)==p['successful_runtime_protocols']==27 and all(r['passed'] and r['exit_code']==r['expected_exit_code'] for r in protocols);comparisons=0
    for key in ['core','edges']:
        for protocol in reports[key]['protocols']:
            prefix=p['final_attempts'][key]+'/'+protocol['id']+'/';state=get(prefix+'analysis/analysis-status.json');assert state['result_admitted']==(protocol['exit_code']==0) and prefix+'analysis/WARNINGS.md' in files
            if protocol['exit_code']!=0:assert not any(n.startswith(prefix+'analysis/results/') for n in files);continue
            for case in protocol['comparisons']:
                comparisons+=1
                for name,value in case['complete_original_outputs'].items():assert byte_pin(files[prefix+'direct-'+case['case']+'/'+name])==value and files[prefix+'direct-'+case['case']+'/'+name]==files[prefix+'analysis/worker/cases/'+case['case']+'/'+name]
            manifest=get(prefix+'analysis/manifest.json');assert manifest['result_admitted'] and manifest['runtime_environment_fallback'] is False
    assert comparisons==p['complete_original_case_comparisons']==63;native=reports['native'];assert native['tests_per_JVM']==p['native_tests_per_JVM']==56 and len(native['JVMs'])==3 and all(r['tests']==56 and r['exit_code']==r['skipped']==0 for r in native['JVMs']);assert reports['boundaries']['original_preparation_cases']==16 and reports['overlap']['native_original_table_comparisons']==8
    fixture=ROOT/reports['fixture']['fixture']['path'];assert pin(fixture)=={k:reports['fixture']['fixture'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(fixture) as z:
        index=json.loads(z.read('INDEX.json'));assert len(z.namelist())==len(set(z.namelist()))==len(index['files'])+1 and len(index['protocols'])==27
        for name,value in index['files'].items():assert byte_pin(z.read(name))==value
    omissions=get('EXPLICIT-OMISSIONS.json');assert omissions['status']=='only-private-full-JAR-and-local-OS-library-omissions' and not omissions['retained_scientific_files_deliberately_omitted'] and omissions['final_validation_scientific_inputs_outputs_complete'] and omissions['initial_prototype_temporary_worker_loss_disclosed_in_retention_audit'] and len(omissions['files'])==p['explicit_omission_receipts'];assert all(name not in files for name in omissions['files']);assert get('build/literature-review/smr-runtime/workflow-core-probe-1/RETENTION-AUDIT.json')['initial_manifest_failure_lost_temporary_worker']
    old=get('artifact-parent-entries.json');new=get('artifact-candidate-entries.json');assert len(old)==p['unchanged_parent_entries'] and all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(p['added_entries']);assert all(byte_pin(files['candidate-owned-entry/'+name])==new[name] for name in p['added_entries'])
    for key,entries in [('parent_artifact',old),('artifact',new)]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert len(z.namelist())==len(set(z.namelist())) and {name:byte_pin(z.read(name)) for name in z.namelist()}==entries
    if (ROOT/'build/cli/omicsmatic-0.3.7.jar').exists():assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged']
    print('Verified complete scoped SMR/HEIDI workflow:38 runtime protocols,63 complete original case comparisons,56 native checks/JVM,16 original preparation boundaries; source/version/overlap/precision limits retained; catalog and whole goal separate');return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete scientific/source/artifact checks')
    verify()
