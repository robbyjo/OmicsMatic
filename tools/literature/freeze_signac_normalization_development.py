# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze scoped Signac foundation evidence; preserve prior source, canonical JAR and catalog."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'docs/literature/signac-normalization-development'

def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}

def main():
    assert not TARGET.exists(),'Preserve previous evidence; do not overwrite'
    matrix=BASE/'normalization-matrix-1';proof=read(matrix/'COMPLETED.json')
    assert not (matrix/'INCOMPLETE.json').exists() and not (matrix/'FAILURE.json').exists()
    assert proof['status']=='passed-native-Signac-normalization-and-feature-selection-foundation'
    assert all(p['tests']==p['passed']==1605 and p['skipped']==0 for p in proof['platform_validation']) and len(proof['platform_validation'])==3
    assert proof['exact_new_source_classes']==len(proof['new_classes'])==12 and proof['unchanged_parent_entries']==19619
    assert proof['original_normalization_cases']==1152 and proof['original_selection_cases']==396 and proof['original_metadata_cases']==36
    assert proof['full_original_package_version']=='1.17.1' and not proof['historical_packages_installed']
    assert proof['historical_assay_wrapper_skips']==['1.6.0','1.7.0','1.9.0']
    assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items()) and all(pin(ROOT/n)==v for n,v in proof['dependency_pins'].items())
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    parent=read(ROOT/'docs/literature/stringtie-main-workflow/VALIDATION.json')
    assert pin(ROOT/'docs/literature/stringtie-main-workflow/VALIDATION.json')==proof['parent_validation']
    assert all(pin(ROOT/n)==v for n,v in parent['source_pins'].items())
    example=read(BASE/'normalization-example-1/COMPLETED.json');assert len(example['runs'])==9 and example['candidate']==proof['artifact']
    assert all(pin(ROOT/n)==v for n,v in example['source_pins'].items())
    fixture=ROOT/'src/test/resources/org/jlinalg/sequence/signac-normalization';capture=read(fixture/'CAPTURE.json')
    assert pin(fixture/'original-controls.zip')==capture['archive']
    with zipfile.ZipFile(fixture/'original-controls.zip') as archive:
        assert archive.testzip() is None and len(archive.namelist())==len(set(archive.namelist()))==350
        for name,value in capture['member_pins'].items():
            raw=archive.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    audit=read(BASE/'primary-use-audit-3/PRIMARY-USES.json')
    assert (audit['papers_scanned'],audit['catalogued_software_papers'],audit['new_software_papers_pending_curation'],audit['nonsoftware_name_collisions'])==(250,9,1,1)
    assert [r['paper_id'] for r in audit['records'] if r['classification']=='uncatalogued-reported-software-use-requires-source-curation']==['P134']
    setup=read(BASE/'reference-setup-1/RESULT.json')
    assert setup['status']=='INSTALLED_ORIGINAL_SIGNAC_REFERENCE_ONLY_NOT_ANALYSIS_ADMISSION' and setup['base_library_unchanged'] and setup['base_files']==23902
    # Rehash after the original controls too; read-only intent alone is not an integrity proof.
    before=read(BASE/'reference-setup-1/BASE-BEFORE.json');after=inventory(ROOT/'build/omics-sctransform-r');assert after==before
    private=inventory(BASE/'reference-setup-1/R-lib')
    catalog=ROOT/'docs/literature/omics-2023-2026';checksums={}
    for line in (catalog/'SHA256SUMS').read_text().splitlines():
        digest,name=line.split('  ',1);relative=PurePosixPath(name);assert not relative.is_absolute() and '..' not in relative.parts
        assert pin(catalog/name)['sha256']==digest;checksums[name]=pin(catalog/name)
    assert len(checksums)==402
    summary=read(catalog/'summary.json');assert summary['papers']==250 and summary['paper_tool_observations']==2442 and summary['distinct_named_tools']==387
    counts=summary['implementation_status_counts'];assert counts['native-subset-validated']==16 and counts['upstream-adapter-validated']==52 and counts['missing']==259
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    sources=dict(proof['source_pins']);sources.update(example['source_pins'])
    for name in ['tools/literature/freeze_signac_normalization_development.py','docs/literature/SIGNAC_NORMALIZATION_DEVELOPMENT.md']:
        sources[name]=pin(ROOT/name)
    files={}
    def add(path,name=None):
        name=name or path.relative_to(ROOT).as_posix();raw=path.read_bytes()
        assert name not in files or files[name]==raw;files[name]=raw
    for name in sources:add(ROOT/name,'current-source/'+name)
    for name in ['primary-use-audit-1','primary-use-audit-2','primary-use-audit-3','normalization-original-1','normalization-original-2']:
        for path in sorted((BASE/name).rglob('*')):
            if path.is_file():add(path)
    for path in sorted((BASE/'source-intake-1').rglob('*')):
        if path.is_file() and ('source' not in path.relative_to(BASE/'source-intake-1').parts or path.name=='preprocessing.R'):add(path)
    for path in sorted((BASE/'reference-setup-1').rglob('*')):
        if path.is_file() and not {'R-lib','downloads'}.intersection(path.relative_to(BASE/'reference-setup-1').parts):add(path)
    for folder in [matrix,BASE/'normalization-example-1']:
        for path in sorted(folder.rglob('*')):
            if path.is_file() and (path.suffix in ['.json','.stdout','.stderr','.java']):add(path)
    runtime=dict(status='reference-only-contents-not-portable-scientific-runtime-admission',base_library_unchanged_after_controls=True,
                 base_library_file_count=len(after),private_library_file_count=len(private),private_library_pins=private,
                 Rscript=pin(Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe')),
                 warning='These content hashes establish local integrity, not a portable DLL/dependency admission contract or signatures. Runtime binaries are not distributed here.')
    files['reference-runtime-inventory.json']=(json.dumps(runtime,indent=2)+'\n').encode()
    catalog_snapshot=dict(status='unchanged-catalog-during-Signac-foundation-work',checksums=checksums,summary=summary,
                          reviewed_use_count=827,new_integration_count=68,including_existing=71,missing=259,
                          pending_additional_use='P134 Signac logistic-regression accessibility role; explicit source finding awaits catalog curation')
    files['unchanged-catalog.json']=(json.dumps(catalog_snapshot,indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS

SKIP3 — Historical assay wrappers1.6.0/1.7.0/1.9.0 are incompatible with installed SeuratObject5.4.0. Skip their whole-assay execution on this runtime. For historical reproduction provide an isolated compatible SeuratObject/Signac installation and validate it; do not rewrite original code or hide defunct-argument errors. Their extracted matrix normalization/metadata functions passed separately.

Installation warnings — Bioconductor3.23 annotation Windows binary indexes returned404. Required software packages installed and current full Signac1.17.1 loaded. No annotation-dependent analysis was attempted. No repair is needed for native normalization. Before a future genome-dependent analysis install and verify compatible permitted species annotations; if unavailable, name the missing package and skip that analysis.

Native domain — Only nonnegative integer counts, sorted unique CSC coordinates, finite positive scale/IDF and row/column totals<=2^53-1 are admitted. Correct invalid input or use a separately validated route for a different domain. Never round silently. Review named empty-cell/feature QC warnings. Infinite output is retained: skip finite-only downstream analysis, inspect parameters and rerun with a justified correction; never clip silently.

Incomplete workflow — Native normalization and feature selection are not a full Signac integration. LSI/counting/QC/gene activity/motifs/LinkPeaks/differential accessibility and a public typed workflow remain. The new P134 software-use observation remains pending source curation; P007 SignAC is an institutional name. No public workflow, real study analysis, catalog promotion, biological certification, release, deployment, push or completion of the broader goal is claimed.
'''
    TARGET.mkdir();(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf-8',newline='\n')
    files['WARNINGS.md']=warnings.encode()
    archive_path=TARGET/'controls.zip'
    with zipfile.ZipFile(archive_path,'x') as archive:
        for name,raw in sorted(files.items()):
            relative=PurePosixPath(name);assert not relative.is_absolute() and '..' not in relative.parts
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
    with zipfile.ZipFile(archive_path) as archive:
        assert archive.testzip() is None and len(archive.namelist())==len(set(archive.namelist()))==len(files)
        assert all(archive.read(name)==raw for name,raw in files.items())
    result=dict(proof)
    result.update(source_pins=sources,source_pin_count=len(sources),archive=pin(archive_path),archive_members=len(files),
                  archive_file_pins={n:dict(bytes=len(v),sha256=hashlib.sha256(v).hexdigest()) for n,v in sorted(files.items())},
                  example_checks=9,papers_scanned=250,cataloged_Signac_software_papers=9,additional_pending_software_papers=['P134'],institutional_name_collision=['P007'],
                  base_library_unchanged_after_controls=True,base_library_files=23902,private_reference_library_files=len(private),
                  catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,
                  retained_failed_attempts=['primary-use-audit-1','normalization-original-1'],
                  warnings_document=pin(TARGET/'WARNINGS.md'),released=False,pushed=False,deployed=False,whole_goal_complete=False)
    save(TARGET/'VALIDATION.json',result)
    assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    print(json.dumps(dict(status=result['status'],archive=result['archive'],members=len(files),source_pins=len(sources),base_library_unchanged=True)))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific evidence checks')
    main()
