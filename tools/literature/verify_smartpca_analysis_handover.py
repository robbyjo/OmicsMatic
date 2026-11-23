# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read the saved development handover; no original analyses are rerun."""
from pathlib import Path, PurePosixPath
import argparse, hashlib, json, zipfile

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/smartpca-analysis-handover'

def identity(raw):
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def require(condition,message):
    if not condition:
        raise ValueError(message)

def verify(root=ROOT,jar=None):
    folder=root/FOLDER
    state=json.loads((folder/'STATUS.json').read_bytes())
    require(state['status']=='SAVED_SOURCE_COMPARED_COMPONENTS_WITH_UNFINISHED_WORKFLOW','Wrong handover scope')
    require(not state['workflow_qualified'] and not state['catalog_admitted'] and not state['whole_goal_complete'],'Unfinished scope changed')
    require(state['new_family_credit']==state['new_reviews']==0,'Development cannot award catalog credit')
    for name,wanted in state['source_code'].items():
        require(identity((root/name).read_bytes())==wanted,'Current handover source differs: '+name)
    for name,wanted in state['canonical_metadata'].items():
        require(identity((root/name).read_bytes())==wanted,'Catalog changed since this development checkpoint: '+name)
    inventory_raw=(folder/'INVENTORY.json').read_bytes()
    require(identity(inventory_raw)==state['inventory'],'Evidence inventory differs')
    inventory=json.loads(inventory_raw)
    archive=folder/'evidence.zip'
    require(identity(archive.read_bytes())==state['evidence'],'Evidence archive differs')
    with zipfile.ZipFile(archive) as z:
        names=z.namelist()
        require(len(names)==len(set(names)) and set(names)==set(inventory),'Evidence namespace differs')
        require(sum(i.file_size for i in z.infolist())<=100_000_000,'Evidence exceeds declared capacity')
        for name,wanted in inventory.items():
            path=PurePosixPath(name)
            require(not path.is_absolute() and '..' not in path.parts and '\\' not in name,'Unsafe member')
            require(identity(z.read(name))==wanted,'Evidence member differs: '+name)
        def read(name):
            return json.loads(z.read(name))
        normal=read('handover-normal-build-1/RESULT.json')
        require(normal['exit_code']==0 and normal['source_unchanged'],'Normal build failed or changed source')
        require(normal['normal_tests']==dict(tests=1877,failures=0,errors=0,skipped=0),'Normal test population changed')
        require(normal['producer']==state['producer'],'Producer metadata differs')
        payloads=read('handover-normal-build-1/PAYLOADS.json')
        require(payloads['previous_payloads']==22940 and payloads['modified']==[],'Predecessor payload changed')
        for name in ('component-check-1/RESULT.json','analysis-check-2/RESULT.json','handover-checks-1/RESULT.json'):
            result=read(name)
            require(len(result['hosts'])==3 and all(r['compiler_exit']==r['test_exit']==0 for r in result['hosts']),'Saved cross-JVM check failed: '+name)
        previous=read('handover-checks-1/RESULT.json')['predecessor_readers']
        require(len(previous)==2 and all(r['exit_code']==0 for r in previous),'Frozen reader regression failed')
        require('original-analysis-reference-1/PRECISION-METADATA-INVALIDATED.json' in inventory,'Invalidated original attempt lost')
        failed=read('analysis-check-1/RESULT.json')
        require(len(failed['hosts'])==3 and all(r['test_exit']!=0 for r in failed['hosts']),'Retained failed reference changed')
        for name,wanted in state['source_code'].items():
            require(inventory.get('source-context/'+name)==wanted,'Saved current source context differs: '+name)
    if jar is not None:
        require(identity(jar.read_bytes())=={k:state['producer'][k] for k in ('bytes','sha256')},'Exact development producer differs')
    return dict(status=state['status'],saved_files=len(inventory),normal_tests=1877,three_jvm_component_checks=True,
                original_analysis_reexecuted=False,workflow_qualified=False,catalog_admitted=False,
                new_family_credit=0,new_reviews=0,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path)
    args=parser.parse_args()
    try:
        print(json.dumps(verify(jar=args.jar),indent=2))
    except Exception as error:
        raise SystemExit('FAIL2: smartpca analysis handover check failed: '+str(error)) from error
