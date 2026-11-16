#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the separately reviewed release/development transition; never patch old gates."""
from pathlib import Path
import argparse,json,os,shutil,subprocess,sys,traceback,uuid
sys.dont_write_bytecode=True
MODULE=Path(__file__).resolve().parent;ROOT=MODULE.parents[2];sys.path.insert(0,str(MODULE.parent))
from catalog_source_transition_v2.source import PARENT,TARGET,OWN,GUIDE,EVIDENCE,META,git,pin,rawpin,save,source_guard
from catalog_source_transition_v2.review_notes import JAVA,CATEGORIES
from catalog_source_transition.bridge import COUNTS

BASELINE='357c9301be726b3b6dc2a4e92cf71ff05ed053d2'
STATUS='PASS_REVIEWED_RELEASE_DEVELOPMENT_SOURCE_TRANSITION_AND_PREDECESSOR_CATALOG'
ACTION=('A separately reviewed source transition adds zero tool credit. Old verifiers and their original hashes remain unchanged and refuse later source outside their own scope. '
        'The original parent verifier runs in its exact clean1a9ff82 checkout, with its exact clean357c9301 baseline; no scientific-source or Git overlay is used. '
        'Supply missing permitted checkouts, primary-source caches or software and retry NEW output paths after SKIP3; otherwise skip dependent analysis. '
        'FAIL2 admits no complete transition: retain failures, read the named cause and correct it before retrying NEW paths. '
        'Do not refresh historical source/evidence identities to suppress refusal. Original0.6.6 metadata and score-overflow failures remain unaccepted; indexes/editions must not be silently substituted. '
        'Whole-tool, author-environment, biological and whole-paper validity and the full250-paper objective remain unqualified.')
class PrerequisiteUnavailable(Exception):pass
def load(path):return json.loads(Path(path).read_bytes())
def require_pins(root,identities):
    for name,identity in identities.items():
        path=root/name
        if path.is_symlink() or not path.is_file() or pin(path)!=identity:raise ValueError('Reviewed source/evidence identity differs: '+name)
def load_catalog_registry(root=ROOT):
    from catalog_source_transition.bridge import load_catalog_registry as previous
    try:return previous(root)
    except FileNotFoundError as error:
        raise PrerequisiteUnavailable('Complete permitted primary-source cache required under '+str(Path(root)/'build/literature-review')+'; supply the exact retained cache or skip dependent admission. Missing: '+str(error.filename)) from error
def metadata_guard(root,expected,snapshot=None):
    metadata={name:(root/name).read_bytes() for name in META} if snapshot is None else dict(snapshot)
    if set(metadata)!=set(META) or any(not isinstance(raw,bytes) for raw in metadata.values()) or {name:rawpin(raw) for name,raw in metadata.items()}!=expected:
        raise ValueError('Exact complete predecessor metadata state required')
    return metadata
def parent_run(parent_checkout,baseline_checkout,out,rescan):
    for checkout,expected,label in [(parent_checkout,PARENT,'parent'),(baseline_checkout,BASELINE,'baseline')]:
        path=Path(checkout).resolve()
        if not path.is_dir():raise PrerequisiteUnavailable('Missing exact clean '+label+' checkout: '+str(path))
        try:head=git(path,'rev-parse','HEAD').decode().strip()
        except (OSError,subprocess.CalledProcessError) as error:raise PrerequisiteUnavailable('Git checkout unavailable: '+str(path)) from error
        if head!=expected:raise ValueError('Exact '+label+' commit required; retain this checkout and supply a separate one: '+expected)
        if git(path,'status','--porcelain=v1','-z','--untracked-files=all'):raise ValueError('Historical '+label+' sources must remain clean')
    destination=Path(parent_checkout).resolve()/'build'/('release-source-transition-v2-'+uuid.uuid4().hex)
    command=[sys.executable,'-X','utf8','-B','tools/literature/catalog_source_transition/bridge.py','--verify','--baseline-checkout',str(Path(baseline_checkout).resolve()),'--out',str(destination)]
    if rescan:command.append('--rescan-sources')
    with (out/'parent.stdout').open('xb') as so,(out/'parent.stderr').open('xb') as se:
        result=subprocess.run(command,cwd=parent_checkout,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=3600)
    save(out/'parent.process.json',dict(command=command,cwd=str(parent_checkout),exit_code=result.returncode,parent=PARENT,baseline=BASELINE,output=str(destination),old_verifier_and_Git_unmodified=True))
    if result.returncode==3:raise PrerequisiteUnavailable('Original parent skipped a prerequisite; read parent.stdout and its retained FAILURE.json')
    if result.returncode:raise ValueError('Unchanged parent verifier refused; retain and read original parent records')
    parent=load(destination/'COMPLETED.json')
    if parent['status']!='PASS_REVIEWED_APPLICATION_SOURCE_TRANSITION_AND_UNCHANGED_PREDECESSOR_CATALOG' or any(parent[key]!=value for key,value in COUNTS.items()):
        raise ValueError('Complete qualified predecessor scope differs')
    shutil.copyfile(destination/'COMPLETED.json',out/'PARENT-COMPLETED.json')
    for checkout in [parent_checkout,baseline_checkout]:
        if git(checkout,'status','--porcelain=v1','-z','--untracked-files=all'):raise ValueError('Historical source changed during parent verification')
    return parent
