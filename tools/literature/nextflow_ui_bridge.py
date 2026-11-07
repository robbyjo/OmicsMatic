# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replace only one stale UI count with the existing inventory counter."""
import base64
import gzip
import json
from pathlib import Path
from catalog_source_integrity import digest, reverse_delta

ROOT = Path(__file__).resolve().parents[2]


def main():
    relative = 'tools/literature/check_review_ui.cjs'; path = ROOT / relative
    before = path.read_bytes()
    old = b"    assert.equal(await page.locator('#results article').count(), 48);\n"
    new = b"    assert.equal(await page.locator('#results article').count(), countStatus('upstream-adapter-validated'));\n"
    assert before.count(old) == 1
    after = before.replace(old, new); assert after.replace(new, old) == before
    index = before[:before.index(old)].count(b'\n')
    row = dict(path=relative, before_sha256=digest(before), after_sha256=digest(after),
        before_bytes=len(before), after_bytes=len(after), edits=[dict(after_start_line=index, after_end_line=index+1,
        after_chunk_sha256=digest(new), before_bytes=len(old), before_chunk_sha256=digest(old),
        before_gzip_base64=base64.b64encode(gzip.compress(old, mtime=0)).decode())])
    assert reverse_delta(after, row) == before
    target = ROOT / 'docs/literature/omics-2023-2026/nextflow-ui-shared-source-compatibility.json'; assert not target.exists()
    target.write_text(json.dumps(dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='nextflow',
        scope='Only one stale literal adapter-count expectation is replaced with the existing countStatus inventory counter. Every earlier browser check reconstructs exactly; no scientific assertion is relaxed.', paths=[row]), indent=2)+'\n', encoding='utf-8', newline='\n')
    assert path.read_bytes() == before; path.write_bytes(after)
    print('Only the stale browser count changed; exact prior check bytes reconstruct.')


if __name__ == '__main__': main()
