# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify saved native pySCENIC AUCell component evidence; no original fit is rerun."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/pyscenic-aucell-component-checkpoint'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify(root=ROOT,jar=None):
    assert __debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O'
    q=root/FOLDER;state=json.loads((q/'STATUS.json').read_bytes())
    assert state['status']=='PASS_NATIVE_PYSCENIC_AUCELL_COMPONENT_ONLY' and state['native_component_qualified']
    assert not any(state[k] for k in ('full_SCENIC_workflow_qualified','catalog_admitted','whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'))
    assert state['new_family_credit']==state['new_reviews']==0
    assert (state['normal_tests'],state['previous_payloads_unchanged'],state['focused_methods_per_JVM'])==(2059,22995,7),'Reported component counts differ from actual evidence'
    for name,wanted in state['source_code'].items():assert pin((root/name).read_bytes())==wanted,'Changed native component source: '+name
    index_raw=(q/'EVIDENCE.json').read_bytes();archive_raw=(q/'evidence.zip').read_bytes()
    assert pin(index_raw)==state['evidence_index'] and pin(archive_raw)==state['evidence_archive'],'Changed saved component evidence index/archive'
    index=json.loads(index_raw)['files']
    with zipfile.ZipFile(io.BytesIO(archive_raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names)) and set(names)=={'objects/'+v['sha256'] for v in index.values()}
        assert sum(i.file_size for i in archive.infolist())<536_870_912
        for info in archive.infolist():assert info.file_size<67_108_864 and pin(archive.read(info))['sha256']==info.filename[8:]
        def raw(name):
            p=PurePosixPath(name);assert not p.is_absolute() and '..' not in p.parts and '\\' not in name
            value=archive.read('objects/'+index[name]['sha256']);assert pin(value)==index[name];return value
        def obj(name):return json.loads(raw(name))
        normal=obj('normal-build-2/RESULT.json');assert normal['exit_code']==0 and normal['source_unchanged'] and normal['producer']==state['producer']
        assert normal['normal_tests']==dict(tests=2059,failures=0,errors=0,skipped=0)
        payload=obj('normal-build-2/PAYLOADS.json');assert payload['previous_payloads']==22995 and not payload['modified']
        assert all(n.startswith(('org/jlinalg/enrichment/PyScenicAucell','org/jlinalg/omics/pyscenic-ce41b61/')) for n in payload['added'])
        built=obj('normal-build-2/SOURCE-INPUTS.json')
        for name,wanted in state['source_code'].items():
            if name.startswith('src/'):assert built[name]==wanted,'Actual final producer source differs: '+name
        packaged=obj('packaged-check-2/COMPLETED.json');assert packaged['producer']==state['producer'] and len(packaged['hosts'])==3
        assert {h['host'] for h in packaged['hosts']}=={'windows21','windows25','linux21'}
        for host in packaged['hosts']:
            assert host['compiler_exit']==host['test_exit']==0 and host['focused_methods']==7 and host['example_calls']==1 and host['source_resources_checked']==13 and host['engine_origins_checked']==4
            text=raw('packaged-check-2/'+host['host']+'/java.stdout').decode();assert 'PASS all7 pySCENIC AUCell original-reference methods' in text and 'PASS exact packaged engine origins and13 original source/license resources' in text
        source=obj('original-aucell-reference-2/COMPLETED.json');assert not source['source_modified'] and source['permutation_cases']==114
        assert {k:source['versions'][k] for k in ('pyscenic','ctxcore','numpy','pandas')}==dict(pyscenic='0.12.1',ctxcore='0.2.0',numpy='1.23.5',pandas='1.5.3')
        assert len(source['cases'])==37 and sum(r['expected_native_refusal'] for r in source['cases'])==8 and sum(r['status']=='ORIGINAL_REFUSED' for r in source['cases'])==6
        assert (source['successful_explicit_rank_calls'],source['successful_prepared_score_calls'],source['successful_expression_score_calls'])==(37,62,31)
        assert len(obj('original-aucell-reference-2/PERMUTATIONS.json'))==114
        fixture=root/'src/test/resources/org/jlinalg/enrichment/pyscenic-original-aucell-reference.zip'
        with zipfile.ZipFile(fixture) as reference:
            assert json.loads(reference.read('COMPLETED.json'))==source
            for case in source['cases']:
                name=case['name'];result=obj('original-aucell-reference-2/'+name+'/RESULT.json');assert json.loads(reference.read(name+'/RESULT.json'))==result
                assert 'conditions' in result
        assert b'AssertionError' in raw('original-aucell-reference-2/parallel-failed-workers/stderr')
        assert obj('runtime-install-1/FAILURE.json')['complete_result'] is False
        runtime=obj('runtime-index-2/INVENTORY.json');assert len(runtime['files'])==17067 and len(runtime['packages'])==66 and runtime['system_libraries_base_OS_not_indexed']
        assert runtime['original_core_AUCell_geneSignature_recovery_bytes_match_reference']
        primary=obj('primary-audit-1/SUMMARY.json');assert primary['papers_checked']==250 and len(primary['anchors'])==34 and len(primary['structures'])==7
        roles=obj('SOURCE-ROLES-DRAFT.json');assert len(roles['roles'])==5 and roles['new_reviews']==roles['new_family_credit']==0 and not roles['canonical_modified']
        assert roles['missed_provider_candidates'][0]['paper_id']=='P153' and roles['missed_provider_candidates'][0]['tool_id']=='scenicplus'
        before=obj('catalog-snapshot/IDENTITIES.json');assert before['before']==before['after'] and len(before['before'])==9
        for host in ('windows','linux'):
            assert obj('predecessor-checks-1/'+host+'-catalog.RESULT.json')['exit_code']==0
            measured=obj('predecessor-checks-1/'+host+'-catalog.stdout');assert measured['counts']['scoped_families']==154 and measured['counts']['reviewed_uses']==1697
    if jar is not None:assert pin(jar.read_bytes())=={k:state['producer'][k] for k in ('bytes','sha256')},'Provide the exact final component producer'
    return dict(status=state['status'],normal_tests=2059,previous_payloads_unchanged=22995,focused_methods_per_JVM=7,
        unique_original_cases=37,original_permutations=114,native_returned_scoring_cases=29,native_refusals=8,original_refusals=6,
        example_calls=3,source_resources_per_JVM=13,prior_catalog_readers_passed=2,native_component_qualified=True,
        full_SCENIC_workflow_qualified=False,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path);a=p.parse_args()
    try:print(json.dumps(verify(jar=a.jar),indent=2))
    except Exception as error:raise SystemExit('FAIL2: saved pySCENIC AUCell component check failed: '+repr(error)+'. Restore exact published source/evidence and final producer; retain altered or failed evidence. Do not refresh old hashes to bypass failure.') from error
