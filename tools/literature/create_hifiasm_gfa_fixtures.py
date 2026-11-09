# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently index every completed original graph view without admitting or relabeling its parent control batch."""
import argparse,hashlib,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);original=a.original.resolve();cases=[];selected=[]
    for manifest in sorted(original.rglob('original-output-files.json')):
        expected=json.loads(manifest.read_bytes());home=manifest.parent/'assembly';selected.append(manifest.relative_to(ROOT).as_posix())
        for name,want in expected.items():
            if not name.endswith('.gfa'):continue
            file=home/name;assert pin(file)==want;segments=[];links=[];placements=[];lengths=[];rawlines=[];sequence_bases=0;fasta=bytearray();omitted=[]
            for line in file.read_text(encoding='utf8').splitlines():
                rawlines.append(line);fields=line.split('\t')
                if fields[0]=='S':
                    tags=fields[3:];length=int(next(t.split(':',2)[2] for t in tags if t.startswith('LN:i:')));sequence=fields[2];assert sequence=='*' or len(sequence)==length;lengths.append(length);segments.append(dict(id=fields[1],sequence_sha256=hashlib.sha256(sequence.encode()).hexdigest(),length=length,tags=tags))
                    if sequence=='*':omitted.append(fields[1])
                    else:
                        sequence_bases+=len(sequence);fasta.extend(('>'+fields[1]+'\n').encode());parts=[sequence[i:i+60] for i in range(0,len(sequence),60)] or [''];fasta.extend(('\n'.join(parts)+'\n').encode())
                elif fields[0]=='L':links.append(dict(source=fields[1],source_strand=fields[2],target=fields[3],target_strand=fields[4],cigar=fields[5],tags=fields[6:]))
                elif fields[0]=='A':
                    gap=fields[3:7]==['*']*4;placements.append(dict(segment=fields[1],offset=int(fields[2]),strand=fields[3],read=fields[4],start=None if gap else int(fields[5]),end=None if gap else int(fields[6]),gap=gap,tags=fields[7:]))
                else:assert not line or line.startswith('#') or fields[0]=='H',fields[0]
            n50=l50=None;total=sum(lengths);acc=0
            if total:
                for i,value in enumerate(sorted(lengths,reverse=True)):
                    acc+=value
                    if acc>=(total+1)//2:n50=value;l50=i+1;break
            cases.append(dict(path=file.relative_to(ROOT).as_posix(),pin=want,segments=segments,links=links,placements=placements,stats=dict(segments=len(segments),sequence_bearing=len(segments)-len(omitted),declared_bases=total,sequence_bases=sequence_bases,maximum=max(lengths) if lengths else None,n50=n50,l50=l50),fasta=dict(sha256=hashlib.sha256(fasta).hexdigest(),bytes=len(fasta),omitted=omitted),source_lines_sha256=hashlib.sha256(('\n'.join(rawlines)+('\n' if rawlines else '')).encode()).hexdigest()))
    assert cases and sum(len(c['links']) for c in cases)>0,'Require actual source links, not only sequence smoke tests';save(out/'cases.json',dict(schema_version=1,cases=cases));save(out/'COMPLETED.json',dict(status='prepared-complete-retained-hifiasm-graph-components-only',graph_views=len(cases),completed_source_case_manifests=selected,segment_rows=sum(len(c['segments']) for c in cases),link_rows=sum(len(c['links']) for c in cases),placement_rows=sum(len(c['placements']) for c in cases),cases=pin(out/'cases.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},original_batch_relabelled=False,full_adapter_admitted=False,implementation_credit=False,analysis_admitted=False,whole_goal_complete=False));print('Indexed',len(cases),'complete original graph views; parent batches unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original graph binding checks')
    main()
