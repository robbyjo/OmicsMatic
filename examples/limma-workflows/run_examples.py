"""Run the six prepared-data examples with an explicitly supplied original R profile."""
from pathlib import Path
import argparse
import json
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--java', default='java')
    parser.add_argument('--rscript', required=True, type=Path)
    parser.add_argument('--r-libraries', required=True, type=Path,
                        help='UTF-8 file, one exact original-package library directory per line')
    parser.add_argument('--out', required=True, type=Path, help='new output directory')
    parser.add_argument('--case', choices=['LM', 'VOOM', 'EDGER_VOOM_LMFIT', 'BATCH', 'NORMALIZE', 'CAMERAPR'])
    args = parser.parse_args()
    jar = args.jar.resolve()
    if not jar.is_file() or not args.r_libraries.is_file():
        print('SKIP3: DEPENDENT ANALYSIS SKIPPED. Supply normal JAR ' + str(jar) + ' and exact R library-list file ' + str(args.r_libraries) + '. No analysis executed.')
        return 3
    if shutil.which(args.java) is None:
        print('SKIP3: DEPENDENT ANALYSIS SKIPPED. Restore the permitted Java runtime or supply --java with its executable path: ' + args.java + '. No analysis executed.')
        return 3
    libraries = args.r_libraries.read_text(encoding='utf-8').splitlines()
    if not libraries or any(not value for value in libraries):
        raise ValueError('Library list must contain explicit nonempty directory paths')
    output = args.out.resolve()
    output.mkdir(parents=True, exist_ok=False)
    root = Path(__file__).resolve().parent
    names = [args.case] if args.case else ['LM', 'VOOM', 'EDGER_VOOM_LMFIT', 'BATCH', 'NORMALIZE', 'CAMERAPR']
    rows = {}
    for name in names:
        source = root / name
        request = json.loads((source / 'request-template.json').read_bytes())
        folder = output / name
        folder.mkdir()
        request.update(out=str(folder / 'analysis'), rscript=str(args.rscript.resolve()),
                       r_libraries=[str(Path(value).resolve()) for value in libraries])
        for key in ['matrix', 'design', 'weights', 'libraries', 'norm_factors', 'block', 'batch', 'batch2', 'covariates', 'trend_covariate', 'statistics', 'gene_sets']:
            if key in request:
                request[key] = str(source / request[key])
        path = folder / 'request.json'
        path.write_text(json.dumps(request, indent=2) + '\n', encoding='utf-8')
        command = [args.java, '-cp', str(jar), 'org.jlinalg.cli.LimmaWorkflowCli', '--request', str(path)]
        with (folder / 'RUN.stdout').open('xb') as stdout, (folder / 'RUN.stderr').open('xb') as stderr:
            result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
        row = dict(command=command, exit_code=result.returncode,
                   warnings=str(folder / 'analysis/WARNINGS.md'),
                   stderr=str(folder / 'RUN.stderr'))
        if result.returncode == 0:
            command = [args.java, '-cp', str(jar), 'org.jlinalg.cli.LimmaWorkflowCli', '--verify', str(folder / 'analysis')]
            with (folder / 'VERIFY.stdout').open('xb') as stdout, (folder / 'VERIFY.stderr').open('xb') as stderr:
                verified = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
            row['VERIFY_exit_code'] = verified.returncode
            if verified.returncode:
                row['exit_code'] = 2
        rows[name] = row
        label = {0: 'COMPLETED WITH WARNINGS', 2: 'FAIL2: NO COMPLETE RESULT', 3: 'SKIP3: DEPENDENT ANALYSIS SKIPPED'}.get(row['exit_code'], 'FAIL2: NO COMPLETE RESULT')
        print(name + ': ' + label + '; read ' + row['warnings'] + ' and ' + row['stderr'], flush=True)
    (output / 'COMPLETED.json').write_text(json.dumps(rows, indent=2) + '\n', encoding='utf-8')
    return 2 if any(row['exit_code'] not in (0, 3) for row in rows.values()) else 3 if any(row['exit_code'] == 3 for row in rows.values()) else 0


if __name__ == '__main__':
    raise SystemExit(main())
