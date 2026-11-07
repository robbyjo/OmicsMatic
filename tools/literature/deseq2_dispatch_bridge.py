# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reconstruct every prior dispatcher byte across only the two DESeq2 route/help insertions."""
import base64
import difflib
import gzip
import json
from pathlib import Path
from catalog_source_integrity import digest,reverse_delta,verify_source
ROOT=Path(__file__).resolve().parents[2]


def main():
    name='src/main/java/org/jlinalg/cli/JLinAlgCli.java';before=ROOT/'build/literature-review/deseq2-runtime/public-workflow-1/JLinAlgCli.before.java';old=before.read_bytes();new=(ROOT/name).read_bytes()
    allowed=[b'            if (arguments.length > 0 && arguments[0].equals("deseq2-tools"))\n                return Deseq2InferenceCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n            if (arguments.length > 0 && arguments[0].equals("deseq2-normalize"))\n                return Deseq2NormalizationCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n',b'              java -jar omicsmatic-<version>.jar deseq2-tools --help\n              java -jar omicsmatic-<version>.jar deseq2-normalize --help\n']
    a,b=old.splitlines(keepends=True),new.splitlines(keepends=True);edits=[]
    for kind,i,j,k,l in difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes():
        if kind=='equal':continue
        original,updated=b''.join(a[i:j]),b''.join(b[k:l]);assert kind=='insert' and not original and updated in allowed;allowed.remove(updated)
        edits.append(dict(after_start_line=k,after_end_line=l,after_chunk_sha256=digest(updated),before_bytes=0,before_chunk_sha256=digest(b''),before_gzip_base64=base64.b64encode(gzip.compress(b'',mtime=0)).decode()))
    assert not allowed and len(edits)==2
    row=dict(path=name,before_sha256=digest(old),after_sha256=digest(new),before_bytes=len(old),after_bytes=len(new),edits=edits);assert reverse_delta(new,row)==old
    data=ROOT/'docs/literature/omics-2023-2026';target=data/'deseq2-dispatch-shared-source-compatibility.json';assert not target.exists()
    target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='deseq2',scope='Only two fixed DESeq2 command routes and their help lines. Every prior dispatcher byte reconstructs; no scientific source waiver or implementation credit.',paths=[row]),indent=2)+'\n',encoding='utf-8',newline='\n')
    verify_source(ROOT,data,name,digest(old))
    for scientific in ['src/main/java/org/jlinalg/cli/Deseq2InferenceCli.java','src/main/java/org/jlinalg/sequence/Deseq2SizeFactors.java']:
        try:verify_source(ROOT,data,scientific,'0'*64)
        except ValueError:pass
        else:raise AssertionError('Scientific source mismatch accepted')
    print('Exact DESeq2 dispatcher inverse passed; scientific mismatches remain rejected')


if __name__=='__main__':main()
