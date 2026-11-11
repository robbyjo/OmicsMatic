# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Wire verified HTSeq metadata and prominent recovery actions with an exact renderer inverse."""
from setup_deseq2_reference import ROOT,save
from wire_signac_catalog import delta
from htseq_catalog import verify


def main():
    data=ROOT/'docs/literature/omics-2023-2026';verify(ROOT,data)
    path=ROOT/'tools/literature/render_review.py';before=path.read_bytes();after=before
    changes=[
        (b'from signac_catalog import signac_metadata, SIGNAC_WARNING, SIGNAC_ACTIONS\n',b'from signac_catalog import signac_metadata, SIGNAC_WARNING, SIGNAC_ACTIONS\nfrom htseq_catalog import htseq_metadata, HTSEQ_WARNING, HTSEQ_ACTIONS\n'),
        (b'        elif tool_id == "signac":\n            tool.update(signac_metadata(ROOT, DATA))\n',b'        elif tool_id == "signac":\n            tool.update(signac_metadata(ROOT, DATA))\n        elif tool_id == "htseq":\n            tool.update(htseq_metadata(ROOT, DATA))\n'),
        (b'"deseq2", "signac", "favor"',b'"deseq2", "signac", "htseq", "favor"'),
        (b'.write_text(FUMA_WARNING + QUANT_WARNING',b'.write_text(HTSEQ_WARNING + FUMA_WARNING + QUANT_WARNING'),
        (b'    warning_body = FUMA_ACTIONS + QUANT_ACTIONS',b'    warning_body = HTSEQ_ACTIONS + FUMA_ACTIONS + QUANT_ACTIONS'),
        (b"page.replace('<p id=\"overview\"></p>', FUMA_ACTIONS + QUANT_ACTIONS",b"page.replace('<p id=\"overview\"></p>', HTSEQ_ACTIONS + FUMA_ACTIONS + QUANT_ACTIONS")]
    for old,new in changes:assert after.count(old)==1,(old,after.count(old));after=after.replace(old,new)
    record=data/'htseq-catalog-shared-source-compatibility.json';out=ROOT/'build/literature-review/htseq-runtime/catalog-source-intake-1'
    assert not record.exists() and not out.exists();out.mkdir();(out/'render-before-htseq.py').write_bytes(before)
    save(record,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='htseq',
        scope='Verified HTSeq metadata plus required warning/actions at the front of both generated pages. Every prior renderer byte reconstructs. No scientific source waiver or registry reclassification.',paths=[delta('tools/literature/render_review.py',before,after)]))
    assert path.read_bytes()==before;path.write_bytes(after)
    print('Wired verified HTSeq metadata and prominent recovery sections; all prior renderer bytes preserved by inverse')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source-preservation checks')
    main()
