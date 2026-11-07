# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Wire Signac metadata/recovery and the one-observation UI count with exact reversible deltas."""
import base64
import difflib
import gzip
import json
from pathlib import Path
from catalog_source_integrity import digest, reverse_delta

ROOT=Path(__file__).resolve().parents[2]


def delta(path,before,after):
    edits=[]
    a,b=before.splitlines(keepends=True),after.splitlines(keepends=True)
    for tag,i,j,k,l in difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes():
        if tag=='equal':
            continue
        old,new=b''.join(a[i:j]),b''.join(b[k:l])
        edits.append(dict(after_start_line=k,after_end_line=l,after_chunk_sha256=digest(new),before_bytes=len(old),
            before_chunk_sha256=digest(old),before_gzip_base64=base64.b64encode(gzip.compress(old,mtime=0)).decode()))
    row=dict(path=path,before_sha256=digest(before),after_sha256=digest(after),before_bytes=len(before),after_bytes=len(after),edits=edits)
    assert reverse_delta(after,row)==before
    return row


def main():
    record=ROOT/'docs/literature/omics-2023-2026/signac-catalog-shared-source-compatibility.json'
    out=ROOT/'build/literature-review/signac-runtime/catalog-source-intake-1'
    assert not record.exists() and not out.exists()
    path=ROOT/'tools/literature/render_review.py'
    before=path.read_bytes()
    after=before
    changes=[
        (b'from deseq2_catalog import deseq2_metadata, DESEQ2_WARNING, DESEQ2_ACTIONS\n',b'from deseq2_catalog import deseq2_metadata, DESEQ2_WARNING, DESEQ2_ACTIONS\nfrom signac_catalog import signac_metadata, SIGNAC_WARNING, SIGNAC_ACTIONS\n',1),
        (b'        elif tool_id == "deseq2":\n            tool.update(deseq2_metadata(ROOT, DATA))\n',b'        elif tool_id == "deseq2":\n            tool.update(deseq2_metadata(ROOT, DATA))\n        elif tool_id == "signac":\n            tool.update(signac_metadata(ROOT, DATA))\n',1),
        (b'"chromvar", "deseq2", "favor"',b'"chromvar", "deseq2", "signac", "favor"',1),
        (b'DESEQ2_WARNING + FASTP_WARNING',b'DESEQ2_WARNING + SIGNAC_WARNING + FASTP_WARNING',1),
        (b'DESEQ2_ACTIONS + FASTP_ACTIONS',b'DESEQ2_ACTIONS + SIGNAC_ACTIONS + FASTP_ACTIONS',2)]
    for old,new,count in changes:
        assert after.count(old)==count,(old,after.count(old))
        after=after.replace(old,new)
    rows=[delta('tools/literature/render_review.py',before,after)]
    ui=ROOT/'tools/literature/check_review_ui.cjs'
    old_ui=ui.read_bytes()
    assert old_ui.count(b'/2444 tool observations/')==1
    new_ui=old_ui.replace(b'/2444 tool observations/',b'/2445 tool observations/')
    rows.append(delta('tools/literature/check_review_ui.cjs',old_ui,new_ui))
    out.mkdir()
    (out/'render-before-signac.py').write_bytes(before)
    (out/'review-ui-before-signac.cjs').write_bytes(old_ui)
    assert path.read_bytes()==before and ui.read_bytes()==old_ui
    record.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='signac',
        scope='Only Signac verified metadata/warning/action wiring and the P134 recovered-observation overview assertion. Exact inverse reconstructs both prior shared sources. No scientific source waiver.',paths=rows),indent=2)+'\n',encoding='utf8',newline='\n')
    path.write_bytes(after)
    ui.write_bytes(new_ui)
    print('Saved exact renderer/UI inverses; wired Signac metadata and prominent required actions')


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact shared-source guards')
    main()
