#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Write an independent large synthetic paired BAM using bounded BGZF blocks."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,struct

ROOT=Path(__file__).resolve().parents[3]
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--pairs',type=int,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    if not 1<=a.pairs<=20000000:raise ValueError('Use1..20000000 pairs; generation has no biological meaning')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    source=ROOT/'tools/literature/sambamba_validation/make_cases.py';spec=importlib.util.spec_from_file_location('independent_bam_writer',source)
    writer=importlib.util.module_from_spec(spec);spec.loader.exec_module(writer)
    header=writer.HEADER.encode();buffer=bytearray();compressed=hashlib.sha256();decoded=hashlib.sha256();total=0;written=0
    eof=bytes.fromhex('1f8b08040000000000ff0600424302001b0003000000000000000000')
    with (out/'input.bam').open('xb') as stream:
        def write(raw):
            nonlocal total,written
            decoded.update(raw);total+=len(raw);buffer.extend(raw)
            while len(buffer)>=32000:
                block=writer.bgzf(bytes(buffer[:32000]));assert block[-28:]==eof
                block=block[:-28];stream.write(block);compressed.update(block);written+=len(block);del buffer[:32000]
        write(b'BAM\1'+struct.pack('<i',len(header))+header+struct.pack('<ii',1,5)+b'chr1\0'+struct.pack('<i',10000))
        for first in [True,False]:
            def template(quality):
                return writer.record(writer.read('large_000000000',1000 if first else 2000,quality,99 if first else 147,
                    mate=2000 if first else 1000,tlen=1010 if first else -1010))
            high,low=template(40),template(20);name_length=len(b'large_000000000\0')
            prefix,suffix=low[:36],low[36+name_length:]
            for i in range(a.pairs):
                name=('large_%09d'%i).encode()+b'\0'
                assert len(name)==name_length
                write(high if i==0 else prefix+name+suffix)
        if buffer:
            block=writer.bgzf(bytes(buffer));assert block[-28:]==eof
            block=block[:-28];stream.write(block);compressed.update(block);written+=len(block)
        stream.write(eof);compressed.update(eof);written+=len(eof)
    result=dict(status='INDEPENDENT_LARGE_SYNTHETIC_INPUT_NOT_ANALYSIS',pairs=a.pairs,records=2*a.pairs,
        decoded_bytes=total,compressed_bytes=written,decoded_sha256=decoded.hexdigest(),sha256=compressed.hexdigest(),
        unique_best_pair='large_000000000',expected_duplicate_records=2*(a.pairs-1),expected_retained_records=2,
        generator_memory_is_bounded_by_BGZF_block=True,biological_data=False,
        independent_fixture_writer_sha256=hashlib.sha256(source.read_bytes()).hexdigest())
    (out/'INPUT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result),flush=True)
if __name__=='__main__':main()
