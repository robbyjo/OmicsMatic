# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real public Signac routes, reference normalization and explicit recovery outcomes."""
import argparse
import concurrent.futures
import copy
import json
import math
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_deseq2_inference import JAVA21, JAVA25, linux
from validate_signac_tools_native import BASE, JAR

PATHS = {'counts', 'features', 'cells', 'idf', 'existing_features', 'regions', 'object_cells_file',
    'positions', 'motif_matrix', 'peak_features', 'motif_labels', 'query', 'background', 'rscript',
    'r_motif_library', 'r_signac_library', 'r_base_library', 'r_library', 'peak_ranges', 'fasta', 'pfm',
    'expression', 'gene_features', 'expression_cells', 'gene_coords', 'peak_metadata', 'metadata',
    'macs2_home', 'cell_metadata', 'annotation', 'fai', 'path', 'fragments', 'map', 'r-library',
    'r-base-library'}
EXAMPLES = [('normalize', 'signac-normalize/request.json'), ('lsi', 'signac-lsi/request.json'),
    ('fragments', 'signac-fragments/feature-request.json'), ('annotations', 'signac-annotations/fast-request.json'),
    ('motifs', 'signac-motifs/request.json'), ('scan', 'signac-scan/request.json'),
    ('links', 'signac-links/request.json'), ('markers', 'seurat-differential/paired.json'),
    ('peaks', 'signac-peaks/pooled-request.json'), ('coverage', 'signac-coverage/gene-request.json'),
    ('chromvar', 'chromvar-background/request.json')]


def read(path):
    return json.loads(path.read_bytes())


def transport(raw, parent, platform, inputs):
    if isinstance(raw, dict):
        result = {}
        for key, value in raw.items():
            if key in PATHS and isinstance(value, str):
                path = (parent / value).resolve()
                if path.is_file():
                    inputs[str(path)] = pin(path)
                result[key] = linux(path) if platform == 'linux-java21' else str(path)
            elif key == 'r_libraries':
                paths = [(parent / v).resolve() for v in value]
                result[key] = [linux(p) if platform == 'linux-java21' else str(p) for p in paths]
            else:
                result[key] = transport(value, parent, platform, inputs)
        return result
    if isinstance(raw, list):
        return [transport(v, parent, platform, inputs) for v in raw]
    return raw


