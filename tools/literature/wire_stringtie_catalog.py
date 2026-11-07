# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add the exact StringTie provider/warning wiring and retain a reversible renderer-only source delta."""
import base64
import difflib
import gzip
import json
from pathlib import Path
from catalog_source_integrity import digest,reverse_delta

ROOT=Path(__file__).resolve().parents[2]
def main():
    path=ROOT/'tools/literature/render_review.py';before=path.read_bytes();after=before
    changes=[
      (b'from bigwig_catalog import bigwig_metadata, BIGWIG_WARNING, BIGWIG_ACTIONS\n',b'from bigwig_catalog import bigwig_metadata, BIGWIG_WARNING, BIGWIG_ACTIONS\nfrom stringtie_catalog import stringtie_metadata, STRINGTIE_WARNING, STRINGTIE_ACTIONS\n',1),
      (b'        elif tool_id == "ucsc-bedgraphtobigwig":\n            tool.update(bigwig_metadata(ROOT, DATA))\n',b'        elif tool_id == "ucsc-bedgraphtobigwig":\n            tool.update(bigwig_metadata(ROOT, DATA))\n        elif tool_id == "stringtie":\n            tool.update(stringtie_metadata(ROOT, DATA))\n',1),
      (b'"ucsc-bedgraphtobigwig", "favor"',b'"ucsc-bedgraphtobigwig", "stringtie", "favor"',1),
      (b'BIGWIG_WARNING + FASTP_WARNING',b'BIGWIG_WARNING + STRINGTIE_WARNING + FASTP_WARNING',1),
      (b'BIGWIG_ACTIONS + FASTP_ACTIONS',b'BIGWIG_ACTIONS + STRINGTIE_ACTIONS + FASTP_ACTIONS',2)]
    for old,new,count in changes:assert after.count(old)==count,(old,after.count(old));after=after.replace(old,new)
    restored=after
    for old,new,count in reversed(changes):assert restored.count(new)==count;restored=restored.replace(new,old)
    assert restored==before;edits=[];a,b=before.splitlines(keepends=True),after.splitlines(keepends=True)
    for tag,i,j,k,l in difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes():
        if tag=='equal':continue
        old,new=b''.join(a[i:j]),b''.join(b[k:l]);edits.append(dict(after_start_line=k,after_end_line=l,after_chunk_sha256=digest(new),before_bytes=len(old),before_chunk_sha256=digest(old),before_gzip_base64=base64.b64encode(gzip.compress(old,mtime=0)).decode()))
    row=dict(path='tools/literature/render_review.py',before_sha256=digest(before),after_sha256=digest(after),before_bytes=len(before),after_bytes=len(after),edits=edits);assert reverse_delta(after,row)==before
    saved=ROOT/'build/literature-review/stringtie-runtime/render-before-stringtie.py';record=ROOT/'docs/literature/omics-2023-2026/stringtie-catalog-shared-source-compatibility.json';assert not saved.exists() and not record.exists();saved.write_bytes(before)
    record.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='stringtie',scope='Only the StringTie verified metadata import/branch and prominent warning/action wiring. Removing the exact insertions/replacements reconstructs every prior renderer byte. Scientific source checks remain exact.',paths=[row]),indent=2)+'\n',encoding='utf-8',newline='\n');assert path.read_bytes()==before;path.write_bytes(after);print('Exact StringTie renderer-only inverse verified and saved.')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact renderer reconstruction')
    main()
