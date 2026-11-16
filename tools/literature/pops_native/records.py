#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the native PoPS development extension and exact saved scientific records."""
from pathlib import Path
import argparse, hashlib, importlib.util, io, json, os, subprocess, zipfile

ROOT = Path(__file__).resolve().parents[3]
DOC = 'docs/literature/pops-native-development'
BASE = '82162f75683ebd651a05f8a21d8e482ca54c2885'


class MissingPrerequisite(Exception):
    pass


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_json(path):
    return json.loads(path.read_bytes())


def pin(data):
    return dict(bytes=len(data), sha256=sha(data))


def git(root, *args):
    result = subprocess.run(['git', *args], cwd=root, capture_output=True)
    if result.returncode:
        raise RuntimeError('Git command failed: ' + repr(args) + ': ' + result.stderr.decode(errors='replace'))
    return result.stdout


def baseline_tree(root):
    tree = {}
    for row in git(root, 'ls-tree', '-rz', '--full-tree', BASE).split(b'\0'):
        if not row:
            continue
        header, name = row.split(b'\t', 1)
        mode, kind, identity = header.decode().split()
        assert kind == 'blob' and mode in ['100644', '100755'], (mode, name)
        tree[name.decode('utf-8')] = dict(mode=mode, git_blob=identity)
    assert len(tree) == 13439, len(tree)
    return tree


def git_blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


def canonical_baseline_bytes(raw, expected):
    if git_blob(raw) == expected:
        return raw
    # Git's Windows text checkout may convert LF to CRLF. This exact byte
    # conversion is the only alternate form; binary files are never normalized.
    assert b'\0' not in raw and b'\r\n' in raw, 'Existing baseline file differs'
    normalized = raw.replace(b'\r\n', b'\n')
    assert git_blob(normalized) == expected, 'Existing baseline file differs beyond line endings'
    return normalized


def properties(data):
    return dict(line.split('=', 1) for line in data.decode('utf-8').splitlines() if line and not line.startswith('#') and '=' in line)


