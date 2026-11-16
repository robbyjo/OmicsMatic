#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Write NEW independent synthetic BAM/BGZF fixtures; no aligner or markdup is used."""
import argparse,hashlib,json,re,struct,zlib
from pathlib import Path

HEADER=("@HD\tVN:1.6\tSO:coordinate\n@SQ\tSN:chr1\tLN:10000\n"
 "@RG\tID:g1\tSM:synthetic\tLB:L1\n@RG\tID:g2\tSM:synthetic\tLB:L1\n"
 "@RG\tID:g3\tSM:synthetic\tLB:L2\n@PG\tID:fixture\tPN:independent-fixture\tVN:1\n")
OPS={c:i for i,c in enumerate('MIDNSHP=X')};BASES='=ACMGRSVTWYHKDBN'
def bgzf(raw):
    result=bytearray()
    for at in range(0,len(raw),32000):
        block=raw[at:at+32000];codec=zlib.compressobj(6,zlib.DEFLATED,-15)
        data=codec.compress(block)+codec.flush();length=18+len(data)+8
        result.extend(b'\x1f\x8b\x08\x04'+b'\0'*4+b'\0\xff\x06\0BC\x02\0'+struct.pack('<H',length-1))
        result.extend(data);result.extend(struct.pack('<II',zlib.crc32(block),len(block)))
    result.extend(bytes.fromhex('1f8b08040000000000ff0600424302001b0003000000000000000000'))
    return bytes(result)
def read(name,pos,quality=30,flag=0,cigar='10M',rg='g1',mate=-1,tlen=0,tags=b''):
    ops=[(int(n),c) for n,c in re.findall(r'(\d+)([MIDNSHP=X])',cigar)]
    length=sum(n for n,c in ops if c in 'MIS=X')
    return dict(name=name,pos=pos,quality=quality,flag=flag,cigar=cigar,ops=ops,length=length,rg=rg,mate=mate,tlen=tlen,tags=tags.hex())
def record(r):
    mapped=not r['flag']&4;pos=r['pos']-1 if r['pos']>0 else -1
    reference_span=sum(n for n,c in r['ops'] if c in 'MDN=X')
    start=max(0,pos);end=start+max(1,reference_span)-1
    bin_=0
    for shift,offset in [(14,4681),(17,585),(20,73),(23,9),(26,1)]:
        if start>>shift==end>>shift:bin_=offset+(start>>shift);break
    if not mapped:bin_=4680
    name=r['name'].encode('ascii')+b'\0';length=r['length'];mate=r['mate']-1 if r['mate']>0 else -1
    core=struct.pack('<iiBBHHHiiii',0 if mapped else -1,pos,len(name),60 if mapped else 0,bin_,len(r['ops']),r['flag'],length,0 if mate>=0 else -1,mate,r['tlen'])
    cigar=b''.join(struct.pack('<I',(n<<4)|OPS[c]) for n,c in r['ops'])
    bases=bytes([0x11]*(length//2)+([0x10] if length%2 else []))
    quals=bytes([r['quality']]*length)
    aux=(b'RGZ'+r['rg'].encode()+b'\0') if r['rg'] is not None else b''
    body=core+name+cigar+bases+quals+aux+bytes.fromhex(r['tags'])
    return struct.pack('<i',len(body))+body
def write_case(root,name,reads,expected=None,header=HEADER):
    out=root/name;out.mkdir()
    reads=sorted(enumerate(reads),key=lambda x:(x[1]['pos']<1,max(0,x[1]['pos']),x[0]))
    ordered=[r for _,r in reads]
    text=header.encode();raw=b'BAM\1'+struct.pack('<i',len(text))+text+struct.pack('<ii',1,5)+b'chr1\0'+struct.pack('<i',10000)
    raw+=b''.join(record(r) for r in ordered);bam=bgzf(raw);(out/'input.bam').write_bytes(bam)
    manifest=dict(case=name,records=ordered,record_count=len(ordered),input_sha256=hashlib.sha256(bam).hexdigest(),decoded_sha256=hashlib.sha256(raw).hexdigest(),hand_expected_duplicate_names=expected,classification_expectation_is_complete_for_this_case=expected is not None)
    (out/'CASE.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
def generate(root):
    root.mkdir(parents=True,exist_ok=False)
    rich=b'NMi'+struct.pack('<i',2)+b'ZZf'+struct.pack('<I',0x80000000)+b'QNf'+struct.pack('<I',0x7fc12345)+b'XABc'+struct.pack('<I',3)+bytes([253,0,4])
    reads=[read('single_hi',100,40),read('single_lo',100,20),read('same_library_other_RG',100,30,rg='g2'),read('different_library',100,10,rg='g3'),
      read('reverse_hi',200,40,flag=16),read('reverse_lo',200,20,flag=16),read('forward_other_strand',200,5),
      read('soft_hi',500,40,cigar='5S10M'),read('soft_lo',495,20,cigar='15M'),
      read('hard_hi',600,40,cigar='5H10M'),read('hard_lo',595,20),
      read('reverse_soft_hi',700,40,flag=16,cigar='10M5S'),read('reverse_soft_lo',705,20,flag=16),
      read('tags_hi',800,40,tags=rich+b'RXZAAA\0'),read('tags_lo',800,20,tags=rich+b'RXZCCC\0')]
    write_case(root,'single_clipping_libraries_tags',reads,['single_lo','same_library_other_RG','reverse_lo','soft_lo','hard_lo','reverse_soft_lo','tags_lo'])
    pairs=[]
    for name,q in [('pair_hi',40),('pair_lo',20)]:pairs += [read(name,300,q,99,mate=400,tlen=110),read(name,400,q,147,mate=300,tlen=-110)]
    write_case(root,'paired_quality',pairs,['pair_lo','pair_lo'])
    fragment=pairs[:2]+[read('fragment_high_quality',300,60)]
    write_case(root,'paired_and_fragment',fragment,['fragment_high_quality'])
    old=[read('primary_old_duplicate',100,30,1024),read('secondary_old_duplicate',200,30,1280),
      read('supplementary_old_duplicate',300,30,3072),read('secondary_clean',400,30,256),
      read('unmapped_old_duplicate',0,30,1028,cigar='*')]
    write_case(root,'preexisting_flags',old,['secondary_old_duplicate','supplementary_old_duplicate'])
    write_case(root,'missing_read_group',[read('no_rg_hi',100,40,rg=None),read('no_rg_lo',100,20,rg=None)],['no_rg_lo'])
    write_case(root,'quality_tie',[read('tie_first',100,40),read('tie_second',100,40)],None)
    write_case(root,'missing_base_qualities',[read('known_quality',100,40),read('missing_quality',100,255)],None)
    write_case(root,'empty',[],[])
    (root/'COMPLETED.json').write_text(json.dumps(dict(status='INDEPENDENT_SYNTHETIC_BAM_FIXTURES_WRITTEN_NOT_UPSTREAM_VALIDATION',cases=sorted(p.name for p in root.iterdir() if p.is_dir())),indent=2)+'\n')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();generate(a.out)
