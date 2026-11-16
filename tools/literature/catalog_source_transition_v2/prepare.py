#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One-time capture of a completed, separately reviewed source transition."""
from pathlib import Path
import argparse,json,shutil,sys,uuid,zipfile,xml.etree.ElementTree as ET
sys.dont_write_bytecode=True
MODULE=Path(__file__).resolve().parent;ROOT=MODULE.parents[2];sys.path.insert(0,str(MODULE.parent))
from catalog_source_transition_v2.source import PARENT,TARGET,OWN,GUIDE,EVIDENCE,META,git,pin,save,source_guard
from catalog_source_transition_v2.bridge import STATUS,COUNTS,load_catalog_registry
from catalog_source_transition_v2.review_notes import JAVA,CATEGORIES
from catalog_source_transition_v2.records import inventory,archive_check,verify as readback

def load(path):return json.loads(path.read_bytes())
def freeze(review,dispatch,out):
    folder=ROOT/EVIDENCE
    if folder.exists():raise FileExistsError('Retain existing evidence; never refresh old identities to force acceptance')
    if git(ROOT,'rev-parse','HEAD').decode().strip()!=TARGET or git(ROOT,'diff','--name-only','HEAD'):raise ValueError('Exact clean tracked target source required')
    changes=load(review/'source-intake-1/CHANGE-INVENTORY.json');target_tree=load(review/'source-intake-1/TARGET-TREE.json')
    if changes['unknown'] or len(changes['files'])!=272 or changes['main_java_added']!=16 or changes['main_java_modified']!=1:raise ValueError('Exact complete source review required')
    if {row['category'] for row in changes['files']}-set(CATEGORIES):raise ValueError('Unknown source category')
    if {Path(row['path']).stem for row in changes['files'] if row['path'].startswith('src/main/java/')}!=set(JAVA):raise ValueError('Complete per-class review required')
    ast=load(review/'ast-review-1/COMPLETED.json')
    if ast['status']!='PASS_COMPLETE_PRODUCTION_JAVA_SYNTAX_SCOPE_REVIEW' or ast['old_dispatcher_lines_removed']!=0 or len(ast['new_dispatcher_lines'])!=3:raise ValueError('Exact production member review required')
    controls=load(review/'guard-controls-2/COMPLETED.json')
    if controls['status']!='PASS_REAL_ISOLATED_RELEASE_SOURCE_AND_METADATA_GUARD_CONTROLS' or len(controls['controls'])!=12:raise ValueError('Complete real guard controls required')
    failure=load(review/'guard-controls-1/checkout.process.json')
    if failure['exit_code']!=128 or 'Filename too long' not in (review/'guard-controls-1/checkout.stderr').read_text(errors='replace'):raise ValueError('Retained initial checkout failure required')
    parent=load(review/'predecessor-check-1/COMPLETED.json')
    if parent['status']!='PASS_UNMODIFIED_PARENT_IN_EXACT_CLEAN_HISTORICAL_CHECKOUT' or not parent['primary_sources_rescanned']:raise ValueError('Genuine unchanged parent source rescan required')
    if any(parent['parent'][key]!=value for key,value in COUNTS.items()):raise ValueError('Complete predecessor counts differ')
    # Capture the raw original parent's new records without modifying its sources.
    original_records=Path(parent['parent_output']);copy=review/'predecessor-check-1'/('parent-records-'+uuid.uuid4().hex);copy.mkdir(exist_ok=False)
    for path in original_records.iterdir():
        if path.is_symlink() or not path.is_file():raise ValueError('Unexpected original parent output member')
        shutil.copyfile(path,copy/path.name)
    validation=load(dispatch/'dispatch-validation-1/VALIDATION.json');linux=load(review/'linux-workspace-tests-1/COMPLETED.json');runtime=load(dispatch/'alias-runtime-1/COMPLETED.json')
    if validation['tests']!=dict(tests=207,failures=0,errors=0,skipped=4) or linux['tests']!=dict(tests=6,failures=0,errors=0,skipped=0):raise ValueError('Exact platform regression scope differs')
    if validation['changed_class_entries']!=['org/jlinalg/cli/JLinAlgCli.class'] or validation['other_class_entries_identical']!=19688:raise ValueError('Preceding executable class identities differ')
    with zipfile.ZipFile(dispatch/'dispatch-validation-1/reports.zip') as z:
        workspace=ET.fromstring(z.read('build/test-results/test/TEST-org.jlinalg.cli.NextflowCaptureWorkspaceTest.xml'))
    skipped={case.attrib['name'] for case in workspace.findall('testcase') if case.find('skipped') is not None}
    linux_cases={case.attrib['name'] for path in (review/'linux-workspace-tests-1/reports').glob('*.xml') for case in ET.fromstring(path.read_bytes()).findall('testcase') if case.attrib.get('classname')=='org.jlinalg.cli.NextflowCaptureWorkspaceTest' and case.find('skipped') is None and case.find('failure') is None and case.find('error') is None}
    if len(skipped)!=4 or not skipped.issubset(linux_cases):raise ValueError('All four actual Windows skips must pass on Linux')
    if runtime['status']!='PASS_NORMAL_ALIAS_ORIGINAL_EXECUTION_REPLAY_CONFIGURATION_ISOLATION_AND_FAILURES' or runtime['candidate']!=validation['candidate']:raise ValueError('Actual normal-command original runtime controls required')
    if any(row['exit_code']!=row['expected_exit_code'] for row in runtime['checks']):raise ValueError('A runtime control did not meet its explicit outcome')
    if load(dispatch/'dispatch-closure-1/COMMITTED.json')['head']!=TARGET:raise ValueError('Exact tested dispatcher commit required')
    guard=source_guard(ROOT,target_tree)
    registry=load_catalog_registry(ROOT)
    if registry!=load(Path('D:/projects/JLinAlg/build/git-publish/20261115-all-main-1/checkout')/'docs/literature/application-source-transition-20261115/REGISTRY.json') or len(registry)!=485:raise ValueError('Complete485-definition predecessor registry required')
    out.mkdir(parents=True,exist_ok=False)
    readers=['sambamba_streaming_validation/records.py','sambamba_execution_validation/qualification_records.py','sambamba_workflows/check_development.py','genorm_native/records.py','bai_validation/records.py']
    import subprocess
    for index,reader in enumerate(readers):
        command=[sys.executable,'-X','utf8','-B',str(ROOT/'tools/literature'/reader)]
        with (out/('checkpoint-'+str(index)+'.stdout')).open('xb') as so,(out/('checkpoint-'+str(index)+'.stderr')).open('xb') as se:
            result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=120)
        report=load(out/('checkpoint-'+str(index)+'.stdout'))
        if result.returncode or report['current_project_sources_match_snapshot'] is not True:raise ValueError('New development checkpoint source readback differs: '+reader)
        save(out/('checkpoint-'+str(index)+'.process.json'),dict(command=command,exit_code=result.returncode))
    own={path.relative_to(ROOT).as_posix():pin(path) for path in MODULE.iterdir() if path.is_file()};own[GUIDE]=pin(ROOT/GUIDE)
    private=inventory(review,dispatch)
    folder.mkdir();(folder/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    for source,name in [(review/'source-intake-1/TARGET-TREE.json','TARGET-TREE.json'),(review/'source-intake-1/CHANGE-INVENTORY.json','CHANGE-INVENTORY.json'),(review/'ast-review-1/JAVA-MEMBER-DELTA.json','JAVA-MEMBER-DELTA.json')]:shutil.copyfile(source,folder/name)
    save(folder/'REVIEW-DISPOSITIONS.json',dict(java=JAVA,categories=CATEGORIES));save(folder/'REGISTRY.json',registry)
    save(folder/'PRIVATE-RECORDS.json',dict(review_roots=['predecessor-check-1','linux-workspace-tests-1','source-intake-1','ast-review-1','guard-controls-1','guard-controls-2','preparation-1'],dispatch_roots=['dispatch-validation-1','alias-runtime-1','dispatch-closure-1'],control_checkout_sources_not_duplicated=True,files=private))
    with zipfile.ZipFile(folder/'own-source-snapshot.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in own:z.write(ROOT/name,name)
        z.write(ROOT/'LICENSE','LICENSE-project.txt')
    archive_check(folder/'own-source-snapshot.zip',dict(own,**{'LICENSE-project.txt':pin(ROOT/'LICENSE')}))
    metadata={name:identity for name,identity in private.items() if Path(name).suffix.lower() not in ['.jar','.class','.bam','.bai']}
    save(folder/'METADATA-PINS.json',metadata)
    with zipfile.ZipFile(folder/'validation-metadata.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in metadata:
            label,relative=name.split('/',1);z.write((review if label=='review' else dispatch)/relative,name)
    archive_check(folder/'validation-metadata.zip',metadata)
    from catalog_source_transition_v2.bridge import ACTION
    (folder/'WARNINGS.md').write_bytes(('# Warnings and required actions\n\n'+ACTION+'\n').encode())
    public={path.name:pin(path) for path in folder.iterdir() if path.is_file()}
    checks=dict(windows_tests=validation['tests'],linux_workspace_tests=linux['tests'],all_four_windows_skips_passed_on_Linux=True,existing_packaged_class_entries_identical=19688,
        changed_packaged_class_entries=validation['changed_class_entries'],fresh_successful_original_mark_remove_phases=8,fresh_failed_original_mark_phases=1,guard_controls=12,
        original_Windows_analysis_skipped=True,original_066_overflow_analysis_unaccepted=True,whole_Java_suite_repeated=False)
    save(folder/'QUALIFICATION.json',dict(status=STATUS,parent=PARENT,target=TARGET,source_pins=own,evidence_pins={EVIDENCE+'/'+name:identity for name,identity in public.items()},public_files=public,project_license=pin(ROOT/'LICENSE'),
        catalog_metadata_pins=changes['catalog_metadata_pins'],source_guard=guard,changed_files=272,tracked_target_files=13297,main_java_added=16,main_java_modified=1,application_checks=checks,
        complete_predecessor_scope=COUNTS,old_verifiers_and_Git_unmodified=True,new_scoped_integration_credit=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False))
    if inventory(review,dispatch)!=private:raise ValueError('Retained new records changed during capture')
    saved=readback(review,dispatch);save(out/'COMPLETED.json',dict(status='PASS_CAPTURED_RELEASE_DEVELOPMENT_SOURCE_REVIEW',saved=saved,application_checks=checks))
    print(json.dumps(dict(status='PASS_CAPTURED_RELEASE_DEVELOPMENT_SOURCE_REVIEW',tracked_files=13297,changed_files=272,private_files=len(private),guard_controls=12)),flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--review-base',type=Path,required=True);p.add_argument('--dispatch-base',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    freeze(a.review_base.resolve(strict=True),a.dispatch_base.resolve(strict=True),a.out.resolve())
