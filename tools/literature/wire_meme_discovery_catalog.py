# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add verified classic MEME metadata/recovery text with an exact inverse for every renderer change."""
from setup_deseq2_reference import ROOT, save
from wire_signac_catalog import delta
from meme_discovery_catalog import verify


def main():
    data = ROOT/'docs/literature/omics-2023-2026'; verify(ROOT,data)
    path = ROOT/'tools/literature/render_review.py'; before = path.read_bytes(); after = before
    changes = [
        (b'from htseq_catalog import htseq_metadata, HTSEQ_WARNING, HTSEQ_ACTIONS\n',
         b'from htseq_catalog import htseq_metadata, HTSEQ_WARNING, HTSEQ_ACTIONS\nfrom meme_discovery_catalog import meme_discovery_metadata, MEME_DISCOVERY_WARNING, MEME_DISCOVERY_ACTIONS\n'),
        (b'        elif tool_id == "htseq":\n            tool.update(htseq_metadata(ROOT, DATA))\n',
         b'        elif tool_id == "htseq":\n            tool.update(htseq_metadata(ROOT, DATA))\n        elif tool_id == "meme":\n            tool.update(meme_discovery_metadata(ROOT, DATA))\n'),
        (b'"signac", "htseq", "favor"', b'"signac", "htseq", "meme", "favor"'),
        (b'.write_text(HTSEQ_WARNING + FUMA_WARNING', b'.write_text(MEME_DISCOVERY_WARNING + HTSEQ_WARNING + FUMA_WARNING'),
        (b'    warning_body = HTSEQ_ACTIONS + FUMA_ACTIONS', b'    warning_body = MEME_DISCOVERY_ACTIONS + HTSEQ_ACTIONS + FUMA_ACTIONS'),
        (b"page.replace('<p id=\"overview\"></p>', HTSEQ_ACTIONS + FUMA_ACTIONS", b"page.replace('<p id=\"overview\"></p>', MEME_DISCOVERY_ACTIONS + HTSEQ_ACTIONS + FUMA_ACTIONS")]
    for old,new in changes:
        assert after.count(old) == 1, (old,after.count(old)); after = after.replace(old,new)
    record = data/'meme-discovery-catalog-shared-source-compatibility.json'
    out = ROOT/'build/literature-review/meme-runtime/discovery-catalog-source-intake-1'
    assert not record.exists() and not out.exists(); out.mkdir()
    (out/'render-before.py').write_bytes(before)
    for name in ['summary.json','tool-inventory.json','papers.json','SHA256SUMS']:
        (out/name).write_bytes((data/name).read_bytes())
    (out/'reviewed-uses.json').write_bytes((ROOT/'tools/literature/reviewed-uses.json').read_bytes())
    save(record,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='meme',
        scope='Verified scoped classic MEME metadata and prominent required actions. Exact previous renderer bytes reconstruct; no scientific-source waiver, provider reclassification or role rewrite.',
        paths=[delta('tools/literature/render_review.py',before,after)]))
    assert path.read_bytes() == before; path.write_bytes(after)
    print('Wired verified classic MEME discovery metadata and prominent recovery; prior renderer bytes reconstruct exactly')


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable source and scope checks')
    main()
