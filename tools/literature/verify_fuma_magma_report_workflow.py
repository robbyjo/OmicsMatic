"""Check five packaged supplied MAGMA reports against complete synthetic original R results."""
from pathlib import Path
import argparse
import csv
import hashlib
import importlib.util
import json
import math
import subprocess
import sys
import zipfile


class Unavailable(Exception):
    """Java cannot run; dependent report analysis was skipped."""


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(condition, cause):
    if not condition:
        raise ValueError(cause)


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.reader(stream, delimiter='\t'))


def compare(actual, reference, columns=None):
    left, right = table(actual), table(reference)
    if columns:
        indices = [left[0].index(name) for name in columns]
        left = [[row[i] for i in indices] for row in left]
    check(left[0] == right[0] and len(left) == len(right), f'Complete table population/header differs: {actual}')
    numeric = zeros = 0
    maximum = 0.0
    for line, (a, b) in enumerate(zip(left[1:], right[1:]), 1):
        check(len(a) == len(b), f'Field population differs: {actual}:{line}')
        for column, (x, y) in enumerate(zip(a, b)):
            try:
                u, v = float(x), float(y)
            except ValueError:
                check(x == y, f'Text/NA field differs: {actual}:{line}:{column}')
                continue
            check(math.isfinite(u) and math.isfinite(v), f'Nonfinite numeric report field: {actual}:{line}:{column}')
            numeric += 1
            if v == 0:
                check(u == 0, f'Source zero differs: {actual}:{line}:{column}')
                zeros += 1
            else:
                relative = abs(u-v)/abs(v)
                check(relative <= 1e-14, f'Numeric field differs: {actual}:{line}:{column}: {x} versus {y}')
                maximum = max(maximum, relative)
    return {'rows': len(right)-1, 'fields': sum(len(row) for row in right[1:]),
            'numeric_fields': numeric, 'exact_zeros': zeros, 'maximum_relative_difference': maximum}


