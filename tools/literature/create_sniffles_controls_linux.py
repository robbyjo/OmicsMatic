# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create deterministic synthetic long-read BAM controls with declared SVs and HP/PS tags.

The BAM alignments are constructed controls, not an aligner result or biological
benchmark. All original callers still perform their own candidate construction,
clustering, consensus, filtering and genotyping.
"""
import argparse
import hashlib
import json
import random
import sys
from pathlib import Path


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf8', newline='\n')


def main():
    import pysam
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(root / 'build/literature-review/sniffles-runtime')
    out.mkdir(parents=True)
    rng = random.Random(391027)
    sequence = ''.join(rng.choices('ACGT', k=60000))
    insertion = ''.join(rng.choices('ACGT', k=90))
    (out / 'reference.fa').write_text('>chrControl\n' + '\n'.join(sequence[i:i+80] for i in range(0,len(sequence),80)) + '\n', encoding='ascii')
    pysam.faidx(str(out / 'reference.fa'))
    (out / 'tandem-repeats.bed').write_text('chrControl\t8500\t9500\n', encoding='ascii')
    alignments = []
    for sample, offset in [('sampleA', 0), ('sampleB', 8)]:
        header = dict(HD=dict(VN='1.6',SO='coordinate'),SQ=[dict(SN='chrControl',LN=len(sequence))],
                      RG=[dict(ID=sample,SM=sample,PL='PACBIO')])
        bam = out / (sample + '.bam')
        rows = []
        with pysam.AlignmentFile(str(bam), 'wb', header=header) as stream:
            for index in range(40):
                start = 900 + index * 2
                has_variants = index % 2 == 0
                segments = []
                bases = []
                cursor = start
                events = [(4000+offset,'DEL',120),(9000+offset,'INS',90),(14000+offset,'DEL',70)] if has_variants else []
                for position, kind, size in events:
                    segments.append((0,position-cursor));bases.append(sequence[cursor:position]);cursor=position
                    if kind=='DEL':segments.append((2,size));cursor+=size
                    else:segments.append((1,size));bases.append(insertion)
                segments.append((0,22000-cursor));bases.append(sequence[cursor:22000])
                read = pysam.AlignedSegment(stream.header)
                read.query_name = 'mControl/0' + str(index + 1000) + '/0_22000'
                read.flag = 16 if index % 4 < 2 else 0
                read.reference_id=0;read.reference_start=start;read.mapping_quality=60
                read.cigartuples=segments;read.query_sequence=''.join(bases)
                read.query_qualities=pysam.qualitystring_to_array('I'*len(read.query_sequence))
                read.set_tag('RG',sample);read.set_tag('NM',sum(size for _,_,size in events))
                read.set_tag('HP',1 if has_variants else 2);read.set_tag('PS',1001)
                stream.write(read)
                rows.append(dict(read=read.query_name,reference_start_zero_based=start,reverse=bool(read.flag&16),
                                 hp=1 if has_variants else 2,ps=1001,cigar=read.cigarstring,events=events))
        pysam.index(str(bam))
        # Complete original pysam round-trip, including ordering, sequence, tags and CIGARs.
        with pysam.AlignmentFile(str(bam),'rb') as stream:
            back=list(stream.fetch('chrControl'))
            assert len(back)==40
            for read,row in zip(back,rows):
                assert read.query_name==row['read'] and read.cigarstring==row['cigar']
                assert read.reference_start==row['reference_start_zero_based']
                assert read.get_tag('HP')==row['hp'] and read.get_tag('PS')==1001
                assert len(read.query_sequence)==sum(n for op,n in read.cigartuples if op in [0,1,4,7,8])
        alignments.append(dict(sample=sample,bam=pin(bam),index=pin(Path(str(bam)+'.bai')),reads=rows))
    save(out/'truth.json',dict(description='Constructed long-read alignments; no mapping or biology claim',
        reference=pin(out/'reference.fa'),insertion_sequence=insertion,samples=alignments,
        events_are_zero_based_reference_positions=True,SVLEN_deletions_negative_in_v2_VCF=True))
    save(out/'COMPLETED.json',dict(status='PASS_SYNTHETIC_INDEXED_LONG_READ_CONTROL_CREATION_ONLY',
        files={p.name:pin(p) for p in out.iterdir() if p.is_file()},pysam=pysam.__version__,python=sys.version,
        implementation_credit=False,biological_benchmark=False,
        source_pins={Path(__file__).relative_to(root).as_posix():pin(Path(__file__))}))
    print('Created two 40-read indexed BAMs with DEL/INS controls and HP/PS tags',flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete synthetic-alignment checks')
    main()
