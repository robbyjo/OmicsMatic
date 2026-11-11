# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Wire missing-status Salmon metadata and warnings; reconstruct the exact prior renderer."""
import base64
import difflib
import gzip
import json
from pathlib import Path
from catalog_source_integrity import digest,reverse_delta

ROOT=Path(__file__).resolve().parents[2]


def main():
    relative='tools/literature/render_review.py';path=ROOT/relative;before=path.read_bytes();after=before
    replacements=[
        (b'from nextflow_catalog import nextflow_metadata, NEXTFLOW_WARNING, NEXTFLOW_ACTIONS\n',b'from nextflow_catalog import nextflow_metadata, NEXTFLOW_WARNING, NEXTFLOW_ACTIONS\nfrom salmon_catalog import salmon_metadata, SALMON_WARNING, SALMON_ACTIONS\n',1),
        (b'        elif tool_id == "nextflow":\n            tool.update(nextflow_metadata(ROOT, DATA))\n',b'        elif tool_id == "nextflow":\n            tool.update(nextflow_metadata(ROOT, DATA))\n        elif tool_id == "salmon":\n            tool.update(salmon_metadata(ROOT, DATA))\n',1),
        (b'"mashr", "nextflow", "favor"',b'"mashr", "nextflow", "salmon", "favor"',1),
        (b'NEXTFLOW_WARNING + FASTP_WARNING',b'NEXTFLOW_WARNING + SALMON_WARNING + FASTP_WARNING',1),
        (b'NEXTFLOW_ACTIONS + FASTP_ACTIONS',b'NEXTFLOW_ACTIONS + SALMON_ACTIONS + FASTP_ACTIONS',2),
    ]
    for old,new,count in replacements:assert after.count(old)==count,(old,after.count(old));after=after.replace(old,new)
    restored=after
    for old,new,count in reversed(replacements):assert restored.count(new)==count;restored=restored.replace(new,old)
    assert restored==before
    a,b=before.splitlines(keepends=True),after.splitlines(keepends=True);edits=[]
    for kind,i,j,k,l in difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes():
        if kind=='equal':continue
        old,new=b''.join(a[i:j]),b''.join(b[k:l]);edits.append(dict(after_start_line=k,after_end_line=l,after_chunk_sha256=digest(new),before_bytes=len(old),before_chunk_sha256=digest(old),before_gzip_base64=base64.b64encode(gzip.compress(old,mtime=0)).decode()))
    row=dict(path=relative,before_sha256=digest(before),after_sha256=digest(after),before_bytes=len(before),after_bytes=len(after),edits=edits);assert reverse_delta(after,row)==before
    data=ROOT/'docs/literature/omics-2023-2026';record=data/'salmon-native-shared-source-compatibility.json';assert not record.exists()
    saved=ROOT/'build/literature-review/salmon-runtime/render-before.py';assert not saved.exists();saved.write_bytes(before)
    record.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='salmon',scope='Only native-development metadata, missing-status validation note and prominent Salmon warning/action wiring are added. The tool remains missing. Removing exact permitted strings reconstructs every prior renderer byte; no scientific guard is relaxed.',paths=[row]),indent=2)+'\n',encoding='utf-8',newline='\n')
    assert path.read_bytes()==before;path.write_bytes(after);print('Missing-status Salmon metadata wired; exact prior renderer reconstruction verified.')


if __name__=='__main__':main()
