# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit only the typed Nextflow route/help insertion and reconstruct every earlier dispatcher byte."""
import argparse,base64,difflib,gzip,json
from pathlib import Path
from catalog_source_integrity import digest,reverse_delta
ROOT=Path(__file__).resolve().parents[2]
def main():
    if not __debug__:raise RuntimeError('Never disable exact reconstruction checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--before',type=Path,required=True);args=parser.parse_args();before=args.before.resolve();assert before.is_relative_to((ROOT/'build/literature-review/nextflow-runtime').resolve())
    path='src/main/java/org/jlinalg/cli/JLinAlgCli.java';old=before.read_bytes();new=(ROOT/path).read_bytes();a,b=old.splitlines(keepends=True),new.splitlines(keepends=True)
    permitted=[b'            if (arguments.length > 0 && arguments[0].equals("nextflow-tools"))\n                return NextflowToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n',b'              java -jar omicsmatic-<version>.jar nextflow-tools --help\n'];edits=[]
    for kind,i,j,k,l in difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes():
        if kind=='equal':continue
        original,updated=b''.join(a[i:j]),b''.join(b[k:l]);assert kind=='insert' and not original and updated in permitted
        permitted.remove(updated);edits.append(dict(after_start_line=k,after_end_line=l,after_chunk_sha256=digest(updated),before_bytes=0,before_chunk_sha256=digest(b''),before_gzip_base64=base64.b64encode(gzip.compress(b'',mtime=0)).decode()))
    assert not permitted and len(edits)==2
    row=dict(path=path,before_sha256=digest(old),after_sha256=digest(new),before_bytes=len(old),after_bytes=len(new),edits=edits);assert reverse_delta(new,row)==old
    target=ROOT/'docs/literature/omics-2023-2026/nextflow-dispatch-shared-source-compatibility.json';assert not target.exists();target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='nextflow',scope='Adds only the fixed typed Nextflow route before ambient project configuration and one help example. Every earlier dispatch/scientific source byte reconstructs exactly. This does not credit Nextflow or excuse changes to numerical/analysis source.',paths=[row]),indent=2)+'\n',encoding='utf-8',newline='\n');print('Exact Nextflow route/help inverse verified; no scientific-source exception admitted.')
if __name__=='__main__':main()
