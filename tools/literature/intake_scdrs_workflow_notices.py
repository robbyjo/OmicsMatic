# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain the Statsmodels notice and verify licenses in source-audited workflow wheels."""
import argparse
import json
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    resource = ROOT / 'src/main/resources/org/jlinalg/omics/scdrs'
    destination = resource / 'statsmodels-0.13.5-LICENSE.txt'
    assert not destination.exists()
    source = '/var/tmp/omicsmatic-scdrs-reference-2/environment/lib/python3.10/site-packages/statsmodels-0.13.5.dist-info/LICENSE.txt'
    command = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/bin/cat', source]
    result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=60)
    (out / 'license-read.stderr').write_bytes(result.stderr)
    save(out / 'license-read.process.json', dict(command=command, exit_code=result.returncode))
    assert result.returncode == 0 and b'Redistribution and use' in result.stdout
    destination.write_bytes(result.stdout)
    records = {}
    for path in sorted(resource.glob('*.whl')):
        with zipfile.ZipFile(path) as z:
            names = [n for n in z.namelist() if 'license' in n.lower() and n.endswith(('.txt', 'LICENSE', 'LICENSE.md'))]
            assert names, path.name
            records[path.name] = {name: dict(bytes=len(z.read(name))) for name in names}
    assert len(records) == 3
    save(out / 'COMPLETED.json', dict(status='PASS_SCDRS_WORKFLOW_THIRD_PARTY_NOTICES_RETAINED_ONLY',
         statsmodels_notice=pin(destination), bundled_wheel_notices=records, new_catalog_credit=False))
    print('PASS_SCDRS_WORKFLOW_THIRD_PARTY_NOTICES_RETAINED_ONLY')


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable notice identity checks')
    main()
