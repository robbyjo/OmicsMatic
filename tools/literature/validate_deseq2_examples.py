# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the two literal development examples and compare original R outputs."""
import argparse
import csv
import json
import math
from pathlib import Path
import subprocess
from setup_deseq2_reference import BASES, RSCRIPT, environment, pin, save

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/deseq2-runtime'


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.reader(stream, delimiter='\t'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists(); out.mkdir()
    jar = BASE / 'normalization-development/omicsmatic-0.3.7-deseq2-normalization-development.jar'
    frozen = BASE / 'normalization-build-1/omicsmatic-0.3.7-deseq2-normalization-development.jar'
    assert pin(jar) == pin(frozen)
    example = ROOT / 'examples/deseq2-normalization'; cases = []; analyses = []
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in example.iterdir() if p.is_file()}
    source_pins.update({p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), ROOT / 'tools/literature/check_deseq2_examples.R', ROOT / 'tools/literature/deseq2-normalization-development.init.gradle']})
    try:
        for name in ['normalize', 'frozen-means']:
            request = example / (name + '.json'); payload = json.loads(request.read_bytes())
            target = (example / payload['out']).resolve(); assert not target.exists()
            command = ['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe', '-cp', str(jar),
                       'org.jlinalg.cli.Deseq2NormalizationCli', '--request', str(request)]
            def run(label, expected):
                p = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=60)
                (out / (label + '.stdout')).write_bytes(p.stdout); (out / (label + '.stderr')).write_bytes(p.stderr)
                save(out / (label + '.process.json'), dict(command=command, exit_code=p.returncode, expected_exit_code=expected))
                assert p.returncode == expected
            run(name, 0)
            status = json.loads((target / 'analysis-status.json').read_bytes())
            assert status['status'] == 'completed-with-warnings' and status['usable_for_differential_fit']
            original = table(example / payload['counts']); genes = [row[0] for row in original[1:]]
            published = table(target / 'normalized-counts.tsv')
            assert original[0] == published[0] and genes == [row[0] for row in published[1:]]
            c = dict(name=name, counts=[[int(x) for x in row[1:]] for row in original[1:]], type=payload['type'])
            if 'geometric_means' in payload:
                means = table(example / payload['geometric_means']); assert [row[0] for row in means[1:]] == genes
                c['geometric_means'] = [float(row[1]) for row in means[1:]]
            if 'controls' in payload:
                c['controls'] = [genes.index(row[0]) for row in table(example / payload['controls'])[1:]]
            cases.append(c)
            before = {p.relative_to(target).as_posix(): pin(p) for p in target.rglob('*') if p.is_file()}
            run(name + '-existing-output', 2)
            assert before == {p.relative_to(target).as_posix(): pin(p) for p in target.rglob('*') if p.is_file()}
            analyses.append(dict(name=name, path=target.relative_to(ROOT).as_posix(), files=before))
        save(out / 'numeric-reference-input.json', cases)
        command = [str(RSCRIPT), '--vanilla', str(ROOT / 'tools/literature/check_deseq2_examples.R'),
                   str(out / 'numeric-reference-input.json'), str(out / 'original.json'),
                   str(BASE / 'reference-setup-1/R-lib'), *map(str, BASES)]
        p = subprocess.run(command, cwd=ROOT, env=environment(), stdin=subprocess.DEVNULL, capture_output=True, timeout=60)
        (out / 'reference.stdout').write_bytes(p.stdout); (out / 'reference.stderr').write_bytes(p.stderr)
        save(out / 'reference-process.json', dict(command=command, exit_code=p.returncode)); assert p.returncode == 0
        original = json.loads((out / 'original.json').read_bytes()); comparisons = 0
        for observed, expected in zip(analyses, original, strict=True):
            assert expected['name'] == [observed['name']]
            target = ROOT / observed['path']
            actual_factors = [row[1] for row in table(target / 'size-factors.tsv')[1:]]
            actual_rows = [row[1:] for row in table(target / 'normalized-counts.tsv')[1:]]
            for a, b in [(actual_factors, expected['factors']), *zip(actual_rows, expected['normalized'], strict=True)]:
                for value, source in zip(a, b, strict=True):
                    assert math.isclose(float(value), float(source), rel_tol=2e-12, abs_tol=0)
                    comparisons += 1
        assert all(pin(ROOT / n) == expected for n, expected in source_pins.items())
        save(out / 'COMPLETED.json', dict(status='passed-literal-native-DESeq2-examples-against-original',
             artifact=pin(jar), source_pins=source_pins, examples=analyses, original_R_calls=2,
             finite_numeric_comparisons=comparisons, existing_output_refusals=2,
             exact_gene_and_sample_identities_retained=True, full_DESeq2_inference_performed=False))
        print('2 literal examples matched original R; exact identities retained; existing outputs preserved')
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_LITERAL_DESEQ2_EXAMPLE_VALIDATION', error=str(error)))
        raise


if __name__ == '__main__':
    main()
