#!/usr/bin/env python3
"""Collect original CI reports on the frozen synthetic example, without installing."""
from pathlib import Path
import argparse
import csv
import datetime as dt
import hashlib
import json
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import install_plink


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--work', type=Path, required=True)
    args = parser.parse_args()
    home, work = args.home.resolve(), args.work.resolve()
    if work.is_relative_to(home) or home.is_relative_to(work):
        parser.error('--work must be outside the sealed runtime home and its ancestors')
    work.mkdir(parents=True, exist_ok=False)
    record = dict(status='running', producer_sha256=sha(__file__), steps=[],
                  scope='Actual original synthetic CI reports only; independent mathematics and Java import are separate gates.')
    shutil.copy2(__file__, work / 'producer-snapshot.py')
    try:
        if sys.platform != 'win32' or not install_plink.available(home):
            record.update(status='skipped-dependency-unavailable')
            (work / 'WARNINGS.md').write_text(
                '# ORIGINAL CI COLLECTION SKIPPED\n\nRequired action: where permitted run '
                'python tools/literature/install_plink.py --home NEW_PRIVATE_HOME --allow-install; '
                'add --allow-download only for permitted missing downloads. Supply the sealed Windows '
                'x86_64 profile using --home and select a new --work directory. If unavailable or '
                'installation is forbidden, skip this collection; no scientific result was produced.\n')
            return 3
        record['runtime_identity'] = install_plink.identity()
        record['version_probes'] = install_plink.probe(home, work)
        example = ROOT / 'examples/plink-tools'
        inputs = work / 'inputs'
        inputs.mkdir()
        for extension in ['bed', 'bim', 'fam']:
            shutil.copy2(example / ('known.' + extension), inputs / ('known.' + extension))
        with (example / 'linear-cohort.tsv').open() as stream:
            quantitative = list(csv.DictReader(stream, delimiter='\t'))
        with (example / 'logistic-cohort.tsv').open() as stream:
            binary = list(csv.DictReader(stream, delimiter='\t'))
        genotype = json.loads(__import__('gzip').decompress((ROOT / 'src/test/resources/org/jlinalg/genetics/plink-associations/input-design.json.gz').read_bytes()))['a1_genotypes']
        with (inputs / 'phenotypes.tsv').open('w') as pheno, (inputs / 'covariates.tsv').open('w') as covar:
            pheno.write('FID\tIID\tQT\tCC\tSEP\n')
            covar.write('FID\tIID\tAGE\tSEX\n')
            for i, (q, b) in enumerate(zip(quantitative, binary, strict=True)):
                assert (q['fid'], q['iid']) == (b['fid'], b['iid'])
                prefix = q['fid'] + '\t' + q['iid']
                pheno.write(prefix + '\t' + q['phenotype'] + '\t' + str(int(b['phenotype']) + 1) + '\t' + str(2 if genotype[0][i] > 0 else 1) + '\n')
                covar.write(prefix + '\t' + q['AGE'] + '\t' + q['SEX'] + '\n')
        record['input_sha256'] = {p.name: sha(p) for p in inputs.iterdir()}
        specs = []
        for engine in ['plink1', 'plink2']:
            for model in ['linear', 'logistic']:
                for level in ['0.8', '0.95']:
                    specs.append((engine, model, level, 'QT' if model == 'linear' else 'CC'))
            for level in ['0.955', '0.9999999', '0.123456789123', '0.29', '0.58', '0.01', '0.999', '0.1234565']:
                specs.append((engine, 'linear', level, 'QT'))
        for model in ['logistic', 'firth-fallback', 'firth']:
            for level in ['0.95', '0.955']:
                specs.append(('plink2', model, level, 'SEP'))
        environment = os.environ.copy()
        environment['PATH'] = str(Path(os.environ.get('SystemRoot', 'C:/Windows')) / 'System32')
        environment['OMP_NUM_THREADS'] = '1'
        environment['LC_ALL'] = 'C'
        for engine, model, level, phenotype in specs:
            assert install_plink.available(home)
            name = engine + '-' + model + '-' + phenotype + '-' + level
            out = work / name
            out.mkdir()
            command = [str(home / install_plink.LOCK['engines'][engine]['executable']), '--bfile', str(inputs / 'known'),
                       '--threads', '1', '--memory', '128' if engine == 'plink1' else '640',
                       '--pheno', str(inputs / 'phenotypes.tsv'), '--pheno-name', phenotype,
                       '--covar', str(inputs / 'covariates.tsv'), '--covar-name', 'AGE,SEX', '--ci', level]
            if engine == 'plink1':
                command += ['--keep-allele-order', '--linear' if model == 'linear' else '--logistic', 'hide-covar']
                suffix = 'assoc.' + model
            else:
                command += ['--glm', 'hide-covar']
                if model != 'linear':
                    command += [{'logistic': 'no-firth', 'firth-fallback': 'firth-fallback', 'firth': 'firth'}[model]]
                suffix = phenotype + '.glm.' + ('logistic.hybrid' if model == 'firth-fallback' else model)
            command += ['--out', str(out / 'result')]
            result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True, timeout=120)
            (out / 'stdout').write_bytes(result.stdout)
            (out / 'stderr').write_bytes(result.stderr)
            assert result.returncode == 0, (name, result.stderr)
            assert install_plink.available(home)
            report = out / ('result.' + suffix)
            lines = report.read_text().splitlines()
            names = lines[0].lstrip('#').split()
            rows = [dict(zip(names, line.split(), strict=True)) for line in lines[1:] if line.strip()]
            assert len(rows) == 96
            record['steps'].append(dict(name=name, engine=engine, model=model, level=level, phenotype=phenotype,
                                        command=command, exit_code=result.returncode, report=str(report), report_sha256=sha(report),
                                        output_sha256={p.name: sha(p) for p in out.iterdir()}, header=names, rows=len(rows),
                                        upstream_errors=sum(r.get('ERRCODE', '.') != '.' for r in rows)))
        for name, digest in record['input_sha256'].items():
            assert sha(inputs / name) == digest
        assert sha(__file__) == record['producer_sha256']
        assert len(record['steps']) == 30
        record.update(status='passed-original-CI-process-contracts;independent-math-and-Java-gates-pending',
                      reports=30, original_rows=2880, recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat())
        return 0
    except BaseException as failure:
        record.update(status='failed-retained', error=repr(failure))
        (work / 'WARNINGS.md').write_text('# ORIGINAL CI COLLECTION FAILED\n\n' + repr(failure) +
                                        '\n\nRequired action: inspect verification.json and retained stdout/stderr; correct the cause and rerun to a new --work directory.\n')
        raise
    finally:
        (work / 'verification.json').write_text(json.dumps(record, indent=2) + '\n')
        print(json.dumps({k: record[k] for k in ['status', 'reports', 'original_rows'] if k in record}), flush=True)


if __name__ == '__main__':
    sys.exit(main())
