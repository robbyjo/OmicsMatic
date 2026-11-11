# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Resealed corruption refusals and an explicit coordinated-forgery limitation probe."""
import argparse
import csv
import hashlib
import json
import shutil
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_deseq2_inference import JAVA21, JAVA25, linux

BASE = ROOT / 'build/literature-review/signac-runtime'


def read(path):
    return json.loads(path.read_bytes())


def edit_json(root, name, function):
    path = root / name
    value = read(path)
    function(value)
    save(path, value)


def edit_table(root, name, column, value):
    path = root / name
    with path.open(encoding='utf8', newline='') as stream:
        rows = list(csv.DictReader(stream, delimiter='\t'))
    header = list(rows[0])
    rows[0][column] = value
    with path.open('w', encoding='utf8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=header, delimiter='\t', lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)


def reseal(root):
    seal = read(root / 'capture-files.json')
    assert set(seal['files']) == {p.relative_to(root).as_posix() for p in root.rglob('*')
                                 if p.is_file() and p.name != 'capture-files.json'}
    seal['files'] = {name: pin(root / name)['sha256'] for name in seal['files']}
    save(root / 'capture-files.json', seal)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    jar = BASE / 'differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar'
    artifact = pin(jar)
    paired = BASE / 'differential-workflow-matrix-2/windows-java21-pair_wilcox-结果'
    partial = BASE / 'differential-extended-workflow-1/windows-java21-all_partial-结果'
    sampled = BASE / 'differential-extended-workflow-1/windows-java21-pair_sampled_wilcox-结果'
    originals = {str(p): {f.relative_to(p).as_posix(): pin(f) for f in p.rglob('*') if f.is_file()}
                 for p in [paired, partial, sampled]}
    sources = {Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__))}

    def invoke(label, root, expected=2, platform='windows-java21'):
        request = out / (label + '-verify.json')
        save(request, dict(schema_version=1, mode='VERIFY', analysis=root.name))
        if platform == 'linux-java21':
            argv = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java', '-Xmx768m',
                    '-cp', linux(jar), 'org.jlinalg.cli.SeuratDifferentialCli', '--request', linux(request)]
        else:
            argv = [JAVA21 if platform == 'windows-java21' else JAVA25, '-Xmx768m', '-cp', jar,
                    'org.jlinalg.cli.SeuratDifferentialCli', '--request', request]
        argv = list(map(str, argv))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL,
                                    stdout=stdout, stderr=stderr, timeout=120)
        save(out / (label + '.process.json'), dict(command=argv, exit_code=result.returncode,
                                                expected_exit_code=expected))
        assert result.returncode == expected, label + ' violated its expected integrity boundary'

    changes = [
        ('request-settings', paired, lambda d: edit_json(d, 'request.json', lambda v: v.update(seed=18))),
        ('source-request-settings', paired, lambda d: edit_json(d, 'source-request.json', lambda v: v.update(seed=18))),
        ('captured-count-bytes', paired, lambda d: (d / 'inputs/counts').write_bytes((d / 'inputs/counts').read_bytes() + b'\n')),
        ('prepared-parameters', paired, lambda d: edit_json(d, 'worker/parameters.json', lambda v: v.update(seed=18))),
        ('native-fold-summary', paired, lambda d: edit_table(d, 'worker/summaries.tsv', 'fold_change', '999')),
        ('native-detection', paired, lambda d: edit_table(d, 'worker/raw-group-markers.tsv', 'pct_1', '0')),
        ('whole-assay-correction', paired, lambda d: edit_table(d, 'worker/raw-group-markers.tsv', 'p_adj', '1')),
        ('published-probability', paired, lambda d: edit_table(d, 'markers.tsv', 'p_value', '0.5')),
        ('source-guard', paired, lambda d: edit_json(d, 'worker/worker-report.json', lambda v: v['source_guards'].update(FindMarkers=False))),
        ('recorded-process', paired, lambda d: edit_json(d, 'manifest.json', lambda v: v.update(process_exit_code=1))),
        ('unsupported-replay-claim', paired, lambda d: edit_json(d, 'manifest.json', lambda v: v.update(verify_replays_statistical_tests=True))),
        ('removed-required-warnings', paired, lambda d: (d / 'WARNINGS.md').write_text('No warnings.\n', encoding='utf8')),
        ('partial-labelled-complete', partial, lambda d: edit_json(d, 'analysis-status.json', lambda v: v.update(complete_comparison_set=True))),
        ('invalid-sampled-cell', sampled, lambda d: edit_table(d, 'worker/sampling-rule-preview.tsv', 'cell', 'c999999')),
    ]
    runs = []
    try:
        for name, original, mutate in changes:
            target = out / (name + '-TEST-ONLY')
            shutil.copytree(original, target)
            mutate(target)
            reseal(target)
            invoke(name, target)
            runs.append(dict(control=name, expected_exit_code=2,
                             files={p.relative_to(target).as_posix(): pin(p) for p in target.rglob('*') if p.is_file()}))
        # Deliberately falsify source p values and all consumed copies consistently.
        # A Java-only verifier that does not refit the test must not claim authenticity.
        forged = out / 'coordinated-probabilities-TEST-ONLY-NOT-A-SCIENTIFIC-RESULT'
        shutil.copytree(paired, forged)
        with (forged / 'worker/raw-group-markers.tsv').open(encoding='utf8', newline='') as stream:
            rows = list(csv.DictReader(stream, delimiter='\t'))
        new_p = float(rows[0]['p_value']) / 2
        assert 0 < new_p < float(rows[1]['p_value'])
        family = len((forged / 'inputs/features').read_text(encoding='utf8').splitlines())
        for name in ['worker/raw-group-markers.tsv', 'worker/markers.tsv', 'markers.tsv']:
            edit_table(forged, name, 'p_value', format(new_p, '.17g'))
            edit_table(forged, name, 'p_adj', format(min(1, new_p * family), '.17g'))
        reseal(forged)
        for platform in ['windows-java21', 'windows-java25', 'linux-java21']:
            invoke('coordinated-forgery-' + platform, forged, 0, platform)
        (out / 'COORDINATED-FORGERY-WARNING.md').write_text(
            '# TEST ONLY: FABRICATED PROBABILITIES ARE NOT A SCIENTIFIC RESULT\n\n'
            'This fixture deliberately halves one source p value, changes its full-family correction, '
            'updates all consumed table copies and reseals their hashes. All three Java-only verifiers '
            'accept the consistent capture because they do not refit Wilcoxon/LR inference. '
            'The unchanged original RDS and forensic source table are retained but are not independently '
            'reconciled by VERIFY. Never use or interpret this fixture as a measured result. '
            'Hashes and consistency checks establish neither authentic execution nor biological validity.\n',
            encoding='utf8')
        assert pin(jar) == artifact and all(pin(ROOT / n) == p for n, p in sources.items())
        for name, before in originals.items():
            directory = Path(name)
            assert before == {p.relative_to(directory).as_posix(): pin(p) for p in directory.rglob('*') if p.is_file()}
        save(out / 'COMPLETED.json', dict(
            status='passed-resealed-corruption-refusals-with-demonstrated-authentication-limitation',
            artifact=artifact, source_pins=sources, semantic_resealed_refusals=14, runs=runs,
            originals_preserved=True, independent_R_fits=False,
            coordinated_forgery_can_pass=True, coordinated_forgery_verified_on_JVMs=3,
            forged_fixture_is_scientific_result=False,
            coordinated_forgery_files={p.relative_to(forged).as_posix(): pin(p) for p in forged.rglob('*') if p.is_file()}))
        print('14 resealed corruptions refused; coordinated-probability forgery limitation demonstrated on3 JVMs')
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_SEURAT_INTEGRITY_CONTROLS', error=str(error)))
        raise


if __name__ == '__main__':
    main()
