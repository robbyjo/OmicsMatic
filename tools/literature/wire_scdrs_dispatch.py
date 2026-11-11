# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply only the already compiled/tested scDRS dispatch and help insertions."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE
from verify_scdrs_workflow_evidence import inspect, PREFIX
from signac_scan_evidence import byte_pin
from catalog_source_integrity import reverse_delta, verify_source


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    proof, files = inspect()
    group = PREFIX + 'dispatch-build21-1/'
    delta = json.loads(files[group + 'staged-root-delta.json'])['paths'][0]
    name = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    source = ROOT / name
    before = source.read_bytes()
    after = files[group + 'source-snapshot/' + name]
    assert delta['path'] == name and len(delta['edits']) == 2
    assert reverse_delta(after, delta) == before and byte_pin(before)['sha256'] == delta['before_sha256']
    data = ROOT / 'docs/literature/omics-2023-2026'
    descriptor = data / 'scdrs-dispatch-shared-source-compatibility.json'
    assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='scdrs',
        scope='Only the already tested scdrs-tools dispatch/help insertion; predecessor scientific classes, sources and canonical executable unchanged.', paths=[delta]))
    (out / 'root.before.java').write_bytes(before)
    (out / 'root.after.java').write_bytes(after)
    source.write_bytes(after)
    verify_source(ROOT, data, name, delta['before_sha256'])
    save(out / 'COMPLETED.json', dict(status='PASS_SCDRS_LIVE_ROOT_EXACT_TESTED_DISPATCH_DELTA', source=pin(source),
        descriptor=pin(descriptor), canonical_executable_unchanged=proof['canonical_executable_unchanged'], new_catalog_credit=False))
    print('PASS_SCDRS_LIVE_ROOT_EXACT_TESTED_DISPATCH_DELTA', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable exact tested root inverse or scientific preservation checks')
    main()
