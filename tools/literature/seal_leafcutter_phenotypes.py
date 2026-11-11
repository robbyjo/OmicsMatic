# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete original/native preprocessing and PCA controls, identities and local failures; no full named-tool credit."""
import json, subprocess, sys, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import byte_pin, pack

BASE = ROOT / 'build/literature-review/leafcutter-runtime'
DEST = ROOT / 'docs/literature/leafcutter-phenotypes-development'


def main():
    assert not DEST.exists()
    build = BASE / 'phenotype-build-1'; validation = BASE / 'phenotype-validation-1'
    compiled = json.loads((build / 'COMPLETED.json').read_bytes()); receipt = json.loads((validation / 'COMPLETED.json').read_bytes())
    assert receipt['status'] == 'PASS_COMPLETE_ORIGINAL_NATIVE_LEAFCUTTER_PHENOTYPES_THREE_JVM_COMPONENT_ONLY'
    jar = ROOT / compiled['artifact']['path']; assert pin(jar) == {k: compiled['artifact'][k] for k in ['bytes', 'sha256']}
    sources = dict(receipt['source_pins'])
    for home in ['r-python-setup-1', 'phenotype-original-1', 'phenotype-pca-audit-1']:
        for name, want in json.loads((BASE / home / 'COMPLETED.json').read_bytes())['source_pins'].items():
            assert name not in sources or sources[name] == want; sources[name] = want
    for name in ['leafcutter_phenotypes_evidence.py', 'seal_leafcutter_phenotypes.py', 'test_leafcutter_phenotype_comparison.py', 'generate_leafcutter_phenotype_fixtures.py', 'intake_leafcutter_phenotype_sources.py', 'signac_scan_evidence.py']:
        sources['tools/literature/' + name] = pin(ROOT / 'tools/literature' / name)
    sources['docs/literature/LEAFCUTTER_PHENOTYPES_DEVELOPMENT.md'] = pin(ROOT / 'docs/literature/LEAFCUTTER_PHENOTYPES_DEVELOPMENT.md')
    assert all(pin(ROOT / name) == want for name, want in sources.items())
    files = {'current-source/' + name: (ROOT / name).read_bytes() for name in sources}

    def tree(directory, prefix, top_only=False):
        for file in sorted(directory.iterdir() if top_only else directory.rglob('*')):
            if file.is_file() and file.name != 'micromamba' and file.suffix != '.jar' and 'classes' not in file.relative_to(directory).parts and '__pycache__' not in file.parts:
                assert not file.is_symlink(); files[prefix + '/' + file.relative_to(directory).as_posix()] = file.read_bytes()
    for path, prefix in [(build, 'build'), (validation, 'validation'), (BASE / 'phenotype-original-1', 'reference'), (BASE / 'phenotype-source-intake-1', 'source-intake'), (BASE / 'phenotype-fixtures-3', 'fixtures'), (BASE / 'phenotype-pca-audit-1', 'PCA-audit'), (BASE / 'phenotype-fixtures-1', 'history/fixtures-1'), (BASE / 'phenotype-fixtures-2', 'history/fixtures-2'), (BASE / 'phenotype-native-preview-1', 'history/native-preview-1'), (BASE / 'phenotype-native-preview-2', 'history/native-preview-2')]:
        tree(path, prefix)
    tree(BASE / 'r-python-setup-1', 'runtime', top_only=True)
    tree(BASE / 'phenotype-evidence-attempt-1', 'history/evidence-attempt-1')
    test = ROOT / 'tools/literature/test_leafcutter_phenotype_comparison.py'
    command = [sys.executable, '-B', str(test)]; run = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=60)
    files['comparison-tests/stdout'] = run.stdout; files['comparison-tests/stderr'] = run.stderr
    files['comparison-tests/process.json'] = (json.dumps(dict(command=command, exit_code=run.returncode, source_pin=pin(test)), indent=2) + '\n').encode()
    assert run.returncode == 0
    entries = {}
    with zipfile.ZipFile(jar) as bundle:
        for name in compiled['added_entries']:
            raw = bundle.read(name); files['candidate-added-entries/' + name] = raw; entries[name] = byte_pin(raw)
    classification = dict(status='retained-local-previews-and-failures-no-partial-analysis-admission', fixtures_1='Initial 55 literal fixtures retained without source-runtime analysis admission.', fixtures_2='57 fixtures included one uppercase randomized-PCA case ID rejected by the native driver. Fixture3 changes only that label to lowercase; original counts/parameters remain intact. Both generator snapshots are retained.', native_preview_1='The driver wrote provisional early-case outputs and rejected the unsafe uppercase ID before any completion marker. These are retained and unadmitted. A separate relative-path launch failed canonical path validation before output creation.', native_preview_2='Complete57case native preview after the case-label correction; no original-comparison admission was inferred. Final validation repeats all57cases on three JVMs using the same built JAR.', PCA_undefined='The unchanged original runtime emits NaN explained-variance ratios in two zero-rank controls. Raw warnings and typed NaN states remain retained; no meaningful covariates or repaired variance is inferred.', scope='Native current-source preprocessing only. Original PCA controls audit the source orientation and solver behavior but do not add native PCA, an execution adapter, R/Stan differential splicing, historical paper identity or full named-tool credit.')
    classification['evidence_attempt_1'] = 'The portable reader incorrectly expected sorted rows in both original .phen and QQ files. The original .phen family preserves input order; QQ files sort their rows. The first complete packed candidate and reader sources are retained as failed/unadmitted. Corrected this reader assertion only; original controls, native source/build, numerical comparator, JVM validation and PCA audit are unchanged.'
    files['history/CLASSIFICATION.json'] = (json.dumps(classification, indent=2) + '\n').encode()
    assert sum(map(len, files.values())) < 134217728
    DEST.mkdir(); (DEST / '.gitattributes').write_bytes(b'* -text\n'); packing = pack(DEST / 'controls.zip', files)
    save(DEST / 'VALIDATION.json', dict(schema_version=1, status='passed-native-LeafCutter-phenotype-preprocessing-component-only', archive=pin(DEST / 'controls.zip'), packing=packing, source_pins=sources, artifact=compiled['artifact'], packaged_entries=entries, checks=receipt['checks'], original_PCA_checks=json.loads((BASE / 'phenotype-pca-audit-1/COMPLETED.json').read_bytes())['checks'], canonical_executable_unchanged=receipt['canonical_executable_unchanged'], implementation_credit=False, full_LeafCutter_workflow_admitted=False, whole_goal_complete=False, released_or_deployed=False))
    from leafcutter_phenotypes_evidence import verify
    verify(); assert all(pin(ROOT / name) == want for name, want in sources.items())
    print('Sealed', len(files), 'complete component evidence files;', pin(DEST / 'controls.zip')['bytes'], 'archive bytes; no LeafCutter tool credit')


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete source/evidence checks')
    main()
