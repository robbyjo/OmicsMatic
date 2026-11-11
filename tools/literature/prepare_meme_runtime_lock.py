# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pin original MEME source/build schema and complete independent Tomtom health references."""
import hashlib
import json
from pathlib import Path
import tarfile
import zipfile

ROOT=Path(__file__).resolve().parents[2]
ARCHIVE_SHA='0406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12'


def pin(path):
    with path.open('rb') as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())


def main():
    archive=ROOT/'build/literature-review/fimo-runtime/meme-5.5.9.tar.gz';assert pin(archive)==dict(bytes=56535171,sha256=ARCHIVE_SHA)
    source={}
    with tarfile.open(archive,'r:gz') as z:
        for member in z:
            if member.isfile() and member.name.endswith(('.c','.h')):
                name=member.name.removeprefix('meme-5.5.9/');assert name!=member.name and name not in source
                raw=z.extractfile(member).read();source[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    build=json.loads((ROOT/'build/literature-review/meme-runtime/linux-build-3/COMPLETED.json').read_bytes());assert source==build['original_C_and_H'] and len(source)==615
    builder=ROOT/'tools/literature/build_meme_linux_reference.py';assert pin(builder)==build['script_pin']
    target=ROOT/'src/main/resources/org/jlinalg/omics/meme-runtime-setup';assert not target.exists();target.mkdir(parents=True)
    fixture=ROOT/'src/test/resources/org/jlinalg/sequence/tomtom-contracts/original-comparison-contracts.zip';assert pin(fixture)['sha256']=='62a6e0090ab1a6cbddc4ef2f0bbb5936863a04601efd66e27ba686bab6c7b765'
    with zipfile.ZipFile(fixture) as z:
        raw=z.read('base/inputs/motifs.meme');(target/'health-motifs.meme').write_bytes(raw)
        health=[]
        for label,distance,reverse in [('ed-forward','ed',False),('kullback-both','kullback',True)]:
            member='base/tomtom-'+label+'/result/tomtom.tsv';table=z.read(member);text=table.decode();lines=text.splitlines();rows=[]
            assert lines[0].startswith('Query_ID\tTarget_ID\t')
            for line in lines[1:]:
                if line and not line.startswith('#'):rows.append(line.split('\t'))
            assert len(rows)==9 and all(len(row)==10 for row in rows)
            health.append(dict(label=label,distance=distance,reverse_complement=reverse,source_member=member,source_table=dict(bytes=len(table),sha256=hashlib.sha256(table).hexdigest()),rows=rows))
        (target/'UPSTREAM-COPYING').write_bytes(z.read('UPSTREAM-COPYING'))
    spec=dict(schema_version=1,version='5.5.9',motif_file='health-motifs.meme',motif_pin=pin(target/'health-motifs.meme'),cases=health,
              scope='Two original complete fixed9-pair synthetic numerical/reference tables, all ten original columns retained. No query/target models derived from output. Tiny databases are not P calibration or biological accuracy certificates. Native admission must run fresh on current host; copied records are not tickets.')
    (target/'health-reference.json').write_text(json.dumps(spec,indent=2)+'\n',encoding='utf-8',newline='\n')
    steps=[]
    for row in build['steps']:
        command=[token.replace(build['prefix'],'@HOME@').replace(build['linux_area'],'@AREA@') for token in row['command']]
        cwd=row['cwd'].replace(build['linux_area'],'@AREA@');steps.append(dict(name=row['name'],command=command,cwd=cwd))
    lock=dict(schema_version=1,profile='linux-x86_64-tomtom-5.5.9',version='5.5.9',original_archive=pin(archive),source_url=build['source_url'],reference_builder=pin(builder),original_C_and_H=source,expected_build_steps=steps,
              health_reference=pin(target/'health-reference.json'),health_motifs=pin(target/'health-motifs.meme'),upstream_copying=pin(target/'UPSTREAM-COPYING'),
              scope='Exact current source/build-evidence schema and original health references. Source provenance/integrity is not a signature, runtime admission or user analysis. MEME Suite educational/research/nonprofit terms apply; commercial permission is separate.')
    resource=target.parent/'meme-runtime-lock.json';assert not resource.exists();resource.write_text(json.dumps(lock,indent=2)+'\n',encoding='utf-8',newline='\n')
    (target/'.gitattributes').write_text('* -text\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(lock=pin(resource),health_reference=lock['health_reference'],health_motifs=lock['health_motifs'],C_and_H=len(source))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source and whole-reference checks')
    main()