def verify(args):
    jar = args.jar.resolve()
    check(jar.is_file(), 'Current executable development JAR is missing: ' + str(jar))
    root = Path(__file__).resolve().parents[2]
    preparer = root / 'examples/fuma-magma-report-workflow/prepare_inputs.py'
    spec = importlib.util.spec_from_file_location('fuma_magma_report_example', preparer)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    digest = sha(jar)
    record = {'status': 'running', 'jar_sha256': digest, 'preparer_sha256': sha(preparer), 'attempts': [], 'runs': []}
    try:
        directory = module.prepare(work / 'inputs')
        for recipe, reference, family, placeholders in [
                ('minimal', 'baseline', 12, 0), ('fallback', 'baseline', 12, 0),
                ('padded', 'padded', 3, 7), ('missing', 'missing', 11, 1), ('parent', 'baseline', 12, 0)]:
            check(sha(jar) == digest, 'Development JAR changed before report execution')
            option = 'workflow-options-file' if recipe == 'parent' else 'magma-report-options-file'
            command = [args.java, '-Xmx768m', '-jar', str(jar), 'fuma-tools', '--' + option,
                       str(directory / (recipe + '.json'))]
            attempt = {'recipe': recipe, 'command': command}
            record['attempts'].append(attempt)
            try:
                with (work / (recipe + '.stdout')).open('xb') as out, (work / (recipe + '.stderr')).open('xb') as err:
                    result = subprocess.run(command, cwd=root, stdout=out, stderr=err, timeout=180)
            except OSError as failure:
                raise Unavailable('Cannot launch Java: ' + str(failure)) from failure
            attempt['exit_code'] = result.returncode
            errors = (work / (recipe + '.stderr')).read_text(encoding='utf-8', errors='replace')
            if 'UnsupportedClassVersionError' in errors:
                raise Unavailable('The selected Java is too old for this Java 17+ JAR')
            check(result.returncode == 0, f'{recipe} failed with exit {result.returncode}; inspect retained logs: {errors[-1500:]}')
            check(sha(jar) == digest, 'Development JAR changed during report execution')
            bundle = directory / (recipe + '-result')
            if recipe == 'parent':
                check((bundle / 'workflow-journal.json').is_file(), 'Missing typed parent journal')
                bundle /= 'magma-report'
            session = json.loads((bundle / 'session.json').read_text(encoding='utf-8'))
            status = json.loads((bundle / 'analysis-status.json').read_text(encoding='utf-8'))
            check(status['exit_code'] == 0 and status['status'] == 'completed-with-review-warnings', 'Report status is not completed')
            check(session['external_runtime_required'] is False, 'Unexpected external runtime requirement')
            check(session['gene_rows'] == 4 and session['annotation_rows'] == 4, 'Gene population differs')
            check(session['finite_adjustment_family'] == family and session['placeholder_rows'] == placeholders,
                  'Holm family or placeholder population differs')
            check(session['selected_set_source'] == ('sets' if recipe == 'fallback' else 'gsa'), 'Declared source preference differs')
            check(session['missing_P_rows'] == (1 if recipe == 'missing' else 0), 'Missing-P population differs')
            checks = {'genes.tsv': compare(bundle / 'genes.tsv', directory / 'reference/genes.tsv'),
                      'sets-top.tsv': compare(bundle / 'sets-top.tsv', directory / ('reference/' + reference + '-sets-top.tsv')),
                      'full-tests.tsv': compare(bundle / 'full-tests.tsv', directory / ('reference/' + reference + '-full-tests.tsv'),
                                                ['source_row', 'P', 'Pbon'])}
            all_sets, full_tests, display = table(bundle / 'sets-all.tsv'), table(bundle / 'full-tests.tsv'), table(bundle / 'display-rows.tsv')
            check(len(all_sets) == len(full_tests) and len(display) == len(table(bundle / 'sets-top.tsv')), 'Full/display ledgers lose rows')
            selected = session['selected_set_source']
            selected_path = directory / ('inputs/baseline.tsv' if recipe == 'fallback' else 'inputs/' + reference + '.gsa.out')
            raw_selected = table(selected_path) if recipe == 'fallback' else table(selected_path)[3:]
            check(all_sets[0] == raw_selected[0] + ['Pbon'], 'Prepared full-set header differs')
            check([row[:-1] for row in all_sets[1:]] == raw_selected[1:], 'Full-set input order/fields changed')
            ledger = table(bundle / 'resource-ledger.tsv')
            check(len(ledger) == (4 if recipe == 'parent' else 5), 'Raw snapshot population differs')
            for role, source, snapshot, expected, size in ledger[1:]:
                path, copy = Path(source), bundle / snapshot
                check(sha(path) == expected == sha(copy), 'Source/snapshot SHA differs: ' + role)
                check(path.stat().st_size == copy.stat().st_size == int(size), 'Raw snapshot bytes differ: ' + role)
            check(any(row[0] == 'supplied-' + selected + '-set-results' and row[3] == sha(selected_path)
                      for row in ledger[1:]), 'Selected result identity differs')
            scientific_bytes = sum(path.stat().st_size for path in bundle.rglob('*') if path.is_file() and
                                   (path.parent == bundle and path.suffix == '.tsv' or path.parent == bundle / 'source-inputs'))
            check(scientific_bytes == session['scientific_files_and_snapshot_bytes'], 'Retained scientific byte population differs')
            warnings = (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
            for text in ['HOLM DESPITE Pbon NAME', 'Required action', 'SKIP that affected fitting analysis', 'SYMBOL RESOURCE REVIEW']:
                check(text in warnings, 'Missing interpretation/recovery warning: ' + text)
            if placeholders:
                check('SOURCE REPORT PLACEHOLDERS' in warnings, 'Missing source-placeholder warning')
            if recipe == 'missing':
                check('MISSING P FAMILY' in warnings, 'Missing excluded-NA-family warning')
            record['runs'].append({'recipe': recipe, 'exit_code': 0, 'complete_tables': checks,
                                   'scientific_bytes': scientific_bytes, 'bundle': str(bundle)})
        record.update(status='passed-five-public-synthetic-MAGMA-report-workflows',
                      scope='Exact packaged Java-only supplied report, five public recipes: full original synthetic gene/report/complete Holm families, raw full-set order, snapshots/bytes, both formats, explicit source preference/padding/NA and typed parent. No MAGMA fit/model certification, biological-resource, historical-paper, whole FUMA or release equivalence.')
    except Unavailable as failure:
        record.update(status='skipped-dependency-unavailable', cause=str(failure))
        raise
    except BaseException as failure:
        record.update(status='failed-retained', cause=str(failure))
        raise
    finally:
        (work / 'verification.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': record['status'], 'runs': len(record['runs']), 'jar_sha256': digest}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    try:
        verify(args)
    except Unavailable as failure:
        print(f'MAGMA REPORT VERIFICATION / ANALYSIS SKIPPED: {failure}\nRequired action: supply Java 17 or newer on a permitted supported host, using --java if needed. If installation is unavailable or forbidden, keep this dependent analysis skipped. Rerun to a NEW directory. No R or MAGMA installation is required for these supplied report fixtures.', file=sys.stderr)
        sys.exit(3)
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.SubprocessError) as failure:
        print(f'MAGMA REPORT VERIFICATION FAILED: {failure}\nRequired action: inspect retained logs/verification.json, restore the pinned example/build the current executable development JAR if needed, correct the named input/output cause and rerun to a NEW directory.', file=sys.stderr)
        sys.exit(2)
