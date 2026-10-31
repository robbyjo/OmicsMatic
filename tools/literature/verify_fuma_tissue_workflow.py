"""Check seven packaged synthetic tissue recipes against complete original MAGMA fits."""
from pathlib import Path
import argparse
import csv
import hashlib
import importlib.util
import json
import math
import re
import subprocess
import sys
import zipfile


class Unavailable(Exception):
    """A required runtime is unavailable; dependent analyses remain skipped."""


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(condition, cause):
    if not condition:
        raise ValueError(cause)


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def table(path, original=False):
    if original:
        lines = [line for line in path.read_text(encoding='utf-8').splitlines()
                 if line.strip() and not line.startswith('#')]
        header = lines[0].split()
        rows = [line.split(None, len(header)-1) for line in lines[1:]]
    else:
        with path.open(encoding='utf-8', newline='') as stream:
            header, *rows = csv.reader(stream, delimiter='\t')
    check(all(len(row) == len(header) for row in rows), 'Ragged table: ' + str(path))
    return header, rows


def compare(actual, reference, original=False):
    left_header, left = table(actual)
    right_header, right = table(reference, original)
    expanded = 0
    if original and 'FULL_NAME' in right_header:
        index = right_header.index('FULL_NAME')
        for row in right:
            if row[index] not in ('', '.'):
                row[right_header.index('VARIABLE')] = row[index]
                expanded += 1
        right = [row[:index] + row[index+1:] for row in right]
        right_header = right_header[:index] + right_header[index+1:]
    check(left_header == right_header and len(left) == len(right), 'Complete table population/header differs: ' + str(actual))
    maximum = 0.0
    numeric = zeros = 0
    for line, (a, b) in enumerate(zip(left, right), 1):
        for column, (x, y) in enumerate(zip(a, b)):
            try:
                u, v = float(x), float(y)
            except ValueError:
                check(x == y, f'Text field differs: {actual}:{line}:{column}: {x} versus {y}')
                continue
            check(math.isfinite(u) and math.isfinite(v), f'Nonfinite fit: {actual}:{line}:{column}')
            numeric += 1
            if v == 0:
                check(u == 0, f'Original zero differs: {actual}:{line}:{column}')
                zeros += 1
            else:
                relative = abs(u-v)/abs(v)
                check(relative <= 1e-12, f'Numeric field differs: {actual}:{line}:{column}: {x} versus {y}')
                maximum = max(maximum, relative)
    return {'rows': len(right), 'fields': len(right)*len(right_header), 'numeric_fields': numeric,
            'exact_zeros': zeros, 'source_full_names_expanded': expanded, 'maximum_relative_difference': maximum}


