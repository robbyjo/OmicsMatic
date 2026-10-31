"""Run six packaged synthetic Fisher recipes against complete independent references."""
from pathlib import Path
from collections import Counter
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
    """Java cannot run; dependent verification and analysis remain skipped."""


def check(condition, cause):
    if not condition:
        raise ValueError(cause)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream, delimiter='\t')
        rows = list(reader)
        check(reader.fieldnames and len(set(reader.fieldnames)) == len(reader.fieldnames), 'Invalid output header: ' + str(path))
        check(all(None not in row and all(value is not None for value in row.values()) for row in rows), 'Malformed output row: ' + str(path))
        return rows


def relative(expected, actual, bound, context):
    reference, value = float(expected), float(actual)
    check(not math.isnan(value), context + ': NaN')
    if reference == 0 or not math.isfinite(reference):
        check(value == reference, context + ': zero/infinity differs')
        return 0.0
    difference = abs(value-reference)/abs(reference)
    check(math.isfinite(value) and difference <= bound, context + ': relative difference ' + str(difference))
    return difference


def output_hashes(path):
    """Read only this executable's generated flat ASCII member/hash ledger, not general YAML."""
    text = path.read_text(encoding='utf-8')
    match = re.search(r'^output_sha256: \{(.*?)\}\s*\ncompleted_at:', text, re.MULTILINE | re.DOTALL)
    check(match is not None, 'Unsupported generated manifest hash format: ' + str(path))
    content = match.group(1)
    tokens = list(re.finditer(r'([A-Za-z0-9_./\\-]+): ([0-9a-f]{64})', content))
    check(tokens and re.fullmatch(r'[\s,]*', re.sub(r'([A-Za-z0-9_./\\-]+): ([0-9a-f]{64})', '', content)), 'Invalid generated output hash ledger')
    result = {}
    for token in tokens:
        name = token.group(1).replace('\\', '/')
        check(name not in result and all(part not in ['', '.', '..'] for part in name.split('/')), 'Invalid/duplicate generated output member')
        result[name] = token.group(2)
    return text, result


