# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add only the Signac root route/help, preserving every existing dispatcher byte by inverse."""
import base64
import difflib
import gzip
from setup_deseq2_reference import ROOT,save
from catalog_source_integrity import digest,reverse_delta,verify_source


def main():
    name='src/main/java/org/jlinalg/cli/JLinAlgCli.java';path=ROOT/name;before=path.read_bytes()
    route=b'            if (arguments.length > 0 && arguments[0].equals("signac-tools"))\n                return SignacToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
    marker=b'            ProjectConfiguration.Resolved config = ProjectConfiguration.resolve(arguments,\n'
    help_marker=b'              java -jar omicsmatic-<version>.jar deseq2-normalize --help\n'
    assert before.count(marker)==before.count(help_marker)==1 and route not in before
    after=before.replace(marker,route+marker).replace(help_marker,help_marker+b'              java -jar omicsmatic-<version>.jar signac-tools --help\n')
    a,b=before.splitlines(keepends=True),after.splitlines(keepends=True);edits=[]
    for kind,i,j,k,l in difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes():
        if kind=='equal':continue
        old,new=b''.join(a[i:j]),b''.join(b[k:l]);assert kind=='insert' and not old
        edits.append(dict(after_start_line=k,after_end_line=l,after_chunk_sha256=digest(new),before_bytes=0,before_chunk_sha256=digest(b''),before_gzip_base64=base64.b64encode(gzip.compress(b'',mtime=0)).decode()))
    assert len(edits)==2
    row=dict(path=name,before_sha256=digest(before),after_sha256=digest(after),before_bytes=len(before),after_bytes=len(after),edits=edits);assert reverse_delta(after,row)==before
    base=ROOT/'build/literature-review/signac-runtime/tools-source-intake-1';base.mkdir();(base/'JLinAlgCli.before.java').write_bytes(before)
    record=ROOT/'docs/literature/omics-2023-2026/signac-dispatch-shared-source-compatibility.json';assert not record.exists()
    save(record,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='signac',
         scope='Only the fixed Signac workflow root route and its help line; every prior dispatcher byte reconstructs, including unrelated existing work. No scientific source waiver or catalog implementation credit.',paths=[row]))
    assert path.read_bytes()==before;path.write_bytes(after);verify_source(ROOT,record.parent,name,digest(before))
    print('Added Signac route/help with exact dispatcher inverse; unrelated source retained')


if __name__=='__main__':main()
