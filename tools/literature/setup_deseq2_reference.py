# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a private original DESeq2 reference, preserving all existing R libraries."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
RSCRIPT = Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe')
BASES = [ROOT / 'build/literature-review/signac-runtime/motif-reference-setup-1/R-lib',
         ROOT / 'build/literature-review/signac-runtime/reference-setup-1/R-lib',
         ROOT / 'build/omics-sctransform-r', RSCRIPT.parents[1] / 'library']


def pin(path):
    with path.open('rb') as stream:
        return {'bytes': path.stat().st_size, 'sha256': hashlib.file_digest(stream, 'sha256').hexdigest()}


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def inventory():
    return {str(root): {p.relative_to(root).as_posix(): pin(p) for p in sorted(root.rglob('*'))
                       if p.is_file()} for root in BASES}


def environment():
    env = dict(os.environ)
    for key in list(env):
        if key.startswith('LC_') or key in {'LANG', 'LANGUAGE', 'R_LIBS', 'R_LIBS_USER', 'R_LIBS_SITE',
                                          'R_PROFILE', 'R_PROFILE_USER', 'R_ENVIRON', 'R_ENVIRON_USER',
                                          'R_DEFAULT_PACKAGES', 'R_TESTS'}:
            env.pop(key, None)
    env.update(LANG='C', LC_ALL='C', OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1')
    return env


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-install', action='store_true')
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/deseq2-runtime') and not out.exists()
    out.mkdir()
    action = ('Read installer.stdout and installer.stderr. Where permitted supply DESeq2 1.52.0 and '
              'its named dependencies in a NEW private library. Preserve this attempt and all existing '
              'R libraries. Skip original DESeq2 analysis until its package and DLLs load successfully.')
    before = inventory()
    save(out / 'BASES-BEFORE.json', before)
    result = {'status': 'SKIP3_DESEQ2_REFERENCE_UNAVAILABLE', 'required_action': action,
              'analysis_admitted': False, 'installation_authorized': args.allow_install}
    try:
        if not args.allow_install:
            raise RuntimeError('Installation/download forbidden; dependent analysis skipped')
        intake = ROOT / 'build/literature-review/deseq2-runtime/reference-intake-2'
        binary = intake / 'DESeq2_1.52.0.zip'
        assert pin(binary) == {'bytes': 3649042, 'sha256': '95757ed94f5314cc97727adf80dad2bc34fd9d8339ae8e74fda3f47b825994a5'}
        command = [str(RSCRIPT), '--vanilla', str(ROOT / 'tools/literature/install_deseq2_reference.R'),
                   str(out / 'R-lib'), str(out), str(binary), *map(str, BASES)]
        with (out / 'installer.stdout').open('xb') as stdout, (out / 'installer.stderr').open('xb') as stderr:
            process = subprocess.run(command, env=environment(), cwd=ROOT, stdin=subprocess.DEVNULL,
                                     stdout=stdout, stderr=stderr, timeout=600, check=False)
        save(out / 'process.json', {'command': command, 'exit_code': process.returncode})
        report = json.loads((out / 'R-RESULT.json').read_bytes())
        result.update(report)
        if process.returncode != 0 or report['status'] != 'INSTALLED_ORIGINAL_DESEQ2_REFERENCE_ONLY':
            raise RuntimeError(report.get('detail', 'Original runtime did not load'))
        downloads = []
        for p in sorted((out / 'downloads').glob('*')):
            downloads.append({'path': p.relative_to(out).as_posix(), **pin(p)})
        result.update(downloads=downloads, original_binary=pin(binary), analysis_admitted=False)
    except Exception as error:
        result.update(status='SKIP3_DESEQ2_REFERENCE_UNAVAILABLE', detail=str(error))
    finally:
        after = inventory()
        save(out / 'BASES-AFTER.json', after)
        result['preexisting_libraries_unchanged'] = before == after
        result['preexisting_file_count'] = sum(map(len, before.values()))
        if before != after:
            result.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED', detail='Inspect before/after inventories')
        save(out / 'RESULT.json', result)
        (out / 'WARNINGS.md').write_text('# ' + result['status'] + '\n\n' + result.get('detail', '') +
                                       '\n\nRequired action: ' + action + '\n\nSetup is not analysis admission.\n', encoding='utf-8')
    print(json.dumps(result))
    return 0 if result['status'] == 'INSTALLED_ORIGINAL_DESEQ2_REFERENCE_ONLY' else 3


if __name__ == '__main__':
    raise SystemExit(main())
