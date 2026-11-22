#!/usr/bin/env python3
"""Validate packaged reported-CI API/GLM against the frozen original reports."""
from pathlib import Path
import argparse
import csv
import datetime as dt
import gzip
import hashlib
import json
import math
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import install_plink


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def table(path):
    with Path(path).open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))


def raw_table(path):
    lines = Path(path).read_text(encoding='utf-8').splitlines()
    names = lines[0].lstrip('#').split()
    return [dict(zip(names, line.split(), strict=True)) for line in lines[1:] if line.strip()]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--java', action='append', required=True)
    parser.add_argument('--javac', required=True)
    args = parser.parse_args()
    jar, home, work = args.jar.resolve(), args.home.resolve(), args.work.resolve()
    if work.is_relative_to(home) or home.is_relative_to(work):
        parser.error('--work must be outside the original runtime home')
    if not str(work).isascii() or not str(jar).isascii() or not str(ROOT).isascii():
        parser.error('Use ASCII harness, JAR and repository paths; Unicode analysis paths are supplied through UTF-8 recipes')
    work.mkdir(parents=True, exist_ok=False)
    record = dict(status='running', jar_sha256=sha(jar), producer_sha256=sha(__file__), steps=[],
                  api_rows=0, typed_workflows=0, typed_rows=0, negative_controls=0,
                  suppressed_api_intervals=0, suppressed_typed_intervals=0,
                  scope='Actual packaged native reader and original typed GLM on Java runtimes. '
                        'Reported limits and SE compared exactly to original text; beta from OR uses '
                        'the established4ULP arithmetic tolerance. No native Firth estimator or full-project gate claim.')
    shutil.copy2(__file__, work / 'producer-snapshot.py')

    def save():
        (work / 'verification.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')

    def run(command, label, expected=0):
        result = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True, timeout=180)
        stdout, stderr = work / (label + '.stdout'), work / (label + '.stderr')
        stdout.write_bytes(result.stdout)
        stderr.write_bytes(result.stderr)
        record['steps'].append(dict(command=list(map(str, command)), exit_code=result.returncode,
                                    stdout_sha256=sha(stdout), stderr_sha256=sha(stderr)))
        save()
        assert result.returncode == expected, (label, result.returncode, result.stderr.decode(errors='replace'))
        return stdout, result.stderr

    try:
        if sys.platform != 'win32' or not install_plink.available(home):
            record['status'] = 'skipped-dependency-unavailable'
            (work / 'WARNINGS.md').write_text('# ORIGINAL CI VALIDATION SKIPPED\n\nRequired action: where permitted '
                                            'run python tools/literature/install_plink.py --home NEW_PRIVATE_HOME '
                                            '--allow-install; add --allow-download only for permitted missing '
                                            'downloads. Supply the sealed Windows x86_64 profile and rerun to a new '
                                            '--work directory. Otherwise skip this validation; no original workflow was run.\n')
            return 3
        source = ROOT / 'src/test/resources/org/jlinalg/genetics/plink-confidence'
        provenance = json.loads((source / 'provenance.json').read_text())
        for name, digest in provenance['files_sha256'].items():
            assert sha(source / name) == digest
        record['reference_provenance_sha256'] = sha(source / 'provenance.json')
        record['input_sha256'] = {str(p.relative_to(ROOT)): sha(p) for p in
                                  [*source.iterdir(), *(ROOT / 'examples/plink-tools').iterdir(),
                                   ROOT / 'examples/plink-association-import/ConfidenceImportExample.java'] if p.is_file()}
        for name, digest in record['input_sha256'].items():
            target = work / 'source-snapshot' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / name, target)
            assert sha(target) == digest
        references = work / 'references'
        references.mkdir()
        cases = table(source / 'cases.tsv')
        assert len(cases) == 30
        for case in cases:
            assert re.fullmatch(r'[A-Za-z0-9_.-]+', case['name'])
            (references / case['name'].removesuffix('.gz')).write_bytes(gzip.decompress((source / case['name']).read_bytes()))
        example = ROOT / 'examples/plink-tools'
        with (example / 'linear-cohort.tsv').open() as stream:
            rows = list(csv.DictReader(stream, delimiter='\t'))
        design = json.loads(gzip.decompress((ROOT / 'src/test/resources/org/jlinalg/genetics/plink-associations/input-design.json.gz').read_bytes()))
        separated = work / 'separated.tsv'
        with separated.open('w') as stream:
            stream.write('fid\tiid\tphenotype\tAGE\tSEX\n')
            for i, row in enumerate(rows):
                stream.write(row['fid'] + '\t' + row['iid'] + '\t' + str(2 if design['a1_genotypes'][0][i] > 0 else 1) + '\t' + row['AGE'] + '\t' + row['SEX'] + '\n')
        classes = work / 'classes'
        classes.mkdir()
        run([args.javac, '--release', '17', '-Xlint:all', '-Werror', '-cp', jar, '-d', classes,
             ROOT / 'examples/plink-association-import/ConfidenceImportExample.java'], 'compile-public-API-example')
        sealed = work / '运行环境'
        shutil.copytree(home, sealed)

        def compare(values, original, case, typed):
            expected = {r.get('SNP', r.get('ID')): r for r in original}
            assert len(values) == len(expected) == 96
            assert {r['variant'] for r in values} == set(expected)
            for row in values:
                raw = expected[row['variant']]
                assert row['ea'] == raw['A1']
                assert float(row['confidence_level_requested']) == float(case['level'])
                assert row['ci_scale'] == ('BETA' if case['model'] == 'LINEAR' else 'ODDS_RATIO')
                effect_status = row['status' if typed else 'effect_status']
                if raw.get('ERRCODE', '.') != '.':
                    assert effect_status == 'UNREPORTABLE_UPSTREAM_ERROR'
                    assert row['ci_status'] == 'UNREPORTABLE_ESTIMATE'
                    assert row['ci_lower'] == row['ci_upper'] == row['beta'] == row['standard_error'] == ''
                    record['suppressed_typed_intervals' if typed else 'suppressed_api_intervals'] += 1
                else:
                    assert effect_status == 'VALID' and row['ci_status'] == 'REPORTED'
                    lower = next(k for k in raw if re.fullmatch(r'L[0-9]+(?:\.[0-9]+)?', k))
                    upper = 'U' + lower[1:]
                    assert float(row['ci_lower']) == float(raw[lower]) and float(row['ci_upper']) == float(raw[upper])
                    se = raw['LOG(OR)_SE' if case['engine'] == 'PLINK2' and case['model'] != 'LINEAR' else 'SE']
                    assert float(row['standard_error']) == float(se)
                    if case['model'] == 'LINEAR':
                        assert float(row['beta']) == float(raw['BETA'])
                    else:
                        expected_log = math.log(float(raw['OR']))
                        assert abs(float(row['beta']) - expected_log) <= 4 * math.ulp(expected_log)

        for runtime, java in enumerate(args.java):
            run([java, '-version'], str(runtime) + '-java-version')
            for index, case in enumerate(cases):
                label = str(runtime) + '-' + str(index)
                report = references / case['name'].removesuffix('.gz')
                original = raw_table(report)
                api, _ = run([java, '-cp', str(classes) + ';' + str(jar), 'ConfidenceImportExample', report,
                              example / 'known.bim', case['engine'], case['model'], '128', case['level']], label + '-API')
                compare(table(api), original, case, False)
                record['api_rows'] += 96
                model = case['model'].lower().replace('_', '-')
                cohort = separated if case['phenotype'] == 'SEP' else example / ('linear-cohort.tsv' if model == 'linear' else 'logistic-cohort.tsv')
                out = work / ('结果-' + label)
                options = dict(task='glm', engine=case['engine'].lower(), home=str(sealed), bfile=str(example / 'known'),
                               cohort=str(cohort), model=model, covariates='AGE,SEX', missing='error',
                               **{'genome-build': 'unspecified', 'confidence-level': case['level'], 'timeout-seconds': '60'}, out=str(out))
                if model != 'linear':
                    options['binary-coding'] = '1/2' if case['phenotype'] == 'SEP' else '0/1'
                recipe = work / (label + '.json')
                recipe.write_text(json.dumps(options, ensure_ascii=False, indent=2), encoding='utf-8')
                _, stderr = run([java, '-jar', jar, 'plink-tools', '--options-file', recipe], label + '-GLM')
                assert b'WARNINGS' in stderr and (out / 'WARNINGS.md').is_file()
                compare(table(out / 'results.tsv'), original, case, True)
                assert (out / 'options-input.json').read_bytes() == recipe.read_bytes()
                assert not (out / 'partial').exists()
                record['typed_workflows'] += 1
                record['typed_rows'] += 96
                save()
            for index, level in enumerate(['0', '0.009', '1', 'NaN', 'Infinity', 'bad']):
                out = work / (str(runtime) + '-invalid-' + str(index))
                _, stderr = run([java, '-jar', jar, 'plink-tools', '--task', 'glm', '--engine', 'plink2',
                                 '--home', sealed, '--bfile', example / 'known', '--cohort', example / 'linear-cohort.tsv',
                                 '--model', 'linear', '--covariates', 'AGE,SEX', '--missing', 'error', '--genome-build', 'unspecified',
                                 '--confidence-level', level, '--out', out], str(runtime) + '-invalid-' + str(index), 2)
                assert not out.exists() and b'NOT STARTED' in stderr
                record['negative_controls'] += 1
            out = work / (str(runtime) + '-missing-home')
            _, stderr = run([java, '-jar', jar, 'plink-tools', '--task', 'glm', '--engine', 'plink2',
                             '--home', work / 'absent', '--bfile', example / 'known', '--cohort', example / 'linear-cohort.tsv',
                             '--model', 'linear', '--covariates', 'AGE,SEX', '--missing', 'error', '--genome-build', 'unspecified',
                             '--confidence-level', '.95', '--out', out], str(runtime) + '-missing-home', 3)
            assert not (out / 'results.tsv').exists() and b'SKIPPED' in stderr
            assert '--allow-install' in (out / 'WARNINGS.md').read_text()
            record['negative_controls'] += 1
        for name, digest in record['input_sha256'].items():
            assert sha(ROOT / name) == digest
        assert sha(jar) == record['jar_sha256'] and sha(__file__) == record['producer_sha256']
        assert record['api_rows'] == record['typed_rows'] == 2880 * len(args.java)
        assert record['typed_workflows'] == 30 * len(args.java) and record['negative_controls'] == 7 * len(args.java)
        assert record['suppressed_api_intervals'] == record['suppressed_typed_intervals'] == 6 * len(args.java)
        record.update(status='passed', actual_JVMs=len(args.java), completed_utc=dt.datetime.now(dt.timezone.utc).isoformat())
        return 0
    except BaseException as failure:
        record.update(status='failed-retained', error=repr(failure))
        (work / 'WARNINGS.md').write_text('# PACKAGED CI VALIDATION FAILED\n\n' + repr(failure) +
                                        '\n\nRequired action: inspect verification.json and retained API/GLM stdout, stderr and analysis warnings. Correct the cause and rerun to a new --work directory; failed validation does not establish a working implementation.\n')
        raise
    finally:
        save()
        print(json.dumps({k: record[k] for k in ['status', 'api_rows', 'typed_workflows', 'typed_rows', 'negative_controls']}), flush=True)


if __name__ == '__main__':
    sys.exit(main())
