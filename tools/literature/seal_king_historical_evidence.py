# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Stage and verify own historical KING/native cutoff artifacts before publishing; exclude original software/data."""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE
HOME=ROOT/'docs/literature/king-historical-development'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE) and not HOME.exists()
    out.mkdir(parents=True);stage=out/'staging';stage.mkdir();canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
    state=dict(status='FAIL2_HISTORICAL_KING_PUBLIC_EVIDENCE_SEAL',new_catalog_credit=False,whole_goal_complete=False)
    try:
        cli=['KingHistoricalToolsCli','KingHistoricalAudit','KingHistoricalProfiles','KingHistoricalRequest',
             'KingRequest','KingAudit','NextflowParameters','NextflowFiles','NextflowJson','SimpleJson']
        sources=[ROOT/'src/main/java/org/jlinalg/cli'/(n+'.java') for n in cli]
        sources += [ROOT/'src/main/java/org/jlinalg/genetics'/(n+'.java') for n in ['KingRobustKinship','KingKinshipThreshold']]
        sources += [ROOT/'src/test/java/org/jlinalg/genetics/KingKinshipThresholdTest.java',ROOT/'examples/native-king/KingThresholdExample.java',
                    ROOT/'src/main/resources/org/jlinalg/omics/king-historical/setup_king_historical_runtime.py',
                    ROOT/'docs/literature/HISTORICAL_KING_INTEGRATION.md',ROOT/'docs/literature/NATIVE_KING_THRESHOLDS.md']
        scripts=['probe_king_historical.py','audit_king_historical_reference.py','build_king_historical.py','validate_king_historical.py',
                 'test_king_historical_contracts.py','test_king_threshold_contracts.py','build_king_threshold_api.py',
                 'setup_snprelate_reference.py','seal_king_historical_evidence.py','verify_king_historical_evidence.py']
        sources += [ROOT/'tools/literature'/n for n in scripts];pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
        summary_paths={'historical-gates':BASE/'king-historical-gates-2/COMPLETED.json',
            'historical-contracts':BASE/'king-historical-contracts-1/COMPLETED.json','native-contracts':BASE/'king-threshold-contracts-2/COMPLETED.json',
            'historical-build21':BASE/'king-historical-build21-2/COMPLETED.json','historical-build25':BASE/'king-historical-build25-1/COMPLETED.json',
            'native-build21':BASE/'king-threshold-api21-1/COMPLETED.json','native-build25':BASE/'king-threshold-api25-1/COMPLETED.json',
            'source-probe':BASE/'king-historical-probe-2/COMPLETED.json',
            'snprelate-runtime-setup-only':ROOT/'build/literature-review/snprelate-runtime/private-original-setup-4/COMPLETED.json'}
        summaries={k:json.loads(p.read_bytes()) for k,p in summary_paths.items()};assert all(v['status'].startswith('PASS_') for v in summaries.values())
        for key in ['historical-build21','historical-build25','native-build21','native-build25','native-contracts']:
            assert all(pin(ROOT/name)==value for name,value in summaries[key]['source_pins'].items()),key
        artifacts={}
        for name,path in [('king-historical-javac21.jar',BASE/'king-historical-build21-2/king-historical-development.jar'),
            ('king-historical-javac25.jar',BASE/'king-historical-build25-1/king-historical-development.jar'),
            ('king-exact-threshold-javac21.jar',BASE/'king-threshold-api21-1/king-threshold-api.jar'),
            ('king-exact-threshold-javac25.jar',BASE/'king-threshold-api25-1/king-threshold-api.jar')]:
            shutil.copyfile(path,stage/name);artifacts[name]=pin(stage/name)
        payload={'own-source/'+p.relative_to(ROOT).as_posix():p.read_bytes() for p in sources}
        payload.update({'summaries/'+k+'.json':p.read_bytes() for k,p in summary_paths.items()})
        consumer=BASE/'king-threshold-api21-1/external-consumer/KingThresholdExample.class'
        payload['native-consumer/KingThresholdExample.class']=consumer.read_bytes()
        records=[];reference=BASE/'king-historical-gates-2'
        for row in summaries['historical-gates']['records']:
            if row['case'].startswith('official-'):continue
            bundle=reference/row['bundle'];spec=json.loads((bundle/'source-request.json').read_bytes())
            assert spec['bfile'].endswith(('/own-inputs/cohort','\\own-inputs\\cohort')) or 'own-boundaries' in spec['bfile']
            record=dict(bundle=row['bundle'],profile=row['profile'],case=row['case'],expected=row['actual'],
                        input_pins={e:pin(bundle/'inputs'/('cohort.'+e)) for e in ['bed','bim','fam']});records.append(record)
            for path in sorted(bundle.rglob('*')):
                if path.is_file():payload['synthetic/bundles/'+bundle.name+'/'+path.relative_to(bundle).as_posix()]=path.read_bytes()
        assert len(records)==28 and sum(r['expected']==2 for r in records)==8
        with zipfile.ZipFile(stage/'controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(payload.items()):
                item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type,item.external_attr=zipfile.ZIP_DEFLATED,0o100644<<16;z.writestr(item,raw)
        proof=dict(status='PASS_FROZEN_HISTORICAL_KING_AND_EXACT_NATIVE_CUTOFF_EVIDENCE',schema_version=1,
            new_catalog_credit=False,whole_goal_complete=False,source_pins=pins,artifacts=artifacts,archive=pin(stage/'controls.zip'),
            members={n:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for n,raw in sorted(payload.items())},synthetic_bundles=records,
            original_KING_programs_source_or_author_example_bundled=False,full_original_execution_cases=36,full_admitted_cases=26,
            full_source_discrepancy_failures=10,full_portable_saved_checks=72,jdk25_contract_records=66,native_test_methods=19,
            native_test_executions=57,original_plink_reference_pair_checks=2880,native_external_consumers=4,
            snprelate_setup_only=True,root_cli_catalog_hosted_roles_and_full_goal_complete=False,canonical_executable_unchanged=before,
            scope='Exact historical default kinship/duplicate adapters and native exact decimal cutoffs. Source-discrepant analyses rejected without repair. No original-data reproduction, unspecified sample-selection policy, hosted-context equivalence, production GWAS scale or whole-goal claim.')
        save(stage/'VALIDATION.json',proof)
        command=[sys.executable,str(ROOT/'tools/literature/verify_king_historical_evidence.py'),'--home',str(stage),'--out',str(out/'verified-staging'),
                 '--java','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe']
        r=subprocess.run(command,capture_output=True,timeout=240);(out/'verify.stdout').write_bytes(r.stdout);(out/'verify.stderr').write_bytes(r.stderr)
        assert r.returncode==0,(r.returncode,r.stdout[-2400:],r.stderr[-2400:])
        assert pin(canonical)==before and all(pin(ROOT/name)==value for name,value in pins.items())
        HOME.mkdir()
        for path in stage.iterdir():shutil.copyfile(path,HOME/path.name)
        assert all(pin(HOME/name)==value for name,value in artifacts.items()) and pin(HOME/'controls.zip')==proof['archive']
        state.update(status='PASS_VERIFIED_PUBLIC_HISTORICAL_KING_AND_NATIVE_CUTOFF_EVIDENCE',archive=proof['archive'],source_pins=pins,
                     published_files={p.relative_to(ROOT).as_posix():pin(p) for p in HOME.iterdir()},canonical_executable_unchanged=before)
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(__import__('traceback').format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:3000],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/author-data exclusion or staged scientific verification')
    raise SystemExit(main())
