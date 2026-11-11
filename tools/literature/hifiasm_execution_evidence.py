# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Strict complete declared hifiasm execution evidence; no catalog credit or historical-source repinning."""
import io,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

STATUS='passed-complete-source-bound-hifiasm-workflows-and-native-admission'
BASE='build/literature-review/hifiasm-runtime/'
DIRECTORY='docs/literature/hifiasm-execution-development'


def verify(emit=True,directory=None):
    home=Path(directory) if directory is not None else ROOT/DIRECTORY;p=json.loads((home/'VALIDATION.json').read_bytes());assert p['status']==STATUS and p['full_declared_hifiasm_workflow_admitted'] and not p['implementation_credit'] and not p['whole_goal_complete'] and not p['whole_paper_reproduced'] and not p['scientific_source_exception_added'];files={}
    for name,record in p['archives'].items():
        assert Path(name).name==name
        if 'parts' in record:
            chunks=[]
            for item in record['parts']:
                assert Path(item['name']).name==item['name'] and pin(home/item['name'])==item['pin'];chunks.append((home/item['name']).read_bytes())
            raw=b''.join(chunks);assert byte_pin(raw)==record['pin'];part=unpack(io.BytesIO(raw),record)
        else:assert pin(home/name)==record['pin'];part=unpack(home/name,record)
        for member,raw in part.items():
            assert member not in files or files[member]==raw;files[member]=raw
    for name,value in p['source_pins'].items():assert byte_pin(files['current-source/'+name])==value and pin(ROOT/name)==value,name
    jar=ROOT/p['artifact']['path'];assert pin(jar)=={k:p['artifact'][k] for k in ['bytes','sha256']};assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged'];built=json.loads(files[BASE+'execution-build-9/COMPLETED.json']);assert built['artifact']==p['artifact'] and built['parent_artifact']==p['parent_artifact'];assert all(p['source_pins'][name]==value for name,value in built['source_pins'].items())
    read=lambda name:json.loads(files[BASE+name+'/COMPLETED.json'])
    original=read('paper-modes-1');public=read('public-workflows-4');failures=read('final-failure-evidence-1');portable=read('portable-controls-4');retained=read('retained-results-2');large=read('large-parent-workflow-1');native=read('native-current-4');stream=read('yak-stream-native-1')
    assert original['status']=='passed-twenty-seven-complete-supported-original-hifiasm-mode-controls-only' and original['case_count']==27;assert public['status']=='passed-twenty-seven-fresh-public-original-hifiasm-and-native-graph-workflows' and public['case_count']==27 and public['graph_views']==246 and len(public['editions'])==6 and public['artifact']==p['artifact'];assert all(v['offline_integrity_verified'] and v['exit_code']==0 and v['report']['analysis_admitted'] for v in public['editions'].values());assert failures['status']=='passed-eleven-source-bound-hifiasm-skip-owned-failure-and-final-admission-controls' and len(failures['cases'])==11 and failures['artifact']==p['artifact'];assert portable['status']=='passed-three-JVM-complete-typed-hifiasm-requests-source-export-and-forbidden-install' and len(portable['runs'])==3 and portable['artifact']==p['artifact'];assert retained['status']=='passed-three-JVM-complete-retained-result-integrity-and-sixteen-corruption-controls' and len(retained['baselines'])==3 and retained['corruption_cases']==len(retained['corruptions'])==16 and all(v['exit_code']==2 for v in retained['corruptions'].values()) and retained['artifact']==p['artifact'];assert large['status']=='passed-full-large-parent-gzip-trio-ul-workflow-under128MiB-Java-heap' and large['parent_entries']>25000000 and large['original_graphs_byte_identical'] and large['artifact']==p['artifact'];assert native['status']=='passed-unchanged-complete-native-class-families-bound-to-current-hifiasm-candidate' and native['artifact']==p['artifact'];assert stream['status']=='passed-three-JVM-complete-original-streaming-YAK-with27000027-entries-under128MiB-heap' and len(stream['runs'])==3
    with zipfile.ZipFile(jar) as current:
        for component in native['components'].values():
            for name,value in component['unchanged_complete_class_family'].items():assert byte_pin(current.read(name))==value
        predecessor=json.loads(files[BASE+'execution-build-8/COMPLETED.json']);prefix='org/jlinalg/assembly/HifiasmYak';expected={n for n in predecessor['added_entries'] if n==prefix+'.class' or n.startswith(prefix+'$')};assert expected and {n for n in current.namelist() if n==prefix+'.class' or n.startswith(prefix+'$')}==expected
        for name in expected:assert current.read(name)==files['closed-native-class/execution-build-8/'+name]
    example=json.loads(files[BASE+'example-3/result/COMPLETED.json']);assert example['status']=='PASS_COMPLETE_ORIGINAL_HIFIASM_AND_NATIVE_GRAPH_WORKFLOW' and example['analysis_admitted'];sourcebuilds=read('reference-builds-2');assert sourcebuilds['status']=='passed-requested-original-hifiasm-source-builds-only' and len(sourcebuilds['editions'])==6;bindings=read('bound-runtimes-2');assert bindings['status']=='passed-six-original-hifiasm-host-runtime-bindings-only' and len(bindings['editions'])==6
    from gseapy_current_evidence import verify as parent_verify
    parent=parent_verify(False);assert parent['artifact']==p['parent_artifact'];assert p['positive_workflows']==27 and p['complete_graph_views']==246 and p['large_parent_workflow_additional'] and p['skip_failure_controls']==11 and p['retained_corruptions']==16
    if emit:print('Verified six original source/runtime profiles,27 fresh public workflows,246 complete graph views, three-JVM native components, large-parent/gzip execution,11 actual skip/failure controls and16 retained-result corruptions; no catalog or whole-paper credit.')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/archive/admission gates')
    verify()
