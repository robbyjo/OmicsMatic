# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind predecessor metadata providers to an internally consistent exact prior catalog; no scientific source changes."""
import argparse
import gzip
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE
from signac_scan_evidence import byte_pin
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    proof = json.loads((data / 'mtag-catalog-validation.json').read_bytes())
    files = {}
    for name in ['tool-inventory.json', 'summary.json']:
        raw = (BASE / 'catalog-render-1' / (name + '.before')).read_bytes()
        assert byte_pin(raw) == proof['catalog_snapshot'][name] == pin(data / name)
        archive = 'scdrs-prior-' + name + '.gz'
        assert not (data / archive).exists()
        (data / archive).write_bytes(gzip.compress(raw, mtime=0))
        files[name] = dict(archive=archive, archive_pin=pin(data / archive), decoded_pin=byte_pin(raw))
    ledger = data / 'scdrs-prior-metadata-view.json'
    assert not ledger.exists()
    save(ledger, dict(status='EXACT_PRE_SCDRS_GLOBAL_CATALOG_METADATA_VIEW', files=files, scientific_paths_overridden=False))
    names = ['fuma', 'mashr', 'eagle', 'shapeit', 'iqtree', 'mafft', 'pal2nal', 'fastp', 'nextflow', 'salmon',
        'tomtom', 'bigwig', 'stringtie', 'chromvar', 'deseq2', 'signac', 'htseq', 'repeat', 'scvi', 'quantification']
    source = ROOT / 'tools/literature/render_review.py'
    before = source.read_bytes()
    anchor = b'from catalog_shared_build_context import fuma_metadata, mashr_metadata\n'
    assert before.count(anchor) == 1
    addition = ('from scdrs_catalog import ' + ', '.join(n + '_metadata' for n in names) + '\n').encode()
    after = before.replace(anchor, anchor + addition)
    delta = inverse(before, after)
    delta['path'] = source.relative_to(ROOT).as_posix()
    assert reverse_delta(after, delta) == before and len(delta['edits']) == 1
    descriptor = data / 'scdrs-legacy-metadata-view-shared-source-compatibility.json'
    assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='scdrs', paths=[delta],
        scope='Only exact prior global catalog metadata inputs for earlier providers. No scientific source, check or source allowlist changes.'))
    (out / 'renderer.before.py').write_bytes(before)
    (out / 'renderer.after.py').write_bytes(after)
    source.write_bytes(after)
    verify_source(ROOT, data, delta['path'], delta['before_sha256'])
    save(out / 'COMPLETED.json', dict(status='PASS_SCDRS_EXACT_PRIOR_METADATA_VIEW_AND_PROVIDER_IMPORT', ledger=pin(ledger), descriptor=pin(descriptor), renderer=pin(source)))
    print('PASS_SCDRS_EXACT_PRIOR_METADATA_VIEW_AND_PROVIDER_IMPORT', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable exact archived metadata and unchanged scientific-source checks')
    main()
