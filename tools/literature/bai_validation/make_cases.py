#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent, explicitly synthetic BAMs and hand-geometry regional expectations."""
from pathlib import Path
import argparse,hashlib,json,random,re,struct,zlib

OPS={c:i for i,c in enumerate('MIDNSHP=X')}
REFERENCES=[('empty_first',99999),('chrA',536870911),('empty_middle',400000),('chrB',300000),('empty_last',77777)]
EOF=bytes.fromhex('1f8b08040000000000ff0600424302001b0003000000000000000000')

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def row(name,reference,position,cigar='10M',flag=0):
    operations=[(int(n),c) for n,c in re.findall(r'(\d+)([MIDNSHP=X])',cigar)]
    length=sum(n for n,c in operations if c in 'MIS=X')
    span=sum(n for n,c in operations if c in 'MDN=X') if not flag&4 else 1
    return dict(name=name,reference=reference,start=position,flag=flag,cigar=cigar,operations=operations,length=length,end=position+max(1,span))
def reg2bin(start,end):
    if start<0:return 4680
    for shift,base in [(14,4681),(17,585),(20,73),(23,9),(26,1)]:
        if start>>shift==(end-1)>>shift:return base+(start>>shift)
    return 0
def record(r):
    name=r['name'].encode('ascii')+b'\0';length=r['length']
    core=struct.pack('<iiBBHHHiiii',r['reference'],r['start'],len(name),0 if r['flag']&4 else 60,reg2bin(r['start'],r['end']),len(r['operations']),r['flag'],length,-1,-1,0)
    cigar=b''.join(struct.pack('<I',n<<4|OPS[c]) for n,c in r['operations'])
    sequence=bytes([0x11]*(length//2)+([0x10] if length%2 else []))
    body=core+name+cigar+sequence+bytes([30])*length+b'RGZsynthetic\0'
    return struct.pack('<i',len(body))+body
def bgzf(raw,sizes):
    output=bytearray();blocks=[];at=0;index=0
    while at<len(raw):
        count=min(sizes[index%len(sizes)],len(raw)-at);index+=1
        while True:
            decoded=raw[at:at+count];codec=zlib.compressobj(6,zlib.DEFLATED,-15);encoded=codec.compress(decoded)+codec.flush();size=26+len(encoded)
            if size<=65536:break
            count//=2
        blocks.append(dict(compressed=len(output),decoded=at,decoded_bytes=count,compressed_bytes=size))
        output.extend(b'\x1f\x8b\x08\x04'+b'\0'*4+b'\0\xff\x06\0BC\x02\0'+struct.pack('<H',size-1)+encoded+struct.pack('<II',zlib.crc32(decoded),count));at+=count
    blocks.append(dict(compressed=len(output),decoded=at,decoded_bytes=0,compressed_bytes=len(EOF)));output.extend(EOF)
    return bytes(output),blocks
def query_rows(references,records):
    intervals=set()
    for i,(_,length) in enumerate(references):
        intervals.update([(i,1,1),(i,length,length),(i,1,length)])
        for boundary in [16384,32768,131072,1048576,8388608,67108864]:
            if boundary<length:
                intervals.update([(i,boundary,boundary),(i,boundary+1,boundary+1),(i,max(1,boundary-1),boundary+1)])
    for r in records:
        if r['reference']<0:continue
        i=r['reference'];length=references[i][1]
        for p in [r['start'],r['start']+1,r['end'],r['end']+1]:
            if 1<=p<=length:intervals.add((i,p,p))
        intervals.add((i,max(1,r['start']),min(length,r['end']+1)))
    intervals.update([(1,327681,700001),(1,1,700001),(1,16385,500001),(3,32769,100001)])
    rng=random.Random(731904)
    for _ in range(400):
        i=rng.randrange(len(references));length=references[i][1];start=rng.randint(1,length);end=rng.randint(start,length)
        intervals.add((i,start,end))
    for number,(i,start,end) in enumerate(sorted(intervals)):
        names=[r['name'] for r in records if r['reference']==i and not r['flag']&4 and r['start']<end and r['end']>=start]
        yield '\t'.join([str(number),references[i][0],str(start),str(end),','.join(names)])
def write_case(root,name,records,sizes=(32000,)):
    out=root/name;out.mkdir();references=REFERENCES
    assert records==sorted(records,key=lambda r:(r['reference']<0,r['reference'],r['start']))
    header='@HD\tVN:1.6\tSO:coordinate\n'+''.join('@SQ\tSN:'+n+'\tLN:'+str(length)+'\n' for n,length in references)+'@RG\tID:synthetic\tSM:synthetic\tLB:L1\n@PG\tID:independent-bai-fixture\tVN:1\n'
    text=header.encode();raw=b'BAM\1'+struct.pack('<i',len(text))+text+struct.pack('<i',len(references))
    for label,length in references:
        label=label.encode()+b'\0';raw+=struct.pack('<i',len(label))+label+struct.pack('<i',length)
    for r in records:raw+=record(r)
    bam,blocks=bgzf(raw,sizes);(out/'input.bam').write_bytes(bam)
    queries=list(query_rows(references,records));(out/'queries.tsv').write_bytes(('\n'.join(queries)+'\n').encode())
    save(out/'CASE.json',dict(case=name,explicitly_synthetic=True,not_biological_or_upstream_validation=True,references=references,records=records,bam=pin(bam),decoded=pin(raw),blocks=blocks,queries=len(queries),query_scope='ordered mapped records with reference-span overlap, including N and D; placed-unmapped records excluded explicitly'))
def generate(out):
    out.mkdir(parents=True,exist_ok=False)
    wide=[row('first_reference_start',1,0),row('cross_16k',1,16374,'20M'),row('spliced_128k',1,32769,'2M131072N3M'),row('long_gap',1,499999,'1M200000N1M'),row('after_long_gap',1,799999),row('last_bai_coordinate',1,536870901),row('second_reference_start',3,16),row('placed_unmapped',3,32999,'*',4),row('second_reference_middle',3,99999),row('second_reference_last',3,199999)]
    tail=[row('unplaced_first',-1,-1,'*',4),row('unplaced_second',-1,-1,'*',4)]
    write_case(out,'wide_no_tail',wide)
    write_case(out,'wide_unplaced_tail',wide+tail)
    write_case(out,'unplaced_only',tail)
    write_case(out,'empty_references',[])
    cross=[row('spanning_many_members',1,9,'50000M')]+[row('block_cross_'+str(i),1,60000+i*103,'100M') for i in range(150)]
    write_case(out,'cross_bgzf_members',cross,sizes=(61,127,4096,65536))
    save(out/'COMPLETED.json',dict(status='SYNTHETIC_INPUTS_AND_GEOMETRIC_EXPECTATIONS_WRITTEN_NOT_RUNTIME_VALIDATION',cases=sorted(p.name for p in out.iterdir() if p.is_dir()),generator=pin(Path(__file__).read_bytes())))
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();generate(a.out)
