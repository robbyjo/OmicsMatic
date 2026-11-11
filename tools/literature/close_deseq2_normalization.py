# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the native normalization milestone without promoting the whole DESeq2 tool."""
import hashlib
import json
from pathlib import Path
import zipfile
import xml.etree.ElementTree as ET
from setup_deseq2_reference import pin, save

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/deseq2-runtime'
EVIDENCE = ROOT / 'docs/literature/deseq2-normalization-development'


def read(path):
    return json.loads(path.read_bytes())


def main():
    assert not EVIDENCE.exists(); EVIDENCE.mkdir()
    matrix = read(BASE / 'normalization-matrix-2/COMPLETED.json')
    assert matrix['tests_per_JVM'] == 119 and matrix['actual_main_protocols'] == 15
    assert len(matrix['runs']) == 3 and all(r['passed'] == 119 and r['skipped'] == 0 for r in matrix['runs'])
    examples = read(BASE / 'normalization-examples-1/COMPLETED.json')
    assert examples['original_R_calls'] == 2 and examples['existing_output_refusals'] == 2
    setup = read(BASE / 'reference-setup-1/RESULT.json')
    forbidden = read(BASE / 'reference-forbidden-setup-1/RESULT.json')
    assert setup['status'] == 'INSTALLED_ORIGINAL_DESEQ2_REFERENCE_ONLY' and setup['preexisting_libraries_unchanged']
    assert setup['preexisting_file_count'] == 27958 and not setup['analysis_admitted']
    assert forbidden['status'] == 'SKIP3_DESEQ2_REFERENCE_UNAVAILABLE' and not forbidden['installation_authorized'] and not forbidden['analysis_admitted'] and forbidden['preexisting_libraries_unchanged']
    for name, record in [(path, expected) for path, expected in matrix['source_and_fixture_pins'].items()] + list(examples['source_pins'].items()):
        assert pin(ROOT / name) == record, 'Validated source changed: ' + name
    for result in examples['examples']:
        for name, expected in result['files'].items():
            assert pin(ROOT / result['path'] / name) == expected
    normal_tests = list((BASE / 'normalization-build-1').glob('TEST-*.xml'))
    assert len(normal_tests) == 2
    normal_counts = [ET.parse(p).getroot().attrib for p in normal_tests]
    assert sum(int(r['tests']) for r in normal_counts) == 119 and all(r['failures'] == r['errors'] == r['skipped'] == '0' for r in normal_counts)
    artifact = ROOT / matrix['artifact']['path']; assert pin(artifact) == {k: matrix['artifact'][k] for k in ('bytes', 'sha256')}
    assert pin(BASE / 'normalization-development' / artifact.name) == pin(artifact)
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == matrix['canonical_artifact_unchanged']
    baseline = read(ROOT / 'build/literature-review/github-checkpoint-20261104/worktree-manifest.json')
    assert all(pin(ROOT / p['path']) == {k: p[k] for k in ('bytes', 'sha256')} for p in baseline['files']), 'A pre-existing worktree file changed'
    summary = read(ROOT / 'docs/literature/omics-2023-2026/summary.json')
    assert summary['papers'] == 250 and summary['distinct_named_tools'] == 387
    assert summary['implementation_status_counts']['native-subset-validated'] == 16 and summary['implementation_status_counts']['upstream-adapter-validated'] == 53 and summary['implementation_status_counts']['missing'] == 258
    warnings = '''# DEVELOPMENT: NATIVE NORMALIZATION ONLY

This milestone implements the raw-integer median-location subset of DESeq2 1.52.0 size-factor estimation. It does not implement differential inference, dispersion fitting, VST, rlog, shrinkage, quantifier offsets, pseudobulk assembly or a whole-paper analysis. No new catalog credit, public release, push, deployment or main-dispatcher route is claimed.

## Undefined factors: action required before a later fit

NA, NaN, zero and infinite factors are retained, not repaired. A completed normalization can have usable_for_differential_fit=false. Review counts, zeros, explicit means and control selection; preserve the attempt and choose a new output directory after correction. The flag checks numeric prerequisites only, not biological design adequacy.

## FAIL2: no result admitted

Read the named error, WARNINGS.md, analysis-status.json and manifest. Partial tables in a failed attempt are not results. Correct malformed/fractional/negative counts, duplicate or mismatched identities, invalid means/controls, or capacity/permission errors. Never silently round, rename or drop observations. Existing output directories are refused and preserved.

## External inference requirements and SKIP3

This Java normalization command needs no R runtime. A separate validated upstream workflow remains necessary for DESeq2 inference. If a needed runtime cannot be installed or installation is forbidden, skip that dependent analysis and name the required software and recovery steps. The forbidden-install control created SKIP3 evidence without analysis or library mutation. It is a policy control, not a claim that installation was forbidden for this permitted reference setup.

## Preserved attempts and validation limits

The first official-host archive download returned HTTP403; the listed Posit mirror succeeded. The first Java comparison failed two cases involving all-NaN supplied means; the guard was corrected and all cases passed. The first platform matrix overcounted actual command protocols by three; its metadata correction and old validator are retained. A new matrix ran all checks plus an actual help command on each platform, supporting the final count of 15 protocols.

The reference runtime used current R4.6.1 dependencies, not complete historical environments for the 47 papers. The loaded original function matched source body/formals. Finite numerical comparisons use relative tolerance 2e-12; missing/nonfinite kinds are checked separately. No biological validity, exact historical package behavior, cryptographic authenticity or full project-suite validation is claimed. Original-package setup includes a small synthetic smoke fit; it does not admit a user inference workflow.

Build and run instructions are in the development guide and examples/deseq2-normalization/README.md. The frozen development JAR is local; its path and SHA-256 are recorded in VALIDATION.json.
'''
    (EVIDENCE / 'WARNINGS.md').write_text(warnings, encoding='utf-8', newline='\n')
    sources = {n: v for n, v in matrix['source_and_fixture_pins'].items() if not n.startswith('build/')}
    sources.update(examples['source_pins'])
    for p in [Path(__file__), ROOT / 'docs/literature/DESEQ2_NORMALIZATION_DEVELOPMENT.md', EVIDENCE / 'WARNINGS.md']:
        sources[p.relative_to(ROOT).as_posix()] = pin(p)
    paths = set(ROOT / n for n in sources)
    for folder in ['reference-intake-1', 'reference-intake-2/source', 'reference-setup-1',
                   'reference-forbidden-setup-1', 'size-factor-controls-1', 'size-factor-controls-2',
                   'normalization-matrix-1', 'normalization-matrix-2', 'normalization-examples-1']:
        for p in (BASE / folder).rglob('*'):
            if p.is_file() and 'R-lib' not in p.parts and 'downloads' not in p.parts:
                paths.add(p)
    paths.add(BASE / 'reference-intake-2/DOWNLOADS.json')
    paths.update(normal_tests)
    paths.add(BASE / 'normalization-build-1/isolated-executable.init.gradle')
    for result in examples['examples']:
        paths.update(p for p in (ROOT / result['path']).rglob('*') if p.is_file())
    entries = {p.relative_to(ROOT).as_posix(): pin(p) for p in sorted(paths)}
    archive = EVIDENCE / 'controls.zip'
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for p in sorted(paths):
            z.writestr(p.relative_to(ROOT).as_posix(), p.read_bytes())
        z.writestr('BUNDLE-FILES.json', json.dumps(entries, indent=2) + '\n')
    with zipfile.ZipFile(archive) as z:
        assert len(z.namelist()) == len(entries) + 1 and len(z.namelist()) == len(set(z.namelist()))
        for name, expected in entries.items():
            raw = z.read(name)
            assert {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()} == expected
    validation = {'schema_version': 1, 'status': 'passed-native-DESeq2-size-factor-development-not-whole-tool',
         'artifact': matrix['artifact'], 'archive': {'path': 'controls.zip', **pin(archive)},
         'archive_members': len(entries) + 1, 'source_pins': sources,
         'original_package_version': '1.52.0', 'loaded_function_body_and_formals_match_source': True,
         'original_package_cases': 76, 'seeded_random_matrices': 48, 'normal_Gradle_tests': 119,
         'platform_runs': matrix['runs'], 'actual_main_protocols': 15, 'literal_examples': 2,
         'literal_example_original_R_calls': 2, 'literal_example_numeric_comparisons': examples['finite_numeric_comparisons'],
         'literal_example_existing_output_refusals': 2, 'preexisting_R_library_files_unchanged': 27958,
         'forbidden_install_control': 'SKIP3; no analysis; preexisting libraries unchanged',
         'first_numeric_failure_preserved': True, 'first_protocol_count_correction_preserved': True,
         'prior_executable_file_entries_unchanged': True, 'new_normalization_class_entries': 7,
         'canonical_artifact_unchanged': matrix['canonical_artifact_unchanged'],
         'preexisting_worktree_files_unchanged': len(baseline['files']),
         'catalog_papers': 250, 'catalog_distinct_tools': 387, 'new_credited_integrations': 69,
         'including_existing_adapters': 72, 'missing_tools': 258, 'catalog_implementation_credit': False,
         'DESeq2_catalog_status': 'method-overlap', 'root_dispatch_available': False,
         'full_DESeq2_inference_available': False, 'historical_whole_packages_executed': False,
         'full_project_test_suite_run': False, 'released': False, 'pushed': False, 'deployed': False,
         'whole_goal_complete': False, 'warnings_document': pin(EVIDENCE / 'WARNINGS.md')}
    save(EVIDENCE / 'VALIDATION.json', validation)
    print(json.dumps({k: validation[k] for k in ('status', 'archive', 'archive_members', 'new_credited_integrations', 'missing_tools')}))


if __name__ == '__main__':
    main()
