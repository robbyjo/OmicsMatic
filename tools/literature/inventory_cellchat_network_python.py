# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inventory an existing permitted Python installation; never install dependencies."""
import argparse, json, os, subprocess, sys
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--python', type=Path, required=True)
    a = p.parse_args()
    out, python = a.out.resolve(), a.python.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/cellchat-runtime')
    out.mkdir(parents=True)
    env = {k: v for k, v in os.environ.items() if not k.startswith('PYTHON') and k != 'VIRTUAL_ENV'}
    env.update(PYTHONDONTWRITEBYTECODE='1', PYTHONNOUSERSITE='1', PYTHONSAFEPATH='1')
    probe = ('import sys,json,importlib.metadata as m;'
             'print(json.dumps(dict(prefix=sys.prefix,base_prefix=sys.base_prefix,version=sys.version,'
             'packages={n:m.version(n) for n in ["umap-learn","numpy","scipy","numba","llvmlite","scikit-learn"]})))')
    r = subprocess.run([str(python), '-c', probe], env=env, capture_output=True, timeout=60)
    (out / 'probe.stdout').write_bytes(r.stdout)
    (out / 'probe.stderr').write_bytes(r.stderr)
    assert r.returncode == 0, 'Missing exact Python dependencies; skip dependent analysis'
    metadata = json.loads(r.stdout)
    assert metadata['version'].startswith('3.13.13 ')
    assert metadata['packages'] == {'umap-learn': '0.5.12', 'numpy': '2.5.4', 'scipy': '1.18.1',
                                   'numba': '0.68.0', 'llvmlite': '0.50.0', 'scikit-learn': '1.9.1'}
    venv, base = Path(metadata['prefix']), Path(metadata['base_prefix'])
    assert venv != base and python.is_relative_to(venv), 'Use the declared private environment'
    files = set()
    for root in [venv, base / 'Lib', base / 'DLLs']:
        for directory, folders, names in os.walk(root, followlinks=False):
            d = Path(directory)
            folders[:] = [n for n in folders if d / n != base / 'Lib/site-packages']
            assert not d.is_symlink()
            for name in names:
                f = d / name
                assert f.is_file() and not f.is_symlink()
                files.add(f)
    files.update(f for f in base.glob('python*') if f.is_file())
    save(out / 'python-files.json', dict(schema_version=1, status='PYTHON_NETWORK_DEPENDENCY_INVENTORY_ONLY',
         python=str(python), venv=str(venv), base=str(base), metadata=metadata,
         files={str(f): pin(f) for f in sorted(files)}, analysis_admitted=False))
    print('Inventoried', len(files), 'existing files; no installation or analysis', flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable inventory checks')
    try:
        main()
    except Exception as error:
        print('SKIP3_NETWORK_PYTHON_UNAVAILABLE: ' + repr(error) +
              '\nREQUIRED ACTION: Skip dependent analysis. Supply the exact permitted private Python dependencies, '
              'or install them only where allowed; preserve this attempt and retry NEW paths.', file=sys.stderr)
        raise SystemExit(3)
