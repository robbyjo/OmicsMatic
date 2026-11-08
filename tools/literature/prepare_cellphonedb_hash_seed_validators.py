# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive new startup-revision protocol controls from frozen controls, with explicit changes only."""
import json
from pathlib import Path
from prepare_cellphonedb_hash_seed import ROOT,replace_once

def generated():
    tools=ROOT/'tools/literature';result={}
    for old,new in [('validate_cellphonedb_workflow_linux.py','validate_cellphonedb_hash_seed_linux.py'),('validate_cellphonedb_workflow_windows.py','validate_cellphonedb_hash_seed_windows.py')]:
        text=(tools/old).read_text(encoding='utf8').replace('CellPhoneDbWorkflowCli','CellPhoneDbWorkflowCliV2').replace('CellPhoneDbSetupCli','CellPhoneDbSetupCliV2').replace('PASS_COMPILED_CELLPHONEDB_WORKFLOW_DEVELOPMENT_ONLY','PASS_COMPILED_CELLPHONEDB_HASH_SEED_DEVELOPMENT_ONLY').replace('safe-worker-controls-3/request.json','hash-seed-controls-1/request.json').replace('timeout=360','timeout=600')
        if 'linux' in new:
            text=replace_once(text,"assert len(report['cases'])==19","assert len(report['cases'])==len(request['analyses']) and report['runner_revision']==2 and report['python_startup_audit']['status']=='PASS_EFFECTIVE_PYTHON_STARTUP_ONLY' and report['python_startup_audit']['requested_seed']==request.get('python_hash_seed',0) and report['python_startup_audit']['independent_native_seed0_hashes']==(request.get('python_hash_seed',0)==0)")
            text=replace_once(text,"small['analyses']=small['analyses'][:1];missing=","small['analyses']=small['analyses'][:1]\n        for seed in [123,4294967295]:\n            seeded=json.loads(json.dumps(small));seeded['python_hash_seed']=seed;execute('effective-seed-'+str(seed),seeded,0)\n        missing=")
            text=text.replace('PASS_SIX_PACKAGED_CELLPHONEDB_WORKFLOW_CONTROLS_ONLY','PASS_EIGHT_PACKAGED_CELLPHONEDB_HASH_SEED_CONTROLS_ONLY')
        result[tools/new]=text.encode()
    old='validate_cellphonedb_safe_protocols_linux.py';text=(tools/old).read_text(encoding='utf8')
    # A NEW exported kit combines the unchanged original setup/codec and revised launcher members.
    text=replace_once(text,"kit=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-workflow'","kit=ROOT/'build/literature-review/cellphonedb-runtime/hash-seed-windows-1/exported-kit'")
    text=text.replace('safe-worker-controls-3/request.json','hash-seed-controls-1/request.json')
    result[tools/'validate_cellphonedb_hash_seed_protocols_linux.py']=text.encode()
    return result

def verify():
    for path,raw in generated().items():assert path.read_bytes()==raw,'Derived protocol control changed: '+str(path)

if __name__=='__main__':
    for path,raw in generated().items():
        if path.exists():assert path.read_bytes()==raw,'Existing derived control protected'
        else:path.write_bytes(raw)
    verify();print(json.dumps([p.relative_to(ROOT).as_posix() for p in generated()],indent=2))
