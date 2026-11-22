"""Audit complete synthetic native SNP annotation outputs against pinned original R tables."""
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
    """Java is unavailable; dependent analyses were skipped."""


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
    if columns is not None:
        selected = [left[0].index(c) for c in columns]
        left = [[row[i] for i in selected] for row in left]
        left[0] = right[0]
    check(bool(left) and bool(right) and left[0] == right[0], 'Header differs: ' + str(actual))
    check(len(left) == len(right), 'Complete row population differs: ' + str(actual))
    maximum = 0.0
    for line, (a, b) in enumerate(zip(left[1:], right[1:]), 2):
        check(len(a) == len(b), f'Column population differs at {actual}:{line}')
        for column, (x, y) in enumerate(zip(a, b), 1):
            if x == y:
                continue
            try:
                fx, fy = float(x), float(y)
            except ValueError:
                raise ValueError(f'Field differs at {actual}:{line}:{column}: {x!r} vs {y!r}') from None
            if fx == 0 or fy == 0:
                check(fx == fy, f'Rounded zero differs at {actual}:{line}:{column}')
            elif math.isfinite(fx) and math.isfinite(fy):
                check(math.isclose(fx, fy, rel_tol=2e-12, abs_tol=0), f'Numeric field differs at {actual}:{line}:{column}')
                maximum = max(maximum, abs(fx-fy) / max(abs(fx), abs(fy)))
            else:
                check(fx == fy or math.isnan(fx) and math.isnan(fy), f'Undefined/infinite field differs at {actual}:{line}:{column}')
    return {'rows': len(left)-1, 'fields': sum(len(row) for row in left[1:]), 'maximum_relative_difference': maximum}


def verify(args):
    jar = args.jar.resolve()
    check(jar.is_file(), 'Runnable development JAR is missing: ' + str(jar))
    root = Path(__file__).resolve().parents[2]
    preparer = root / 'examples/fuma-variant-annotation-workflow/prepare_inputs.py'
    spec = importlib.util.spec_from_file_location('fuma_variant_annotation_example', preparer)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    record = {'status': 'running', 'jar_sha256': sha(jar), 'preparer_sha256': sha(preparer), 'runs': []}
    try:
        directory = module.prepare(work / 'inputs')
        for recipe in ['minimal', 'bed', 'parent']:
            option = 'workflow-options-file' if recipe == 'parent' else 'annotation-options-file'
            command = [args.java, '-Xmx1024m', '-jar', str(jar), 'fuma-tools', '--' + option,
                       str(directory / (recipe + '.json'))]
            try:
                with (work / (recipe + '.stdout')).open('xb') as stdout, (work / (recipe + '.stderr')).open('xb') as stderr:
                    result = subprocess.run(command, cwd=root, stdout=stdout, stderr=stderr, timeout=180)
            except OSError as failure:
                raise Unavailable('Cannot launch Java: ' + str(failure)) from failure
            errors = (work / (recipe + '.stderr')).read_text(encoding='utf-8', errors='replace')
            if result.returncode == 3 or 'UnsupportedClassVersionError' in errors:
                raise Unavailable(errors)
            check(result.returncode == 0, f'{recipe} exit {result.returncode}; inspect {work / (recipe + ".stderr")} and output WARNINGS.md')
            bundle = directory / (recipe + '-result')
            if recipe == 'parent':
                parent = json.loads((bundle / 'manifest.json').read_text(encoding='utf-8'))
                check(parent['status'] == 'completed-with-review-warnings' and parent['stages']['variant-annotation']['exit_code'] == 0,
                      'Parent did not complete the selected annotation stage')
                bundle = bundle / 'variant-annotation'
            status = json.loads((bundle / 'analysis-status.json').read_text(encoding='utf-8'))
            session = json.loads((bundle / 'session.json').read_text(encoding='utf-8'))
            check(status['exit_code'] == 0 and session['status'] == 'completed-with-review-warnings', 'Child did not admit a completed analysis')
            check(session['external_runtime_required'] is False and session['candidate_rows'] == 10 and session['gene_resource_rows'] == 7,
                  'Native input population/runtime scope differs')
            for path, digest in session['input_sha256'].items():
                check(sha(Path(path)) == digest, 'Input changed: ' + path)
            for row in table(bundle / 'resource-ledger.tsv')[1:]:
                check(sha(Path(row[1])) == row[3] == sha(bundle / row[2]), 'Source snapshot/hash differs: ' + row[0])
            checks = {}
            for name in ['snps.tsv', 'annov.tsv', 'independent.tsv', 'annotation-statistics.tsv']:
                checks[name] = compare(bundle / name, directory / 'reference' / name)
            checks['nearest'] = compare(bundle / 'nearest-gene-evidence.tsv', directory / 'reference/nearest.tsv',
                                        ['variant_row', 'gene_row', 'distance'])
            if recipe == 'minimal':
                check(not (bundle / 'annot.bed').exists() and session['selected_BED_tracks'] == 0, 'Unrequested BED output appeared')
                checks['annotations.tsv'] = compare(bundle / 'annotations.tsv', directory / 'inputs/annotations.tsv')
            else:
                checks['annot.bed'] = compare(bundle / 'annot.bed', directory / 'reference/annot.bed')
                checks['annotations.tsv'] = compare(bundle / 'annotations.tsv', directory / 'reference/annotations-with-bed.tsv')
                check(session['selected_BED_tracks'] == 2, 'Selected track population differs')
            scientific_bytes = sum(path.stat().st_size for path in bundle.rglob('*') if path.is_file() and
                                   ('source-inputs' in path.relative_to(bundle).parts or path.parent == bundle and path.suffix in {'.tsv', '.bed'}))
            check(scientific_bytes == session['scientific_files_and_snapshot_bytes'], 'Retained scientific byte population differs')
            warning = (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
            for text in ['Required action', 'UNDEFINED/INFINITE ENRICHMENT', 'TYPED RESOURCE VIEW', 'NUMERICAL SOURCE FINDING']:
                check(text in warning, 'Missing numerical/source warning: ' + text)
            record['runs'].append({'recipe': recipe, 'exit_code': 0, 'complete_tables': checks,
                                   'scientific_bytes': scientific_bytes, 'bundle': str(bundle)})
        record.update(status='passed-three-public-synthetic-SNP-annotation-workflows',
                      scope='Every full synthetic source result field/row and exact original nearest query/subject pairs, raw input snapshots/hashes, explicit plain/gzip BED and typed parent. Java-only supplied-resource postprocessing. No ANNOVAR prediction, historical biological resource, full FUMA or release equivalence.')
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
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    try:
        verify(args)
    except Unavailable as failure:
        print(f'SNP ANNOTATION VERIFICATION / ANALYSIS SKIPPED: {failure}\nRequired action: supply Java 17 or newer on a permitted supported host, using --java if needed. If installation is unavailable or forbidden, keep the analysis skipped. Rerun to a NEW directory. No ANNOVAR or R installation is required for these supplied synthetic inputs.', file=sys.stderr)
        sys.exit(3)
    except (OSError, ValueError, subprocess.SubprocessError) as failure:
        print(f'SNP ANNOTATION VERIFICATION FAILED: {failure}\nRequired action: inspect retained logs/verification.json, build the current executable development JAR if needed, correct the named input/output cause and rerun to a NEW directory.', file=sys.stderr)
        sys.exit(2)
