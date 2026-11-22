"""Run five packaged synthetic association recipes against all nine complete reference tables."""
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
    """Java cannot run; dependent verification/analysis remains skipped."""


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(condition, cause):
    if not condition:
        raise ValueError(cause)


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.reader(stream, delimiter='\t'))


def compare(actual, expected, filename):
    check(actual and expected and actual[0] == expected[0], 'Reference header differs: ' + filename)
    check(len(actual) == len(expected), 'Complete row population differs: ' + filename)
    numeric = {'input_value', 'adjusted_p', 'adjusted_ln_p'} if filename == 'all-tests.tsv' else set()
    maximum = 0.0
    count = 0
    for index, (observed, reference) in enumerate(zip(actual[1:], expected[1:]), 1):
        check(len(observed) == len(reference) == len(actual[0]), 'Malformed row: ' + filename)
        for column, got, want in zip(actual[0], observed, reference):
            cause = f'{filename} row {index}, {column}: expected {want}, observed {got}'
            if column in numeric and want != 'NA':
                a, b = float(got), float(want)
                count += 1
                check(not math.isnan(a), cause)
                if b == 0 or not math.isfinite(b):
                    check(a == b, cause)
                else:
                    relative = abs(a-b)/abs(b)
                    check(relative <= 2e-14, cause + f'; relative difference {relative}')
                    maximum = max(maximum, relative)
            else:
                check(got == want, cause)
    return {'rows': len(actual)-1, 'fields': sum(len(row) for row in actual[1:]),
            'numeric_fields': count, 'maximum_numeric_relative_difference': maximum}


