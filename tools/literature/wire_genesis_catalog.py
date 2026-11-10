# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Connect GENESIS catalog metadata and prominent warnings using an exact renderer-only inverse."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); a = p.parse_args(); out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/genesis-runtime'); out.mkdir(parents=True)
    name = 'tools/literature/render_review.py'; file = ROOT / name; before = file.read_bytes()
    assert b'from genesis_catalog import' not in before and before.count(b'from snprelate_catalog import') >= 10
    after = before.replace(b'from snprelate_catalog import', b'from genesis_catalog import')
    after = b'from genesis_catalog import genesis_metadata, GENESIS_WARNING, GENESIS_ACTIONS\n' + after
    changes = [
        (b'        elif tool_id == "snprelate":\n', b'        elif tool_id == "genesis":\n            tool.update(genesis_metadata(ROOT,DATA))\n        elif tool_id == "snprelate":\n'),
        (b'if tool_id not in {"snprelate",', b'if tool_id not in {"genesis", "snprelate",'),
        (b'write_text(SNPRELATE_WARNING + ', b'write_text(GENESIS_WARNING + SNPRELATE_WARNING + '),
        (b'warning_body = SNPRELATE_ACTIONS + ', b'warning_body = GENESIS_ACTIONS + SNPRELATE_ACTIONS + '),
        (b'page.replace(\'<p id="overview"></p>\', SNPRELATE_ACTIONS + ', b'page.replace(\'<p id="overview"></p>\', GENESIS_ACTIONS + SNPRELATE_ACTIONS + '),
    ]
    for old, new in changes:
        assert after.count(old) == 1, old; after = after.replace(old, new)
    row = inverse(before, after); row['path'] = name; assert reverse_delta(after, row) == before
    data = ROOT / 'docs/literature/omics-2023-2026'; descriptor = data / 'genesis-renderer-shared-source-compatibility.json'; assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='genesis',
                         scope='Explicit GENESIS provider/card/warnings; exact predecessor metadata view. No scientific source or verifier change.', paths=[row]))
    (out / 'renderer.before.py').write_bytes(before); (out / 'renderer.after.py').write_bytes(after); assert file.read_bytes() == before; file.write_bytes(after)
    verify_source(ROOT, data, name, row['before_sha256'])
    save(out / 'COMPLETED.json', dict(status='PASS_GENESIS_RENDERER_EXACT_METADATA_INVERSE', renderer=pin(file), descriptor=pin(descriptor), producer=pin(Path(__file__)), new_catalog_credit=False, whole_goal_complete=False))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact renderer-only inverse checks')
    main()