def verify(root=ROOT, private_base=None, rescan_primary=False):
    root = Path(root).resolve()
    status = read_json(root / DOC / 'STATUS.json')
    assert status['status'] == 'PASS_NATIVE_POPS_PREPARED_MATRIX_DEVELOPMENT'
    assert status['baseline_commit'] == BASE and status['new_catalog_credit'] == 0
    sources_raw = (root / DOC / 'SOURCE-PINS.json').read_bytes()
    evidence_raw = (root / DOC / 'EVIDENCE-PINS.json').read_bytes()
    assert pin(sources_raw) == status['source_pins'] and pin(evidence_raw) == status['evidence_pins']
    sources = json.loads(sources_raw)
    evidence = json.loads(evidence_raw)
    for name, expected in {**sources['files'], **evidence['files']}.items():
        assert pin((root / name).read_bytes()) == expected, name
    baseline = read_json(root / DOC / 'BASELINE-SOURCE.json')
    tree = baseline_tree(root)
    assert baseline['commit'] == BASE and set(baseline['files']) == set(tree)
    for name, entry in tree.items():
        expected = baseline['files'][name]
        assert expected['git_blob'] == entry['git_blob'] and expected['mode'] == entry['mode'], name
        raw = (root / name).read_bytes()
        canonical = canonical_baseline_bytes(raw, entry['git_blob'])
        assert pin(canonical) == expected['canonical'], name
    generated = {DOC + '/' + name for name in ['STATUS.json', 'SOURCE-PINS.json', 'EVIDENCE-PINS.json']}
    allowed = set(sources['files']) | set(evidence['files']) | generated
    existing = {s.decode('utf-8') for s in git(root, 'ls-files', '-z').split(b'\0') if s}
    existing |= {s.decode('utf-8') for s in git(root, 'ls-files', '--others', '--exclude-standard', '-z').split(b'\0') if s}
    assert existing == set(tree) | allowed, ('Unexpected or missing source files', sorted(existing - set(tree) - allowed), sorted((set(tree) | allowed) - existing))
    with zipfile.ZipFile(root / DOC / 'VALIDATION-RECORDS.zip') as archive:
        assert len(archive.namelist()) == len(set(archive.namelist()))
        assert set(archive.namelist()) == set(evidence['validation_members'])
        records = {}
        for name, expected in evidence['validation_members'].items():
            data = archive.read(name)
            assert pin(data) == expected, name
            records[name] = data
    load = lambda name: json.loads(records[name])
    for name in ['reference-driver-1', 'cv-driver-1', 'eigen-driver-1', 'java-3', 'linux-replay-driver-2',
                 'tutorial-reference-driver-1', 'tutorial-native-driver-3', 'primary-driver-1', 'baseline-catalog-driver-2']:
        process = load(name + '/PROCESS.json')
        assert process['exit_code'] == 0, name
        if 'source_unchanged' in process:
            assert process['source_unchanged'], name
    for folder in ['java-3', 'linux-replay-driver-2', 'tutorial-native-driver-3']:
        for name, identity in load(folder + '/SOURCE-BEFORE.json').items():
            if name.startswith(('src/', 'tools/')):
                assert sha((root / name).read_bytes()) == identity, (folder, name)
    original = load('original-controls-1/STATUS.json')
    assert original['cases'] == 13 and original['successful_cases'] == 11 and original['retained_expected_failures'] == 2 and original['sort_cases'] == 50
    cv = load('cv-original-1/STATUS.json')
    assert cv['models'] == 9 and cv['penalties_per_model'] == 25 and not cv['function_replacement_performed']
    eigen = load('eigen-original-1/STATUS.json')
    assert len(eigen['synthetic_cases']) == 22 and eigen['tutorial']['eigenvalues'] == 16980
    tutorial = load('tutorial-original-1/STATUS.json')
    assert tutorial['prediction_genes'] == 18383 and tutorial['input_features'] == 508 and tutorial['selected_features'] == 258
    native = properties(records['tutorial-native-3/COMPLETED.properties'])
    assert native['status'] == 'PASS_COMPLETE_NATIVE_POPS_TUTORIAL_COMPARISON'
    assert [int(native[k]) for k in ['target_genes', 'prediction_genes', 'input_features', 'selected_features']] == [16980, 18383, 508, 258]
    assert float(native['maximum_absolute_prediction_error']) < 2.42e-14
    linux = properties(records['linux-replay-2/COMPLETED.properties'])
    assert linux['tests_succeeded'] == '17' and linux['tests_failed'] == linux['tests_skipped'] == linux['tests_aborted'] == '0'
    census = load('primary-1/CENSUS.json')
    assert census['papers_scanned'] == 250 and len(census['software_papers']) == 6 and set(census['rejected_name_matches']) == {'P239'}
    assert census['observations_pending_catalog_admission'] == ['P236']
    baseline_process = load('baseline-catalog-driver-2/PROCESS.json')
    assert baseline_process['baseline_head'] == BASE and baseline_process['baseline_clean']
    continuity = load('CLASS-CONTINUITY.json')
    assert continuity['existing_class_changes'] == [] and continuity['existing_resource_changes'] == []
    assert all(s.startswith('org/jlinalg/genetics/Pops') for s in continuity['added_classes'])
    inventory = read_json(root / 'docs/literature/omics-2023-2026/summary.json')
    assert inventory['papers'] == 250 and inventory['paper_tool_observations'] == 2691
    assert inventory['implementation_status_counts']['native-subset-validated'] == 22
    assert inventory['implementation_status_counts']['missing'] == 263
    primary_rescanned = False
    if rescan_primary:
        if not (root / 'build/literature-review/xml').is_dir():
            raise MissingPrerequisite('Primary XML cache missing. Restore all 250 exact files under build/literature-review/xml using the recorded XML URLs and hashes, or omit --rescan-primary for saved-record readback. No primary rescan was performed.')
        missing = [p['pmcid'] for p in read_json(root / 'docs/literature/omics-2023-2026/papers.json')
                   if not (root / 'build/literature-review/xml' / (p['pmcid'] + '.xml.gz')).is_file()]
        if missing:
            raise MissingPrerequisite('Primary XML files missing: ' + ', '.join(missing) + '. Restore these exact cached files using the recorded XML URLs/hashes before requesting a primary rescan.')
        spec = importlib.util.spec_from_file_location('pops_primary_records', root / 'tools/literature/pops_native/primary.py')
        primary_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(primary_module)
        assert primary_module.census(root) == census
        primary_rescanned = True
    private_files = 0
    if private_base is not None:
        base = Path(private_base).resolve()
        if not base.is_dir():
            raise MissingPrerequisite('Named private evidence directory missing. Restore the original complete build/pops records at the supplied --private-base path. Saved public record verification does not require this optional directory.')
        private = read_json(root / DOC / 'PRIVATE-RECORDS.json')
        actual = set()
        for current, directories, names in os.walk(base):
            parent = Path(current)
            if parent == base:
                directories[:] = [name for name in directories if name not in private['excluded_top_directories']]
            for name in names:
                path = parent / name
                if path.is_file():
                    actual.add(path.relative_to(base).as_posix())
        assert actual == set(private['files']), ('Named private file membership differs', sorted(actual - set(private['files']))[:10], sorted(set(private['files']) - actual)[:10])
        for name, expected in private['files'].items():
            assert pin((base / name).read_bytes()) == expected, name
        private_files = len(actual)
    return dict(status='PASS_CURRENT_NATIVE_POPS_DEVELOPMENT_EXTENSION_AND_SAVED_SCIENTIFIC_RECORDS',
                baseline_commit=BASE, existing_baseline_files_checked=len(tree), current_existing_sources_unchanged=True,
                scientific_tests_each_platform=17, original_successful_cases=11, expected_failures_retained=2,
                loo_cross_validation_scores=225, original_sort_cases=50, eigenvalue_spectra=22,
                full_tutorial_prediction_genes=18383, full_tutorial_selected_features=258,
                maximum_absolute_tutorial_score_error=float(native['maximum_absolute_prediction_error']),
                primary_files_rescanned=250 if primary_rescanned else 0, complete_named_private_files_checked=private_files,
                fresh_scientific_execution_performed=False, current_catalog_changed=False,
                new_catalog_credit=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--private-base', type=Path)
    parser.add_argument('--rescan-primary', action='store_true')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    try:
        result = verify(private_base=args.private_base, rescan_primary=args.rescan_primary)
        (args.out / 'COMPLETED.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
        print(json.dumps(result))
        return 0
    except MissingPrerequisite as error:
        record = dict(status='SKIP3_NATIVE_POPS_RECORD_PREREQUISITE_MISSING', reason=str(error), dependent_verification_skipped=True)
        (args.out / 'SKIPPED.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
        print('SKIP3:', error)
        return 3
    except Exception as error:
        record = dict(status='FAIL2_NATIVE_POPS_RECORD_VERIFICATION', cause=repr(error), detail=str(error),
                      required_action='Retain the exact source, artifacts and raw failures. Resolve the named difference before using the affected evidence. Never refresh old hashes or substitute a runtime/version to obtain a pass.')
        (args.out / 'FAILURE.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
        print('FAIL2:', repr(error))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