def verify(args):
    root = Path(__file__).resolve().parents[2]
    jar = args.jar.resolve()
    check(jar.is_file(), 'Current executable development JAR is missing: ' + str(jar))
    preparer = root / 'examples/fuma-known-association-workflow/prepare_inputs.py'
    spec = importlib.util.spec_from_file_location('fuma_association_example', preparer)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    digest = sha(jar)
    record = {'status': 'running', 'jar_sha256': digest, 'preparer_sha256': sha(preparer), 'attempts': [], 'runs': []}
    try:
        directory = module.prepare(work / 'inputs')
        protected = {name: sha(directory / name) for name in module.MEMBERS}
        for recipe in ['exact', 'selected-reference', 'across-traits', 'supplied-adjusted', 'parent']:
            check(sha(jar) == digest, 'Development JAR changed before execution')
            flag = '--workflow-options-file' if recipe == 'parent' else '--known-options-file'
            command = [args.java, '-Xmx768m', '-jar', str(jar), 'fuma-tools', flag, str(directory / (recipe + '.json'))]
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
                raise Unavailable('Selected Java cannot load this Java 17+ JAR')
            if result.returncode == 3:
                raise Unavailable(f'{recipe} returned SKIP 3: {errors.strip()}; inspect retained bundle WARNINGS.md')
            check(result.returncode == 0, f'{recipe} failed with exit {result.returncode}; inspect retained stderr: {errors[-1800:]}')
            check(sha(jar) == digest, 'Development JAR changed during execution')
            for name, expected in protected.items():
                check(sha(directory / name) == expected, 'Protected input/reference changed: ' + name)
            bundle = directory / (recipe + '-result')
            if recipe == 'parent':
                parent = read(bundle / 'manifest.json')
                check(parent['status'] == 'completed-with-review-warnings' and parent['exit_code'] == 0, 'Typed parent did not complete')
                check(parent['stages']['known-associations']['exit_code'] == 0, 'Typed association stage did not complete')
                check(read(bundle / 'workflow-journal.json')['status'] == parent['status'], 'Typed parent journal is not final')
                bundle /= 'known-associations'
            status = read(bundle / 'analysis-status.json')
            session = read(bundle / 'session.json')
            check(status['status'] == session['status'] == 'completed-with-review-warnings' and status['exit_code'] == 0,
                  'Association analysis did not complete')
            check(session['external_runtime_required'] is False and session['fisher_enrichment_performed'] is False,
                  'Unexpected fitting or enrichment claim')
            reference = 'exact' if recipe == 'parent' else recipe
            checks = {}
            for filename in ['analysis-sources.tsv', 'family-audit.tsv', 'family-members.tsv', 'all-tests.tsv',
                             'known-union.tsv', 'known-contributors.tsv', 'target-decisions.tsv',
                             'baseline-coverage.tsv', 'known-links.tsv']:
                checks[filename] = compare(table(bundle / filename), table(directory / 'reference' / reference / filename), filename)
            for key, filename in [('source_rows', 'analysis-sources.tsv'), ('family_rows', 'family-audit.tsv'),
                                  ('member_rows', 'family-members.tsv'), ('test_rows', 'all-tests.tsv'),
                                  ('known_union_rows', 'known-union.tsv'), ('known_contributor_rows', 'known-contributors.tsv'),
                                  ('target_rows', 'target-decisions.tsv'), ('baseline_coverage_rows', 'baseline-coverage.tsv'),
                                  ('known_link_rows', 'known-links.tsv')]:
                check(session[key] == checks[filename]['rows'], 'Session/result population differs: ' + key)
            check(session['scientific_result_rows'] == sum(value['rows'] for value in checks.values()), 'Scientific row population differs')
            for source, expected in session['source_sha256'].items():
                check(sha(Path(source)) == expected, 'Declared input hash differs: ' + source)
            ledger = table(bundle / 'resource-ledger.tsv')
            check(len(ledger) == (5 if recipe == 'parent' else 6), 'Exact snapshot/resource population differs')
            for role, source, snapshot, expected, size in ledger[1:]:
                original, copy = Path(source), bundle / snapshot
                check(sha(original) == expected == sha(copy), 'Source/snapshot identity differs: ' + role)
                check(original.stat().st_size == copy.stat().st_size == int(size), 'Source/snapshot byte population differs: ' + role)
            scientific_bytes = sum(path.stat().st_size for path in bundle.rglob('*') if path.is_file()
                                   and (path.suffix == '.tsv' or path.parent.name == 'source-inputs'))
            check(scientific_bytes == session['scientific_files_and_snapshot_bytes'], 'Scientific/snapshot byte population differs')
            warnings = (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
            for text in ['Required action', 'DECLARED FAMILY AND REFERENCE REVIEW', 'ENCODING AND CUTOFF',
                         'SUPPLIED ADJUSTED VALUES', 'SOURCE ANNOTATION/BUILD DIFFERENCES', 'never installs software']:
                check(text in warnings, 'Missing interpretation/recovery warning: ' + text)
            if recipe == 'supplied-adjusted':
                rows = table(bundle / 'family-audit.tsv')
                header = rows[0]
                check(all(row[header.index('adjustment')] == 'SUPPLIED_ADJUSTED' and
                          row[header.index('computed_adjustment_size')] == 'NA' for row in rows[1:]),
                      'Imported adjusted values falsely certify the original correction denominator')
            record['runs'].append({'recipe': recipe, 'exit_code': 0, 'complete_tables': checks,
                                   'scientific_bytes': scientific_bytes, 'bundle': str(bundle)})
        record.update(status='passed-five-public-synthetic-known-association-workflows',
                      numeric_rule='Relative error <=2e-14 for nonzero finite numeric fields; no absolute allowance. Exact zeros, infinities, NA, selection, integer and text fields.',
                      scope='Exact packaged Java-only complete-family adjustment/baseline union/target review. Complete independent R raw-P adjustment references and independent log extension, plus union/coverage/link references. All source/member/test/evidence rows and raw snapshots. No fitted associations, Fisher enrichment, historical paper defaults, whole FUMA or release equivalence.')
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
        print(f'ASSOCIATION VERIFICATION / DEPENDENT ANALYSIS SKIPPED: {failure}\nRequired action: provide Java 17+ on a permitted supported host, using --java if needed. If installation is unavailable or forbidden, keep the dependent analysis skipped. Inspect retained logs and verification.json, then rerun to a NEW directory. No MAGMA, R, Python package or upstream FUMA installation is required by these supplied-result Java recipes.', file=sys.stderr)
        sys.exit(3)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as failure:
        print(f'ASSOCIATION VERIFICATION FAILED: {failure}\nRequired action: inspect retained logs, verification.json and partial bundles; correct the named input/output cause, restore the pinned example/build the current executable development JAR if needed, then rerun to a NEW directory.', file=sys.stderr)
        sys.exit(2)
