# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reconstruct the exact earlier dispatcher from only the two fixed Tomtom route/help insertions."""
import argparse
import base64
import difflib
import gzip
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from catalog_source_integrity import digest,reverse_delta,verify_source


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--before',type=Path,required=True);args=parser.parse_args();before=args.before.resolve();assert before.is_relative_to((ROOT/'build/literature-review/meme-runtime').resolve());relative='src/main/java/org/jlinalg/cli/JLinAlgCli.java';old,new=before.read_bytes(),(ROOT/relative).read_bytes();a,b=old.splitlines(keepends=True),new.splitlines(keepends=True)
    permitted=[b'            if (arguments.length > 0 && arguments[0].equals("tomtom-tools"))\n                return TomtomToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n',b'              java -jar omicsmatic-<version>.jar tomtom-tools --help\n'];edits=[]
    for kind,i,j,k,l in difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes():
        if kind=='equal':continue
        original,updated=b''.join(a[i:j]),b''.join(b[k:l]);assert kind=='insert' and not original and updated in permitted;permitted.remove(updated);edits.append(dict(after_start_line=k,after_end_line=l,after_chunk_sha256=digest(updated),before_bytes=0,before_chunk_sha256=digest(b''),before_gzip_base64=base64.b64encode(gzip.compress(b'',mtime=0)).decode()))
    assert not permitted and len(edits)==2;row=dict(path=relative,before_sha256=digest(old),after_sha256=digest(new),before_bytes=len(old),after_bytes=len(new),edits=edits);assert reverse_delta(new,row)==old
    data=ROOT/'docs/literature/omics-2023-2026';target=data/'tomtom-dispatch-shared-source-compatibility.json';assert not target.exists();target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='tomtom',scope='Only the fixed typed Tomtom route and one help entry. Every earlier dispatcher/source byte reconstructs exactly; this does not waive scientific source hashes or itself grant integration credit.',paths=[row]),indent=2)+'\n',encoding='utf-8',newline='\n');verify_source(ROOT,data,relative,digest(old))
    for name in ['src/main/java/org/jlinalg/cli/TomtomWorkflow.java','src/main/java/org/jlinalg/sequence/TomtomResults.java']:
        try:verify_source(ROOT,data,name,'0'*64)
        except ValueError:pass
        else:raise AssertionError('Incorrect scientific source hash accepted: '+name)
    print('Exact Tomtom route/help inverse verified; scientific hash mismatches remain rejected.')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact dispatcher reconstruction')
    main()
