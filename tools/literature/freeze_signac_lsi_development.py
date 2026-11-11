# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze final typed LSI evidence without promoting full Signac or changing prior catalog evidence."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'docs/literature/signac-lsi-development'
def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}
def main():
    assert not TARGET.exists(),'Preserve existing evidence; choose an explicitly new target for a later milestone'
    core=read(BASE/'lsi-build-5/BUILD.json');request=read(BASE/'lsi-request-build-3/BUILD.json')
    native=read(BASE/'lsi-numerics-4/COMPLETED.json');workflow=read(BASE/'lsi-workflow-5/COMPLETED.json')
    owned=read(BASE/'lsi-owned-process-4/COMPLETED.json');example=read(BASE/'lsi-example-4/COMPLETED.json');checked=read(BASE/'lsi-checked-example-1/COMPLETED.json')
    records=[core,request,native,workflow,owned,example,checked]
    for record in records:assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    assert request['parent_artifact']==core['artifact'] and request['unchanged_parent_entries']==19651 and request['added_classes']==1
    assert core['unchanged_parent_entries']==19631 and core['added_classes']==12 and core['added_resources']==8
    assert native['artifact']==example['artifact']==checked['artifact']==request['artifact']
    assert workflow['artifact']==owned['artifact']==core['artifact']
    assert all(p['tests']==p['passed']==1650 and p['skipped']==0 for p in native['platform_validation']) and len(native['platform_validation'])==3
    assert len(workflow['actual_fits'])==len(workflow['independent_original_comparisons'])==workflow['fresh_actual_fits']==22
    assert workflow['retained_actual_fits']==0 and workflow['copied_verifications']==69
    assert len(workflow['rehashed_scientific_rejections'])==9 and all(r['rejected_platforms']==3 for r in workflow['rehashed_scientific_rejections'])
    assert len(workflow['failure_gates'])==4 and len(owned['runs'])==2 and all(r['actual_live_R_process_observed'] and r['observed_processes_stopped'] and r['no_results_admitted'] for r in owned['runs'])
    assert len(example['runs'])==3 and all(r['fresh_actual_fit'] and r['identities_preserved'] for r in example['runs'])
    assert checked['fresh_actual_fit'] and checked['independent_reference_tables_identical']
    for artifact in [core['artifact'],request['artifact']]:assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(ROOT/core['artifact']['path']) as old,zipfile.ZipFile(ROOT/request['artifact']['path']) as new:
        assert new.testzip() is None and len(new.namelist())==len(set(new.namelist()))
        assert set(new.namelist())-set(old.namelist())=={'org/jlinalg/cli/SignacLsiRequestCli.class'}
        assert all(old.read(n)==new.read(n) for n in old.namelist())
    parent=ROOT/'docs/literature/signac-normalization-development';prior=read(parent/'VALIDATION.json')
    assert all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'controls.zip')==prior['archive']
    root_parent=read(ROOT/'docs/literature/stringtie-main-workflow/VALIDATION.json');assert all(pin(ROOT/n)==v for n,v in root_parent['source_pins'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==core['canonical_executable_unchanged']
    source_profiles=read(ROOT/'src/main/resources/org/jlinalg/omics/signac-lsi/SOURCE-PROFILES.json')
    for profile in source_profiles['records']:
        assert pin(ROOT/profile['original_source'])==profile['original_source_pin']
        assert pin(ROOT/'src/main/resources/org/jlinalg/omics/signac-lsi'/profile['resource'])==profile['function']
    # Recheck both prior runtime libraries after all upstream fits and failure tests.
    before=read(BASE/'reference-setup-1/BASE-BEFORE.json');after=inventory(ROOT/'build/omics-sctransform-r');assert before==after and len(after)==23902
    private=inventory(BASE/'reference-setup-1/R-lib')
    catalog=ROOT/'docs/literature/omics-2023-2026';catalog_pins={}
    for line in (catalog/'SHA256SUMS').read_text().splitlines():
        digest,name=line.split('  ',1);path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;assert pin(catalog/name)['sha256']==digest;catalog_pins[name]=pin(catalog/name)
    assert len(catalog_pins)==402;summary=read(catalog/'summary.json');counts=summary['implementation_status_counts']
    assert summary['papers']==250 and summary['paper_tool_observations']==2442 and summary['distinct_named_tools']==387
    assert counts['native-subset-validated']==16 and counts['upstream-adapter-validated']==52 and counts['missing']==259
    assert len(read(ROOT/'tools/literature/reviewed-uses.json')['reviews'])==827
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    sources=dict(prior['source_pins'])
    for record in records:sources.update(record['source_pins'])
    for name in ['tools/literature/freeze_signac_lsi_development.py','docs/literature/SIGNAC_LSI_DEVELOPMENT.md']:sources[name]=pin(ROOT/name)
    assert request['artifact']['sha256'] in (ROOT/'docs/literature/SIGNAC_LSI_DEVELOPMENT.md').read_text()
    files={}
    def add(path,name=None):
        name=name or path.relative_to(ROOT).as_posix();raw=path.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for name in sources:add(ROOT/name,'current-source/'+name)
    for folder in sorted(BASE.glob('lsi-*')):
        if folder.is_dir():
            for path in sorted(folder.rglob('*')):
                if path.is_file() and path.suffix!='.jar' and 'javadoc' not in path.relative_to(folder).parts:add(path)
    # Include the exact demonstrated request output and transitive source/normalization proof.
    for path in sorted((ROOT/checked['analysis_path']).rglob('*')):
        if path.is_file():add(path)
    add(parent/'VALIDATION.json');add(parent/'controls.zip');add(parent/'WARNINGS.md')
    files['runtime-content-inventory.json']=(json.dumps(dict(status='reference-content-integrity-not-complete-portable-runtime-admission',base_library_unchanged=True,base_files=len(after),private_files=len(private),private_file_pins=private),indent=2)+'\n').encode()
    files['unchanged-catalog.json']=(json.dumps(dict(status='unchanged-during-typed-Signac-LSI-development',checksums=catalog_pins,summary=summary,reviewed_uses=827,new_integrations=68,including_existing=71,missing=259),indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS

UTF-8 REQUESTS — These Windows Java/R launchers can change Unicode arguments before application code receives them. Use SignacLsiRequestCli with a request file stored in an ASCII-only path. Put exact Unicode input/output paths inside UTF-8 JSON; relative paths resolve against that request. The child R process uses a UTF-8 file and relative worker paths. Do not accept escaped IDs or transliterated paths.

SKIP3 — Missing Rscript, either library directory, a required exact package or working native DLL skips dependent LSI. Read WARNINGS.md and original logs. Where permitted provide the required private runtime (Signac1.17.1, Matrix1.7.5, SeuratObject5.4.0, RSpectra0.16.2, irlba2.4.1); the existing setup helper can prepare NEW private reference paths. If installation is forbidden or unavailable, keep analysis skipped. No automatic installation occurs inside an analysis.

FAIL2 — Invalid counts, empty feature selection, excessive dimensions, nonfinite output, invalid singular triplets, interrupted/timed-out processes and changed identities do not admit result tables. Retain attempts, correct the named cause and rerun into NEW directories. Existing destinations are never overwritten. Constant scaled components can produce source NaN; choose a scientifically justified correction or explicitly unscaled analysis rather than clipping/replacing NaN silently.

HISTORICAL SOURCE PROFILES — Archived functions run on current pinned dependencies, not historical whole-package installations. Signac1.6/1.7 do not forward tol. Earlier historical assay-wrapper API failures and annotation-index warnings remain in the transitive normalization evidence. This matrix LSI route does not repair or bypass those requirements.

DEVELOPMENT SCOPE — Exact package/source and numerical checks are validated, but complete DLL/host closure and biological accuracy are not certified. Windows R fits, three-platform Java/native/captured checks and Unicode request workflows are distinct claims. No fragments/QC/gene activity/motifs/chromVAR/LinkPeaks/differential accessibility/WNN/clustering or full Signac integration is claimed. The integration count remains68 (71 including pre-existing adapters); P134 use curation remains pending. No root dispatch, canonical executable replacement, release, deployment, push or broader goal completion occurred.
'''
    TARGET.mkdir();(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n');(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');files['WARNINGS.md']=warnings.encode()
    archive=TARGET/'controls.zip'
    with zipfile.ZipFile(archive,'x') as z:
        for name,data in sorted(files.items()):
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    assert archive.stat().st_size<100_000_000,'Do not silently exceed normal GitHub file limits'
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(files)
        assert all(z.read(name)==raw for name,raw in files.items())
    result=dict(schema_version=1,status='passed-typed-Signac-LSI-development-and-UTF8-request-workflows',source_pins=sources,
                artifact=request['artifact'],core_artifact=core['artifact'],parent_normalization_validation=pin(parent/'VALIDATION.json'),parent_normalization_archive=pin(parent/'controls.zip'),
                archive=pin(archive),archive_members=len(files),archive_file_pins={n:dict(bytes=len(v),sha256=hashlib.sha256(v).hexdigest()) for n,v in sorted(files.items())},
                platform_validation=native['platform_validation'],fresh_core_upstream_fits=22,independent_original_comparisons=22,core_captured_verifications=69,rehashed_scientific_rejections=27,
                final_request_actual_fits=3,final_request_verifications=9,exact_checked_in_request_actual_fits=1,actual_owned_process_cleanup_checks=2,
                retained_runtime_library_unchanged=True,base_library_files=23902,private_library_files=len(private),
                catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,
                canonical_executable_unchanged=core['canonical_executable_unchanged'],root_parent_sources_unchanged=True,literature_pages_withheld=True,
                historical_packages_installed=False,complete_runtime_DLL_host_closure_certified=False,full_Signac_workflow_available=False,catalog_implementation_credit=False,
                warnings_document=pin(TARGET/'WARNINGS.md'),released=False,pushed=False,deployed=False,whole_goal_complete=False)
    save(TARGET/'VALIDATION.json',result)
    assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==core['canonical_executable_unchanged']
    print(json.dumps(dict(status=result['status'],archive=result['archive'],members=len(files),source_pins=len(sources),native_tests_per_platform=1650)))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete evidence checks')
    main()
