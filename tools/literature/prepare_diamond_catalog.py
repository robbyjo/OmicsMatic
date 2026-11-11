# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare the exact DIAMOND renderer delta after source and routed execution validation."""
import argparse, gzip, json
from pathlib import Path
from validate_diamond_execution import ROOT, BASE, pin, save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source
from verify_diamond_evidence import verify


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    verify()
    data = ROOT/'docs/literature/omics-2023-2026'
    for name in ['tool-inventory.json', 'papers.json', 'summary.json', 'README.md', 'WARNINGS.md']:
        (out/name).write_bytes((data/name).read_bytes())
    (out/'reviewed-uses.json').write_bytes(gzip.decompress((data/'diamond-prior-reviews.json.gz').read_bytes()))
    name = 'tools/literature/render_review.py'
    path = ROOT/name
    before = path.read_bytes()
    text = before.decode('utf8')
    assert text.count('from cellchat_catalog import') == 12
    text = text.replace('from cellchat_catalog import', 'from diamond_catalog import')
    marker = 'from diamond_catalog import cellchat_metadata, CELLCHAT_WARNING, CELLCHAT_ACTIONS\n'
    assert text.count(marker) == 1
    text = text.replace(marker, 'from diamond_catalog import diamond_metadata, DIAMOND_WARNING, DIAMOND_ACTIONS\n'+marker)
    replacements = [
        ('        elif tool_id == "cellchat":\n', '        elif tool_id == "diamond":\n            tool.update(diamond_metadata(ROOT,DATA))\n        elif tool_id == "cellchat":\n'),
        ('if tool_id not in {"cellchat",', 'if tool_id not in {"diamond", "cellchat",'),
        ('write_text(CELLCHAT_WARNING +', 'write_text(DIAMOND_WARNING + CELLCHAT_WARNING +'),
        ('warning_body = CELLCHAT_ACTIONS +', 'warning_body = DIAMOND_ACTIONS + CELLCHAT_ACTIONS +'),
        ("page.replace('<p id=\"overview\"></p>', MONOCLE_ACTIONS +", "page.replace('<p id=\"overview\"></p>', DIAMOND_ACTIONS + MONOCLE_ACTIONS +")]
    for old, new in replacements:
        assert text.count(old) == 1, old
        text = text.replace(old, new)
    compile(text, name, 'exec')
    after = text.encode()
    row = inverse(before, after)
    row['path'] = name
    assert reverse_delta(after, row) == before
    descriptor = data/'diamond-renderer-shared-source-compatibility.json'
    assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='diamond',
        scope='Twelve imports select an exact predecessor metadata view; add only DIAMOND availability, preserved validation-note handling and prominent Markdown/HTML actions. Exact prior renderer reconstructs. No scientific-source exception.', paths=[row]))
    (out/'render_review.before.py').write_bytes(before)
    (out/'render_review.after.py').write_bytes(after)
    path.write_bytes(after)
    verify_source(ROOT, data, name, row['before_sha256'])
    save(out/'COMPLETED.json', dict(status='PASS_DIAMOND_RENDERER_DERIVATION_ONLY',
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), path, descriptor]},
        scientific_sources_changed=False, source_exceptions_added=False, whole_goal_complete=False))
    print('Prepared exact DIAMOND renderer delta; no scientific source changed')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact renderer preservation checks')
    main()
