# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise scDRS refusal, permission, process and coherently resealed saved-contract controls."""
import argparse
import copy
import gzip
import json
import os
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux

JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--build', required=True, type=Path)
    p.add_argument('--baseline', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='FAIL2_SCDRS_TOOLS_SAFETY_CONTROLS', new_catalog_credit=False, full_adapter_validated=False)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    baseline = a.baseline.resolve()
    baseline_before = pin(baseline / 'manifest.json')

    def run(label, args, expected=0, timeout=900):
        save(out / (label + '.command.json'), dict(command=args))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(args, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout)
        save(out / (label + '.process.json'), dict(exit_code=result.returncode))
        assert result.returncode == expected, (label, result.returncode, (out / (label + '.stderr')).read_bytes()[-2000:])

    try:
        build = json.loads((a.build / 'COMPLETED.json').read_bytes())
        assert build['status'] == 'PASS_STANDALONE_SCDRS_WORKFLOW_DEVELOPMENT_BUILD_ONLY'
        artifact = ROOT / build['artifact']['path']
        assert pin(artifact) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
        assert json.loads((baseline / 'COMPLETED.json').read_bytes())['result_admitted']
        run('baseline-verify', [JAVA, '-jar', str(artifact), '--verify', str(baseline)])
        base_request = json.loads((BASE / 'typed-workflow-smoke-1/request.json').read_bytes())
        controls = BASE / 'workflow-controls-1'
        refused = []

        def refusal(name, edit, code=2, platform='linux', input_stage=True):
            directory = out / name
            directory.mkdir()
            request = copy.deepcopy(base_request)
            request['output_dir'] = linux(directory / 'bundle') if platform == 'linux' else str(directory / 'bundle')
            edit(request)
            recipe = directory / 'request.json'
            save(recipe, request)
            command = (['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java', '-jar', linux(artifact), '--request', linux(recipe)]
                if platform == 'linux' else [JAVA, '-jar', str(artifact), '--request', str(recipe)])
            run(name, command, code)
            bundle = directory / 'bundle'
            assert not (bundle / 'COMPLETED.json').exists()
            if bundle.exists():
                session = json.loads((bundle / 'session.json').read_bytes())
                assert not session['result_admitted'] and session['status'].startswith('SKIP3_' if code == 3 else 'FAIL2_')
                assert b'REQUIRED ACTION' in (bundle / 'WARNINGS.md').read_bytes()
            if not input_stage: assert not (bundle / 'source-analysis-started.json').exists()
            refused.append(dict(case=name, exit_code=code, result_admitted=False))

        refusal('missing-runtime-profile', lambda r: r.update(runtime_profile=linux(out / 'not-installed/runtime.json')), 3, input_stage=False)
        refusal('explicit-version-mismatch', lambda r: r.update(version='1.0.0'), 3, input_stage=False)
        refusal('windows-analysis-prerequisite', lambda r: None, 3, platform='windows', input_stage=False)
        refusal('duplicate-cell-identities', lambda r: r['inputs'].update(h5ad=linux(controls / 'duplicate-cells.h5ad')), input_stage=False)
        refusal('small-overlap-source-skip', lambda r: r['inputs'].update(gene_sets=linux(controls / 'too-few-overlapping.gs')), 3, input_stage=False)
        def historical_text(r):
            r.update(version='1.0.1', runtime_profile=linux(BASE / 'workflow-profile-1.0.1-1/runtime.json'))
            r['inputs']['covariates'] = linux(controls / 'categorical-sex.cov')
        refusal('historical-text-covariates', historical_text, input_stage=False)
        def invalid_scores(r):
            r['operation'] = 'downstream'
            r['inputs'].update(gene_sets=None, covariates=None, scores=linux(controls / 'trait-nonfinite.full_score.gz'))
            r['scoring'] = None
        refusal('nonfinite-downstream-score-input', invalid_scores, input_stage=False)
        refusal('input-byte-capacity', lambda r: r['limits'].update(input_bytes=1), input_stage=False)
        refusal('working-capacity', lambda r: r['limits'].update(working_bytes=1), input_stage=False)
        refusal('wall-timeout', lambda r: r['limits'].update(wall_seconds=1))
        refusal('unknown-request-field', lambda r: r.update(unreviewed_shell='echo ignored'), input_stage=False)
        refusal('invalid-control-count', lambda r: r['scoring'].update(controls=0), input_stage=False)
        def gene_budget(r):
            r['inputs']['gene_sets'] = linux(BASE / 'thousand-gene-controls-1/thousand-genes.gs')
            r['limits']['working_bytes'] = 100_000_000
        refusal('retained-control-gene-work-budget', gene_budget, input_stage=False)
        gene_error = json.loads((out / 'retained-control-gene-work-budget/bundle/source-worker-failure.json').read_bytes())
        assert 'retained control-gene/weight sets' in gene_error['cause']
        # An existing output is never consumed, merged or removed by a new run.
        existing = out / 'existing-output'
        existing.mkdir()
        (existing / 'sentinel.txt').write_text('preserve this existing output\n')
        sentinel = pin(existing / 'sentinel.txt')
        recipe = out / 'existing-output-request.json'
        request = copy.deepcopy(base_request)
        request['output_dir'] = linux(existing)
        save(recipe, request)
        run('existing-output-refusal', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java', '-jar', linux(artifact), '--request', linux(recipe)], 2)
        assert pin(existing / 'sentinel.txt') == sentinel and len(list(existing.iterdir())) == 1
        refused.append(dict(case='existing-output-preserved', exit_code=2, result_admitted=False))
        # The installer must skip without creating an environment when install
        # permission is absent, even with complete local archives available.
        no_install = out / 'setup-without-install-permission'
        environment = '/var/tmp/omicsmatic-scdrs-install-forbidden-control-1/environment'
        run('install-not-authorized', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/python3',
            linux(ROOT / 'src/main/resources/org/jlinalg/omics/scdrs/prepare_scdrs_runtime.py'), '--version', '1.0.2',
            '--out', linux(no_install), '--environment-home', environment,
            '--archive-dir', linux(BASE / 'workflow-lock-1/archives')], 3)
        assert json.loads((no_install / 'setup.json').read_bytes())['status'] == 'SKIP3_SCDRS_RUNTIME_UNAVAILABLE'
        run('forbidden-environment-absent', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/test', '!', '-e', environment])
        refused.append(dict(case='install-not-authorized', exit_code=3, environment_created=False))
        # Saved verification is read-only and requires no original runtime.
        tampered = []

        def reseal(directory):
            manifest = json.loads((directory / 'manifest.json').read_bytes())
            files = {p.relative_to(directory).as_posix(): pin(p) for p in sorted(directory.rglob('*'))
                     if p.is_file() and p.relative_to(directory).as_posix() not in ['manifest.json', 'COMPLETED.json']}
            manifest['files'] = files
            save(directory / 'manifest.json', manifest)
            completed = json.loads((directory / 'COMPLETED.json').read_bytes())
            completed['manifest'] = pin(directory / 'manifest.json')
            save(directory / 'COMPLETED.json', completed)

        def json_edit(directory, name, edit):
            path = directory / name
            value = json.loads(path.read_bytes())
            edit(value)
            save(path, value)

        def tamper(name, edit, coherent=True):
            directory = out / 'saved-tamper' / name
            shutil.copytree(baseline, directory)
            edit(directory)
            if coherent: reseal(directory)
            manifest_pin = pin(directory / 'manifest.json')
            run('saved-refusal-' + name, [JAVA, '-jar', str(artifact), '--verify', str(directory)], 2)
            assert pin(directory / 'manifest.json') == manifest_pin
            tampered.append(dict(case=name, coherently_resealed=coherent, refused=True, verifier_read_only=True))

        tamper('simple-output-byte-edit', lambda d: (d / 'original/trait-synthetic.scdrs_cell_corr').write_bytes(b'corrupt\n'), False)
        tamper('source-version', lambda d: json_edit(d, 'runtime-profile.json', lambda v: v.update(version='1.0.0')))
        tamper('source-commit', lambda d: json_edit(d, 'runtime-profile.json', lambda v: v.update(source_commit='0' * 40)))
        tamper('source-lock', lambda d: json_edit(d, 'runtime-profile.json', lambda v: v['source_lock'].update(sha256='0' * 64)))
        tamper('setup-helper', lambda d: json_edit(d, 'runtime-profile.json', lambda v: v['preparer'].update(sha256='0' * 64)))
        tamper('dependency-version', lambda d: json_edit(d, 'runtime-profile.json', lambda v: v['versions']['distributions'].update(numpy='2.0.0')))
        tamper('module-escape', lambda d: json_edit(d, 'runtime-profile.json', lambda v: v['versions']['modules'].update(numpy='/usr/lib/python3/numpy.py')))
        def link_escape(v):
            v['closure']['environment']['files']['synthetic-escape-link'] = dict(kind='symlink', target='/etc/passwd')
        tamper('environment-link-escape', lambda d: json_edit(d, 'runtime-profile.json', link_escape))
        tamper('runtime-total-bytes', lambda d: json_edit(d, 'runtime-profile.json', lambda v: v['closure']['environment'].update(regular_bytes=1)))
        def source_digest(v):
            first = next(iter(v['closure']['source']['files']))
            v['closure']['source']['files'][first]['sha256'] = '0' * 64
        tamper('source-file-digest', lambda d: json_edit(d, 'runtime-profile.json', source_digest))
        tamper('unsupported-authentication-claim', lambda d: json_edit(d, 'session.json', lambda v: v.update(producer_authenticated=True)))
        tamper('false-whole-paper-claim', lambda d: json_edit(d, 'session.json', lambda v: v.update(whole_paper_reproduced=True)))
        tamper('unfinished-source-process', lambda d: json_edit(d, 'source-process-cleanup.json', lambda v: v.update(parent_still_alive=True)))
        tamper('different-source-command', lambda d: json_edit(d, 'source-process-command.json', lambda v: v.update(shell=True)))
        tamper('missing-start-marker', lambda d: (d / 'source-analysis-started.json').unlink())
        tamper('wrong-source-functions', lambda d: json_edit(d, 'source-workflow-completed.json', lambda v: v.update(source_functions=['replacement-method'])))
        tamper('extra-ignored-original-output', lambda d: (d / 'original/extra.tsv').write_text('must not be silently admitted\n'))
        tamper('extra-ignored-native-output', lambda d: (d / 'native-derived/extra.tsv').write_text('must not be silently admitted\n'))
        def change_bh(directory):
            path = next((directory / 'native-derived').iterdir())
            rows = path.read_text().splitlines()
            fields = rows[1].split('\t')
            fields[2] = '0.123456789'
            rows[1] = '\t'.join(fields)
            path.write_text('\n'.join(rows) + '\n')
        tamper('native-family-bh-value', change_bh)
        def change_score(directory):
            path = directory / 'original/trait-synthetic.score.gz'
            rows = gzip.decompress(path.read_bytes()).decode().splitlines()
            row = rows[1].split('\t')
            row[1] = 'NaN'
            rows[1] = '\t'.join(row)
            path.write_bytes(gzip.compress(('\n'.join(rows) + '\n').encode()))
        tamper('nonfinite-source-output', change_score)
        def change_ledger(directory):
            path = directory / 'inspection/identifier-dispositions.tsv'
            lines = path.read_text().splitlines()
            lines[1] = lines[1].replace('cell-000', 'silently-repaired-id')
            path.write_text('\n'.join(lines) + '\n')
        tamper('identifier-disposition-ledger', change_ledger)
        assert pin(baseline / 'manifest.json') == baseline_before
        state.update(status='PASS_SCDRS_PREREQUISITE_INPUT_PROCESS_AND_RESEALED_SAVED_CONTRACT_CONTROLS_ONLY',
            refused_runs=refused, saved_refusals=tampered, baseline_unchanged=True,
            saved_checks_claim_independent_refitting_or_authenticity=False,
            original_article_data_used=False, artifact=pin(artifact))
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
    assert pin(canonical) == before and pin(baseline / 'manifest.json') == baseline_before
    state['canonical_executable_unchanged'] = before
    save(out / 'COMPLETED.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause',
        'These controls cover stated prerequisite, input, process and saved-output contracts. They do not authenticate a producer or independently refit source statistics.')
        + '\n\nREQUIRED ACTION: Preserve this attempt. Resolve any named failing control and retry a NEW output. No failed safety gate grants tool/catalog admission.\n',
        encoding='utf-8', newline='\n')
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable refusal and immutable-input checks')
    raise SystemExit(main())
