# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete native helper controls and explicit exclusions; award no Monocle workflow credit."""
import json
from pathlib import Path
import zipfile
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack, byte_pin


def main():
    base=ROOT/'build/literature-review/monocle-runtime'
    target=ROOT/'docs/literature/monocle3-pseudotime-development'
    assert not target.exists();target.mkdir()
    build=json.loads((base/'native-build-1/COMPLETED.json').read_bytes())
    matrix=json.loads((base/'native-matrix-2/COMPLETED.json').read_bytes())
    example=json.loads((base/'example-1/COMPLETED.json').read_bytes())
    assert build['artifact']==matrix['artifact']==example['artifact']
    assert matrix['tests_per_JVM']==529 and len(matrix['JVMs'])==3
    assert all(r['exit_code']==0 and r['tests']==529 and r['skipped']==0 for r in matrix['JVMs'])
    assert len(example['runs'])==2 and all(r['exit_code']==0 for r in example['runs'])
    files={}
    # Every original successful/failed helper outcome and RDS is retained.
    # Invalid first transports/test readers are preserved and explicitly excluded.
    for name in ['pseudotime-original-1','pseudotime-original-2','pseudotime-fixture-1','native-matrix-1','native-matrix-2','example-1']:
        for path in (base/name).rglob('*'):
            if path.is_file():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for path in (base/'native-build-1').rglob('*'):
        if path.is_file() and path.suffix!='.jar':files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['COMPLETED.json','SOURCE.json']:
        for path in (base/'source-intake-1').rglob(name):files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['RESULT.json','R-RESULT.json','WARNINGS.md','installer.stdout','installer.stderr','BASES-BEFORE.json','BASES-AFTER.json']:
        path=base/'reference-setup-1'/name
        assert path.is_file();files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    source_names=['src/main/java/org/jlinalg/singlecell/Monocle3Pseudotime.java',
        'src/test/java/org/jlinalg/cli/Monocle3PseudotimeTest.java',
        'src/test/resources/org/jlinalg/singlecell/monocle3-pseudotime/original-controls-v1.json.gz',
        'docs/literature/MONOCLE3_PSEUDOTIME_DEVELOPMENT.md','.gitattributes',
        'docs/literature/omics-2023-2026/monocle-checkout-shared-source-compatibility.json']
    source_names+=['tools/literature/'+n for n in ['intake_monocle_sources.py','collect_monocle_pseudotime.py',
        'collect_monocle_pseudotime.R','prepare_monocle_pseudotime_fixture.py','build_monocle_pseudotime.py',
        'validate_monocle_pseudotime.py','seal_monocle_pseudotime.py','monocle_pseudotime_evidence.py']]
    paths=[ROOT/n for n in source_names]
    for folder in [ROOT/'src/main/resources/org/jlinalg/omics/monocle3-pseudotime',ROOT/'examples/monocle3-pseudotime']:
        paths+=sorted(p for p in folder.rglob('*') if p.is_file())
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
    files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in sources})
    parent=ROOT/build['parent_artifact']['path'];candidate=ROOT/build['artifact']['path']
    assert pin(candidate)=={k:build['artifact'][k] for k in ['bytes','sha256']}
    assert pin(parent)=={k:build['parent_artifact'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(candidate) as new:
        before={n:byte_pin(old.read(n)) for n in old.namelist()}
        after={n:byte_pin(new.read(n)) for n in new.namelist()}
        assert all(after[n]==v for n,v in before.items())
        assert set(after)-set(before)==set(build['added_entries']) and len(build['added_entries'])==28
        files['artifact-parent-entries.json']=(json.dumps(before,sort_keys=True)+'\n').encode()
        files['artifact-candidate-entries.json']=(json.dumps(after,sort_keys=True)+'\n').encode()
        for name in build['added_entries']:files['candidate-owned-entry/'+name]=new.read(name)
    archive=target/'controls.zip';layout=pack(archive,files)
    warning=target/'WARNINGS.md'
    warning.write_text('''# Native Monocle prepared-pseudotime component: scope and recovery

This is a Java API for a caller-prepared projected cell graph, not a complete Monocle workflow. Use MONOCLE3_PSEUDOTIME_DEVELOPMENT.md and the executed example. The canonical/released JAR is unchanged. No catalog integration credit is awarded.

**Retain native warnings prominently.** Preserve root-distance/path-sum overflow and infinite pseudotime. Inspect the complete graph, coordinates, edge weights and root population. Do not repair IDs, impute, rescale or infer biological absence silently.

**Invalid input or a logical-budget exception admits no result.** Preserve the exact inputs, correct the named cause and retry. One-dimensional multiple-root inputs reproduce a genuine original-domain refusal. Logical limits are not heap/OS quotas.

**SKIP3: original Monocle 3 1.3.1 runtime analysis is skipped under the attempted R 4.6.1 profile.** Required archived grr 0.9.5 fails to compile because STRING_PTR is unavailable. Required action: preserve reference-setup-1 and provision a separately validated compatible R/dependency environment, or declare and validate a distinct newer original profile. Do not silently patch grr or substitute an edition. No partial installation admits user analysis. Typed original workflows remain separate work.

The original-helper controls use synthetic prepared graphs and exact original class/accessor/function declarations. They do not load or fit the whole Monocle package. Invalid first transport/test-reader attempts are retained and excluded. No biological, whole-paper, historical-environment or execution-authentication claim is made. Cell Ranger remains excluded.
''',encoding='utf8',newline='\n')
    proof=dict(schema_version=1,status='passed-native-Monocle3-prepared-pseudotime-component',archive=pin(archive),**layout,
        artifact=build['artifact'],parent_artifact=build['parent_artifact'],source_pins=sources,warnings_document=pin(warning),
        unchanged_parent_entries=len(before),added_entries=build['added_entries'],
        canonical_executable_unchanged=build['canonical_executable_unchanged'],
        original_profiles=['1.0.0','1.3.1','1.4.27'],original_calls=519,original_returns=516,original_errors=3,
        invalidated_transport_calls=519,tests_per_JVM=529,JVMs=3,independent_tests_per_JVM=10,example_JVMs=2,
        original_full_package_loaded=False,original_full_package_fit=False,workflow_admitted=False,implementation_credit=False,
        native_DLL_source_authentication=False,execution_authentication=False,biological_validity_certified=False,
        whole_paper_reproduced=False,full_project_test_suite_run=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/n)==v for n,v in sources.items());save(target/'VALIDATION.json',proof)
    (target/'SHA256SUMS').write_text(''.join(pin(p)['sha256']+'  '+p.name+'\n' for p in [archive,warning,target/'VALIDATION.json']),encoding='utf8',newline='\n')
    print(json.dumps({k:proof[k] for k in ['status','archive','original_returns','original_errors','tests_per_JVM','JVMs','implementation_credit']}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/control checks')
    main()
