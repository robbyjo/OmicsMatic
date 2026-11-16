#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate an owned synthetic paired BAM for actual resource/parent-death controls."""
from pathlib import Path
import argparse,gzip,hashlib,importlib.util,json,struct

ROOT=Path(__file__).resolve().parents[3]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--pairs',type=int,default=100000);a=p.parse_args()
    if not 1000<=a.pairs<=500000:raise ValueError('Require1000..500000 synthetic pairs')
    a.out.mkdir(parents=True,exist_ok=False)
    source=ROOT/'tools/literature/sambamba_validation/make_cases.py'
    spec=importlib.util.spec_from_file_location('sambamba_independent_fixture',source);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    header=m.HEADER.encode();raw=bytearray(b'BAM\1'+struct.pack('<i',len(header))+header+struct.pack('<ii',1,5)+b'chr1\0'+struct.pack('<i',10000))
    for first in [True,False]:
        for i in range(a.pairs):
            r=m.read('stress_%08d'%i,1000 if first else 2000,40 if i==0 else 20,99 if first else 147,
                mate=2000 if first else 1000,tlen=1010 if first else -1010)
            raw.extend(m.record(r))
    bam=m.bgzf(raw);dest=a.out/'stress.bam';dest.write_bytes(bam)
    proof=dict(status='INDEPENDENT_SYNTHETIC_PAIRED_STRESS_INPUT_NOT_ANALYSIS',pairs=a.pairs,records=a.pairs*2,
        expected_unique_best_pair='stress_00000000',expected_duplicate_records=2*(a.pairs-1),
        decoded_bytes=len(raw),compressed_bytes=len(bam),sha256=hashlib.sha256(bam).hexdigest(),
        fixture_writer_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),biological_data=False)
    (a.out/'INPUT.json').write_bytes((json.dumps(proof,indent=2)+'\n').encode());print(json.dumps(proof),flush=True)
if __name__=='__main__':main()
