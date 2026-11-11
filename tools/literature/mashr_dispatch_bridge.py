#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve the exact earlier dispatcher; admit only the fixed native mash route/help."""
import argparse
import base64
import difflib
import gzip
import json
from pathlib import Path
from catalog_source_integrity import digest,reverse_delta

ROOT=Path(__file__).resolve().parents[2]


def main():
    if not __debug__:raise RuntimeError('Never disable exact source reconstruction')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--before',required=True,type=Path);args=parser.parse_args()
    before=args.before.resolve();assert before.is_relative_to((ROOT/'build/literature-review/mashr-runtime').resolve())
    relative='src/main/java/org/jlinalg/cli/JLinAlgCli.java';old=before.read_bytes();new=(ROOT/relative).read_bytes();a,b=old.splitlines(keepends=True),new.splitlines(keepends=True)
    permitted=[b'        if (arguments.length > 0 && arguments[0].equals("mashr-tools"))\n            return MashToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n',b'              java -jar omicsmatic-<version>.jar mashr-tools --help\n']
    edits=[]
    for kind,i,j,k,l in difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes():
        if kind=='equal':continue
        old_chunk,new_chunk=b''.join(a[i:j]),b''.join(b[k:l]);assert kind=='insert' and not old_chunk and new_chunk in permitted
        permitted.remove(new_chunk);edits.append(dict(after_start_line=k,after_end_line=l,after_chunk_sha256=digest(new_chunk),before_bytes=0,before_chunk_sha256=digest(b''),before_gzip_base64=base64.b64encode(gzip.compress(b'',mtime=0)).decode()))
    assert not permitted and len(edits)==2
    row=dict(path=relative,before_sha256=digest(old),after_sha256=digest(new),before_bytes=len(old),after_bytes=len(new),edits=edits)
    assert reverse_delta(new,row)==old
    target=ROOT/'docs/literature/omics-2023-2026/mashr-dispatch-shared-source-compatibility.json';assert not target.exists()
    target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='mashr',scope='Adds only the fixed native mashr-tools dispatch and help example; every earlier dispatch/scientific byte reconstructs exactly. Catalog promotion remains pending the complete tool/role gates.',paths=[row]),indent=2)+'\n',encoding='utf-8',newline='\n')
    print('Exact dispatcher inverse verified; no scientific path admitted to compatibility exceptions.')


if __name__=='__main__':main()
