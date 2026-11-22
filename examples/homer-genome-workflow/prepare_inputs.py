#!/usr/bin/env python3
"""Create new synthetic HOMER genomic example inputs; never overwrite a directory."""
import argparse
import hashlib
import json
from pathlib import Path
import random

SEED = 20261028
VARIANTS = ('given-gc-normalized', 'numeric-cpg-forward', 'asymmetric-masked-overlap', 'known-only', 'denovo-only')


def prepare(out):
    out = Path(out).absolute()
    out.mkdir(parents=True, exist_ok=False)
    rng = random.Random(SEED)
    dna = list(''.join(rng.choice('ACGT') for _ in range(200000)))
    for role, count, first in [('targets', 64, 1000), ('background', 128, 80000)]:
        rows = []
        for i in range(count):
            start = first + i * 500
            gc = [.25, .4, .5, .6, .75][i % 5]
            seq = ''.join(rng.choice('GC') if rng.random() < gc else rng.choice('AT') for _ in range(120))
            if role == 'targets' and i < 48:
                seq = seq[:35] + 'ACGTACGA' + seq[43:]
            dna[start:start + 120] = seq
            rows.append(f'chr1\t{start}\t{start+120}\t{"T" if role == "targets" else "B"}{i+1:04d}\t1\t{"-" if i%3 == 0 else "+"}\n')
        (out/(role+'.bed')).write_text(''.join(rows), encoding='utf-8', newline='\n')

    def fasta(name, values):
        (out/name).write_text('>chr1 synthetic\n' + ''.join(''.join(values[i:i+80])+'\n' for i in range(0,len(values),80)), encoding='utf-8', newline='\n')

    fasta('genome.fa', dna)
    masked = list(dna)
    for start,end in [(2800,3300),(80900,81300)]:
        masked[start:end] = [b.lower() for b in masked[start:end]]
    fasta('mask-genome.fa', masked)
    (out/'models.motif').write_text(''.join('>'+seq+'\t'+name+'\t10\n'+''.join('\t'.join('.97' if b == base else '.01' for b in 'ACGT')+'\n' for base in seq) for seq,name in [('ACGTACGA','implanted'),('CCCCCCCC','polyC')]), encoding='utf-8', newline='\n')
    homer = []
    for line in (out/'targets.bed').read_text().splitlines():
        f = line.split('\t')
        homer.append(f'{f[3]}\t{f[0]}\t{int(f[1])+1}\t{f[2]}\t{"1" if f[5] == "-" else "0"}\t{f[4]}\t0.125\n')
    (out/'targets.homer').write_text(''.join(homer), encoding='utf-8', newline='\n')
    (out/'overlapping-background.bed').write_text((out/'background.bed').read_text()+'chr1\t1000\t1120\tbackground overlaps first target\t1\t+\n', encoding='utf-8', newline='\n')
    base = {
        'genome': str(out/'genome.fa'), 'reference-build': 'synthetic-seed-20261028',
        'peaks': str(out/'targets.bed'), 'peak-format': 'bed6',
        'background': str(out/'background.bed'), 'background-format': 'bed6',
        'background-mode': 'supplied', 'size': 'given', 'unknown-strand': 'error',
        'duplicate-ids': 'error', 'alphabet': 'strict-dna', 'mask-lowercase': 'no',
        'background-overlap': 'remove-original', 'weighting': 'gc', 'strands': 'both',
        'statistic': 'binomial', 'known-motifs': str(out/'models.motif'),
        'comparison-motifs': str(out/'models.motif'), 'motif-length': '8',
        'mismatches': '1', 'motifs-to-find': '1', 'normalization-order': '2',
    }
    for variant in VARIANTS:
        options = dict(base)
        if variant in ('numeric-cpg-forward', 'denovo-only'):
            options.update({'peaks':str(out/'targets.homer'),'peak-format':'homer','size':'200',
                'weighting':'cpg','normalization-order':'0','strands':'forward','statistic':'hypergeometric',
                'background-overlap':'retain','known-strand-policy':'original-wrapper'})
        if variant == 'asymmetric-masked-overlap':
            options.update({'genome':str(out/'mask-genome.fa'),'background':str(out/'overlapping-background.bed'),
                'size':'-50,150','mask-lowercase':'yes','normalization-order':'1'})
        if variant == 'known-only':
            options['analysis'] = 'known'
            for key in ('comparison-motifs','motif-length','mismatches','motifs-to-find'):
                options.pop(key,None)
        if variant == 'denovo-only':
            options['analysis'] = 'denovo'
            options.pop('known-motifs')
            options.pop('known-strand-policy')
        (out/(variant+'.json')).write_text(json.dumps(options,indent=2)+'\n',encoding='utf-8',newline='\n')
    hashes = {p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.iterdir())}
    (out/'provenance.json').write_text(json.dumps(dict(seed=SEED,targets=64,background=128,
        synthetic=True,files=hashes,scope='Synthetic technical checks only; no paper dataset or biological calibration.'),indent=2)+'\n',encoding='utf-8',newline='\n')
    return out


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    print(prepare(parser.parse_args().out))
