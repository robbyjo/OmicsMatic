# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add one root route/help line with an exact inverse; never waive scientific source checks."""
import argparse
import base64
import difflib
import gzip
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from catalog_source_integrity import digest, reverse_delta, verify_source


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    source=ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java';before=source.read_bytes()
    parent=json.loads((ROOT/'build/literature-review/meme-runtime/root-build-17/COMPLETED.json').read_bytes())
    relative=source.relative_to(ROOT).as_posix();assert pin(source)==parent['source_pins'][relative]
    data=ROOT/'docs/literature/omics-2023-2026';sums=data/'SHA256SUMS'
    (out/'JLinAlgCli-before.java').write_bytes(before);(out/'SHA256SUMS-before').write_bytes(sums.read_bytes())
    eol=b'\r\n' if b'\r\n' in before else b'\n'
    anchor=b'            if (arguments.length > 0 && arguments[0].equals("meme-tools"))'+eol+b'                return MemeToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);'+eol
    assert before.count(anchor)==1
    addition=b'            if (arguments.length > 0 && arguments[0].equals("chipseeker-tools"))'+eol+b'                return ChipSeekerToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);'+eol
    after=before.replace(anchor,anchor+addition)
    help_line=b'              java -jar omicsmatic-<version>.jar meme-tools --help'+eol
    assert after.count(help_line)==1
    after=after.replace(help_line,help_line+b'              java -jar omicsmatic-<version>.jar chipseeker-tools --help'+eol)
    old_lines=before.splitlines(keepends=True);new_lines=after.splitlines(keepends=True);edits=[]
    for tag,a,b,c,d in difflib.SequenceMatcher(a=old_lines,b=new_lines,autojunk=False).get_opcodes():
        if tag=='equal':continue
        assert tag=='insert';old=b''.join(old_lines[a:b]);new=b''.join(new_lines[c:d])
        edits.append(dict(after_start_line=c,after_end_line=d,after_chunk_sha256=digest(new),before_bytes=len(old),before_chunk_sha256=digest(old),before_gzip_base64=base64.b64encode(gzip.compress(old,mtime=0)).decode('ascii')))
    row=dict(path=relative,before_sha256=digest(before),after_sha256=digest(after),before_bytes=len(before),after_bytes=len(after),edits=edits)
    assert len(edits)==2 and reverse_delta(after,row)==before
    compatibility=data/'chipseeker-dispatch-shared-source-compatibility.json';assert not compatibility.exists()
    save(compatibility,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='chipseeker',
        scope='Only one chipseeker-tools root route and help line. Every previous root byte reconstructs exactly. No scientific implementation, review, inventory status or release availability is changed by this dispatch record.',paths=[row]))
    source.write_bytes(after);verify_source(ROOT,data,relative,digest(before))
    names=[line.split('  ',1)[1] for line in sums.read_text(encoding='utf-8').splitlines()]
    actual={p.name:p for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    order=[n for n in names if n in actual]+sorted(actual.keys()-set(names))
    sums.write_text(''.join(pin(actual[n])['sha256']+'  '+n+'\n' for n in order),encoding='utf-8',newline='\n')
    save(out/'COMPLETED.json',dict(status='wired-one-root-dispatch-and-exact-metadata-inverse-not-workflow-admission',source=pin(source),
        compatibility=pin(compatibility),checksums=pin(sums),restored_original_sha256=digest(before),changed_scientific_sources=False,implementation_credit=False))
    print('Added root route/help and exact inverse; scientific guards and implementation counts unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact dispatch inverse checks')
    main()
