# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Wire only the frozen complete-workflow Salmon catalog provider; preserve exact prior rendering bytes."""
import argparse
import base64
import difflib
import gzip
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from catalog_source_integrity import digest, reverse_delta
from salmon_workflow_catalog import verify


def main():
    if not __debug__:
        raise RuntimeError('Never disable exact metadata-only promotion checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/salmon-runtime').resolve()) and not out.exists()
    data = ROOT / 'docs/literature/omics-2023-2026'
    proof = verify(ROOT, data)
    assert proof['status'] == 'passed-typed-Salmon-bulk-workflows-portable-bundles-and-source-populations'
    relative = 'tools/literature/render_review.py'
    path = ROOT / relative; before = path.read_bytes()
    old = b'from salmon_catalog import salmon_metadata, SALMON_WARNING, SALMON_ACTIONS\n'
    new = b'from salmon_workflow_catalog import salmon_metadata, SALMON_WARNING, SALMON_ACTIONS\n'
    assert before.count(old) == 1 and new not in before
    after = before.replace(old, new)
    assert after.replace(new, old) == before
    out.mkdir()
    for name in ['tool-inventory.json', 'papers.json', 'summary.json', 'catalog-enrichment.json', 'SHA256SUMS']:
        (out / ('before-' + name + '.gz')).write_bytes(gzip.compress((data / name).read_bytes(), mtime=0))
    (out / 'render-before.py').write_bytes(before)
    edits = []
    for kind, i, j, k, l in difflib.SequenceMatcher(None, before.splitlines(keepends=True), after.splitlines(keepends=True), autojunk=False).get_opcodes():
        if kind == 'equal':
            continue
        original = b''.join(before.splitlines(keepends=True)[i:j]); updated = b''.join(after.splitlines(keepends=True)[k:l])
        assert kind == 'replace' and original == old and updated == new
        edits.append(dict(after_start_line=k, after_end_line=l, after_chunk_sha256=digest(updated), before_bytes=len(original), before_chunk_sha256=digest(original),
                          before_gzip_base64=base64.b64encode(gzip.compress(original, mtime=0)).decode()))
    assert len(edits) == 1
    row = dict(path=relative, before_sha256=digest(before), after_sha256=digest(after), before_bytes=len(before), after_bytes=len(after), edits=edits)
    assert reverse_delta(after, row) == before
    record = data / 'salmon-workflow-shared-source-compatibility.json'
    assert not record.exists()
    record.write_text(json.dumps(dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='salmon',
        scope='Only the Salmon catalog metadata/warnings provider import changes to the independently verified later complete-workflow checkpoint. '
              'The earlier native decoder/role provider remains immutable and is checked transitively. Every earlier renderer byte reconstructs exactly; no scientific source exception is allowed.', paths=[row]), indent=2) + '\n', encoding='utf-8', newline='\n')
    assert path.read_bytes() == before
    path.write_bytes(after)
    print('Frozen complete Salmon workflow catalog provider wired; exact previous renderer bytes reconstruct.')


if __name__ == '__main__':
    main()
