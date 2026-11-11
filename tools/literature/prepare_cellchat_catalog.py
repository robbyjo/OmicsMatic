# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare exact renderer evolution and publish validated CellChat root supplements."""
import argparse, json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source
BASE = ROOT / 'build/literature-review/cellchat-runtime'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    routed = json.loads((BASE/'catalog-root-validation-1/COMPLETED.json').read_bytes())
    assert routed['status'] == 'PASS48_COMPLETE_CELLCHAT_ROOT_BUNDLES30_SKIPS6_PREDECESSOR_FLOWS'
    for name in ['tool-inventory.json', 'papers.json', 'summary.json', 'README.md', 'WARNINGS.md']:
        (out/name).write_bytes((data/name).read_bytes())
    (out/'reviewed-uses.json').write_bytes((ROOT/'tools/literature/reviewed-uses.json').read_bytes())
    name = 'tools/literature/render_review.py'
    path = ROOT/name
    before = path.read_bytes()
    text = before.decode('utf8')
    assert text.count('from cistarget_catalog import') == 11
    text = text.replace('from cistarget_catalog import', 'from cellchat_catalog import')
    marker = 'from cellchat_catalog import cistarget_metadata, CISTARGET_WARNING, CISTARGET_ACTIONS\n'
    assert text.count(marker) == 1
    text = text.replace(marker, 'from cellchat_catalog import cellchat_metadata, CELLCHAT_WARNING, CELLCHAT_ACTIONS\n'+marker)
    marker = '        elif tool_id == "cistarget":\n'
    assert text.count(marker) == 1
    text = text.replace(marker, '        elif tool_id == "cellchat":\n            tool.update(cellchat_metadata(ROOT,DATA))\n'+marker)
    for old, new in [
        ('if tool_id not in {"cistarget",', 'if tool_id not in {"cellchat", "cistarget",'),
        ('write_text(CISTARGET_WARNING +', 'write_text(CELLCHAT_WARNING + CISTARGET_WARNING +'),
        ('warning_body = CISTARGET_ACTIONS +', 'warning_body = CELLCHAT_ACTIONS + CISTARGET_ACTIONS +')]:
        assert text.count(old) == 1, old
        text = text.replace(old, new)
    compile(text, name, 'exec')
    after = text.encode()
    row = inverse(before, after)
    row['path'] = name
    assert reverse_delta(after, row) == before
    descriptor = data/'cellchat-renderer-shared-source-compatibility.json'
    assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='cellchat',
        scope='Eleven imports use a source-preserving predecessor metadata view; add only CellChat availability/validation-note handling and prominent warnings in Markdown/both HTML pages. Exact prior renderer reconstructs; no scientific-source exception.', paths=[row]))
    (out/'render_review.before.py').write_bytes(before)
    (out/'render_review.after.py').write_bytes(after)
    path.write_bytes(after)
    verify_source(ROOT, data, name, row['before_sha256'])
    home = ROOT/'docs/literature/cellchat-catalog-development'
    assert not home.exists()
    home.mkdir()
    published = {}
    for compiler, label in [('javac21', 'catalog-root-build21-2'), ('javac25', 'catalog-root-build25-2')]:
        report = json.loads((BASE/label/'COMPLETED.json').read_bytes())
        source = ROOT/report['artifact']['path']
        assert pin(source) == {k: report['artifact'][k] for k in ['bytes', 'sha256']}
        target = home/('cellchat-root-'+compiler+'.jar')
        target.write_bytes(source.read_bytes())
        published[target.relative_to(ROOT).as_posix()] = pin(target)
    save(out/'COMPLETED.json', dict(status='PASS_CELLCHAT_RENDERER_DERIVATION_AND_ROOT_PUBLICATION_ONLY',
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), path, descriptor]},
        published_root_artifacts=published, predecessor_provider_files_changed=False,
        scientific_source_exceptions_added=False, whole_goal_complete=False))
    print('Prepared CellChat renderer and source-preserving root supplements')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact renderer/artifact checks')
    main()