def original_normalization(root, raw):
    archive = ROOT / 'src/test/resources/org/jlinalg/sequence/signac-normalization/original-controls.zip'
    name = 'hand-count-versus-document-frequency/'
    with zipfile.ZipFile(archive) as source:
        def data(key):
            return source.read(name + key).decode('utf8')
        selected = [line.split('\t') for line in data('selections.tsv').splitlines()]
        spec = raw['selection']
        match = [r for r in selected if r[0] == spec['kind'] and float(r[1]) == spec.get('value', 0)]
        assert len(match) == 1
        expected = match[0][3].split(',') if match[0][3] else []
        assert (root / 'variable-features.tsv').read_text(encoding='utf8').splitlines() == expected
        for key in ['features.tsv', 'cells.tsv']:
            assert (root / key).read_bytes() == source.read(name + key)
        compared = 0
        nonfinite = 0
        if raw['mode'] == 'NORMALIZE':
            method = raw.get('tfidf_method', 1)
            scale = raw.get('scale_factor', 10000)
            rows = [line.split('\t') for line in data('normalized.tsv').splitlines()]
            rows = [r for r in rows if int(r[0]) == method and float(r[1]) == scale and (r[2] == 'true') == ('idf' in raw)]
            assert len(rows) == 1
            expected = [float(x.replace('Inf', 'inf')) for x in rows[0][3].split(',')]
            matrix = [line.split() for line in (root / 'normalized.mtx').read_text().splitlines() if not line.startswith('%')]
            assert list(map(int, matrix[0])) == [3, 3, 5]
            assert [list(map(int, r[:2])) for r in matrix[1:]] == [[1, 1], [2, 1], [1, 2], [3, 2], [3, 3]]
            actual = [float(r[2].replace('Inf', 'inf')) for r in matrix[1:]]
            assert len(actual) == len(expected)
            for a, b in zip(actual, expected):
                if math.isfinite(b):
                    assert math.isfinite(a) and abs(a-b) <= max(16*math.ulp(b), abs(b)*3e-13), (a, b)
                else:
                    assert a == b
                    nonfinite += 1
                compared += 1
            for code in filter(None, rows[0][4].split(',')):
                assert code in (root / 'WARNINGS.md').read_text(encoding='utf8')
        else:
            assert not (root / 'normalized.mtx').exists()
        return dict(normalized_coordinates_compared=compared, nonfinite_coordinates_retained=nonfinite,
            selected_features=len(expected) if raw['mode'] != 'NORMALIZE' else len((root / 'variable-features.tsv').read_text().splitlines()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    save(out / 'INCOMPLETE.json', dict(status='public-workflow-matrix-in-progress'))
    artifact = pin(JAR)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    canonical_pin = pin(canonical)
    snapshots = out / 'source-requests'
    snapshots.mkdir()
    input_pins = {}
    for workflow, name in EXAMPLES:
        path = ROOT / 'examples' / name
        (snapshots / (workflow + '.json')).write_bytes(path.read_bytes())
        input_pins[str(path)] = pin(path)
    processes = []

    def invoke(label, workflow, request, platform='windows-java21', expected=0):
        path = out / (label + '.json')
        save(path, request)
        if platform == 'linux-java21':
            argv = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java',
                '-Djava.awt.headless=true', '-Xmx768m', '-jar', linux(JAR), 'signac-tools',
                '--workflow', workflow, '--request', linux(path)]
        else:
            argv = [JAVA21 if platform == 'windows-java21' else JAVA25, '-Djava.awt.headless=true',
                '-Xmx768m', '-jar', JAR, 'signac-tools', '--workflow', workflow, '--request', path]
        argv = list(map(str, argv))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            process = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=300)
        row = dict(label=label, workflow=workflow, platform=platform, command=argv,
            exit_code=process.returncode, expected_exit_code=expected)
        save(out / (label + '.process.json'), row)
        processes.append(row)
        assert process.returncode == expected, (label, process.returncode, 'inspect retained stdout/stderr')

    try:
        specifications = []
        for workflow, name in EXAMPLES:
            path = ROOT / 'examples' / name
            platform = 'linux-java21' if workflow == 'peaks' else 'windows-java21'
            request = transport(read(path), path.parent, platform, input_pins)
            target = out / (workflow + '-result')
            (request['options'] if workflow == 'lsi' else request)['out'] = linux(target) if platform == 'linux-java21' else str(target)
            specifications.append((workflow, workflow, request, platform, target))
        for name in ['supplied-idf-request', 'overflow-request', 'method4-request', 'top-features-request',
                     'empty-selection-request', 'keep-existing-request']:
            path = ROOT / 'examples/signac-normalize' / (name + '.json')
            input_pins[str(path)] = pin(path)
            request = transport(read(path), path.parent, 'windows-java21', input_pins)
            target = out / (name + '-result')
            request['out'] = str(target)
            specifications.append((name, 'normalize', request, 'windows-java21', target))
        save(out / 'INPUT-PINS.json', input_pins)

        def run_case(spec):
            label, workflow, request, platform, target = spec
            invoke(label, workflow, request, platform)
            state = read(target / 'analysis-status.json')
            assert state['status'] in {'completed', 'completed-with-warnings'}, (label, state)
            assert state.get('results_admitted', True) is True, (label, state)
            comparison = original_normalization(target, request) if workflow == 'normalize' else None
            return dict(label=label, workflow=workflow, platform=platform, result=str(target),
                original_normalization_comparison=comparison)

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            analyses = list(pool.map(run_case, specifications))
        checks = [(row, platform) for row in analyses for platform in ['windows-java21', 'windows-java25', 'linux-java21']]
        def check(row):
            analysis, platform = row
            target = Path(analysis['result'])
            invoke(analysis['label'] + '-verify-' + platform, analysis['workflow'],
                dict(schema_version=1, mode='VERIFY', analysis=linux(target) if platform == 'linux-java21' else str(target)), platform)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            list(pool.map(check, checks))

        # Native arithmetic and nonfinite/Unicode transport through each actual root launcher.
        additional = []
        for platform in ['windows-java25', 'linux-java21']:
            for filename in ['request.json', 'overflow-request.json']:
                path = ROOT / 'examples/signac-normalize' / filename
                label = 'native-' + platform + '-' + path.stem
                request = transport(read(path), path.parent, platform, input_pins)
                target = out / (label + '-result')
                request['out'] = linux(target) if platform == 'linux-java21' else str(target)
                invoke(label, 'normalize', request, platform)
                additional.append(dict(label=label, comparison=original_normalization(target, request)))

        missing_r = copy.deepcopy(specifications[1][2])
        missing_r['options'].update(rscript=str(out / 'MISSING-Rscript.exe'), out=str(out / 'missing-r-result'))
        invoke('missing-r', 'lsi', missing_r, expected=3)
        missing_macs = copy.deepcopy(next(row[2] for row in specifications if row[1] == 'peaks'))
        missing_macs.update(macs2_home=linux(out / 'MISSING-MACS'), out=linux(out / 'missing-macs-result'))
        invoke('missing-macs', 'peaks', missing_macs, 'linux-java21', expected=3)
        missing_counts = copy.deepcopy(specifications[0][2])
        missing_counts.update(counts=str(out / 'MISSING-counts.mtx'), out=str(out / 'missing-counts-result'))
        invoke('missing-counts', 'normalize', missing_counts, expected=2)
        invoke('wrong-workflow-verify', 'fragments', dict(schema_version=1, mode='VERIFY', analysis=str(out / 'normalize-result')), expected=2)
        for label in ['missing-r', 'missing-macs', 'missing-counts']:
            root = out / (label + '-result')
            warning = (root / 'WARNINGS.md').read_text(encoding='utf8')
            assert 'Required action' in warning or 'REQUIRED ACTION' in warning or 'Required actions' in warning, label
            assert not (root / 'COMPLETED.json').exists(), label
            assert (root / 'analysis-status.json').is_file(), label
        assert pin(JAR) == artifact and pin(canonical) == canonical_pin
        assert all(pin(Path(name)) == value for name, value in input_pins.items())
        save(out / 'COMPLETED.json', dict(status='passed-real-public-Signac-workflow-matrix', artifact=artifact,
            analyses=analyses, additional_native_platform_runs=additional, completed_analyses=len(analyses)+len(additional),
            successful_portable_verifications=len(checks), expected_missing_runtime_skips=2, expected_failures=2,
            processes=sorted(processes, key=lambda r: r['label']), canonical_executable_preserved=canonical_pin,
            analysis_installed_software=False, prior_scientific_classes_required_byte_identical=True,
            original_normalization_reference_rechecked=True, other_workflows_reuse_frozen_parent_scientific_validation=True,
            whole_historical_packages=False, biological_validity_certified=False))
        print(f'{len(analyses)+len(additional)} public analyses, {len(checks)} portable VERIFY calls, 2 SKIP3 and 2 FAIL2 controls passed')
    except Exception as failure:
        save(out / 'FAILURE.json', dict(status='FAIL2_PUBLIC_SIGNAC_WORKFLOW_MATRIX', error=str(failure)))
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable scientific/transport admission checks')
    main()
