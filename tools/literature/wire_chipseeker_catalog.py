# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record exact reversible catalog-renderer edits; never change scientific guards."""
from chipseeker_catalog_support import ROOT, write_metadata_delta

if __name__ == '__main__':
    path = ROOT/'tools/literature/render_review.py'
    before = path.read_bytes()
    after = before
    edits = [
        (b'from meme_discovery_catalog import meme_discovery_metadata, MEME_DISCOVERY_WARNING, MEME_DISCOVERY_ACTIONS\n',
         b'from meme_discovery_current_catalog import meme_discovery_metadata, MEME_DISCOVERY_WARNING, MEME_DISCOVERY_ACTIONS\nfrom chipseeker_catalog import chipseeker_metadata, CHIPSEEKER_WARNING, CHIPSEEKER_ACTIONS\n'),
        (b'        elif tool_id == "meme":\n            tool.update(meme_discovery_metadata(ROOT, DATA))\n',
         b'        elif tool_id == "meme":\n            tool.update(meme_discovery_metadata(ROOT, DATA))\n        elif tool_id == "chipseeker":\n            tool.update(chipseeker_metadata(ROOT, DATA))\n'),
        (b'"signac", "htseq", "meme", "favor"', b'"signac", "htseq", "meme", "chipseeker", "favor"'),
        (b'write_text(MEME_DISCOVERY_WARNING + HTSEQ_WARNING', b'write_text(CHIPSEEKER_WARNING + MEME_DISCOVERY_WARNING + HTSEQ_WARNING'),
        (b'warning_body = MEME_DISCOVERY_ACTIONS + HTSEQ_ACTIONS', b'warning_body = CHIPSEEKER_ACTIONS + MEME_DISCOVERY_ACTIONS + HTSEQ_ACTIONS'),
        (b"page.replace('<p id=\"overview\"></p>', MEME_DISCOVERY_ACTIONS + HTSEQ_ACTIONS",
         b"page.replace('<p id=\"overview\"></p>', CHIPSEEKER_ACTIONS + MEME_DISCOVERY_ACTIONS + HTSEQ_ACTIONS")]
    for old, new in edits:
        assert after.count(old) == 1, old
        after = after.replace(old, new)
    write_metadata_delta(path, before, after,
        ROOT/'docs/literature/omics-2023-2026/chipseeker-render-shared-source-compatibility.json',
        'Only ChIPseeker metadata/warnings routing and the exact derived current MEME metadata provider. No scientific source or validation guard changes.')
