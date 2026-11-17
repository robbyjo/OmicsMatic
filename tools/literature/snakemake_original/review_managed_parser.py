#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently review exported original managed parser/process controls; execute no software."""
from pathlib import Path
import argparse,json,shutil,sys
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_runtime import pin,write
ROOT=HERE.parents[2]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['source','reference','compiled','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-reviewer.py');index=json.loads((a.source/'EXPORTED.json').read_bytes())['files']
    assert set(index)=={p.relative_to(a.source).as_posix() for p in a.source.rglob('*') if p.is_file() and p.name!='EXPORTED.json'}
    for name,expected in index.items():
        path=Path(name);assert not path.is_absolute() and '..' not in path.parts and not (a.source/name).is_symlink();assert pin(a.source/name)==expected,name
    before=json.loads((a.source/'IMMUTABLE-BEFORE.json').read_bytes());assert before==json.loads((a.source/'IMMUTABLE-AFTER.json').read_bytes())==json.loads((a.reference/'IMMUTABLE-BEFORE.json').read_bytes())
    compiled=json.loads((a.compiled/'COMPILED.json').read_bytes());assert compiled['adapter_class_source']=='byte-pinned-jar'
    complete=json.loads((a.source/'COMPLETED.json').read_bytes());assert complete['compiled']==pin(a.compiled/'COMPILED.json') and not complete['user_analysis_admitted'] and not complete['whole_goal_complete'] and complete['new_catalog_credit']==0
    assert len(complete['cases'])==27 and {r['mode'] for r in complete['cases']}=={'conda','apptainer','conda-apptainer'}
    rows=[];policies=spawns=blocked=rejected=diagnostics=0
    for row in complete['cases']:
        case=a.source/'cases'/row['case'];bound=json.loads((case/'SUPERVISED-POLICY.json').read_bytes())['bound'];controller=json.loads((case/'CONTROLLER.json').read_bytes());ledger=json.loads((case/'process/process.json').read_bytes());assert pin(case/'process/process.json')==bound['processLedger'] and pin(case/'CONTROLLER.json')==bound['controllerReceipt']
        assert ledger['exit_code']==bound['exitCode']==row['exit_code'] and not ledger['owned_live_after_cleanup'] and not ledger.get('suppressed') and 'cleanup_state' not in ledger and not controller['integrity_errors']
        assert (ledger['status']=='completed-process-only')==(row['exit_code']==0);assert ledger['link_data_roots']==bound['softwareData']
        observed={}
        for identity in ledger['owned_processes']:observed.setdefault(str(identity['pid']),set()).add(identity['startTicks'])
        assert set(bound['policyProcesses'])=={str(pid) for pid in bound['policy']['processes']}
        for pid,identity in bound['policyProcesses'].items():assert observed[pid]=={identity['startTicks']} and identity['startTicks']>0
        for name,expected in bound['policy']['files'].items():assert pin(case/'policy-records'/name)==expected
        if row['scenario'].startswith('legacy-'):assert bound['parserPolicy'] is None and controller['schema_version']==1
        else:
            assert controller['schema_version']==2;parser=bound['parserPolicy'];assert parser['upstreamApiCalled']==row['api_called']
            if parser['accepted']:assert controller['upstream_api_called']
            else:
                rejected+=1;assert not row['api_called'] and row['exit_code']==3 and not bound['policy']['deploymentCalls'] and not bound['policy']['spawnCalls'];assert not list((case/'events').iterdir()) and not (case/'working/api-called.txt').exists() and not (case/'working/result.tsv').exists()
        if row['exit_code']==0:assert (case/'working/result.tsv').read_bytes()==b'total\tpython\n5\t3.14.10\n' and (case/'working/api-called.txt').exists()
        for name in ['process.stdout','process.stderr']:
            path=case/'process'/name;assert path.is_file();diagnostics+=1
        policies+=len(bound['policyProcesses']);spawns+=bound['policy']['spawnCalls'];blocked+=bound['policy']['blockedCalls'];rows.append(dict(case=row['case'],source_exit=row['exit_code'],policy_processes=bound['policyProcesses'],parser_rejected=bound['parserPolicy'] is not None and not bound['parserPolicy']['accepted'],ledger=bound['processLedger']))
    assert policies==39 and spawns==12 and blocked==6 and rejected==9
    write(out/'COMPLETED.json',dict(status='PASS_INDEPENDENT_PACKAGED_ORIGINAL_MANAGED_PROFILE_PROCESS_AND_UNCHANGED_SOFTWARE_REVIEW',cases=rows,policy_processes=policies,original_local_spawns=spawns,blocked_deployment_calls=blocked,before_api_profile_rejections=rejected,original_process_diagnostics=diagnostics,original_software_matches_reference=True,source_export=pin(a.source/'EXPORTED.json'),user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False))
    print('PASS_INDEPENDENT_27_CASES_39_POLICY_IDENTITIES_12_SPAWNS_9_BEFORE_API_REJECTIONS',flush=True)
if __name__=='__main__':main()
