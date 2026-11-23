# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read saved native FST/global-dispatch qualification; no original C is rerun."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,sys,zipfile
from check_smartpca_fst_workflows import compare_original
from smartpca_dispatch_compatibility import verify as bridge_verify
ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/smartpca-fst-qualification'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify(root=ROOT,jar=None):
    assert __debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O'
    folder=root/FOLDER;state=json.loads((folder/'STATUS.json').read_bytes())
    assert state['status']=='PASS_NATIVE_SMARTPCA_PCA_FST_GLOBAL_WORKFLOWS' and state['native_fst_workflow_qualified'] and state['global_CLI_dispatch_available']
    assert not any(state[k] for k in ('catalog_admitted','whole_tool_qualified','whole_paper_reproduced','whole_goal_complete')) and state['new_family_credit']==state['new_reviews']==0
    for name,wanted in state['source_code'].items():assert pin((root/name).read_bytes())==wanted,'Qualified source differs: '+name
    index_raw=(folder/'EVIDENCE.json').read_bytes();archive_raw=(folder/'evidence.zip').read_bytes();assert pin(index_raw)==state['evidence_index'] and pin(archive_raw)==state['evidence_archive'];index=json.loads(index_raw)['files']
    with zipfile.ZipFile(io.BytesIO(archive_raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names)) and set(names)=={'objects/'+v['sha256'] for v in index.values()}
        assert sum(i.file_size for i in archive.infolist())<536_870_912
        for info in archive.infolist():assert info.file_size<33_554_432 and pin(archive.read(info))['sha256']==info.filename[8:]
        def raw(name):
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
            value=archive.read('objects/'+index[name]['sha256']);assert pin(value)==index[name],name;return value
        def obj(name):return json.loads(raw(name))
        class View:
            def __init__(self,name):self.name=name
            def __truediv__(self,value):return View(self.name+'/'+str(value))
            def read_bytes(self):return raw(self.name)
            def open(self,**kwargs):return io.StringIO(raw(self.name).decode('utf-8'))
        for name,wanted in state['source_code'].items():assert index['qualified-source/'+name]==wanted
        normal=obj('normal-build-2/RESULT.json');assert normal['exit_code']==0 and normal['source_unchanged'] and normal['producer']==state['producer']
        assert normal['normal_tests']==dict(tests=1906,failures=0,errors=0,skipped=0)
        payload=obj('normal-build-2/PAYLOADS.json');assert payload['previous_payloads']==22973 and payload['modified']==['org/jlinalg/cli/JLinAlgCli.class']
        assert all(n.startswith(('org/jlinalg/genetics/SmartpcaFst','org/jlinalg/cli/Smartpca')) for n in payload['added'])
        build=obj('normal-build-2/SOURCE-INPUTS.json')
        for name,wanted in state['source_code'].items():
            if name.startswith('src/'):assert build[name]==wanted,'Actual normal producer source differs: '+name
        reference=obj('original-fst-reference-2/COMPLETED.json');assert reference['program_calls']==24 and reference['direct_function_calls']==23 and reference['original_source_files_unchanged']==159 and not reference['original_scientific_source_modified']
        fixture=root/'src/test/resources/org/jlinalg/genetics/smartpca-original-fst-reference.zip'
        with zipfile.ZipFile(fixture) as originals:cases={row['case']:row for n in originals.namelist() if n.startswith('program/') and n.endswith('/reference.json') for row in [json.loads(originals.read(n))]}
        assert len(cases)==24
        commands=0;comparisons=0
        for host,run,count in [('windows21','protocol-windows21-1',104),('windows25','protocol-windows25-1',129),('linux21','protocol-linux21-1',129)]:
            result=obj(run+'/COMPLETED.json');assert result['status']=='PASS_GLOBAL_NATIVE_SMARTPCA_FST_PROTOCOL' and result['producer']=={k:state['producer'][k] for k in ('bytes','sha256')}
            calls=result['commands'];assert len(calls)==count and len({r['label'] for r in calls})==count
            for call in calls:
                assert call['exit_code']==call['expected_exit'] and obj(run+'/commands/'+call['label']+'/RESULT.json')==call
                started=obj(run+'/commands/'+call['label']+'/STARTED.json');assert started['pid']>0 and started['command'][2]=='-jar' and started['command'][4]=='smartpca-tools'
            claims={r['case']:r for r in result['original_comparisons']};assert len(claims)==23
            for name,case in cases.items():
                capture=run+'/cases/'+name+'/capture'
                if case['kind']=='ORIGINAL_FSTONLY_REFUSAL':assert capture+'/native-result.json' not in index and capture+'/COMPLETED.json' not in index;continue
                assert compare_original(View(capture),case)==claims[name];comparisons+=1
            for label,message in [('control-resealed-false-locus','Finite scientific value differs'),('control-resealed-coherent-false-FST','Finite scientific value differs'),('control-resealed-coherent-false-replay-parent','Finite scientific value differs')]:assert message in raw(run+'/commands/'+label+'/stderr').decode()
            timeout=obj(run+'/bad-actual-deadline/compute-process.json');assert timeout['deadline_exceeded'] and timeout['owned_thread_stopped'] and not timeout['calculation_returned'];commands+=count
        assert commands==362 and comparisons==69
        hosts=obj('host-checks-1/COMPLETED.json');assert len(hosts['hosts'])==3 and all(h['workflow_methods']==9 and h['component_methods']==5 and h['example_commands']==6 and h['all_exit_codes_zero'] for h in hosts['hosts'])
        core=obj('component-check-1/RESULT.json');assert len(core['hosts'])==3 and all(h['compiler_exit']==h['test_exit']==0 for h in core['hosts'])
        roles=obj('source-roles-1.json');assert roles==obj('source-roles-linux-1.json') and roles['papers_checked']==250 and len(roles['roles'])==6 and roles['new_reviews']==roles['new_family_credit']==0
        assert obj('original-fst-reference-1/CHECKER-FAILURE.json')['old_attempt_qualified'] is False
        metadata=obj('catalog-snapshot/METADATA.json');assert metadata['before']==metadata['after'] and len(metadata['before'])==9
        for name,wanted in metadata['before'].items():assert index['catalog-snapshot/'+name]==wanted
        bridge=bridge_verify(root,raw);assert bridge==obj('DISPATCHER-BRIDGE.json')
    if jar is not None:assert pin(jar.read_bytes())=={k:state['producer'][k] for k in ('bytes','sha256')},'Use the exact qualified complete producer'
    return dict(status=state['status'],normal_tests=1906,packaged_protocol_commands=362,source_finite_cases=23,source_refusals=1,source_direct_jackknife_cases=23,
        finite_original_comparisons_across_profiles=69,methods_per_JVM=14,example_commands=18,unchanged_previous_payloads=22972,exact_dispatch_source_extension_lines=3,
        native_fst_workflow_qualified=True,global_CLI_dispatch_available=True,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path);a=p.parse_args()
    try:print(json.dumps(verify(jar=a.jar),indent=2))
    except Exception as error:raise SystemExit('FAIL2: saved smartpca FST qualification failed: '+repr(error)+'. Restore the exact published source/checkpoint and producer; retain altered evidence. Never refresh old hashes to bypass a failure.') from error
