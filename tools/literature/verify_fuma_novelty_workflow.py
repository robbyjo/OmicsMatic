"""Run five packaged synthetic locus-novelty recipes against complete independent references."""
from pathlib import Path
import argparse
import csv
import hashlib
import importlib.util
import json
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


def verify(args):
    root = Path(__file__).resolve().parents[2]
    jar = args.jar.resolve()
    check(jar.is_file(), 'Current executable development JAR is missing: ' + str(jar))
    preparer = root / 'examples/fuma-novelty-workflow/prepare_inputs.py'
    spec = importlib.util.spec_from_file_location('fuma_novelty_example', preparer)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    digest = sha(jar)
    record = {'status': 'running', 'jar_sha256': digest, 'preparer_sha256': sha(preparer), 'attempts': [], 'runs': []}
    try:
        directory = module.prepare(work / 'inputs')
        protected = {name: sha(directory / name) for name in module.MEMBERS}
        for recipe in ['inclusive', 'strict', 'all-known', 'unknown-coverage', 'parent']:
            check(sha(jar) == digest, 'Development JAR changed before execution')
            flag = '--workflow-options-file' if recipe == 'parent' else '--novelty-options-file'
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
                check(parent['stages']['locus-novelty']['exit_code'] == 0, 'Typed novelty stage did not complete')
                check(read(bundle / 'workflow-journal.json')['status'] == parent['status'], 'Typed parent journal is not final')
                bundle /= 'locus-novelty'
            status = read(bundle / 'analysis-status.json')
            session = read(bundle / 'session.json')
            check(status['status'] == session['status'] == 'completed-with-review-warnings' and status['exit_code'] == 0,
                  'Novelty analysis did not complete')
            check(session['external_runtime_required'] is False, 'Unexpected upstream runtime requirement')
            checks = {}
            reference = 'inclusive' if recipe == 'parent' else recipe
            for filename in ['locus-novelty.tsv', 'known-matches.tsv', 'known-rows.tsv', 'coverage-rows.tsv']:
                actual = table(bundle / filename)
                expected = table(directory / 'reference' / reference / filename)
                check(actual == expected, 'Complete integer/text reference differs: ' + str(bundle / filename))
                checks[filename] = {'rows': len(actual)-1, 'fields': sum(len(row) for row in actual[1:])}
            for key, filename in [('lead_rows', 'locus-novelty.tsv'), ('known_rows', 'known-rows.tsv'),
                                  ('known_match_rows', 'known-matches.tsv'), ('coverage_rows', 'coverage-rows.tsv')]:
                check(session[key] == checks[filename]['rows'], 'Session/result population differs: ' + key)
            check(session['scientific_result_rows'] == sum(value['rows'] for value in checks.values()), 'Scientific row population differs')
            for source, expected in session['source_sha256'].items():
                check(sha(Path(source)) == expected, 'Declared input hash differs: ' + source)
            ledger = table(bundle / 'resource-ledger.tsv')
            expected_resources = (2 if recipe == 'unknown-coverage' else 3) + (0 if recipe == 'parent' else 1)
            check(len(ledger) == expected_resources+1, 'Exact snapshot/resource population differs')
            for role, source, snapshot, expected, size in ledger[1:]:
                original, copy = Path(source), bundle / snapshot
                check(sha(original) == expected == sha(copy), 'Source/snapshot identity differs: ' + role)
                check(original.stat().st_size == copy.stat().st_size == int(size), 'Source/snapshot byte population differs: ' + role)
            scientific_bytes = sum(path.stat().st_size for path in bundle.rglob('*') if path.is_file()
                                   and (path.suffix == '.tsv' or path.parent.name == 'source-inputs'))
            check(scientific_bytes == session['scientific_files_and_snapshot_bytes'], 'Scientific/snapshot byte population differs')
            warnings = (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
            for text in ['Required action', 'Coverage is an investigator assertion', 'PAPER RULE CHOICES',
                         'not deduplicated into independent evidence', 'never installs software']:
                check(text in warnings, 'Missing interpretation/recovery warning: ' + text)
            if recipe == 'unknown-coverage':
                check(session['novel_relative_to_reference'] == 0, 'Unknown reference coverage falsely implies novelty')
            record['runs'].append({'recipe': recipe, 'exit_code': 0, 'complete_tables': checks,
                                   'scientific_bytes': scientific_bytes, 'bundle': str(bundle)})
        record.update(status='passed-five-public-synthetic-locus-novelty-workflows',
                      scope='Exact packaged Java-only supplied-reference novelty review. Complete independently computed integer/text populations, all known/source rows and raw snapshots; explicit 250kb endpoint/trait choices, unknown coverage and typed parent. No catalog acquisition, LD/allele/causal inference, universal novelty, historical FUMA defaults or whole-paper/release equivalence.')
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
        print(f'NOVELTY VERIFICATION / DEPENDENT ANALYSIS SKIPPED: {failure}\nRequired action: provide Java 17+ on a permitted supported host, using --java if needed. If installation is unavailable or forbidden, keep the dependent analysis skipped. Inspect retained logs and verification.json, then rerun to a NEW directory. No MAGMA, Python package, R or upstream FUMA installation is required by these supplied-reference Java recipes.', file=sys.stderr)
        sys.exit(3)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as failure:
        print(f'NOVELTY VERIFICATION FAILED: {failure}\nRequired action: inspect retained logs, verification.json and partial bundles; correct the named input/output cause, restore the pinned example/build the current executable development JAR if needed, then rerun to a NEW directory.', file=sys.stderr)
        sys.exit(2)
