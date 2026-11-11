# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Connect only scDRS metadata, prominent warnings and README refresh through an exact renderer inverse."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    name = 'tools/literature/render_review.py'
    path = ROOT / name
    before = path.read_bytes()
    assert b'from scdrs_catalog import' not in before and before.count(b'from mtag_catalog import') >= 10
    after = before.replace(b'from mtag_catalog import', b'from scdrs_catalog import')
    old = b'from refresh_mtag_catalog_metadata import refresh as refresh_mtag_metadata\n'
    assert after.count(old) == 1
    after = after.replace(old, b'from refresh_scdrs_catalog_metadata import refresh as refresh_scdrs_metadata\n')
    after = b'from scdrs_catalog import scdrs_metadata, SCDRS_WARNING, SCDRS_ACTIONS\n' + after
    edits = [
        (b'        elif tool_id == "mtag":\n', b'        elif tool_id == "scdrs":\n            tool.update(scdrs_metadata(ROOT,DATA))\n        elif tool_id == "mtag":\n'),
        (b'if tool_id not in {"mtag",', b'if tool_id not in {"scdrs", "mtag",'),
        (b'write_text(MTAG_WARNING + ', b'write_text(SCDRS_WARNING + MTAG_WARNING + '),
        (b'warning_body = MTAG_ACTIONS + ', b'warning_body = SCDRS_ACTIONS + MTAG_ACTIONS + '),
        (b'page.replace(\'<p id="overview"></p>\', MTAG_ACTIONS + ', b'page.replace(\'<p id="overview"></p>\', SCDRS_ACTIONS + MTAG_ACTIONS + '),
        (b'    refresh_mtag_metadata(ROOT, DATA)\n', b'    refresh_scdrs_metadata(ROOT, DATA)\n')]
    for old, new in edits:
        assert after.count(old) == 1, old
        after = after.replace(old, new)
    delta = inverse(before, after)
    delta['path'] = name
    assert reverse_delta(after, delta) == before
    data = ROOT / 'docs/literature/omics-2023-2026'
    descriptor = data / 'scdrs-renderer-shared-source-compatibility.json'
    assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='scdrs', paths=[delta],
        scope='Only scDRS metadata/card/warnings and current README hook. Exact archived root and data views preserve predecessor science and all scientific verifier rules.'))
    (out / 'renderer.before.py').write_bytes(before)
    (out / 'renderer.after.py').write_bytes(after)
    path.write_bytes(after)
    verify_source(ROOT, data, name, delta['before_sha256'])
    save(out / 'COMPLETED.json', dict(status='PASS_SCDRS_RENDERER_EXACT_METADATA_INVERSE', renderer=pin(path), descriptor=pin(descriptor), producer=pin(Path(__file__)), whole_goal_complete=False))
    print('PASS_SCDRS_RENDERER_EXACT_METADATA_INVERSE', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable exact metadata-only renderer inverses')
    main()