def verify(args):
    root = Path(__file__).resolve().parents[2]
    jar = args.jar.resolve()
    check(jar.is_file(), 'Executable development JAR is missing: ' + str(jar))
    preparer = root / 'examples/fuma-tissue-workflow/prepare_inputs.py'
    spec = importlib.util.spec_from_file_location('fuma_tissue_example', preparer)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    digest = sha(jar)
    record = {'status': 'running', 'jar_sha256': digest, 'preparer_sha256': sha(preparer),
              'attempts': [], 'runs': [], 'completed_MAGMA_calls': 0}
    try:
        directory = module.prepare(work / 'inputs', args.home)
        recipes = read(directory / 'recipes.json')
        protected = {name: sha(directory / name) for name in module.MEMBERS}
        for recipe in ['detailed', 'general', 'long', 'mean', 'drop', 'parent', 'marginal']:
            check(sha(jar) == digest, 'Development JAR changed before execution')
            command = [args.java, '-Xmx768m', '-jar', str(jar)]
            if recipe in ('parent', 'marginal'):
                flag = '--workflow-options-file' if recipe == 'parent' else '--celltype-options-file'
                command += ['fuma-tools', flag, str(directory / (recipe + '.json'))]
            else:
                command.append('magma-tools')
                for key, value in recipes[recipe].items():
                    if key in ('gene-bundle', 'properties', 'out'):
                        value = str(directory / value)
                    command += ['--' + key, value]
            attempt = {'recipe': recipe, 'command': command}
            record['attempts'].append(attempt)
            try:
                with (work / (recipe + '.stdout')).open('xb') as out, (work / (recipe + '.stderr')).open('xb') as err:
                    result = subprocess.run(command, cwd=root, stdout=out, stderr=err, timeout=240)
            except OSError as failure:
                raise Unavailable('Cannot launch Java: ' + str(failure)) from failure
            attempt['exit_code'] = result.returncode
            errors = (work / (recipe + '.stderr')).read_text(encoding='utf-8', errors='replace')
            if 'UnsupportedClassVersionError' in errors:
                raise Unavailable('Selected Java cannot load this Java 17+ JAR')
            if result.returncode == 3:
                raise Unavailable(f'{recipe} returned SKIP 3: {errors.strip()}; inspect retained bundle WARNINGS.md')
            check(result.returncode == 0, f'{recipe} failed with exit {result.returncode}; retained stderr: {errors[-1800:]}')
            check(sha(jar) == digest, 'Development JAR changed during execution')
            for name, expected in protected.items():
                check(sha(directory / name) == expected, 'Protected input/reference changed: ' + name)
            bundle = directory / (recipe + '-result')
            if recipe == 'parent':
                parent = read(bundle / 'manifest.json')
                check(parent['status'] == 'completed-with-review-warnings' and parent['exit_code'] == 0,
                      'Typed parent did not complete')
                check(parent['stages']['magma-enrichment']['exit_code'] == 0, 'Parent MAGMA stage failed')
                check(read(bundle / 'workflow-journal.json')['status'] == parent['status'], 'Parent journal not final')
                bundle /= 'magma-enrichment'
            status = read(bundle / 'analysis-status.json')
            warnings = (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
            check('Required action' in warnings, 'Missing prominent review/recovery action')
            if recipe == 'marginal':
                check(status['status'] == 'completed-with-review-warnings' and status['exit_code'] == 0,
                      'Marginal workflow did not complete')
                session = read(bundle / 'session.json')
                check(session['complete_marginal_tests'] == session['requested_original_models'] == 84,
                      'Marginal test population differs')
                check(session['correction'] == 'BH' and not session['step2_requested'] and not session['step3_requested'],
                      'Correction or conditional stages differ from explicit recipe')
                check(len(session['model_calls']) == 2 and session['gene_universe_size'] == 201, 'Marginal model-call universe differs')
                for call in session['model_calls']:
                    expected = {'Detail54': 54, 'General30': 30}[call['dataset']]
                    check(call['status'] == 'completed' and call['expected_models'] == call['fit_rows'] == expected,
                          'Incomplete original marginal model population')
                    check(session[call['call'] + '_exit_code'] == 0, 'Original marginal command failed')
                    raw = bundle / Path(call['native_output'].replace('\\', '/'))
                    check(sha(raw) == call['output_sha256'], 'Original coefficient output identity changed')
                    call_bytes = sum(path.stat().st_size for path in raw.parent.iterdir() if path.is_file())
                    check(call_bytes == call['output_bytes'], 'Retained original call-file byte population differs')
                for source, expected in session['input_sha256'].items():
                    check(sha(Path(source)) == expected, 'Declared marginal input hash differs: ' + source)
                statistics = compare(bundle / 'step1.tsv', directory / 'reference/step1.tsv')
                counts = {}
                header, rows = table(bundle / 'step1.tsv')
                for row in rows:
                    counts[row[0]] = counts.get(row[0], 0) + 1
                check(counts == {'Detail54': 54, 'General30': 30}, 'Correction families differ')
                calls = 2
            else:
                check(status['status'] == 'completed-with-warnings', 'MAGMA bundle did not complete')
                session = (bundle / 'session.yaml').read_text(encoding='utf-8')
                expected_recipe = 'detailed' if recipe == 'parent' else recipe
                expected_rows = {'detailed': 54, 'general': 30, 'long': 3, 'mean': 3, 'drop': 3}[expected_recipe]
                for pattern in [r'^analysis_exit_code: 0$', r'^hidden_conditioning_variables: \[Average\]$',
                                r'^ordinary_conditioning_variables: \[\]$',
                                r'^reported_model_coefficient_rows: ' + str(expected_rows) + r'$']:
                    check(re.search(pattern, session, re.M), 'Missing original-call/hidden-population record: ' + pattern)
                check('condition-hide=Average' in session and 'correct=all' in session, 'Required upstream model flags not recorded')
                check(sha(bundle / 'input.properties.tsv') == sha(directory / recipes[expected_recipe]['properties']),
                      'Supplied property/Average bytes changed')
                check('HIDDEN CONDITIONING VARIABLES' in warnings and 'not multiple-test adjustment' in warnings,
                      'Hidden-role/correction warning missing')
                statistics = compare(bundle / 'results.tsv', directory / ('reference/' + expected_recipe + '.gsa.out.txt'), True)
                check(statistics['rows'] == expected_rows, 'Hidden coefficient leaked or target rows lost')
                check(all(row[0] != 'Average' for row in table(bundle / 'results.tsv')[1]), 'Average reported as target')
                # Inspect original effective denominators; mean imputation does not imply one repaired NGENES.
                genes = {int(row[table(bundle / 'results.tsv')[0].index('NGENES')]) for row in table(bundle / 'results.tsv')[1]}
                check(genes == ({200, 201} if recipe == 'mean' else {198} if recipe == 'drop' else {201}),
                      'Effective original NGENES population differs')
                calls = 1
            record['completed_MAGMA_calls'] += calls
            record['runs'].append({'recipe': recipe, 'exit_code': 0, 'table': statistics, 'bundle': str(bundle)})
        record.update(status='passed-seven-public-synthetic-tissue-workflows',
                      scope='Five direct supplied-property fits, typed parent and optional existing marginal/BH workflow. Complete original coefficients and explicit 54/30/84 R BH families, preserved inputs and effective NGENES. Windows MAGMA 1.10 adapter; no expression/Average computation, biological GTEx resource, historical paper, full FUMA, Java statistical solver or release equivalence.')
    except Unavailable as failure:
        record.update(status='skipped-dependency-unavailable', cause=str(failure))
        raise
    except BaseException as failure:
        record.update(status='failed-retained', cause=str(failure))
        raise
    finally:
        (work / 'verification.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': record['status'], 'runs': len(record['runs']),
                      'completed_MAGMA_calls': record['completed_MAGMA_calls'], 'jar_sha256': digest}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--home', required=True, type=Path)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    try:
        verify(args)
    except Unavailable as failure:
        print(f'TISSUE VERIFICATION / DEPENDENT ANALYSIS SKIPPED: {failure}\nRequired action: provide Java 17+ and a privately admitted unmodified Windows x86_64 MAGMA 1.10 home using --java/--home. Install only where permitted using the documented installer. If installation is unavailable or forbidden, keep these fits skipped. The current adapter has no Linux fitting profile. Inspect retained logs, verification.json and bundle WARNINGS.md; rerun to a NEW directory.', file=sys.stderr)
        sys.exit(3)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as failure:
        print(f'TISSUE VERIFICATION FAILED: {failure}\nRequired action: inspect retained logs, verification.json and partial bundles; correct the named input/runtime/output cause, restore the pinned example/build the current executable development JAR if needed, then rerun to a NEW directory.', file=sys.stderr)
        sys.exit(2)
