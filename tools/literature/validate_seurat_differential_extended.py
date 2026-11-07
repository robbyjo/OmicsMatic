# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real sampled, layer, partial and failed Seurat marker comparisons."""
import argparse
import json
import subprocess
from pathlib import Path

from setup_deseq2_reference import ROOT, inventory, pin, save
from validate_deseq2_inference import JAVA21, JAVA25, linux
from validate_seurat_differential import compare

BASE = ROOT / 'build/literature-review/signac-runtime'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    jar = BASE / 'differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar'
    source = BASE / 'differential-extended-requests-1'
    reference = BASE / 'differential-extended-reference-1/original'
    base_reference = BASE / 'differential-original-reference-3/original'
    cases = json.loads((reference / 'RESULT.json').read_bytes())['cases']
    names = [*cases, 'native_tfidf', 'counts_mode']
    artifact = pin(jar)
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [
        Path(__file__), ROOT / 'tools/literature/validate_seurat_differential.py',
        ROOT / 'tools/literature/prepare_seurat_differential_extended_requests.py',
        ROOT / 'tools/literature/collect_seurat_differential_extended.R',
        *source.rglob('*'), *reference.rglob('*'), *base_reference.rglob('*')
    ] if p.is_file()}
    before = inventory()
    save(out / 'BASES-BEFORE.json', before)

    def command(label, argv, expected=0):
        argv = list(map(str, argv))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL,
                                    stdout=stdout, stderr=stderr, timeout=240)
        save(out / (label + '.process.json'), dict(command=argv, exit_code=result.returncode,
                                                expected_exit_code=expected))
        assert result.returncode == expected, label + ' failed; inspect retained logs'

    def invoke(platform, request):
        if platform == 'linux-java21':
            return ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java',
                    '-Xmx768m', '-cp', linux(jar), 'org.jlinalg.cli.SeuratDifferentialCli',
                    '--request', linux(request)]
        return [JAVA21 if platform == 'windows-java21' else JAVA25, '-Xmx768m', '-cp', jar,
                'org.jlinalg.cli.SeuratDifferentialCli', '--request', request]

    runs = []
    try:
        for platform in ['windows-java21', 'windows-java25']:
            for name in names:
                label = platform + '-' + name
                request = json.loads((source / (name + '.json')).read_bytes())
                target = out / (label + '-结果')
                request['out'] = str(target)
                path = out / (label + '.json')
                save(path, request)
                failed = name == 'pair_failure'
                command(label, invoke(platform, path), 2 if failed else 0)
                state = json.loads((target / 'analysis-status.json').read_bytes())
                assert state['results_admitted'] is not failed
                verification = out / (label + '-verify.json')
                save(verification, dict(schema_version=1, mode='VERIFY', analysis=target.name))
                for verifier in ['windows-java21', 'windows-java25', 'linux-java21']:
                    command(label + '-verify-' + verifier, invoke(verifier, verification), 2 if failed else 0)
                if failed:
                    assert state['status'] == 'FAIL2_NO_RESULT_ADMITTED'
                    assert cases[name]['error'] in state['error']
                    assert not (target / 'markers.tsv').exists()
                    assert 'FAIL2' in (target / 'WARNINGS.md').read_text(encoding='utf8')
                    comparison = None
                    warning_count = None
                else:
                    expected = base_reference / 'pair_wilcox.tsv' if name == 'native_tfidf' else reference / ('pair_count_means.tsv' if name == 'counts_mode' else name + '.tsv')
                    comparison = compare(expected, target / 'markers.tsv', request['mode'])
                    report = json.loads((target / 'worker/worker-report.json').read_bytes())
                    warning_count = len(report['warnings'])
                    if name in cases:
                        assert comparison['rows'] == cases[name]['rows']
                        assert warning_count == len(cases[name]['warnings'])
                    partial = name == 'all_partial'
                    assert state['complete_comparison_set'] is not partial
                    assert state['failed_source_comparisons'] == (1 if partial else 0)
                    if partial:
                        text = (target / 'WARNINGS.md').read_text(encoding='utf8')
                        assert text.startswith('# PARTIAL SOURCE RESULT')
                        assert 'PARTIAL_SOURCE_COMPARISON_SET' in text and 'SOURCE_COMPARISON_NOT_TESTED' in text
                        assert (out / (label + '.stdout')).read_text(encoding='utf8').startswith('PARTIAL SOURCE RESULT')
                        # The direct original only.pos call emits zero warnings for this caught failure.
                        assert cases[name]['warnings'] == [] and warning_count == 0
                runs.append(dict(case=name, platform=platform, expected_failure=failed,
                                 comparison=comparison, source_warning_count=warning_count,
                                 analysis_status=state, files={p.relative_to(target).as_posix(): pin(p)
                                                              for p in target.rglob('*') if p.is_file()}))
                print(label + (' preserved the original failure and refused verification' if failed else
                               ' matched independent original fields and three portable verifications'), flush=True)
        after = inventory()
        save(out / 'BASES-AFTER.json', after)
        assert before == after and pin(jar) == artifact
        assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        assert all(pin(ROOT / name) == value for name, value in sources.items())
        comparisons = [r['comparison'] for r in runs if r['comparison'] is not None]
        save(out / 'COMPLETED.json', dict(
            status='passed-typed-original-Seurat-extended-layer-sampling-partial-and-failure-controls',
            artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact), source_pins=sources,
            canonical_executable_preserved=canonical, actual_original_requests=20,
            admitted_source_tables=18, expected_source_failures=2, partial_source_comparison_sets=2,
            portable_successful_verifications=54, portable_failed_capture_refusals=6, runs=runs,
            comparisons=dict(finite_fields=sum(c['finite_fields'] for c in comparisons),
                             nonfinite_kinds=sum(c['nonfinite_kinds'] for c in comparisons),
                             maximum_relative_error=max(c['maximum_relative_error'] for c in comparisons)),
            prior_R_library_files_unchanged=sum(len(v) for v in before.values()),
            native_statistical_p_value_replay=False, native_seeded_sampling_replay=False,
            biological_validation=False, whole_goal_complete=False))
        print('Completed20 real requests,54 successful verifications and6 failed-capture refusals', flush=True)
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_TYPED_SEURAT_EXTENDED_MATRIX', error=str(error)))
        raise


if __name__ == '__main__':
    main()
