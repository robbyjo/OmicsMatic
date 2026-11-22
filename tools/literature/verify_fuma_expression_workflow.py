"""Run and audit complete synthetic native FUMA expression/DEG output populations."""
from pathlib import Path
import argparse
import csv
import hashlib
import importlib.util
import json
import math
import subprocess
import sys


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.reader(stream, delimiter='\t'))


def verify(args):
    jar = args.jar.resolve()
    if not jar.is_file():
        raise ValueError('Runnable development JAR is missing: ' + str(jar))
    root = Path(__file__).resolve().parents[2]
    prepare = root / 'examples/fuma-expression-workflow/prepare_inputs.py'
    spec = importlib.util.spec_from_file_location('fuma_expression_example', prepare)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    directory = module.prepare(work / 'inputs')
    record = {'status': 'running', 'jar_sha256': sha(jar), 'example_sha256': sha(prepare), 'runs': []}
    try:
        for method, carrier, target in [('expression', 'options.json', 'result'), ('workflow', 'workflow.json', 'workflow-result')]:
            command = [args.java, '-Xmx512m', '-jar', str(jar), 'fuma-tools', '--method', method,
                       '--' + ('expression-options-file' if method == 'expression' else 'workflow-options-file'), str(directory / carrier)]
            with (work / (method + '.stdout')).open('xb') as stdout, (work / (method + '.stderr')).open('xb') as stderr:
                result = subprocess.run(command, stdout=stdout, stderr=stderr, timeout=120, cwd=root)
            if result.returncode != 0:
                raise ValueError(f'{method} exit {result.returncode}; inspect {work / (method + ".stderr")}')
            bundle = directory / target
            if method == 'workflow':
                parent = json.loads((bundle / 'manifest.json').read_text(encoding='utf-8'))
                assert parent['status'] == 'completed-with-review-warnings'
                assert parent['stages']['expression']['exit_code'] == 0
                bundle = bundle / 'expression'
            session = json.loads((bundle / 'session.json').read_text(encoding='utf-8'))
            assert session['status'] == 'completed-with-review-warnings'
            assert session['expression_source_gate_opened'] and session['deg_source_gate_opened']
            assert session['log2_output_rows'] == session['norm_output_rows'] == 2
            assert session['deg_complete_rows'] == 4 and len(session['input_sha256']) == 6
            for path, digest in session['input_sha256'].items():
                assert sha(Path(path)) == digest, path
            header = ['ensg', 'symbol', 'Liver', 'Brain']
            for name, values in [('log2-expression.tsv', [[10.25, 0], [-1.5, 1]]), ('norm-expression.tsv', [[1, -1], [-2, 2]])]:
                rows = table(bundle / name)
                assert rows[0] == header and len(rows) == 3
                assert [r[:2] for r in rows[1:]] == [['E2', 'Beta'], ['E1', 'Alpha']]
                assert [[float(x) for x in r[2:]] for r in rows[1:]] == values
            rows = table(bundle / 'DEG.txt')
            assert rows[0] == ['Category', 'GeneSet', 'N_genes', 'N_overlap', 'p', 'adjP', 'genes']
            expected = [(['DEG.up', 'Liver', '2', '2'], 1/6, 1/3, 'E1:E2'),
                        (['DEG.up', 'Brain', '0', '0'], 1, 1, ''),
                        (['DEG.down', 'Brain', '2', '0'], 1, 1, ''),
                        (['DEG.twoside', 'Liver', '2', '2'], 1/6, 1/6, 'E1:E2')]
            assert len(rows) == len(expected) + 1
            for row, (fields, p, adjusted, genes) in zip(rows[1:], expected):
                assert row[:4] == fields and row[6] == genes
                assert math.isclose(float(row[4]), p, rel_tol=2e-14)
                assert math.isclose(float(row[5]), adjusted, rel_tol=2e-14)
            audit = table(bundle / 'deg-tests.tsv')
            assert len(audit) == 5 and all(r[15] == 'true' for r in audit[1:])
            assert audit[1][5] == '99' and audit[1][7] == '2'
            assert 'Required action' in (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
            record['runs'].append({'method': method, 'complete_matrix_rows': 4, 'matrix_values': 8,
                                   'complete_deg_rows': 4, 'deg_probabilities': 8, 'bundle': str(bundle)})
        record['status'] = 'passed-two-public-synthetic-expression-workflows'
        record['scope'] = ('Both complete native synthetic command paths and all output rows/values. '
                           'No original runtime, historical biological resources, expression transformation, '
                           'DEG generation, heatmap, celltype MAGMA or complete FUMA coverage claim.')
    except BaseException as failure:
        record.update(status='failed-retained', cause=str(failure))
        raise
    finally:
        (work / 'verification.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(record, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--java', default='java')
    arguments = parser.parse_args()
    try:
        verify(arguments)
    except (OSError, ValueError, AssertionError, subprocess.SubprocessError) as failure:
        print(f'VERIFICATION FAILED: {failure}\nRequired action: inspect retained logs/verification.json, correct the named JAR/input/Java/output cause and rerun to a NEW directory.', file=sys.stderr)
        sys.exit(2)