def verify_bundle(bundle, operation):
    status, session = read(bundle / 'analysis-status.json'), read(bundle / 'session.json')
    check(status == {'status': 'completed-with-review-warnings', 'exit_code': 0} and session['status'] == status['status'], 'Analysis status/session is not completed')
    text, outputs = output_hashes(bundle / 'manifest.yaml')
    check(re.search(r'^operation: ' + re.escape(operation) + r'\s*$', text, re.MULTILINE), 'Unexpected native operation')
    check(re.search(r'^analysis_status: completed-with-review-warnings\s*$', text, re.MULTILINE), 'Manifest does not admit completion')
    actual = {path.relative_to(bundle).as_posix() for path in bundle.rglob('*') if path.is_file() and path != bundle / 'manifest.yaml'}
    check(actual == set(outputs), 'Native manifest member population differs')
    for name, digest in outputs.items():
        check(not (bundle / name).is_symlink() and sha(bundle / name) == digest, 'Native output hash differs: ' + name)
    for source, digest in session['source_sha256'].items():
        check(sha(Path(source)) == digest, 'Original source hash differs: ' + source)
    ledger = table(bundle / 'resource-ledger.tsv')
    for row in ledger:
        original, snapshot = Path(row['source']), bundle / row['snapshot']
        check(sha(original) == sha(snapshot) == row['sha256'], 'Source/snapshot identity differs: ' + row['role'])
        check(original.stat().st_size == snapshot.stat().st_size == int(row['bytes']), 'Source/snapshot byte count differs')
    scientific_bytes = sum(path.stat().st_size for path in bundle.rglob('*') if path.is_file() and (path.suffix == '.tsv' or path.parent.name == 'source-inputs'))
    check(session['scientific_files_and_snapshot_bytes'] == scientific_bytes, 'Scientific/snapshot byte population differs')
    warnings = (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
    check('Required action:' in warnings and 'never installs software' in warnings, 'Missing interpretation/recovery instructions')
    return {'manifest_members': len(outputs), 'snapshots': len(ledger), 'scientific_bytes': scientific_bytes}


def compare_tests(bundle, directory, friendly):
    wanted = table(directory / 'reference' / friendly / 'upstream-tests.tsv')
    actual = table(bundle / 'all-tests.tsv')
    check(len(wanted) == len(actual), 'Upstream test population differs')
    for expected, row in zip(wanted, actual):
        for key, value in expected.items():
            if key in ['input_value', 'adjusted_p'] and value != 'NA':
                relative(value, row[key], 2e-14, 'Upstream ' + key)
            else:
                check(row[key] == value, 'Upstream evidence differs: ' + key)
    check(table(bundle / 'target-decisions.tsv') == table(directory / 'reference' / friendly / 'target-decisions.tsv'), 'Complete upstream decision population differs')
    return verify_bundle(bundle, 'fuma-known-associations')


def compare_fisher(bundle, reference):
    actual = table(bundle / 'fisher-tables.tsv')
    check(len(actual) == len(reference), 'Complete Fisher table population differs')
    numeric_fields, maximum = 0, 0.0
    available, empty, selected, underflows = Counter(), Counter(), Counter(), Counter()
    all_rows = Counter()
    for index, (expected, row) in enumerate(zip(reference, actual), 1):
        check(row['input_row'] == str(index), 'Fisher input row order differs')
        for key in ['family', 'test_id', 'trait', 'context', 'pseudocount', 'confidence']:
            check(row[key] == expected[key], 'Fisher identity/policy differs: ' + key)
        family = row['family']
        all_rows[family] += 1
        if expected['a'] == 'NA':
            check(row['status'] == 'MISSING_COUNTS' and all(row[k] == 'NA' for k in ['original_finer_novel', 'original_finer_known', 'original_comparator_novel', 'original_comparator_known', 'tested_finer_novel', 'tested_finer_known', 'tested_comparator_novel', 'tested_comparator_known', 'p', 'ln_p', 'conditional_odds_ratio', 'lower_confidence', 'upper_confidence', 'support_states', 'adjusted_p', 'adjusted_ln_p', 'selected']), 'Missing counts were fitted or invented')
            continue
        values = [int(expected[k]) for k in ['a', 'b', 'c', 'd']]
        is_empty = sum(values[:2]) == 0 or sum(values[2:]) == 0
        available[family] += 1
        empty[family] += is_empty
        selected[family] += expected['selected'] == 'true'
        underflows[family] += float(expected['adjusted']) == 0 and math.isfinite(float(expected['log_adjusted']))
        check(row['status'] == ('EMPTY_ORIGINAL_GROUP_RETAINED' if is_empty else 'AVAILABLE'), 'Original empty provenance differs')
        for key, value in zip(['finer_novel', 'finer_known', 'comparator_novel', 'comparator_known'], values):
            check(int(row['original_' + key]) == value and int(row['tested_' + key]) == value + int(expected['pseudocount']), 'Original/tested count differs')
        a, b, c, d = [value + int(expected['pseudocount']) for value in values]
        check(int(row['support_states']) == min(a+c, a+b)-max(0, a-d)+1, 'Complete support population differs')
        check(row['selected'] == expected['selected'], 'Inclusive Fisher selection differs')
        logarithmic = expected['encoding'] == 'NATURAL_LOG'
        for key, field in [('p', 'p'), ('ln_p', 'log_p'), ('conditional_odds_ratio', 'OR'), ('lower_confidence', 'lower'), ('upper_confidence', 'upper'), ('adjusted_p', 'adjusted'), ('adjusted_ln_p', 'log_adjusted')]:
            numeric_fields += 1
            reference_value, value = float(expected[field]), float(row[key])
            if key in ['ln_p', 'adjusted_ln_p']:
                if reference_value == 0 or not math.isfinite(reference_value):
                    check(value == reference_value, 'Log zero/infinity differs')
                elif key == 'adjusted_ln_p' and not logarithmic:
                    check(abs(math.expm1(value-reference_value)) <= 2e-14, 'Log of probability adjustment differs')
                else:
                    check(math.isfinite(value) and abs(value-reference_value) <= max(16*math.ulp(reference_value), 2e-14), 'Finite log extension differs')
            else:
                bound = 2e-14
                if key == 'adjusted_p' and logarithmic and reference_value != 0:
                    bound = abs(math.expm1(float(row['adjusted_ln_p'])-float(expected['log_adjusted']))) + 4*math.ulp(reference_value)/reference_value
                maximum = max(maximum, relative(reference_value, value, bound, 'Fisher ' + key))
    audits = table(bundle / 'family-audit.tsv')
    check(len(audits) == len(all_rows), 'Fisher family population differs')
    for row in audits:
        family = row['family']
        check(int(row['expected_rows']) == int(row['actual_rows']) == all_rows[family], 'Declared family population differs')
        check(int(row['available_rows']) == int(row['computed_adjustment_size']) == available[family], 'Available correction denominator differs')
        check(int(row['missing_rows']) == all_rows[family]-available[family] and int(row['empty_original_groups']) == empty[family] and int(row['selected_rows']) == selected[family], 'Family availability/selection audit differs')
        check(int(row['adjusted_probability_underflows']) == underflows[family], 'Family finite-log underflow audit differs')
    check(table(bundle / 'planned-tests.tsv') == [{'family': row['family'], 'test_id': row['test_id']} for row in reference], 'Complete planned IDs differ')
    roots = table(bundle / 'root-audit.tsv')
    check(len({(r['table_input_row'], r['quantity']) for r in roots}) == len(roots), 'Duplicate root evidence')
    for row in roots:
        check(1 <= int(row['table_input_row']) <= len(actual) and actual[int(row['table_input_row'])-1]['status'] != 'MISSING_COUNTS', 'Root refers to unavailable table')
        check(row['quantity'] in ['conditional-odds-ratio', 'lower-confidence-limit', 'upper-confidence-limit'] and row['inverse_parameterization'] in ['true', 'false'], 'Unknown root protocol evidence')
        check(math.isfinite(float(row['root'])) and math.isfinite(float(row['remaining_bracket_width'])) and float(row['remaining_bracket_width']) >= 0 and int(row['iterations']) > 0, 'Invalid finite root bracket')
    warnings = table(bundle / 'fit-warnings.tsv')
    check({int(row['table_input_row']) for row in warnings} == {i for i, row in enumerate(actual, 1) if row['status'] != 'MISSING_COUNTS'}, 'Fit warning population differs')
    check(all('Required action:' in row['warning'] for row in warnings), 'Missing fit-specific review action')
    return {'rows': len(actual), 'numeric_fields': numeric_fields, 'maximum_probability_or_odds_relative_difference': maximum, 'root_rows': len(roots), 'fit_warning_rows': len(warnings)}


def verify(args):
    root = Path(__file__).resolve().parents[2]
    jar = args.jar.resolve()
    check(jar.is_file(), 'Executable development JAR is missing: ' + str(jar))
    preparer = root / 'examples/fuma-fisher-workflow/prepare_inputs.py'
    spec = importlib.util.spec_from_file_location('fuma_fisher_example', preparer)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    record = {'status': 'running', 'jar_sha256': sha(jar), 'preparer_sha256': sha(preparer), 'verifier_sha256': sha(Path(__file__)), 'attempts': [], 'runs': []}
    try:
        directory = module.prepare(work / 'inputs')
        protected = {name: sha(directory / name) for name in module.MEMBERS}
        sequence = [('counts', '--fisher-options-file')]
        for friendly in ['exact', 'selected', 'pooled-strict', 'pooled']:
            sequence.extend([('upstream-' + friendly, '--known-options-file'), (friendly, '--fisher-options-file')])
        sequence.append(('parent', '--workflow-options-file'))
        group_reference = table(directory / 'reference/groups/reference.tsv')
        mapping = read(directory / 'source-provenance.json')['group_cases']
        for recipe, flag in sequence:
            check(sha(jar) == record['jar_sha256'], 'Development JAR changed')
            command = [args.java, '-Xmx768m', '-jar', str(jar), 'fuma-tools', flag, str(directory / (recipe + '.json'))]
            attempt = {'recipe': recipe, 'command': command}
            record['attempts'].append(attempt)
            try:
                with (work / (recipe + '.stdout')).open('xb') as out, (work / (recipe + '.stderr')).open('xb') as err:
                    process = subprocess.run(command, cwd=root, stdout=out, stderr=err, timeout=180)
            except OSError as failure:
                raise Unavailable('Cannot launch Java: ' + str(failure)) from failure
            attempt['exit_code'] = process.returncode
            errors = (work / (recipe + '.stderr')).read_text(encoding='utf-8', errors='replace')
            if 'UnsupportedClassVersionError' in errors:
                raise Unavailable('Selected Java cannot load this Java 17+ JAR')
            if process.returncode == 3:
                raise Unavailable(recipe + ' returned SKIP 3; inspect its retained WARNINGS.md')
            check(process.returncode == 0, recipe + ' failed with exit ' + str(process.returncode) + '; retained stderr: ' + errors[-1800:])
            for name, digest in protected.items():
                check(sha(directory / name) == digest, 'Protected input/reference changed: ' + name)
            bundle = directory / 'results' / recipe
            if recipe.startswith('upstream-'):
                checks = compare_tests(bundle, directory, recipe.removeprefix('upstream-'))
                attempt['verified_upstream_bundle'] = checks
                continue
            friendly = 'pooled' if recipe == 'parent' else recipe
            if recipe == 'parent':
                parent = read(bundle / 'manifest.json')
                check(parent['status'] == 'completed-with-review-warnings' and parent['exit_code'] == 0 and read(bundle / 'workflow-journal.json')['status'] == parent['status'], 'Typed parent is not final/completed')
                check(parent['stages']['known-associations']['exit_code'] == parent['stages']['fisher-enrichment']['exit_code'] == 0 and parent['stages']['fisher-enrichment']['dependencies'] == ['known-associations'], 'Typed dependency status differs')
                for name, digest in parent['output_sha256'].items():
                    check(sha(bundle / name.replace('\\', '/')) == digest, 'Typed parent output hash differs')
                compare_tests(bundle / 'known-associations', directory, friendly)
                bundle /= 'fisher-enrichment'
            reference = table(directory / 'reference/counts/reference.tsv') if recipe == 'counts' else [row for row in group_reference if row['case'] == mapping[friendly]]
            checks = compare_fisher(bundle, reference)
            definitions = table(directory / 'input' / ('count-families.tsv' if recipe == 'counts' else 'fisher-families.tsv'))
            audits = table(bundle / 'family-audit.tsv')
            check(len(definitions) == len(audits), 'Family definition/audit population differs')
            for definition, audit in zip(definitions, audits):
                for key, column in [('family', 'family'), ('correction', 'correction'), ('encoding', 'encoding'), ('missing', 'missing_policy'), ('empty', 'empty_policy'), ('pseudocount', 'pseudocount'), ('confidence', 'confidence')]:
                    check(definition[key] == audit[column], 'Emitted family scientific policy differs: ' + key)
            if recipe != 'counts':
                evidence = {}
                for filename in ['known-association-sources.tsv', 'group-audit.tsv', 'group-members.tsv', 'group-keys.tsv', 'group-contributors.tsv', 'group-dispositions.tsv', 'shared-keys.tsv', 'shared-contributors.tsv', 'unassigned-targets.tsv']:
                    expected = table(directory / 'reference' / friendly / filename)
                    check(table(bundle / filename) == expected, 'Complete group evidence differs: ' + filename)
                    evidence[filename] = len(expected)
                checks['group_evidence'] = evidence
            else:
                check(table(bundle / 'known-association-sources.tsv') == [], 'Count mode invented association sources')
            checks['integrity'] = verify_bundle(bundle, 'fuma-fisher-enrichment')
            session = read(bundle / 'session.json')
            check(session['table_rows'] == checks['rows'] and session['external_runtime_required'] is False, 'Scientific population/runtime status differs')
            if recipe != 'counts':
                upstream_options = read(directory / ('upstream-' + friendly + '.json'))
                imported = session['imported_known_associations']
                for key in ['matching', 'absence', 'gene-namespace', 'trait-namespace']:
                    check(imported[key] == upstream_options[key], 'Imported classification contract differs: ' + key)
                expected_evidence = sum(value for key, value in evidence.items() if key not in ['known-association-sources.tsv', 'group-audit.tsv'])
                check(session['group_evidence'] == expected_evidence and session['group_rows'] == evidence['group-audit.tsv'], 'Complete group evidence population differs')
            else:
                check(session['imported_known_associations'] == {} and session['group_rows'] == session['group_evidence'] == 0, 'Count mode invented classification/group provenance')
            scientific = {'planned-tests.tsv', 'family-audit.tsv', 'fisher-tables.tsv', 'root-audit.tsv', 'fit-warnings.tsv', 'known-association-sources.tsv'}
            if recipe != 'counts':
                scientific.update(evidence)
            check({path.name for path in bundle.glob('*.tsv') if path.name != 'resource-ledger.tsv'} == scientific, 'Complete scientific file population differs')
            check(session['scientific_result_rows'] == sum(len(table(path)) for path in bundle.glob('*.tsv') if path.name != 'resource-ledger.tsv'), 'Complete scientific row population differs')
            warnings = (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
            for text in ['R DEFAULT ROOT PROTOCOL', 'FLOATING-POINT REFERENCE BOUNDARY', 'EMPTY ORIGINAL', 'complete', 'Required action:']:
                check(text in warnings, 'Missing scientific review warning: ' + text)
            record['runs'].append({'recipe': recipe, 'exit_code': 0, 'checks': checks, 'bundle': str(bundle)})
        record.update(status='passed-six-public-synthetic-Fisher-workflows',
                      numeric_rule='Nonzero finite probability/odds/CI relative error <=2e-14;exact zeros/infinities/NA/selection/counts/text. Finite-log extension: max(16 ULPs(reference log),2e-14) in log units;probability-mode adjusted log uses induced relative probability <=2e-14;exponentiated log adjustment uses propagated log difference plus four output ULPs. No absolute probability tolerance.',
                      scope='Exact packaged Java-only complete Fisher correction families,all original/tested counts and root/warning evidence,plus independent raw-input classification/group/contributor/exclusion/overlap references. Six complete Fisher populations,including typed parent,and four separately verified upstream native reviews. No historical paper defaults,whole FUMA or release equivalence.')
    except Unavailable as failure:
        record.update(status='skipped-dependency-unavailable', cause=str(failure))
        raise
    except BaseException as failure:
        record.update(status='failed-retained', cause=str(failure))
        raise
    finally:
        (work / 'verification.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': record['status'], 'Fisher_runs': len(record['runs']), 'commands': len(record['attempts']), 'jar_sha256': record['jar_sha256']}))
    print('FISHER EXAMPLES REVIEW REQUIRED. Required action: read each result WARNINGS.md,complete family/count/group/root evidence and verification.json. These are synthetic examples;declare and justify the original scientific policies for your own study.', file=sys.stderr)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    try:
        verify(args)
    except Unavailable as failure:
        parser.exit(3, f'FISHER VERIFICATION / DEPENDENT ANALYSIS SKIPPED: {failure}\nRequired action: provide Java 17+ on a permitted supported host,using --java if needed. If installation is forbidden,retain the analysis as skipped. No R,MAGMA or upstream FUMA installation is required by these supplied-result Java recipes. Inspect retained logs and verification.json and rerun to a NEW directory.\n')
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as failure:
        parser.exit(2, f'FISHER VERIFICATION FAILED: {failure}\nRequired action: inspect retained logs,verification.json and result WARNINGS.md;restore the pinned inputs or correct the named input/artifact/precision cause and rerun to a NEW directory. For damaged Unicode argv on Windows Java21,use an entirely ASCII command-line carrier path and declare Unicode data paths inside UTF-8 JSON. No partial result is admitted as completed.\n')
