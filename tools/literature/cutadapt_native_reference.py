"""Regenerate bounded original Cutadapt5.2 controls; never install a runtime."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import platform
import random
import sys

import cutadapt
import cutadapt.adapters
import cutadapt._align
import cutadapt._kmer_finder
import cutadapt._match_tables
import cutadapt.kmer_heuristic
import cutadapt.qualtrim
from cutadapt.adapters import BackAdapter
from cutadapt.qualtrim import quality_trim_index


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if cutadapt.__version__ != '5.2':
        raise SystemExit('Require original Cutadapt5.2; no substitution or installation')
    if args.out.exists():
        raise SystemExit('Require a new output directory')
    snapshot = Path(__file__).resolve().parents[2] / 'src/main/resources/org/jlinalg/sequence/cutadapt-5.2'
    source_relationship = {}
    for module in [cutadapt.adapters, cutadapt.kmer_heuristic, cutadapt._match_tables]:
        installed = Path(module.__file__).read_text(encoding='utf8')
        pinned = (snapshot / Path(module.__file__).name).read_text(encoding='utf8')
        if installed != pinned:
            raise SystemExit('Original source differs from pinned release: ' + module.__name__)
        source_relationship[module.__name__] = {'normalized_lf_equal': True, 'normalized_lf_sha256': hashlib.sha256(installed.encode()).hexdigest()}
    args.out.mkdir(parents=True)
    rng = random.Random(20261111)
    adapters = ['AGATCGGAAGAGC', 'ACGT', 'AAAACCCC', 'ANCGTN', 'RYAGTN', 'UCGG', 'A', 'NNACNN']
    cases = []
    fixed = ['', 'ACGT', 'AGATCGGAAGAGC', 'TTAGATCGGAAGAGC', 'AGATC', 'NAGATCGGAAGAGC',
             'AGATCGGAAGAGCAGATCGGAAGAGC', 'ACGTACGT', 'AAAA', 'NNNN', 'acgt', 'UUUU']
    for adapter in adapters:
        for rate in [0, .1, .25, 1]:
            for overlap in [1, 3, 20]:
                for sequence in fixed:
                    cases.append((adapter, rate, overlap, sequence, 'I' * len(sequence), 0, 0))
    for _ in range(6000):
        adapter = rng.choice(adapters)
        prefix = ''.join(rng.choices('ACGTN', k=rng.randrange(31)))
        motif = ''.join(rng.choice({'N':'ACGTN','R':'AG','Y':'CT'}.get(c, c)) for c in adapter.replace('U', 'T'))
        motif = motif[:rng.randrange(len(motif) + 1)]
        for _ in range(rng.randrange(3)):
            i = rng.randrange(len(motif) + 1)
            kind = rng.randrange(3)
            if kind == 0:
                motif = motif[:i] + rng.choice('ACGTN') + motif[i:]
            elif i < len(motif) and kind == 1:
                motif = motif[:i] + motif[i + 1:]
            elif i < len(motif):
                motif = motif[:i] + rng.choice('ACGTN') + motif[i + 1:]
        sequence = prefix + motif + (''.join(rng.choices('ACGTN', k=rng.randrange(9))) if rng.random() < .4 else '')
        if rng.random() < .2:
            sequence = sequence.lower()
        quality = ''.join(chr(33 + rng.choice([0, 5, 10, 20, 30, 40])) for _ in sequence)
        cases.append((adapter, rng.choice([0, .1, .25, 1]), rng.choice([1, 3, 20]), sequence, quality, rng.randrange(5), rng.choice([0, 10, 20])))
    lines = ['adapter\terror_rate\toverlap\tsequence\tquality\tcut_left\tquality_cutoff\ttrimmed_sequence\ttrimmed_quality\tmatch']
    for adapter, rate, overlap, sequence, quality, left, cutoff in cases:
        local, q = sequence[left:], quality[left:]
        start, stop = quality_trim_index(q, 0, cutoff, base=33)
        assert start == 0
        local, q = local[:stop], q[:stop]
        original = BackAdapter(adapter, max_errors=rate, min_overlap=overlap).match_to(local)
        if original is None:
            match = '.'
            trimmed, trimmed_q = local, q
        else:
            match = ','.join(map(str, [original.astart, original.astop, original.rstart, original.rstop, original.score, original.errors]))
            trimmed, trimmed_q = local[:original.rstart], q[:original.rstart]
        lines.append('\t'.join(map(str, [adapter, rate, overlap, sequence, quality, left, cutoff, trimmed, trimmed_q, match])))
    payload = ('\n'.join(lines) + '\n').encode('utf8')
    target = args.out / 'cutadapt-native-controls.tsv.gz'
    target.write_bytes(gzip.compress(payload, mtime=0))
    sources = {}
    for module in [cutadapt.adapters, cutadapt._align, cutadapt.qualtrim, cutadapt._kmer_finder, cutadapt._match_tables, cutadapt.kmer_heuristic]:
        sources[module.__name__] = {'path': module.__file__, 'sha256': sha(module.__file__)}
    record = {'source_version': cutadapt.__version__, 'python': sys.version, 'platform': platform.platform(),
              'controls': len(cases), 'seed': 20261111, 'source_modules': sources,
              'pinned_source_relationship': source_relationship,
              'gzip_sha256': sha(target), 'uncompressed_sha256': hashlib.sha256(payload).hexdigest(),
              'scope': 'Actual unchanged original BackAdapter.match_to and quality_trim_index, including the original kmer prefilter. Bounded synthetic controls; no whole-package or biological equivalence.'}
    (args.out / 'VALIDATION.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf8')
    print(json.dumps(record, indent=2))


if __name__ == '__main__':
    main()
