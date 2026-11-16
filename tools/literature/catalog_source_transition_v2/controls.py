#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real isolated source/metadata guard controls; incorrect controls admit no catalog."""
from pathlib import Path
import argparse,json,os,subprocess,sys,time
sys.dont_write_bytecode=True
MODULE=Path(__file__).resolve().parent;ROOT=MODULE.parents[2];sys.path.insert(0,str(MODULE.parent))
from catalog_source_transition_v2.source import TARGET,META,git,pin,rawpin,save,source_guard
from catalog_source_transition_v2.bridge import metadata_guard

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--inventory',type=Path,required=True);p.add_argument('--baseline-checkout',type=Path,required=True);p.add_argument('--checkout',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve()
    if not out.is_relative_to(ROOT/'build'):raise ValueError('Use NEW owned build output')
    common=(ROOT/Path(git(ROOT,'rev-parse','--git-common-dir').decode().strip())).resolve().parent
    checkout=a.checkout.resolve() if a.checkout else out/'checkout'
    if checkout.exists() or not checkout.is_relative_to(common/'build'):raise ValueError('Choose a NEW checkout within the primary project build directory; use a short Windows path')
    out.mkdir(parents=True,exist_ok=False);scope=json.loads((a.inventory/'TARGET-TREE.json').read_bytes());catalog=json.loads((a.inventory/'CHANGE-INVENTORY.json').read_bytes())['catalog_metadata_pins']
    command=['git','-c','core.longpaths=true','worktree','add','--detach',str(checkout),TARGET]
    with (out/'checkout.stdout').open('xb') as so,(out/'checkout.stderr').open('xb') as se:
        result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=300)
    save(out/'checkout.process.json',dict(command=command,exit_code=result.returncode));assert result.returncode==0
    if not checkout.resolve().is_relative_to(common/'build'):raise ValueError('Unexpected isolated checkout path')
    results=[]
    def check(name,action,refused=False,contains=''):
        start=time.monotonic();failure=None
        try:report=action()
        except ValueError as error:
            failure=str(error)
            if not refused or contains not in failure:raise
        else:
            if refused:raise AssertionError('Incorrect control unexpectedly admitted: '+name)
        row=dict(control=name,expected_refusal=refused,observed_refusal=failure,seconds=round(time.monotonic()-start,2));results.append(row);save(out/(name+'.json'),row);print(json.dumps(row),flush=True)
    check('complete-actual-target-tree',lambda:source_guard(checkout,scope))
    original=checkout/'src/main/java/org/jlinalg/cli/JLinAlgCli.java';before=original.read_bytes();info=original.stat();modified=before.replace(b'sambamba-tools',b'sambamba-to0ls');assert before!=modified and len(before)==len(modified)
    try:
        original.write_bytes(modified);os.utime(original,ns=(info.st_atime_ns,info.st_mtime_ns));git(checkout,'update-index','--assume-unchanged','src/main/java/org/jlinalg/cli/JLinAlgCli.java')
        assert not git(checkout,'diff','--name-only')
        check('same-size-time-git-assume-unchanged-cannot-hide-bytes',lambda:source_guard(checkout,scope),True,'actual source bytes differ')
    finally:
        original.write_bytes(before);os.utime(original,ns=(info.st_atime_ns,info.st_mtime_ns));git(checkout,'update-index','--no-assume-unchanged','src/main/java/org/jlinalg/cli/JLinAlgCli.java')
    unexpected=checkout/'src/main/java/org/jlinalg/GuardOnlyUnexpectedSource.java';assert not unexpected.exists()
    try:
        unexpected.write_bytes(b'// Unexecuted invalid source-guard control; not an admitted implementation.\n')
        check('undeclared-added-source-refused',lambda:source_guard(checkout,scope),True,'source membership differs')
        extension={unexpected.relative_to(checkout).as_posix():pin(unexpected)}
        check('exact-new-consumer-file-may-be-declared',lambda:source_guard(checkout,scope,extension))
        unexpected.write_bytes(b'// Changed declared source control; no accepted implementation.\n')
        check('changed-declared-new-consumer-refused',lambda:source_guard(checkout,scope,extension),True,'new consumer source identity differs')
    finally:unexpected.unlink()
    check('existing-source-cannot-be-declared-away',lambda:source_guard(checkout,scope,{'src/main/java/org/jlinalg/cli/JLinAlgCli.java':rawpin(before)}),True,'only exact NEW files')
    check('path-traversal-extension-refused',lambda:source_guard(checkout,scope,{'../outside.java':rawpin(b'')}),True,'only exact NEW files')
    state={name:(checkout/name).read_bytes() for name in META}
    check('complete-actual-predecessor-metadata',lambda:metadata_guard(checkout,catalog))
    wrong=dict(state);name='docs/literature/omics-2023-2026/summary.json';wrong[name]=wrong[name].replace(b'"papers": 250',b'"papers": 249');assert wrong[name]!=state[name]
    check('changed-metadata-view-refused',lambda:metadata_guard(checkout,catalog,wrong),True,'complete predecessor metadata')
    incomplete=dict(state);incomplete.pop(name)
    check('incomplete-metadata-view-refused',lambda:metadata_guard(checkout,catalog,incomplete),True,'complete predecessor metadata')
    check('exact-explicit-historical-metadata-view',lambda:metadata_guard(checkout,catalog,state))
    old_out=out/'legacy-current-tree-refusal'
    command=[sys.executable,'-X','utf8','-B','tools/literature/catalog_source_transition/bridge.py','--verify','--baseline-checkout',str(a.baseline_checkout.resolve()),'--out',str(old_out)]
    with (out/'legacy.stdout').open('xb') as so,(out/'legacy.stderr').open('xb') as se:
        result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
    save(out/'legacy.process.json',dict(command=command,exit_code=result.returncode));assert result.returncode==2 and not (old_out/'COMPLETED.json').exists()
    failure=json.loads((old_out/'FAILURE.json').read_bytes());assert failure['status']=='FAIL2_APPLICATION_SOURCE_TRANSITION_UNADMITTED'
    results.append(dict(control='unchanged-legacy-current-tree-refusal',expected_refusal=True,status=failure['status']))
    assert not git(checkout,'status','--porcelain=v1','-z','--untracked-files=all')
    save(out/'COMPLETED.json',dict(status='PASS_REAL_ISOLATED_RELEASE_SOURCE_AND_METADATA_GUARD_CONTROLS',controls=results,incorrect_controls_are_not_admitted_catalogs=True,old_verifier_unchanged=True,new_scoped_integration_credit=0))
    print(json.dumps(dict(status='PASS_REAL_ISOLATED_RELEASE_SOURCE_AND_METADATA_GUARD_CONTROLS',controls=len(results))),flush=True)
if __name__=='__main__':main()
