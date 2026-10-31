"""Audit four complete synthetic classic cell-type recipes against original R outputs."""
from pathlib import Path
import argparse
import csv
import hashlib
import importlib.util
import json
import math
import subprocess
import sys


class Unavailable(Exception):
    """Required fitting runtime or Java is unavailable; dependent analysis was skipped."""


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(condition, cause):
    if not condition:
        raise ValueError(cause)


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.reader(stream, delimiter='\t'))


def compare(actual, expected):
    left, right = table(actual), table(expected)
    check(bool(left) and bool(right) and left[0] == right[0], 'Header differs: ' + str(actual))
    check(len(left) == len(right), 'Complete row population differs: ' + str(actual))
    numeric, maximum = 0, 0.0
    for line, (a, b) in enumerate(zip(left[1:], right[1:]), 2):
        check(len(a) == len(b), f'Column population differs at {actual}:{line}')
        for column, (x, y) in enumerate(zip(a, b), 1):
            if x == y:
                continue
            try:
                fx, fy = float(x), float(y)
            except ValueError:
                raise ValueError(f'Field differs at {actual}:{line}:{column}: {x!r} vs {y!r}') from None
            check(math.isfinite(fx) and math.isfinite(fy) and math.isclose(fx, fy, rel_tol=2e-14),
                  f'Numeric field differs at {actual}:{line}:{column}: {x} vs {y}')
            maximum = max(maximum, abs(fx-fy) / max(abs(fx), abs(fy)))
            numeric += 1
    return {'rows': len(left)-1, 'differently_serialized_numeric_fields': numeric, 'max_relative_difference': maximum}


def verify(args):
    jar = args.jar.resolve()
    check(jar.is_file(), 'Runnable development JAR is missing: ' + str(jar))
    root = Path(__file__).resolve().parents[2]
    preparer = root / 'examples/fuma-celltype-workflow/prepare_inputs.py'
    spec = importlib.util.spec_from_file_location('fuma_celltype_example', preparer)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    record = {'status': 'running', 'jar_sha256': sha(jar), 'preparer_sha256': sha(preparer), 'runs': []}
    try:
        directory = module.prepare(work / 'inputs', args.home)
        for recipe, calls, rows in [('marginal', 2, 6), ('within', 4, 14), ('cross', 6, 18), ('parent', 6, 18)]:
            carrier = 'workflow-options-file' if recipe == 'parent' else 'celltype-options-file'
            command = [args.java, '-Xmx1536m', '-jar', str(jar), 'fuma-tools', '--' + carrier,
                       str(directory / (recipe + '.json'))]
            try:
                with (work / (recipe + '.stdout')).open('xb') as stdout, (work / (recipe + '.stderr')).open('xb') as stderr:
                    result = subprocess.run(command, stdout=stdout, stderr=stderr, cwd=root, timeout=300)
            except FileNotFoundError as failure:
                raise Unavailable('Java executable unavailable: ' + str(failure)) from failure
            bundle = directory / (recipe + '-result')
            warning = bundle / 'WARNINGS.md'
            if result.returncode == 3:
                cause = warning.read_text(encoding='utf-8') if warning.is_file() else (work / (recipe + '.stderr')).read_text(encoding='utf-8', errors='replace')
                record['runs'].append({'recipe': recipe, 'exit_code': 3, 'status': 'skipped-dependency-unavailable', 'warnings': str(warning), 'cause': cause})
                raise Unavailable(cause)
            check(result.returncode == 0, f'{recipe} exit {result.returncode}; inspect {work / (recipe + ".stderr")} and {warning}')
            if recipe == 'parent':
                parent = json.loads((bundle / 'manifest.json').read_text(encoding='utf-8'))
                check(parent['status'] == 'completed-with-review-warnings' and parent['stages']['cell-type']['exit_code'] == 0,
                      'Parent did not admit a completed cell-type stage')
                bundle = bundle / 'cell-type'
            session = json.loads((bundle / 'session.json').read_text(encoding='utf-8'))
            check(session['status'] == 'completed-with-review-warnings', 'Child analysis did not complete')
            check(session['gene_universe_size'] == 201 and session['complete_marginal_tests'] == 6, 'Gene/test population differs')
            check(len(session['model_calls']) == calls and sum(c['fit_rows'] for c in session['model_calls']) == rows,
                  'Complete original call/fit population differs')
            for call in session['model_calls']:
                check(call['status'] == 'completed' and session[call['call'] + '_exit_code'] == 0, 'Incomplete original fit')
                check(sha(bundle / call['native_output']) == call['output_sha256'], 'Original output hash differs')
            for path, digest in session['input_sha256'].items():
                check(sha(Path(path)) == digest, 'Input changed: ' + path)
            checks = {'step1.tsv': compare(bundle / 'step1.tsv', directory / 'reference/step1.tsv')}
            if recipe != 'marginal':
                for name in ['step1-2-summary.tsv', 'step2.tsv']:
                    checks[name] = compare(bundle / name, directory / 'reference' / name)
            else:
                check(not (bundle / 'step2.tsv').exists(), 'Unrequested within-dataset fits appeared')
            if recipe in {'cross', 'parent'}:
                checks['step3.tsv'] = compare(bundle / 'step3.tsv', directory / 'reference/step3.tsv')
            else:
                check(not (bundle / 'step3.tsv').exists(), 'Unrequested cross-dataset fits appeared')
            check(len(table(bundle / 'resource-coverage.tsv')) == 403, 'Resource coverage ledger differs')
            check('Required action' in (bundle / 'WARNINGS.md').read_text(encoding='utf-8'), 'Recovery/review instructions missing')
            record['runs'].append({'recipe': recipe, 'exit_code': 0, 'original_MAGMA_calls': calls,
                                   'original_fit_rows': rows, 'complete_tables': checks, 'bundle': str(bundle)})
        record.update(status='passed-four-public-synthetic-celltype-workflows',
                      scope='All output table rows/fields versus pinned original R reference data and actual original Windows MAGMA fits. Synthetic numerical validation; no historical biological resource, Linux fitting, other celltype metric or whole-FUMA equivalence claim.')
    except Unavailable as failure:
        record.update(status='skipped-dependency-unavailable', cause=str(failure))
        raise
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
    parser.add_argument('--home', required=True, type=Path)
    parser.add_argument('--java', default='java')
    arguments = parser.parse_args()
    try:
        verify(arguments)
    except Unavailable as failure:
        print(f'CELL-TYPE VERIFICATION / ANALYSIS SKIPPED: {failure}\nRequired action: supply Java 17+ and a permitted, admitted original Windows x86_64 MAGMA 1.10 home on Windows. See the retained WARNINGS.md/verification.json. If installation is unavailable or forbidden, skip fitting; do not report a completed analysis. Rerun to a NEW directory.', file=sys.stderr)
        sys.exit(3)
    except (OSError, ValueError, subprocess.SubprocessError) as failure:
        print(f'CELL-TYPE VERIFICATION FAILED: {failure}\nRequired action: inspect retained logs/verification.json, build the current JAR if missing, correct the named input/output cause and rerun to a NEW directory.', file=sys.stderr)
        sys.exit(2)
