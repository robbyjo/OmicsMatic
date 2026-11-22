#!/usr/bin/env python3
"""Independently check original ordinary-GLM SE/CI and label retained Firth consistency."""
from pathlib import Path
import argparse
import datetime as dt
import gzip
import hashlib
import json
import math
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def quantum(value, digits):
    return 10 ** (math.floor(math.log10(abs(value))) - digits + 1) if value else 0


def displayed(literal, expected, digits, label, quantile_allowance=0):
    observed = float(literal)
    assert math.isfinite(observed) and math.isfinite(expected), (label, literal, expected)
    assert abs(observed - expected) <= .50001 * quantum(observed, digits) + quantile_allowance + 64 * math.ulp(expected), (label, observed, expected, quantile_allowance)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--work', type=Path, required=True)
    args = parser.parse_args()
    source, work = args.source.resolve(), args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    shutil.copy2(__file__, work / 'producer-snapshot.py')
    record = dict(status='running', producer_sha256=sha(__file__), reports=0, ordinary_rows=0,
                  independently_compared_SE_and_limits=0, separated_consistency_rows=0,
                  unreportable_rows=0, finite_provisional_CI_rows=0,
                  scope='Independent ordinary OLS and ordinary logistic MLE with normal-Wald limits; '
                        'separated/Firth reports receive only printed-column algebra consistency checks, '
                        'not an independent Firth estimator validation. No project importer/CLI claim.')
    record['original_inverse_normal_relative_error_bound'] = 1.15e-9
    record['inverse_normal_sources'] = [
        'https://raw.githubusercontent.com/chrchang/plink-ng/master/1.9/plink_stats.c',
        'https://raw.githubusercontent.com/chrchang/plink-ng/master/2.0/include/plink2_stats.cc']
    try:
        try:
            import numpy as np
            from scipy.stats import norm
        except ImportError as failure:
            record.update(status='skipped-test-dependency', error=repr(failure))
            (work / 'WARNINGS.md').write_text('# CI MATHEMATICAL CHECK SKIPPED\n\n' + str(failure) +
                                            '\n\nRequired action: where permitted install NumPy and SciPy into a private Python environment and rerun with a new --work. Otherwise skip this validation; no independent mathematical result was produced.\n')
            return 3
        proof = json.loads((source / 'verification.json').read_text())
        assert proof['status'].startswith('passed-original-CI-process-contracts')
        record['original_proof_sha256'] = sha(source / 'verification.json')
        design_path = ROOT / 'src/test/resources/org/jlinalg/genetics/plink-associations/input-design.json.gz'
        design = json.loads(gzip.decompress(design_path.read_bytes()))
        record['design_sha256'] = sha(design_path)
        genotype = np.asarray(design['a1_genotypes'], dtype=float)
        y = np.asarray(design['quantitative'])
        binary = np.asarray(design['binary12']) - 1
        n = len(y)
        pairs = {fields[1]: (fields[4], fields[5]) for fields in
                 [line.split() for line in (source / 'inputs/known.bim').read_text().splitlines()]}
        expected = []
        for g in genotype:
            x = np.column_stack([np.ones(n), g, design['age'], design['sex']])
            coefficient = np.linalg.lstsq(x, y, rcond=None)[0]
            residual = y - x @ coefficient
            ols_se = math.sqrt(float(residual @ residual) / (n - 4) * np.linalg.inv(x.T @ x)[1, 1])
            b = np.zeros(4)
            for iteration in range(100):
                fitted = 1 / (1 + np.exp(-(x @ b)))
                information = x.T @ ((fitted * (1 - fitted))[:, None] * x)
                delta = np.linalg.solve(information, x.T @ (binary - fitted))
                b += delta
                if np.max(np.abs(delta)) < 1e-12:
                    break
            else:
                raise AssertionError('Independent ordinary logistic did not converge')
            fitted = 1 / (1 + np.exp(-(x @ b)))
            information = x.T @ ((fitted * (1 - fitted))[:, None] * x)
            logistic_se = math.sqrt(np.linalg.inv(information)[1, 1])
            assert np.max(np.abs(x.T @ (binary - fitted))) < 1e-9
            expected.append(dict(linear=(float(coefficient[1]), ols_se), logistic=(float(b[1]), logistic_se)))
        for step in proof['steps']:
            path = Path(step['report'])
            assert sha(path) == step['report_sha256'] and step['exit_code'] == 0
            lines = path.read_text().splitlines()
            columns = lines[0].lstrip('#').split()
            rows = [dict(zip(columns, line.split(), strict=True)) for line in lines[1:] if line.strip()]
            lowers = [k for k in columns if re.fullmatch(r'L[0-9]+(?:\.[0-9]+)?', k)]
            uppers = [k for k in columns if re.fullmatch(r'U[0-9]+(?:\.[0-9]+)?', k)]
            assert len(lowers) == len(uppers) == 1
            lower, upper = lowers[0], uppers[0]
            assert lower[1:] == upper[1:]
            level = float(step['level'])
            # Match each original invocation's double-precision probability expression;
            # the inverse CDF and model estimates are computed independently by SciPy/NumPy.
            probability = 1 - (1 - level) / 2 if step['engine'] == 'plink1' else (1 + level) * .5
            critical = float(norm.ppf(probability))
            for row in rows:
                if row.get('ERRCODE', '.') != '.':
                    record['unreportable_rows'] += 1
                    if all(row[key] not in ['NA', 'nan', 'NaN', '.'] and math.isfinite(float(row[key])) for key in [lower, upper]):
                        record['finite_provisional_CI_rows'] += 1
                    continue
                ident = row.get('SNP', row.get('ID'))
                ordinal = int(ident[2:]) - 10000
                digits = 4 if step['engine'] == 'plink1' else 6
                se_column = 'LOG(OR)_SE' if step['engine'] == 'plink2' and step['model'] != 'linear' else 'SE'
                if step['phenotype'] != 'SEP':
                    coefficient, se = expected[ordinal][step['model']]
                    if row['A1'] == pairs[ident][1]:
                        coefficient = -coefficient
                    else:
                        assert row['A1'] == pairs[ident][0]
                    lo, hi = coefficient - critical * se, coefficient + critical * se
                    # Original ltqnorm/QuantileToZscore use Acklam's rational
                    # approximation with documented relative error <1.15e-9.
                    # Propagate that bound; SE/model checks retain strict print rounding.
                    radius_allowance = abs(critical) * se * 1.15e-9
                    if step['model'] != 'linear':
                        lo, hi = math.exp(lo), math.exp(hi)
                        lo_allowance, hi_allowance = lo * math.expm1(radius_allowance), hi * math.expm1(radius_allowance)
                    else:
                        lo_allowance = hi_allowance = radius_allowance
                    displayed(row[se_column], se, digits, (step['name'], ident, 'SE'))
                    displayed(row[lower], lo, digits, (step['name'], ident, lower), lo_allowance)
                    displayed(row[upper], hi, digits, (step['name'], ident, upper), hi_allowance)
                    record['ordinary_rows'] += 1
                    record['independently_compared_SE_and_limits'] += 3
                else:
                    odds, se = float(row['OR']), float(row[se_column])
                    base_error = .50001 * quantum(odds, 6) / odds + critical * .50001 * quantum(se, 6) + abs(critical) * se * 1.15e-9
                    for key, sign in [(lower, -1), (upper, 1)]:
                        reported = float(row[key])
                        target = math.log(odds) + sign * critical * se
                        error = base_error + .50001 * quantum(reported, 6) / reported
                        assert abs(math.log(reported) - target) <= error * 1.0001 + 64 * math.ulp(target), (step['name'], ident, key)
                    record['separated_consistency_rows'] += 1
            record['reports'] += 1
        assert record['reports'] == 30 and record['ordinary_rows'] == 2304
        assert record['independently_compared_SE_and_limits'] == 6912
        assert record['separated_consistency_rows'] == 570 and record['unreportable_rows'] == 6 and record['finite_provisional_CI_rows'] == 4
        assert sha(__file__) == record['producer_sha256']
        record.update(status='passed', numpy=np.__version__, scipy=__import__('scipy').__version__,
                      recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat())
        return 0
    except BaseException as failure:
        record.update(status='failed-retained', error=repr(failure))
        (work / 'WARNINGS.md').write_text('# CI MATHEMATICAL CHECK FAILED\n\n' + repr(failure) +
                                        '\n\nRequired action: inspect the retained original reports and model/precision contract; correct the cause, then rerun with a new --work directory. A failed check is not an independent validation.\n')
        raise
    finally:
        (work / 'verification.json').write_text(json.dumps(record, indent=2) + '\n')
        print(json.dumps({k: record[k] for k in ['status', 'reports', 'ordinary_rows', 'independently_compared_SE_and_limits', 'separated_consistency_rows', 'unreportable_rows']}), flush=True)


if __name__ == '__main__':
    sys.exit(main())
