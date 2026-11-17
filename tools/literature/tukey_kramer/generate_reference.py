#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the independent base-R TukeyHSD oracle in a fresh output directory."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time


def pin(path):
    with Path(path).open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return dict(bytes=Path(path).stat().st_size, sha256=digest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rscript', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--tails', action='store_true', help='Generate independent positive-integral tail checks instead of ordinary TukeyHSD fits')
    args = parser.parse_args()
    out = args.out.absolute()
    if out.exists():
        parser.error('Preserve existing evidence and select NEW output')
    out.mkdir(parents=True)
    try:
        runtime = args.rscript.resolve(strict=True)
        worker = Path(__file__).with_name('tail_reference.R' if args.tails else 'reference.R').resolve()
        command = [str(runtime), '--vanilla', str(worker), str(out/'reference')]
        environment = {key:value for key,value in os.environ.items() if not key.startswith('LC_')}
        environment.update(LANG='C', LANGUAGE='C', LC_ALL='C')
        began = time.monotonic()
        with (out/'original.stdout').open('xb') as so, (out/'original.stderr').open('xb') as se:
            child = subprocess.Popen(command, env=environment, stdin=subprocess.DEVNULL, stdout=so, stderr=se)
            try:
                child.wait(timeout=120)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
                raise RuntimeError('Original R oracle timed out; retain partials')
        process = dict(command=command, pid=child.pid, exit_code=child.returncode, seconds=round(time.monotonic()-began, 3), locale='C', worker=pin(worker), rscript=pin(runtime))
        (out/'PROCESS.json').write_text(json.dumps(process, indent=2)+'\n', encoding='utf-8')
        if child.returncode or (out/'original.stderr').stat().st_size:
            raise RuntimeError('Original R reported failure/diagnostics; inspect retained original.stderr before accepting fixtures')
        marker = (out/'reference/COMPLETED.txt').read_text(encoding='utf-8')
        expected_marker = 'PASS_INDEPENDENT_POSITIVE_TAIL_INTEGRALS_AND_EXACT_TWO_GROUP_IDENTITY' if args.tails else 'PASS_ORIGINAL_R_AOV_TUKEYHSD_REFERENCE'
        if not marker.startswith(expected_marker):
            raise RuntimeError('Original R completion marker differs')
        value = dict(status='PASS_INDEPENDENT_POSITIVE_TAIL_REFERENCE' if args.tails else 'PASS_INDEPENDENT_BASE_R_TUKEYHSD_REFERENCE', process=process, files={p.name:pin(p) for p in (out/'reference').iterdir() if p.is_file()}, end_user_runtime_requirement='Native Java analysis does not require R; R is a validation oracle only.')
        (out/'COMPLETED.json').write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')
        print(value['status'])
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        message = ('SKIP3: R oracle skipped; supply a permitted Rscript path. ' if code == 3 else 'FAIL2: no accepted oracle fixture. ')+str(error)+' Preserve partials and retry NEW output. Native Java analysis itself needs no R.'
        (out/'FAILURE.txt').write_text(message+'\n', encoding='utf-8')
        print(message, file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
