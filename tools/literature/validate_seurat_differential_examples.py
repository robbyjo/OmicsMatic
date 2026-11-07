# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the literal documented synthetic marker requests and portable verification."""
import argparse
import json
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_deseq2_inference import JAVA21, JAVA25, linux
from validate_seurat_differential import compare


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    example = ROOT / 'examples/seurat-differential'
    jar = base / 'differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar'
    artifact = pin(jar)
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [*example.iterdir(), Path(__file__),
        ROOT / 'docs/literature/SEURAT_DIFFERENTIAL_DEVELOPMENT.md',
        ROOT / 'tools/literature/prepare_seurat_differential_examples.py'] if p.is_file()}

    def command(label, argv, expected=0):
        argv = list(map(str, argv))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL,
                                    stdout=stdout, stderr=stderr, timeout=240)
        save(out / (label + '.process.json'), dict(command=argv, exit_code=result.returncode,
                                                expected_exit_code=expected))
        assert result.returncode == expected, label + ' failed; inspect retained logs'

    references = {
        'paired': base / 'differential-original-reference-3/original/pair_wilcox.tsv',
        'paired-lr': base / 'differential-original-reference-3/original/pair_LR_latent.tsv',
        'all-lr': base / 'differential-original-reference-3/original/all_LR_latent.tsv',
        'count-means': base / 'differential-extended-reference-1/original/pair_count_means.tsv'}
    runs = []
    try:
        for name, reference in references.items():
            request = example / (name + '.json')
            payload = json.loads(request.read_bytes())
            target = (example / payload['out']).resolve()
            assert target.is_relative_to(ROOT / 'build') and not target.exists()
            argv = [JAVA21, '-Xmx768m', '-cp', jar, 'org.jlinalg.cli.SeuratDifferentialCli', '--request', request]
            command(name, argv)
            state = json.loads((target / 'analysis-status.json').read_bytes())
            assert state['results_admitted'] and state['complete_comparison_set']
            comparison = compare(reference, target / 'markers.tsv', payload['mode'])
            verification = example / ('verify-' + name + '.json')
            for label, java in [('windows-java21', JAVA21), ('windows-java25', JAVA25)]:
                command(name + '-verify-' + label, [java, '-Xmx768m', '-cp', jar,
                        'org.jlinalg.cli.SeuratDifferentialCli', '--request', verification])
            command(name + '-verify-linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026',
                    '--', '/usr/bin/java', '-Xmx768m', '-cp', linux(jar),
                    'org.jlinalg.cli.SeuratDifferentialCli', '--request', linux(verification)])
            before = {p.relative_to(target).as_posix(): pin(p) for p in target.rglob('*') if p.is_file()}
            command(name + '-existing-output', argv, 2)
            assert before == {p.relative_to(target).as_posix(): pin(p) for p in target.rglob('*') if p.is_file()}
            runs.append(dict(example=name, analysis=target.relative_to(ROOT).as_posix(),
                             comparison=comparison, files=before))
        assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        assert all(pin(ROOT / n) == v for n, v in sources.items())
        save(out / 'COMPLETED.json', dict(status='passed-four-literal-Seurat-marker-examples',
                                         artifact=artifact, source_pins=sources,
                                         canonical_executable_preserved=canonical, runs=runs,
                                         original_requests=4, portable_verifications=12,
                                         existing_output_refusals=4,
                                         finite_fields=sum(r['comparison']['finite_fields'] for r in runs),
                                         maximum_relative_error=max(r['comparison']['maximum_relative_error'] for r in runs)))
        print('Four literal marker examples matched originals;12 portable verifications and4 overwrite refusals passed')
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_SEURAT_LITERAL_EXAMPLES', error=str(error)))
        raise


if __name__ == '__main__':
    main()
