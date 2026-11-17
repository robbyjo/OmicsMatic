#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run an installed original spdep reference; never install or replace a runtime."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time


def pin(path):
    return dict(bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['rscript', 'r-home', 'original-source', 'out']:
        parser.add_argument('--'+name, type=Path, required=True)
    parser.add_argument('--r-library', type=Path, action='append', required=True)
    args = parser.parse_args()
    out = args.out.absolute()
    out.mkdir(parents=True, exist_ok=False)
    try:
        worker = Path(__file__).with_name('reference.R').resolve()
        runtime = args.rscript.resolve(strict=True)
        original = args.original_source.resolve(strict=True)
        command = [str(runtime), '--vanilla', str(worker), str(out/'reference'), str(original), *[str(p.resolve()) for p in args.r_library]]
        env = {k:v for k,v in os.environ.items() if not k.startswith('LC_')}
        env.update(LANG='C', LC_ALL='C', LANGUAGE='C', R_HOME=str(args.r_home.resolve()),
            LD_LIBRARY_PATH=str(runtime.parent.parent/'lib'), OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1')
        began = time.monotonic()
        with (out/'original.stdout').open('xb') as so, (out/'original.stderr').open('xb') as se:
            child = subprocess.Popen(command, env=env, stdin=subprocess.DEVNULL, stdout=so, stderr=se)
            (out/'RUNNING.json').write_text(json.dumps(dict(pid=child.pid, command=command), indent=2)+'\n')
            try:
                child.wait(timeout=180)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
                raise RuntimeError('Original reference exceeded its declared 180-second limit and the owned child was stopped')
        record = dict(command=command, exit_code=child.returncode, seconds=round(time.monotonic()-began, 3),
            worker=pin(worker), original_source=pin(original), rscript=pin(runtime))
        (out/'PROCESS.json').write_text(json.dumps(record, indent=2)+'\n')
        if child.returncode == 3:
            raise FileNotFoundError('Declared original R/spdep reference profile is unavailable')
        if child.returncode:
            raise RuntimeError('Original reference failed; inspect retained original diagnostics')
        reference = json.loads((out/'reference/reference.json').read_bytes())
        if reference['status'] != 'PASS_ORIGINAL_SPDEP_LM_MORANTEST_REFERENCE' or len(reference['cases']) != 36:
            raise RuntimeError('Original reference population differs')
        (out/'COMPLETED.json').write_text(json.dumps(dict(status=reference['status'], process=record,
            cases=36, gene_tests=sum(len(c['genes']) for c in reference['cases']),
            files={p.name:pin(p) for p in (out/'reference').iterdir() if p.is_file()}), indent=2)+'\n')
        print('PASS original spdep reference; inspect retained WARNINGS.json')
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        message = ('SKIP3 reference unavailable. ' if code == 3 else 'FAIL2 no accepted reference. ')+str(error)+'. Preserve partials, inspect logs and retry NEW output with the declared permitted runtime. Native analysis needs no R.'
        (out/'FAILURE.txt').write_text(message+'\n', encoding='utf-8')
        print(message, file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
