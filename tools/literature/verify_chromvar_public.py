# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify public main controls, exact source correspondence and private canonical/study preservation."""
import json,re,zipfile
from pathlib import Path
from validate_mashr_native import pin
from signac_scan_evidence import unpack
from catalog_source_integrity import verify_source,reverse_delta
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';EVIDENCE=ROOT/'docs/literature/chromvar-tools-development'
def read(p):return json.loads(p.read_bytes())
def verify():
    r=read(EVIDENCE/'VALIDATION.json');assert r['status']=='passed-public-chromVAR-main-workflows-and-paper-source-correspondence' and pin(EVIDENCE/'controls.zip')==r['archive'] and pin(EVIDENCE/'WARNINGS.md')==r['warnings_document'];data=ROOT/'docs/literature/omics-2023-2026'
    for n,v in r['source_pins'].items():verify_source(ROOT,data,n,v['sha256'])
    files=unpack(EVIDENCE/'controls.zip',r);assert len(files)==r['logical_files']
    for n,v in r['source_pins'].items():assert pin_bytes(files['current-source/'+n])==v
    prefix='build/literature-review/signac-runtime/'
    def archived(n):return json.loads(files[prefix+n])
    controls=archived('chromvar-public-controls-1/COMPLETED.json');example=archived('chromvar-public-example-1/COMPLETED.json');history=archived('chromvar-paper-source-controls-1/COMPLETED.json');build=archived('chromvar-public-workflow-1/BUILD.json')
    assert (controls['actual_original_BACKGROUND_fits'],controls['actual_native_DEVIATIONS'],controls['actual_main_captured_verifications'],controls['actual_dependency_skip'],controls['failed_capture_rejections'])==(2,3,27,1,3) and controls['existing_output_preserved'] and controls['malformed_requests_rejected']==2
    assert len(controls['runs'])==5 and {v['platform'] for v in controls['runs']}=={'windows-java21','windows-java25','linux-java21'}
    assert (example['literal_original_BACKGROUND'],example['literal_native_DEVIATIONS'],example['actual_main_verifications'],example['existing_output_refusals'])==(1,1,6,2) and example['inputs_and_requests_unchanged']
    assert (history['numeric_method_comparisons'],history['source_profile_case_protocols'],history['background_source_and_public_protocols'])==(306,102,45) and history['all_numeric_fields_value_kinds_and_background_RNG_states_match'] and history['paper_1_5_0_commit_unknown']
    pointer=json.loads(files['primary-use-pointers.json']);assert pointer['papers_scanned']==250 and pointer['catalogued_papers']==pointer['mentioned_papers']==5 and len(pointer['census'])==250 and not pointer['long_primary_text_published']
    source=ROOT/'src/main/resources/org/jlinalg/omics/chromvar-paper-sources';profiles=read(source/'SOURCE-PROFILES.json');assert profiles['paper_1_5_0_exact_commit_unknown'] and not profiles['ArchR_pipeline_available'] and profiles['records'][1]['upstream_commit']=='3236dcc515efa01ce54114aee03b227466168126'
    for p in profiles['records']:
        for f in p['functions']:
            raw=(source/f['resource']).read_bytes();target=ROOT/'src/main/resources/org/jlinalg/omics'/('chromvar-background' if f['name'].startswith('get_') else 'chromvar-deviations')/'1.34.1'/(f['name']+'.R');assert raw==target.read_bytes() and pin_bytes(raw)==f['function']
        cpp=(source/p['cpp_resource']).read_bytes();current=(ROOT/'src/main/resources/org/jlinalg/omics/chromvar-background/1.34.1/utils.cpp').read_bytes();assert cpp==current if p['edition']=='1.28.0' else cpp+b'\n'==current
    assert r['artifact']==build['artifact']==controls['artifact']==example['artifact'];a=r['artifact'];assert pin(ROOT/a['path'])=={k:a[k] for k in ['bytes','sha256']} and a['sha256']=='113c62c8ded54cbe9431663612a156fae12077740d2a5720c6a85c33844cbb94'
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==r['canonical_executable_preserved'] and len(r['carried_existing_canonical_study_class_changes'])==12
    bridge=read(data/'chromvar-dispatch-shared-source-compatibility.json')['paths'][0];assert len(bridge['edits'])==2 and reverse_delta((ROOT/bridge['path']).read_bytes(),bridge)==files[prefix+'chromvar-public-workflow-1/JLinAlgCli.before.java']
    parent=ROOT/'docs/literature/chromvar-background-development';assert pin(parent/'VALIDATION.json')==r['parent_background_validation'] and pin(parent/'controls.zip')==r['parent_background_archive']
    with zipfile.ZipFile(ROOT/r['parent_scientific_artifact']['path']) as old,zipfile.ZipFile(ROOT/a['path']) as new:
        exceptions=set(r['carried_existing_canonical_study_class_changes'])|{'org/jlinalg/cli/JLinAlgCli.class'};assert all(old.read(n)==new.read(n) for n in old.namelist() if n not in exceptions)
    assert not any(r[k] for k in ['historical_whole_packages_executed','independent_native_RNG_replay','installed_DLL_source_authentication','ArchR_pipeline_available','full_Signac_workflow_available','catalog_implementation_credit','complete_project_test_suite_run','released','pushed','deployed','whole_goal_complete']) and r['root_dispatch_available']
    links=0
    for p in [ROOT/'docs/literature/CHROMVAR_TOOLS.md',ROOT/'examples/chromvar-tools/README.md']:
        for link in re.findall(r'\]\(([^)]+)\)',p.read_text(encoding='utf8')):
            if not re.match(r'^[a-zA-Z]+:',link):assert (p.parent/link.split('#',1)[0]).resolve().exists();links+=1
    return dict(status='passed-independent-public-chromVAR-source-and-workflow-integrity',archive=r['archive'],current_source_pins=len(r['source_pins']),actual_main_verifications=27,literal_main_verifications=6,original_public_fits=2,native_public_analyses=3,paper_source_numeric_methods=306,paper_source_background_protocols=45,primary_roles=5,paper_1_5_0_commit_unknown=True,canonical_preserved=True,existing_study_class_changes_carried_exactly=12,local_links=links,root_dispatch_available=True,catalog_implementation_credit=False,pushed=False,whole_goal_complete=False)
def pin_bytes(raw):
    import hashlib
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def main():
    out=BASE/'chromvar-public-final-integrity-1.json';assert not out.exists();result=verify();out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(result),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent public source/workflow checks')
    main()
