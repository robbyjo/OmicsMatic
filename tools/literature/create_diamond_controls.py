# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create synthetic protein, translated-query and source-option controls; no paper data."""
import argparse, copy, gzip, json, random
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]


def save(path, value):
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8')


def fasta(path, rows):
    path.write_text(''.join('>'+name+'\n'+'\n'.join(seq[i:i+71] for i in range(0, len(seq), 71))+'\n' for name, seq in rows), encoding='utf8')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/diamond-runtime')
    out.mkdir(parents=True)
    rng = random.Random(20261109)
    aa = 'ACDEFGHIKLMNPQRSTVWY'
    protein = lambda n: ''.join(rng.choice(aa) for _ in range(n))
    base, other = protein(240), protein(215)
    reference = [('sp|SYNTH_A|alpha exact protein', base), ('refB insertion target', base[:100]+'GATHK'+base[100:]),
        ('refC independent target', other), ('repeatA', base[:90]+'P'*35+base[130:]),
        ('duplicate-id first copy', base), ('duplicate-id second copy', other)]
    reference += [('tie_'+str(i), base) for i in range(60)]
    queries = [('qExact metadata', base), ('qMutation', base[:60]+'W'*(12)+base[72:]),
        ('qShort', base[30:130]), ('qGap', base[:90]+base[98:]), ('qNoHit', protein(180)),
        ('qOther', other), ('qLowComplexity', 'P'*100), ('qDuplicate one', base), ('qDuplicate two', other)]
    fasta(out/'reference.faa', reference)
    fasta(out/'protein.faa', queries)
    fasta(out/'all-vs-all.faa', reference[:6])
    codons = dict(zip(aa, ['GCT', 'TGT', 'GAT', 'GAA', 'TTT', 'GGT', 'CAT', 'ATT', 'AAA', 'CTG',
                          'ATG', 'AAT', 'CCT', 'CAA', 'CGT', 'TCT', 'ACT', 'GTT', 'TGG', 'TAT']))
    dna = lambda seq: ''.join(codons[x] for x in seq)
    rc = lambda seq: seq.translate(str.maketrans('ACGT', 'TGCA'))[::-1]
    translated = [('dnaPlus', dna(base)), ('dnaMinus', rc(dna(other))),
        ('dnaOffset', 'A'+dna(base[20:170])+'CC'), ('dnaNoHit', dna(protein(110)))]
    fasta(out/'translated.fna', translated)
    for mate in [1, 2]:
        reads = [(name+'/'+str(mate), seq if mate == 1 else rc(seq)) for name, seq in translated]
        raw = ''.join('@'+name+'\n'+seq+'\n+\n'+'I'*len(seq)+'\n' for name, seq in reads).encode()
        (out/f'mate{mate}.fq').write_bytes(raw)
        (out/f'mate{mate}.fq.gz').write_bytes(gzip.compress(raw, mtime=0))
    (out/'protein.faa.gz').write_bytes(gzip.compress((out/'protein.faa').read_bytes(), mtime=0))
    long_protein = protein(1400)
    fasta(out/'long-reference.faa', [('longExact', long_protein)])
    fasta(out/'long-query.faa', [('longQuery', long_protein)])
    settings = dict(mode='blastp', sensitivity='sensitive', algorithm='auto', evalue=0.001,
        min_bitscore=None, max_target_seqs=50, top_percent=None, max_hsps=1, min_identity=None,
        query_cover=None, subject_cover=None, no_self_hits=False, matrix='BLOSUM62', gap_open=None,
        gap_extend=None, comp_based_stats=1, masking=None, query_gencode=1,
        block_size=0.01, index_chunks=1, threads=1)
    cases = []

    def case(name, updates=None, query='protein.faa', reference='reference.faa', versions=None):
        s = copy.deepcopy(settings)
        s.update(updates or {})
        cases.append(dict(id=name, reference=reference, queries=[query] if isinstance(query, str) else query,
                          settings=s, versions=versions or ['2.0.0', '2.0.15', '2.1.8']))

    case('protein-sensitive-top50')
    case('protein-default-top50', dict(sensitivity='default'))
    case('protein-default-top1', dict(sensitivity='default', max_target_seqs=1))
    case('all-targets', dict(max_target_seqs=0))
    case('top-range-conflicting-targets', dict(max_target_seqs=1, top_percent=10))
    case('top-range-only', dict(max_target_seqs=None, top_percent=10))
    case('top100-source-boundary', dict(max_target_seqs=1, top_percent=100))
    case('minscore-overrides-evalue', dict(evalue=1e-200, min_bitscore=100))
    case('strict-evalue-source', dict(evalue=1e-200))
    case('query-cover', dict(query_cover=90))
    case('subject-cover', dict(subject_cover=90))
    case('high-identity', dict(min_identity=99))
    case('multiple-hsps', dict(max_hsps=0))
    case('all-vs-all', dict(no_self_hits=False), query='all-vs-all.faa', reference='all-vs-all.faa')
    case('all-vs-all-no-self', dict(no_self_hits=True), query='all-vs-all.faa', reference='all-vs-all.faa')
    case('translated-both-strands', dict(mode='blastx'), query='translated.fna')
    case('translated-fastq-gzip-pair', dict(mode='blastx'), query=['mate1.fq.gz', 'mate2.fq.gz'], versions=['2.0.15', '2.1.8'])
    case('translated-fastq-plain-pair', dict(mode='blastx'), query=['mate1.fq', 'mate2.fq'], versions=['2.0.15', '2.1.8'])
    case('translated-fastq-single', dict(mode='blastx'), query='mate1.fq')
    case('translated-fastq-gzip-single', dict(mode='blastx'), query='mate1.fq.gz')
    case('long-exact-rounded-Evalue', query='long-query.faa', reference='long-reference.faa')
    case('protein-gzip', query='protein.faa.gz')
    case('block-query-ordinals', dict(block_size=0.0000003, max_target_seqs=1))
    case('double-indexed', dict(algorithm='0'))
    case('query-indexed', dict(algorithm='1'))
    case('contiguous-seed', dict(algorithm='ctg'), versions=['2.0.15', '2.1.8'])
    case('unmasked-composition0', dict(masking='0', comp_based_stats=0))
    save(out/'cases.json', cases)
    save(out/'INPUT-PROVENANCE.json', dict(status='SYNTHETIC_DIAMOND_SOURCE_CONTROLS_ONLY', seed=20261109,
        reference_records=len(reference), protein_queries=len(queries), translated_queries=len(translated),
        intentional_duplicate_ids=True, biological_replicates=False, paper_data=False))
    print('Prepared', len(cases), 'source-option families; synthetic data only')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable control preservation checks')
    main()
