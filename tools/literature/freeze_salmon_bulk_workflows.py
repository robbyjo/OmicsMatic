# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete Salmon bulk workflow evidence in bounded per-edition archives; retain older failures."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/salmon-runtime'


def read(path):
    return json.loads(path.read_bytes())


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable complete frozen-evidence checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'docs/literature').resolve()) and not out.exists()
    core_paths = [BASE / 'quant-workflow-development-2/control-2', BASE / 'quant-workflow-development-2/control-3']
    core = [read(p / 'COMPLETED.json') for p in core_paths]
    assert all(p['status'] == 'passed-selected-original-Salmon-bulk-workflows-and-complete-portable-replay' and p['focused_tests_per_jvm'] == 446 for p in core)
    assert core[0]['artifact'] == core[1]['artifact'] and core[0]['source_pins'] == core[1]['source_pins']
    editions = sorted(v for p in core for v in p['selected_editions'])
    assert editions == ['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0']
    public_paths = [BASE / 'public-workflow-development-1/windows-matrix-1', BASE / 'public-workflow-development-1/linux-matrix-1']
    public = [read(p / 'COMPLETED.json') for p in public_paths]
    assert all(p['status'] == 'passed-selected-platform-Salmon-root-command-and-exact-dispatch-compatibility' and p['only_changed_archive_entry'] == 'org/jlinalg/cli/JLinAlgCli.class' for p in public)
    assert public[0]['artifact'] == public[1]['artifact'] and public[0]['source_pins'] == public[1]['source_pins']
    platforms = [r for p in public for r in p['runs']]
    assert sorted(r['name'] for r in platforms) == ['linux-java21', 'windows-java21', 'windows-java25']
    assert all(r['tests'] == r['passed'] == 449 and r['skipped'] == 0 and len(r['portable']) == 8 for r in platforms)
    example_path = BASE / 'public-workflow-development-1/example-1'
    example = read(example_path / 'COMPLETED.json')
    assert example['status'] == 'passed-documentation-templates-export-offline-install-index-paired-single-root-example' and example['artifact'] == public[0]['artifact']
    assert len(example['fits']) == 2 and sum(p['audit']['complete_targets'] for p in example['fits']) == 80 and sum(p['audit']['complete_bootstrap_values'] for p in example['fits']) == 4200
    fits = [r for p in core for r in p['runs']]
    assert len(fits) == 5 and all(len(r['fits']) == 7 and r['strict_population_skipped'] and r['original_insufficient_mass_rejected'] for r in fits)
    v19 = next(r for r in fits if r['version'] == '1.9.0')
    assert all(v19[k] for k in ['bootstrap_capacity_rejected_before_fit', 'late_finalization_revoked', 'unexpected_finalization_error_revoked'])
    assert sum(p['admitted_controlled_fit_rows'] for p in core) == 1275 and sum(p['complete_bootstrap_values'] for p in core) == 32720
    artifact = public[0]['artifact']; backend = core[0]['artifact']
    assert pin(ROOT / artifact['path']) == {k: artifact[k] for k in ['bytes', 'sha256']} and pin(ROOT / backend['path']) == {k: backend[k] for k in ['bytes', 'sha256']}
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    assert all(p['canonical_executable_unchanged'] == canonical for p in [*core, *public, example])
    sources = {}
    for proof in [*core, *public, example]:
        for name, value in proof['source_pins'].items():
            if name.startswith('build/'):
                continue
            assert name not in sources or sources[name] == value, name
            assert pin(ROOT / name) == value, name
            sources[name] = value
    for path in [Path(__file__), ROOT / 'docs/literature/SALMON_INDEX_DEVELOPMENT.md']:
        sources[path.relative_to(ROOT).as_posix()] = pin(path)
    roles = read(ROOT / 'docs/literature/omics-2023-2026/salmon-role-coverage.json')
    assert roles['applied'] and roles['reviewed_roles'] == 8 and roles['total_reviews'] == 793 and roles['supporting_pointers'] == 241
    # The first whole-workflow attempt numerically passed but failed final
    # decimal-document replay. It remains a failed historical family.
    failure_path = BASE / 'quant-workflow-development-2/control-1'
    failed = failure_path / '1.9.0-FAILED/paired-em-bias-50'
    assert not (failed / 'COMPLETED.json').exists() and read(failed / 'FAILURE.json')['exit_code'] == 2
    prefix = read(failed / 'OUTPUT-AUDIT.json')
    assert prefix['complete_targets'] == 40 and prefix['complete_bootstrap_values'] == 2000
    out.mkdir()
    plans = {'source-and-validation.zip': {}}

    def add(archive, name, path):
        assert path.is_file() and not path.is_symlink() and name not in plans[archive] and '\\' not in name and '..' not in Path(name).parts
        plans[archive][name] = path

    def tree(archive, prefix, area, exclude=()):
        for path in sorted(area.rglob('*')):
            if not path.is_file() or any(part in exclude for part in path.relative_to(area).parts):
                continue
            add(archive, prefix + '/' + path.relative_to(area).as_posix(), path)

    for name in sources:
        add('source-and-validation.zip', 'source/' + name, ROOT / name)
    metadata_areas = [*core_paths, *public_paths, failure_path]
    for area in metadata_areas:
        label = area.relative_to(BASE).as_posix()
        for path in sorted(area.iterdir()):
            if path.is_file():
                add('source-and-validation.zip', 'validation/' + label + '/' + path.name, path)
    tree('source-and-validation.zip', 'validation/documented-example', example_path, ('captured', 'classes', 'source-snapshot'))
    # Current sources are stored once. Historical source bytes that differ are
    # retained explicitly; identical old snapshots point to the exact same bytes.
    aliases = {}
    for path in sorted((failure_path / 'source-snapshot').rglob('*')):
        if not path.is_file():
            continue
        name = path.relative_to(failure_path / 'source-snapshot').as_posix()
        if name in sources and pin(path) == sources[name]:
            aliases[name] = 'source/' + name
        else:
            saved = 'history/first-workflow-source/' + name
            add('source-and-validation.zip', saved, path); aliases[name] = saved
    write(out / 'HISTORICAL-SOURCE-ALIASES.json', aliases)
    add('source-and-validation.zip', 'history/HISTORICAL-SOURCE-ALIASES.json', out / 'HISTORICAL-SOURCE-ALIASES.json')
    components = ['runtime-admission-development-1/control-3', 'index-reference-development-1/matrix-1', 'index-ledger-development-1/matrix-1',
                  'index-workflow-development-1/control-1', 'quant-workflow-development-1/matrix-1', 'pufferfish-source-capture-1', 'index-edge-controls-3']
    for label in components:
        area = BASE / label; assert (area / 'COMPLETED.json').is_file()
        for path in sorted(area.iterdir()):
            if path.is_file():
                add('source-and-validation.zip', 'components/' + label + '/' + path.name, path)
    for area in sorted((BASE / 'runtime-admission-development-1/control-3').iterdir()):
        if area.is_dir() and area.name not in {'classes', 'source-snapshot'}:
            tree('source-and-validation.zip', 'components/runtime-admission-raw/' + area.name, area)
    for version in editions:
        archive = 'original-' + version + '.zip'; plans[archive] = {}
        owner = next(area for area in core_paths if (area / version).is_dir())
        tree(archive, 'bulk', owner / version)
        tree(archive, 'index-boundaries', BASE / 'index-edge-controls-3' / version)
        tree(archive, 'typed-index', BASE / 'index-workflow-development-1/control-1' / version)
        tree(archive, 'pufferfish-original-dependency', BASE / 'pufferfish-source-capture-1' / version)
        if version == '1.9.0':
            tree(archive, 'historical-first-failure', failure_path / '1.9.0-FAILED')
            tree(archive, 'documented-example', example_path / 'captured')
    archives, inventories = [], {}
    for archive, entries in plans.items():
        target = out / archive; census = {}
        with zipfile.ZipFile(target, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, path in sorted(entries.items()):
                raw = path.read_bytes(); entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); entry.compress_type = zipfile.ZIP_DEFLATED; entry.external_attr = (stat.S_IFREG | 0o644) << 16
                z.writestr(entry, raw); census[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
        assert target.stat().st_size < 95000000
        with zipfile.ZipFile(target) as z:
            assert len(z.infolist()) == len(set(z.namelist())) == len(census) and z.testzip() is None
            for name, expected in census.items():
                raw = z.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == expected
        archives.append(dict(path=archive, **pin(target), entries=len(census))); inventories[archive] = census
    assert all(pin(ROOT / name) == value for name, value in sources.items()) and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    result = dict(schema_version=1, status='passed-typed-Salmon-bulk-workflows-portable-bundles-and-source-populations', frozen_at=datetime.now(timezone.utc).isoformat(),
        exact_editions=editions, artifact=artifact, original_tested_backend=backend, archives=archives, archive_files=inventories, source_pins=sources,
        public_platform_validation=platforms, original_workflow_validation=core, documented_example=example, component_checkpoints=components,
        source_role_validation=roles, canonical_executable_unchanged=canonical,
        controlled_totals=dict(original_workflow_fits=36, original_workflow_rows=1275, original_workflow_bootstrap_values=32720, documented_fits=2, documented_rows=80, documented_bootstrap_values=4200,
                               old_decimal_replay_failure_unadmitted_rows=40, old_decimal_replay_failure_unadmitted_bootstrap_values=2000, late_finalization_unadmitted_rows=80, late_finalization_unadmitted_bootstrap_values=240),
        claims=dict(typed_public_quantification_command_available=True, runtime_setup_export_available=True, permitted_download_install_available=True,
                    controlled_original_fits_admitted=True, producer_host_readmitted_by_portable_verification=False, whole_paper_reproduced=False, biological_accuracy_certified=False, whole_goal_complete=False,
                    released=False, pushed=False, deployed=False),
        scope='Complete bulk paired/single mapping and query-grouped transcriptome SAM/BAM over controlled reused synthetic populations. Exact edition/reference/library/model choices, every requested inferential value, '
              'declared/source target ledgers, supervised process completion and whole-family replay are separate gates. Raw original results are preserved; no missing transcript is imputed or relabeled. '
              'Producer modes/empty directories remain in native inventories; ZIP transport file modes are644. Runtime binaries/cache stay in named owned Linux homes and are not redistributed here. '
              'Source/program hashes establish integrity, not execution authenticity or biological validation.',
        required_action='Inspect WARNINGS.md, FAILURE.json, complete reference/input/output ledgers and every original log. SKIP3: missing/forbidden exact runtime, helper, install/download or admitted capability means skip dependent analysis. '
                        'FAIL2: invalid input, changed evidence, process/numerical/capacity/cancellation/finalization failure admits no result. Preserve every attempt and use NEW paths after correction. '
                        'Explicit source-runtime fits retain false declared-retained coverage. Read examples/salmon-bulk/README.md and SALMON_INDEX_DEVELOPMENT.md. Cell Ranger remains excluded.')
    write(out / 'VALIDATION.json', result)
    sums = {row['path']: row['sha256'] for row in archives}; sums['VALIDATION.json'] = pin(out / 'VALIDATION.json')['sha256']; sums['HISTORICAL-SOURCE-ALIASES.json'] = pin(out / 'HISTORICAL-SOURCE-ALIASES.json')['sha256']
    (out / 'SHA256SUMS').write_text(''.join(value + '  ' + name + '\n' for name, value in sorted(sums.items())), encoding='ascii', newline='\n')
    (out / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(status=result['status'], editions=editions, archives=archives, totals=result['controlled_totals'])))


if __name__ == '__main__':
    main()