def verify(root=ROOT,parent_checkout=None,baseline_checkout=None,out=None,rescan=False,catalog_metadata=None,extensions=None):
    root=Path(root).resolve()
    if parent_checkout is None or baseline_checkout is None or out is None:raise PrerequisiteUnavailable('Supply exact clean1a9ff82 and357c9301 checkouts and a NEW owned output')
    proof=load(root/EVIDENCE/'QUALIFICATION.json')
    if proof['status']!=STATUS or proof['parent']!=PARENT or proof['target']!=TARGET:raise ValueError('Exact source-transition qualification required')
    own_actual={path.relative_to(root).as_posix() for path in (root/OWN).iterdir() if path.is_file()}
    own_expected={name for name in proof['source_pins'] if name.startswith(OWN+'/')}
    if own_actual!=own_expected:raise ValueError('Complete source-transition implementation membership differs')
    require_pins(root,proof['source_pins']);require_pins(root,proof['evidence_pins'])
    changes=load(root/EVIDENCE/'CHANGE-INVENTORY.json')
    if changes['unknown'] or {row['category'] for row in changes['files']}-set(CATEGORIES):raise ValueError('Unreviewed source categories remain')
    java={Path(row['path']).stem for row in changes['files'] if row['path'].startswith('src/main/java/')}
    if java!=set(JAVA):raise ValueError('Complete production Java review differs')
    guard=source_guard(root,load(root/EVIDENCE/'TARGET-TREE.json'),extensions)
    # A future catalog consumer may supply only the exact predecessor's eight
    # metadata byte strings. No source path, source function or Git result is
    # replaced. That consumer must separately validate its actual current catalog.
    metadata=metadata_guard(root,proof['catalog_metadata_pins'],catalog_metadata)
    registry=load_catalog_registry(root)
    if registry!=load(root/EVIDENCE/'REGISTRY.json') or len(registry)!=485:raise ValueError('Complete predecessor registry definitions differ')
    parent=parent_run(parent_checkout,baseline_checkout,Path(out),rescan)
    if {name:pin(Path(parent_checkout)/name) for name in META}!=proof['catalog_metadata_pins']:raise ValueError('Clean historical parent catalog differs')
    registry=load_catalog_registry(root)
    if registry!=load(root/EVIDENCE/'REGISTRY.json') or len(registry)!=485:raise ValueError('Complete predecessor registry definitions differ')
    source_guard(root,load(root/EVIDENCE/'TARGET-TREE.json'),extensions);require_pins(root,proof['source_pins']);require_pins(root,proof['evidence_pins'])
    metadata_guard(root,proof['catalog_metadata_pins'],None if catalog_metadata is None else metadata)
    return dict(status=STATUS,**COUNTS,parent=PARENT,target=TARGET,source_guard=guard,historical_parent_reverified=True,original_parent_status=parent['status'],primary_sources_rescanned=rescan,
        predecessor_metadata_and_registry_unchanged=True,catalog_metadata_is_actual_current_state=catalog_metadata is None,metadata_only_historical_view_used=catalog_metadata is not None,
        old_verifiers_pass_current_tree=False,preceding_scientific_sources_overridden=False,old_verifiers_or_Git_monkeypatched=False,fresh_original_scientific_calls_repeated_by_this_gate=False,
        application_checks=proof['application_checks'],new_scoped_integration_credit=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--verify',required=True,action='store_true');p.add_argument('--parent-checkout',type=Path,required=True);p.add_argument('--baseline-checkout',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None
    try:
        destination=a.out.resolve()
        if not destination.is_relative_to(ROOT/'build'):raise ValueError('Use a NEW output within this checkout build directory')
        destination.mkdir(parents=True,exist_ok=False);out=destination;(out/'WARNINGS.md').write_bytes(('# Warnings and required actions\n\n'+ACTION+'\n').encode())
        result=verify(ROOT,a.parent_checkout,a.baseline_checkout,out,a.rescan_sources);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,PrerequisiteUnavailable) else 2;label='SKIP3_RELEASE_SOURCE_TRANSITION_UNAVAILABLE' if code==3 else 'FAIL2_RELEASE_SOURCE_TRANSITION_UNADMITTED'
        if out is not None:
            try:
                if (out/'COMPLETED.json').exists():(out/'COMPLETED.json').rename(out/('COMPLETION-INVALIDATED-'+uuid.uuid4().hex+'.json'))
                save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
            except BaseException as recording:print('FAIL2_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
        print(label,repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
