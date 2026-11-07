# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply only the Nextflow metadata import, branch and warning/action wiring."""
import argparse
import base64
import difflib
import gzip
import json
from pathlib import Path
from catalog_source_integrity import digest, reverse_delta

ROOT = Path(__file__).resolve().parents[2]


def main():
    if not __debug__: raise RuntimeError('Never disable exact metadata checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', type=Path, required=True)
    args = parser.parse_args()
    saved = args.before.resolve()
    assert saved.is_relative_to((ROOT / 'build/literature-review/nextflow-runtime').resolve()) and not saved.exists()
    relative = 'tools/literature/render_review.py'
    path = ROOT / relative; before = path.read_bytes(); after = before
    changes = [
        (b'from mashr_catalog import mashr_metadata, MASHR_WARNING, MASHR_ACTIONS\n',
         b'from mashr_catalog import mashr_metadata, MASHR_WARNING, MASHR_ACTIONS\nfrom nextflow_catalog import nextflow_metadata, NEXTFLOW_WARNING, NEXTFLOW_ACTIONS\n', 1),
        (b'        elif tool_id == "mashr":\n            tool.update(mashr_metadata(ROOT, DATA))\n',
         b'        elif tool_id == "mashr":\n            tool.update(mashr_metadata(ROOT, DATA))\n        elif tool_id == "nextflow":\n            tool.update(nextflow_metadata(ROOT, DATA))\n', 1),
        (b'"fastp", "mashr", "favor", "fuma"', b'"fastp", "mashr", "nextflow", "favor", "fuma"', 1),
        (b'MASHR_WARNING + FASTP_WARNING', b'MASHR_WARNING + NEXTFLOW_WARNING + FASTP_WARNING', 1),
        (b'MASHR_ACTIONS + FASTP_ACTIONS', b'MASHR_ACTIONS + NEXTFLOW_ACTIONS + FASTP_ACTIONS', 2),
    ]
    for old, new, count in changes:
        assert after.count(old) == count, (old, after.count(old))
        after = after.replace(old, new)
    reverse = after
    for old, new, count in reversed(changes):
        assert reverse.count(new) == count
        reverse = reverse.replace(new, old)
    assert reverse == before
    a, b = before.splitlines(keepends=True), after.splitlines(keepends=True); edits = []
    for kind, i, j, k, l in difflib.SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
        if kind == 'equal': continue
        original, updated = b''.join(a[i:j]), b''.join(b[k:l])
        edits.append(dict(after_start_line=k, after_end_line=l, after_chunk_sha256=digest(updated),
            before_bytes=len(original), before_chunk_sha256=digest(original),
            before_gzip_base64=base64.b64encode(gzip.compress(original, mtime=0)).decode()))
    row = dict(path=relative, before_sha256=digest(before), after_sha256=digest(after), before_bytes=len(before), after_bytes=len(after), edits=edits)
    assert reverse_delta(after, row) == before
    doc = ROOT / 'docs/literature/omics-2023-2026/nextflow-catalog-shared-source-compatibility.json'
    assert not doc.exists()
    saved.write_bytes(before)
    doc.write_text(json.dumps(dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='nextflow',
        scope='Only the Nextflow catalog metadata branch/import and prominent warnings/actions are wired. Removing exact permitted strings reconstructs every prior renderer byte. All earlier scientific source guards remain exact.', paths=[row]), indent=2) + '\n', encoding='utf-8', newline='\n')
    assert path.read_bytes() == before
    path.write_bytes(after)
    print('Exact Nextflow catalog-only inverse saved; all other rendering instructions are unchanged.')


if __name__ == '__main__': main()
