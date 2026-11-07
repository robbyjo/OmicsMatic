# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete source-bound ChIPseeker native/original/root controls; never promote the catalog."""
import argparse
import json
from pathlib import Path
import tarfile
import zipfile
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin

BASE=ROOT/'build/literature-review/chipseeker-runtime'
CURRENT=['native-build-4','files-build-4','annotation-build-3','root-build-1','native-matrix-4','files-matrix-4',
 'nearest-original-3','fixture-intake-2','integer-boundary-source-audit-1','nclist-order-source-intake-1',
 'overlap-order-probe-2','annotation-original-2','annotation-extended-original-1','annotation-matrix-3',
 'annotation-extended-matrix-3','annotation-recovery-4','annotation-source-audit-1','annotation-edition-deltas-1',
 'txdb-native-matrix-1','health-fixture-audit-1','unicode-sqlite-probe-1','reference-setup-4','root-example-1']
HISTORY=['source-intake-1','reference-setup-1','reference-setup-2','reference-setup-3','annotation-original-1',
 'native-Windows-checks-2','native-Windows-checks-3','overlap-order-probe-1','annotation-recovery-2','annotation-recovery-3']


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    target=ROOT/'docs/literature/chipseeker-annotation-development';assert not target.exists();target.mkdir()
    files={};excluded=[]
    def add(path,logical=None):
        name=logical or path.relative_to(ROOT).as_posix();raw=path.read_bytes()
        if name in files:assert files[name]==raw
        files[name]=raw
    for folder in CURRENT:
        assert (BASE/folder).exists(),folder
        for path in sorted((BASE/folder).rglob('*')):
            if not path.is_file():continue
            parts=path.relative_to(BASE/folder).parts
            if 'R-lib' in parts or 'downloads' in parts or path.name.endswith('.jar') or path.name.endswith('.tar.gz'):
                excluded.append(dict(path=path.relative_to(ROOT).as_posix(),**pin(path)));continue
            add(path)
    # Earlier failed attempts remain local; retain their diagnostics and source
    # snapshots here without relabelling them as successful current controls.
    for folder in HISTORY:
        for path in sorted((BASE/folder).rglob('*')):
            if not path.is_file():continue
            parts=path.relative_to(BASE/folder).parts
            if 'R-lib' in parts or 'downloads' in parts or path.name.endswith(('.jar','.tar.gz')):continue
            if path.suffix in {'.json','.log','.stdout','.stderr','.md','.py','.R'} or 'source-snapshot' in parts:add(path)
    for version in ['1.22.1','1.26.2','1.34.1','1.48.1']:
        intake=BASE/'source-intake-2'/version;record=json.loads((intake/'SOURCE.json').read_bytes())
        assert pin(intake/('ChIPseeker_'+version+'.tar.gz'))==record['archive'];add(intake/'SOURCE.json')
        for name,expected in record['source_members'].items():
            path=intake/'selected-source'/name;assert pin(path)==expected;add(path)
    for path in (BASE/'txdbmaker-source-intake-1').rglob('*'):
        if path.is_file() and not path.name.endswith('.tar.gz'):add(path)
    for name in ['probe_unicode_sqlite_1.R','probe_unicode_sqlite_1.py','run_fixture_preflight_1.py']:add(BASE/name)
    # Dependency archives are not redistributed as licensed runtime binaries.
    # Their exact URLs, repository rows and complete byte identities are retained.
    downloads={}
    for setup in ['reference-setup-2','reference-setup-3']:
        folder=BASE/setup
        for path in sorted((folder/'downloads').glob('*')):
            if path.is_file():
                if path.suffix=='.json':add(path)
                else:downloads[path.relative_to(ROOT).as_posix()]=pin(path)
    files['dependency-downloads.json']=(json.dumps(downloads,indent=2)+'\n').encode()
    # Explicitly retain the single deliberately changed source member used only
    # to prove refusal. Never package that installed library as an admissible runtime.
    changed=BASE/'annotation-recovery-4/deliberately-changed-source';meta=json.loads((changed/'DELIBERATE-CHANGE.json').read_bytes())
    modified=changed/'TAMPERED_NOT_ORIGINAL_ChIPseeker_1.48.1.tar.gz';assert pin(modified)==meta['archive']
    member=meta['changes'][0]['member']
    with tarfile.open(modified) as archive:raw=archive.extractfile(member).read()
    files['negative-control-only/modified/'+member]=raw
    original=BASE/'source-intake-2/1.48.1/selected-source'/member
    files['negative-control-only/original/'+member]=original.read_bytes()
    assert raw==original.read_bytes().replace(b'sameStrand = FALSE',b'sameStrand = TRUE')
    paths=set()
    paths.update((ROOT/'src/main/java/org/jlinalg/cli').glob('ChipSeeker*.java'))
    paths.add(ROOT/'src/main/java/org/jlinalg/genetics/ChipSeekerNearestFeatures.java')
    paths.update(p for p in (ROOT/'src/main/resources/org/jlinalg/omics/chipseeker-annotation').rglob('*') if p.is_file())
    paths.add(ROOT/'src/test/java/org/jlinalg/cli/ChipSeekerNearestFeaturesTest.java')
    paths.update(p for p in (ROOT/'src/test/resources/org/jlinalg/genetics/chipseeker-nearest').rglob('*') if p.is_file())
    paths.update(p for p in (ROOT/'examples/chipseeker').rglob('*') if p.is_file())
    paths.update((ROOT/'tools/literature').glob('*chipseeker*'))
    paths.update(ROOT/name for name in ['.gitattributes','src/main/java/org/jlinalg/cli/JLinAlgCli.java',
        'docs/literature/CHIPSEEKER_ANNOTATION_DEVELOPMENT.md','docs/literature/CHIPSEEKER_NEAREST_DEVELOPMENT.md'])
    paths={p for p in paths if p.is_file()}
    source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in sorted(paths)}
    for path in paths:add(path,'current-source/'+path.relative_to(ROOT).as_posix())
    artifacts={};entries={}
    for name,folder in [('native','native-build-4'),('files','files-build-4'),('annotation','annotation-build-3'),('root','root-build-1')]:
        build=json.loads((BASE/folder/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path']
        assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};artifacts[name]=artifact
        with zipfile.ZipFile(jar) as archive:entries[name]={n:dict(byte_pin(archive.read(n)),directory=archive.getinfo(n).is_dir()) for n in archive.namelist()}
    parent=json.loads((BASE/'native-build-4/COMPLETED.json').read_bytes())['parent_artifact'];artifacts['parent']=parent
    with zipfile.ZipFile(ROOT/parent['path']) as archive:entries['parent']={n:dict(byte_pin(archive.read(n)),directory=archive.getinfo(n).is_dir()) for n in archive.namelist()}
    for name,values in entries.items():files['artifact-entries/'+name+'.json']=(json.dumps(values,indent=2)+'\n').encode()
    with zipfile.ZipFile(ROOT/artifacts['root']['path']) as archive:
        owned=[n for n in entries['root'] if n not in entries['parent'] or entries['root'][n]!=entries['parent'][n]]
        for name in owned:files['candidate-owned-entries/'+name]=archive.read(name)
    assert all(pin(ROOT/name)==value for name,value in source_pins.items())
    warning='# CHIPSEEKER WARNINGS AND REQUIRED ACTIONS\n\n'
    warning+='Development workflow only. Require analysis-status.json result_admitted=true and read per-case source statuses, original warnings and process receipts.\n\n'
    warning+='SKIP3: exact R4.6.1, requested ChIPseeker edition, dependency versions, library or UTF-8 locale unavailable. Provision named dependencies only where permitted; if installation is forbidden, skip dependent annotation. Runtime analysis installs nothing. Native NEAREST does not require R.\n\n'
    warning+='FAIL2: no result admitted from an affected atomic batch, including returned/partial R objects. Preserve diagnostics, correct the named cause and retry into NEW paths. Do not drop queries, clip coordinates, repair IDs or substitute software editions to force agreement.\n\n'
    warning+='224 original reference calls:175 returned,49 original errors. Core size-restoration failures and the1.34.1 subset-priority error remain failures. Source classifiers differ between editions; numeric geneChr/geneStrand codes, missing values, dummy boundaries and dropped queries are retained, not repaired or treated as biological absence. The deliberately modified same-version package is negative-control software and was refused.\n\n'
    warning+='Windows original annotation is exercised on Java21/25; Linux Java21 native/root routing is exercised. Original Linux R fitting is not platform-certified by these records. Historic dependency environments, complete package APIs/plots, whole papers, biological validity and execution/native-DLL source authenticity are not certified. No portable VERIFY mode is provided.\n'
    (target/'WARNINGS.md').write_text(warning,encoding='utf-8',newline='\n')
    record=pack(target/'controls.zip',files)
    proof=dict(schema_version=1,status='passed-scoped-ChIPseeker-native-original-annotation-and-root-workflows',
        archive=pin(target/'controls.zip'),warnings_document=pin(target/'WARNINGS.md'),source_pins=source_pins,artifact=artifacts['root'],artifact_chain=artifacts,
        exact_profiles=['1.22.1','1.26.2','1.34.1','1.48.1'],original_reference_calls=224,original_returned_reference_calls=175,original_reference_errors=49,
        native_tests_per_JVM=202,native_JVMs=3,actual_core_returned_comparisons=304,actual_core_original_errors=96,core_native_retained_triples=20976,
        extended_returned_comparisons=23,extended_original_errors=1,recovery_controls=16,excluded_runtime_or_large_artifact_files=excluded,
        canonical_executable_unchanged=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar'),
        original_genomic_classification_reimplemented=False,native_nearest_subset_reimplemented=True,historical_dependency_environment=False,
        native_DLL_source_authentication=False,execution_authentication=False,whole_paper_reproduced=False,biological_validity_certified=False,
        full_project_test_suite_run=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,**record)
    save(target/'VALIDATION.json',proof)
    (target/'SHA256SUMS').write_text(''.join(pin(target/name)['sha256']+'  '+name+'\n' for name in ['controls.zip','WARNINGS.md','VALIDATION.json']),encoding='utf-8',newline='\n')
    save(out/'COMPLETED.json',dict(status='sealed-ChIPseeker-source-and-complete-control-evidence-not-catalog-admission',archive=proof['archive'],validation=pin(target/'VALIDATION.json'),
        logical_files=record['logical_files'],source_files=len(source_pins),artifact=artifacts['root']))
    print(json.dumps(dict(archive=proof['archive'],logical_files=record['logical_files'],source_files=len(source_pins))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete evidence identity checks')
    main()
