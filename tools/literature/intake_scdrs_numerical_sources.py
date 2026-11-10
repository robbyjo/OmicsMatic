# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain the NumPy summation source and notices used by the native scDRS component."""
import argparse
import urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/scdrs-runtime') and not out.exists()
    out.mkdir(parents=True)
    records = {}
    for name, suffix in [('numpy-1.23.5-LICENSE.txt', 'LICENSE.txt'),
                         ('numpy-1.23.5-loops_utils.h.src', 'numpy/core/src/umath/loops_utils.h.src')]:
        url = 'https://raw.githubusercontent.com/numpy/numpy/v1.23.5/' + suffix
        with urllib.request.urlopen(url, timeout=60) as response:
            raw = response.read()
        (out / name).write_bytes(raw)
        records[name] = dict(url=url, **pin(out / name))
    resource = ROOT / 'src/main/resources/org/jlinalg/omics/scdrs'
    resource.mkdir(parents=True, exist_ok=True)
    notices = {'scdrs-1.0.2-LICENSE.txt': ROOT / 'build/literature-review/scdrs-runtime/source-intake-1/1.0.2/source/LICENSE',
               'numpy-1.23.5-LICENSE.txt': out / 'numpy-1.23.5-LICENSE.txt'}
    for name, source in notices.items():
        target = resource / name
        assert not target.exists()
        target.write_bytes(source.read_bytes())
    (resource / '.gitattributes').write_text('*.txt -text\n', encoding='utf-8', newline='\n')
    save(out / 'COMPLETED.json', dict(status='PASS_NUMERICAL_SOURCE_AND_LICENSE_INTAKE_ONLY', sources=records,
         notices={str(p.relative_to(ROOT)): pin(p) for p in resource.iterdir() if p.is_file()}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Do not disable intake integrity checks')
    main()
