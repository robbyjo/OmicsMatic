# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Construct CRAM/BGZF-reference, CSI, empty-call and legacy-DUP probe data.

These are declared synthetic alignments and reference-format controls, not
biological benchmarks. Existing controls and all original raw files are retained.
"""
import argparse,hashlib,json,sys
from pathlib import Path


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')


def main():
    import pysam
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    root=Path(__file__).resolve().parents[2];base=root/'build/literature-review/sniffles-runtime';out=args.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    original=base/'fixtures-1';before={n:pin(original/n) for n in ['reference.fa','sampleA.bam','sampleB.bam']}
    encoding=out/'reference-for-encoding-only.fa';encoding.write_bytes((original/'reference.fa').read_bytes());pysam.faidx(str(encoding))
    with pysam.AlignmentFile(str(original/'sampleA.bam'),'rb') as source:
        header=source.header.to_dict();reads=list(source.fetch(until_eof=True))
    with pysam.AlignmentFile(str(out/'sampleA.cram'),'wc',header=header,reference_filename=str(encoding),format_options=[b'embed_ref=0']) as target:
        for read in reads:target.write(read)
    # This is still fixture construction, before admission or file pinning.
    # The final reference has identical bytes; the CRAM UR encoding path is
    # intentionally absent so older callers must use the declared local cache.
    reference=out/'reference.fa';encoding.rename(reference);Path(str(encoding)+'.fai').rename(Path(str(reference)+'.fai'))
    with pysam.AlignmentFile(str(out/'sampleA.cram'),'rc',reference_filename=str(reference)) as reader:
        cram_header=reader.header.to_dict();roundtrip=list(reader.fetch(until_eof=True))
    assert len(roundtrip)==len(reads)==40 and all(a.query_name==b.query_name and a.cigarstring==b.cigarstring and a.query_sequence==b.query_sequence for a,b in zip(reads,roundtrip))
    assert all(not Path(sq['UR']).exists() for sq in cram_header['SQ'] if sq.get('UR','').startswith('/'))
    with pysam.BGZFile(str(out/'reference-bgzip.fa.bgz'),'wb') as target:target.write(reference.read_bytes())
    pysam.faidx(str(out/'reference-bgzip.fa.bgz'))
    with pysam.FastaFile(str(reference)) as fasta:
        cache=out/'direct-reference-cache';cache.mkdir()
        for name in fasta.references:
            sequence=fasta.fetch(name).upper().encode('ascii');digest=hashlib.md5(sequence,usedforsecurity=False).hexdigest();(cache/digest).write_bytes(sequence)
    with pysam.AlignmentFile(str(out/'no-indels.bam'),'wb',header=header) as target:
        for read in reads:
            if read.get_tag('HP')==2:target.write(read)
    pysam.index(str(out/'no-indels.bam'))
    long_header=json.loads(json.dumps(header));long_header['SQ'][0]['LN']=600000000
    with pysam.AlignmentFile(str(out/'long-contig.bam'),'wb',header=long_header) as target:
        for read in reads:target.write(read)
    pysam.index('-c',str(out/'long-contig.bam'))
    (out/'wrong-contig.bed').write_text('noSuchContig\t0\t50\n',encoding='ascii')
    vcf=(base/'original-controls-2/v107-sampleA/original.vcf').read_text()
    metadata='\n'.join(line for line in vcf.splitlines() if line.startswith('#'))+'\n'
    (out/'known-dup.vcf').write_text(metadata+'chrControl\t9000\tknownDup\tN\t<DUP>\t.\tPASS\tSVTYPE=DUP;SVLEN=1000;END=10000;CHR2=chrControl;STRANDS=-+\tGT:DR:DV\t./.:.:0\n',encoding='utf8',newline='\n')
    assert all(pin(original/n)==value for n,value in before.items())
    save(out/'COMPLETED.json',dict(status='PASS_EXTENDED_SYNTHETIC_SNIFFLES_FORMAT_CONTROL_CREATION_ONLY',
        files={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()},cram_header=cram_header,
        roundtrip_alignments=40,long_contig_length=600000000,no_indel_alignments=20,pysam=pysam.__version__,
        original_inputs_unchanged=before,implementation_credit=False,analysis_admitted=False,
        source_pins={Path(__file__).relative_to(root).as_posix():pin(Path(__file__))}))
    print('Created CRAM with absent encoding UR, BGZF reference, CSI, no-indel BAM and DUP/error probes',flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete constructed-control checks')
    main()
