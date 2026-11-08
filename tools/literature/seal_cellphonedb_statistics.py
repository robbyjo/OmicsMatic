# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal original CellPhoneDB prepared statistics, exact three-JVM controls, unchanged runtime inventories and failures; full tool remains missing."""
import json,subprocess,sys,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin,pack

BASE=ROOT/'build/literature-review/cellphonedb-runtime'
DEST=ROOT/'docs/literature/cellphonedb-statistics-development'


def main():
    assert not DEST.exists();build=BASE/'statistics-build-3';validation=BASE/'statistics-validation-1';compiled=json.loads((build/'COMPLETED.json').read_bytes());receipt=json.loads((validation/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_EXACT_COMPLETE_ORIGINAL_NATIVE_CELLPHONEDB_PREPARED_KERNELS_THREE_JVMS_ONLY';jar=ROOT/compiled['artifact']['path'];assert pin(jar)=={k:compiled['artifact'][k] for k in ['bytes','sha256']};sources=dict(receipt['source_pins']);setup=ROOT/'build/cellphonedb-case-sensitive-1/setup-1'
    for name,want in json.loads((setup/'COMPLETED.json').read_bytes())['source_pins'].items():assert name not in sources or sources[name]==want;sources[name]=want
    for name in ['intake_cellphonedb_sources.py','prepare_cellphonedb_use_reviews.py','cellphonedb-use-reviews.json','cellphonedb-named-software-registry.json','generate_cellphonedb_statistics_fixtures.py','collect_cellphonedb_statistics_linux.py','cellphonedb_statistics_evidence.py','seal_cellphonedb_statistics.py','test_cellphonedb_statistics_comparison.py','signac_scan_evidence.py']:sources['tools/literature/'+name]=pin(ROOT/'tools/literature'/name)
    sources['docs/literature/CELLPHONEDB_STATISTICS_DEVELOPMENT.md']=pin(ROOT/'docs/literature/CELLPHONEDB_STATISTICS_DEVELOPMENT.md');assert all(pin(ROOT/n)==want for n,want in sources.items());files={'current-source/'+name:(ROOT/name).read_bytes() for name in sources}
    def tree(directory,prefix,top_only=False):
        for path in sorted(directory.iterdir() if top_only else directory.rglob('*')):
            if not path.is_file() or path.suffix=='.jar' or 'classes' in path.relative_to(directory).parts or '__pycache__' in path.parts:continue
            assert not path.is_symlink();files[prefix+'/'+path.relative_to(directory).as_posix()]=path.read_bytes()
    tree(build,'build');tree(validation,'validation');tree(BASE/'statistics-original-2','original',True);tree(setup,'setup',True);tree(BASE/'paper-review-drafts-1','paper-drafts',True)
    for directory,prefix in [('statistics-original-1','original-1'),('statistics-build-1','build-1'),('statistics-build-2','build-2'),('statistics-fixtures-1','fixtures-1'),('statistics-fixtures-2','fixtures-2')]:tree(BASE/directory,'history/'+prefix,directory.startswith('statistics-original'))
    for directory,prefix in [('source-intake-1','history/source-intake-1'),('source-intake-2','source-intake')]:
        tree(BASE/directory,prefix,True)
        for version in ['5.0.1','5.0.0','2.1.4']:
            metadata=BASE/directory/version/'pypi-metadata.json'
            if metadata.is_file():files[prefix+'/'+version+'/pypi-metadata.json']=metadata.read_bytes()
    tree(BASE/'source-intake-2/5.0.1','source-intake/5.0.1')
    command=[sys.executable,'-B',str(ROOT/'tools/literature/test_cellphonedb_statistics_comparison.py')];run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);files['comparison-tests/stdout']=run.stdout;files['comparison-tests/stderr']=run.stderr;files['comparison-tests/process.json']=(json.dumps(dict(command=command,exit_code=run.returncode),indent=2)+'\n').encode();assert run.returncode==0;entries={}
    with zipfile.ZipFile(jar) as bundle:
        for name in compiled['added_entries']:raw=bundle.read(name);files['candidate-added-entries/'+name]=raw;entries[name]=byte_pin(raw)
    history=dict(status='complete-failed-attempts-retained-no-partial-batch-admission',source_intake_1='Intake checker initially required an actual bundled MIT notice for every published edition. The5.0.0 wheel/sdist and2.1.4 sdist declare MIT but omit a bundled body. A new intake records that fact, requires the actual5.0.1 notice for development, and grants no older-edition runtime/public-source-bundling credit. Raw older distributions remain local; this public bundle includes only metadata and the5.0.1 complete MIT-noticed source.',original_1='The original DEG helper fails with TypeError on an empty complex-composition table under bound pandas2.2.3. No partial batch was admitted. New fixtures explicitly omit DEG requests from noncomplex statistical controls, preserve original expression data, and separately assert the original error; Java rejects the unsupported DEG family. No original package code changed.',build_1_and_2='Successful compilation checkpoints before the empty-composition boundary and source float32 arithmetic were fully bound. Neither received scientific validation or implementation credit. Complete captured sources/compile logs remain retained.',precision='Actual original input, cluster and interaction dtypes are float32 in the corresponding case. The final new Java component explicitly requires source-representable float32 inputs and retains each float accumulation/division/interaction rounding. All13 complete cases then match exactly on all3JVMs. No tolerance was introduced.',scope='Prepared numerical components only. CellPhoneDB remains missing; unapplied paper/registry drafts do not alter effective catalog counts. No database, full execution adapter, historical-version equivalence or biological inference is admitted.')
    files['history/CLASSIFICATION.json']=(json.dumps(history,indent=2)+'\n').encode();assert sum(map(len,files.values()))<134217728;DEST.mkdir();(DEST/'.gitattributes').write_bytes(b'* -text\n');packing=pack(DEST/'controls.zip',files)
    save(DEST/'VALIDATION.json',dict(schema_version=1,status='passed-exact-native-CellPhoneDB-prepared-components-only',archive=pin(DEST/'controls.zip'),packing=packing,source_pins=sources,artifact=compiled['artifact'],parent_artifact=compiled['parent_artifact'],packaged_entries=entries,checks=receipt['checks'],original_checks=receipt['original_checks'],canonical_executable_unchanged=receipt['canonical_executable_unchanged'],implementation_credit=False,full_CellPhoneDB_workflow_admitted=False,whole_goal_complete=False,released_or_deployed=False))
    from cellphonedb_statistics_evidence import verify
    verify();assert all(pin(ROOT/n)==want for n,want in sources.items());print('Sealed',len(files),'complete component evidence records;',pin(DEST/'controls.zip')['bytes'],'ZIP bytes; CellPhoneDB remains missing')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete component sealing checks')
    main()
