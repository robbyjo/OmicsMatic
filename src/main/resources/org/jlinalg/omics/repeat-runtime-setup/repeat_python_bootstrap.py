"""Isolated, bytecode-free entry point for the pinned historical FamDB script.

Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""
from pathlib import Path
import contextlib
import hashlib
import io
import json
import runpy
import sys


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


home = Path(__file__).resolve().parent.parent
lock = json.loads((home / 'runtime-lock.json').read_text(encoding='utf-8'))
profile = (home / 'profile.txt').read_text(encoding='ascii').strip()
pin = lock['profiles'][profile]
if not pin['python_required'] or not (sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode):
    raise RuntimeError('Historical FamDB requires the pinned isolated -I -S -B interpreter')
if sha(Path(sys.executable).resolve()) != lock['python']['executable']['sha256']:
    raise RuntimeError('Pinned historical FamDB interpreter fingerprint differs')
# -B prevents writes. Moving the read prefix away from source __pycache__ also
# prevents accidental consumption of bytecode from another environment.
sys.pycache_prefix = str(home / 'python' / 'never-read-bytecode')
sys.path.append(str(home / 'python' / 'site-packages'))


def checked_script(path):
    script = Path(path).resolve(strict=True)
    if script.name != 'famdb.py' or sha(script) != pin['source_members']['famdb.py']['sha256']:
        raise RuntimeError('Only the exact pinned original FamDB script is admitted')
    sys.path.append(str(script.parent))
    return script


if sys.argv[1:] == ['--omics-runtime-health']:
    import h5py
    import numpy
    script = checked_script(home / 'repeatmasker' / 'famdb.py')
    captured = io.StringIO()
    sys.argv = [str(script), '--help']
    with contextlib.redirect_stdout(captured), contextlib.redirect_stderr(captured):
        try:
            runpy.run_path(str(script), run_name='__main__')
        except SystemExit as completed:
            if completed.code not in (None, 0):
                raise
    external = {}
    for module in tuple(sys.modules.values()):
        for attribute in ('__file__', '__cached__'):
            value = getattr(module, attribute, None)
            if not isinstance(value, str):
                continue
            path = Path(value)
            if path.is_file():
                path = path.resolve()
                if not path.is_relative_to(home):
                    external[str(path)] = dict(bytes=path.stat().st_size, sha256=sha(path))
    print(json.dumps(dict(python=sys.version.split()[0], executable=str(Path(sys.executable).resolve()),
                         numpy=numpy.__version__, h5py=h5py.__version__, isolated=sys.flags.isolated,
                         no_site=sys.flags.no_site, no_bytecode_writes=sys.flags.dont_write_bytecode,
                         scientific_provider=str(home / 'python' / 'site-packages'), external_files=external), sort_keys=True))
else:
    if len(sys.argv) < 2:
        raise RuntimeError('Expected the pinned FamDB script, not arbitrary Python flags')
    script = checked_script(sys.argv[1])
    sys.argv = sys.argv[1:]
    runpy.run_path(str(script), run_name='__main__')
