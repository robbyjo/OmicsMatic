# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent complete-word integer polynomials audit original MEME sites, matrices, score tails and tilings."""
import argparse
import hashlib
from bisect import bisect_left
from collections import Counter
from decimal import Decimal, ROUND_HALF_EVEN
from fractions import Fraction
from itertools import product
import json
from math import ceil, floor, gcd, log, log2, log10
from pathlib import Path
import xml.etree.ElementTree as ET
BASE = Path(__file__).resolve().parents[2] / 'build/literature-review/meme-runtime'


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')

FOLDERS = ['legacy-discovery-base-2', 'legacy-discovery-patch2-2', 'discovery-original-5.5.4-1',
           'discovery-original-5.5.9-1', 'sites-original-4.9.1-base-1', 'sites-original-4.9.1-patch2-1',
           'sites-original-5.5.4-1', 'sites-original-5.5.9-1']


def reverse(sequence):
    return sequence.translate(str.maketrans('ACGT', 'TGCA'))[::-1]


def rounded(value, digits):
    point = Decimal(str(value))
    return float(point.quantize(Decimal(1).scaleb(point.adjusted() - digits + 1), rounding=ROUND_HALF_EVEN)) if point else 0.


def within(token, value, digits):
    point = Decimal(token)
    # Source values below 1e-300 need no nonzero absolute epsilon substitution.
    quantum = Decimal(1).scaleb(point.adjusted() - digits + 1) if point else Decimal('1e-323')
    return abs(point - Decimal(str(value))) <= quantum / 2 + abs(point) * Decimal('1e-10')


def histogram(rows, weights):
    result = Counter()
    for letters in product(range(4), repeat=len(rows)):
        score = sum(row[b] for row, b in zip(rows, letters))
        mass = 1
        for b in letters:
            mass *= weights[b]
        result[score] += mass
    return result


def lattice(scores, background):
    low = min(map(min, scores)); high = max(map(max, scores))
    assert high > low
    scale = rounded(100 / (high - low), 14)
    scaled = [[floor((v - low) * scale + .5) for v in row] for row in scores]
    fractions = [Fraction(str(rounded(p, 3))) for p in background]
    denominator = 1
    for p in fractions:
        denominator = denominator * p.denominator // gcd(denominator, p.denominator)
    weights = [int(p * denominator) for p in fractions]
    half = len(scores) // 2
    left, right = histogram(scaled[:half], weights), histogram(scaled[half:], weights)
    exact = Counter()
    for a, count_a in left.items():
        for b, count_b in right.items():
            exact[a + b] += count_a * count_b
    tail = 0; total = denominator ** len(scores); tails = [0.] * (len(scores) * 100 + 1)
    for i in range(len(tails) - 1, -1, -1):
        tail += exact[i]
        tails[i] = min(1., float(Fraction(tail, total)))
    return dict(offset=low, scale=scale, scaled=scaled, rounded_background=list(map(float, fractions)),
                tails=tails, exact_denominator=str(total), exact_histogram={str(k): str(v) for k, v in sorted(exact.items())})


def scan(sequence, model, both):
    w = len(model['scaled']); rows = model['scaled']; tails = model['tails']
    values = []
    for start in range(len(sequence) - w + 1):
        word = sequence[start:start + w]
        forward = sum(row['ACGT'.index(b)] for row, b in zip(rows, word))
        back = sum(row['ACGT'.index(b)] for row, b in zip(rows, reverse(word))) if both else -1
        score = max(forward, back)
        values.append(dict(start=start, reverse=back > forward, score=score, p=tails[score]))
    return values


def tiling(sequence, models, both):
    selected = {}; product_p = 1.; scored = 0
    for index, model in enumerate(models):
        sites = scan(sequence, model, both); width = len(model['scaled'])
        if not sites:
            continue
        scored += 1
        for site in sites:
            if site['p'] >= .0001:
                continue
            start = site['start']
            overlapping = sorted(s for s, old in selected.items() if s < start + width and s + old['width'] > start)
            # Source compares right overlaps before left overlaps, each by start.
            ordered = [s for s in overlapping if s >= start] + [s for s in overlapping if s < start]
            overlap_product = 1.; keep = True
            for s in ordered:
                overlap_product *= selected[s]['p']
                if site['p'] >= overlap_product:
                    keep = False
                    break
            if keep:
                for s in overlapping:
                    del selected[s]
                selected[start] = dict(site, motif=index, width=width)
        p = min(s['p'] for s in sites); n = len(sites) * (2 if both else 1)
        sequence_p = p * n if p * n <= .001 else 1 - (1 - p) ** n
        product_p *= sequence_p
    combined = 1. if scored == 0 else 0. if product_p == 0 else product_p * sum((-log(product_p)) ** i / factorial(i) for i in range(scored))
    return [selected[k] for k in sorted(selected)], combined


def factorial(n):
    value = 1
    for i in range(2, n + 1):
        value *= i
    return value


def fasta(path):
    records = []
    for line in path.read_text().splitlines():
        if line.startswith('>'):
            records.append(dict(name=line[1:].split()[0], bases='', weight=1.))
        elif line:
            records[-1]['bases'] += line
    return records


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to(BASE.resolve()) and not out.exists()
    out.mkdir(parents=True)
    families = []; sources = {}; total_sites = 0; total_scans = 0
    for folder in FOLDERS:
        complete = json.loads((BASE / folder / 'COMPLETED.json').read_bytes())
        assert complete['unchanged_inputs_and_runtime']
        xmls = sorted((BASE / folder / 'captured').glob('*/result/meme.xml'))
        if folder in {'legacy-discovery-base-2', 'legacy-discovery-patch2-2'}:
            xmls.append(BASE / folder / 'captured/meme-chip-historical-options/result/meme_out/meme.xml')
        for xml in xmls:
            r = ET.parse(xml).getroot(); model = r.find('model'); train = r.find('training_set')
            if (xml.parent.parent / 'PREPARED-INPUTS.json').exists():
                prepared = json.loads((xml.parent.parent / 'PREPARED-INPUTS.json').read_bytes())
            else:
                mode = model.findtext('type'); both = model.findtext('strands') == 'both'
                fasta_path = (xml.parent.parent / 'seqs-centered') if xml.parent.name == 'meme_out' else (BASE / folder / 'captured/inputs' / (mode + '.fa'))
                prepared = dict(version=r.attrib['version'], mode=mode, both_strands=both, motifs=int(model.findtext('nmotifs')),
                                minw=int(model.findtext('min_width')), maxw=int(model.findtext('max_width')),
                                seed=int(model.findtext('seed')), beta=.01,
                                resolved_background=[.25] * 4, input_records=fasta(fasta_path))
            population = prepared['input_records']; both = prepared['both_strands']; bg = prepared['resolved_background']; beta = prepared['beta']
            source_sequences = train.findall('sequence'); assert len(source_sequences) == len(population)
            for index, (seq, original) in enumerate(zip(source_sequences, population)):
                assert seq.attrib == dict(id='sequence_' + str(index), name=original['name'], length=str(len(original['bases'])), weight=f"{original['weight']:.6f}")
            letters = {x.attrib['id']: x.attrib['symbol'] for x in train.find('alphabet')}
            assert {letters.get(k, '') for k in letters if letters[k] in 'ACGT'} >= set('ACGT')
            motifs = []; score_models = []
            for motif in r.find('motifs'):
                width = int(motif.attrib['width']); counts = [[0.] * 4 for _ in range(width)]; weight = 0.
                sites = motif.findall('./contributing_sites/contributing_site')
                assert len(sites) == int(motif.attrib['sites'])
                for site in sites:
                    ordinal = int(site.attrib['sequence_id'].removeprefix('sequence_')); original = population[ordinal]
                    position = int(site.attrib['position']); reverse_site = site.attrib['strand'] == 'minus'
                    assert both or not reverse_site
                    word = original['bases'][position:position + width]
                    left = original['bases'][max(0, position - 10):position]
                    right = original['bases'][position + width:position + width + 10]
                    if reverse_site:
                        word, left, right = reverse(word), reverse(right), reverse(left)
                    assert ''.join(letters[x.attrib['letter_id']] for x in site.find('site')) == word
                    assert site.findtext('left_flank').strip() == left and site.findtext('right_flank').strip() == right
                    weight += original['weight']
                    for row, b in zip(counts, word):
                        row['ACGT'.index(b)] += original['weight']
                probabilities = [[float(v.text) for v in row] for row in motif.findall('./probabilities/alphabet_matrix/alphabet_array')]
                scores = [[int(v.text) for v in row] for row in motif.findall('./scores/alphabet_matrix/alphabet_array')]
                for i, row in enumerate(counts):
                    for b in range(4):
                        assert abs(probabilities[i][b] - row[b] / weight) <= 5.000001e-7, (folder, xml, motif.attrib['id'], i, b, 'matrix')
                        raw = 100 * log2((row[b] + beta * bg[b]) / (weight + beta) / bg[b])
                        integer = floor(raw + .5) if raw >= 0 else ceil(raw - .5)
                        assert integer == scores[i][b], (folder, xml, motif.attrib['id'], i, b, 'score', integer, scores[i][b])
                computed = lattice(scores, bg); score_models.append(computed)
                for site in sites:
                    ordinal = int(site.attrib['sequence_id'].removeprefix('sequence_')); position = int(site.attrib['position'])
                    observed = scan(population[ordinal]['bases'], computed, both)[position]
                    assert within(site.attrib['pvalue'], observed['p'], 3), (folder, xml, motif.attrib['id'], site.attrib, observed)
                motifs.append(dict(id=motif.attrib['id'], sites=len(sites), total_weight=weight,
                                   independent_counts=counts, score_model=computed))
                total_sites += len(sites)
            scans = r.findall('./scanned_sites_summary/scanned_sites'); assert len(scans) == len(population)
            expected_scans = []
            for index, (seq, original) in enumerate(zip(scans, population)):
                selected, combined = tiling(original['bases'], score_models, both)
                assert seq.attrib['sequence_id'] == 'sequence_' + str(index)
                assert within(seq.attrib['pvalue'], combined, 3), (folder, xml, index, 'combined', seq.attrib['pvalue'], combined)
                assert int(seq.attrib['num_sites']) == len(selected) == len(seq), (folder, xml, index, 'scan-count',len(selected),len(seq))
                for hit, original_hit in zip(seq, selected):
                    assert hit.attrib['motif_id'] == motifs[original_hit['motif']]['id']
                    assert hit.attrib['strand'] == ('minus' if original_hit['reverse'] else 'plus')
                    assert int(hit.attrib['position']) == original_hit['start']
                    assert within(hit.attrib['pvalue'], original_hit['p'], 3)
                expected_scans.append(dict(sequence=index, combined_p=combined, selected=selected)); total_scans += len(selected)
            name = folder + '/' + xml.parent.relative_to(BASE / folder / 'captured').as_posix().removesuffix('/result')
            sources[xml.relative_to(BASE).as_posix()] = pin(xml)
            families.append(dict(name=name, xml=xml.relative_to(BASE).as_posix(), expected=prepared,
                                 motifs=motifs, scans=expected_scans, source_xml=pin(xml)))
            print(name + ': complete sites/matrices/scores/tiling match', flush=True)
    (out / 'AUDITOR-SOURCE.py').write_bytes(Path(__file__).read_bytes())
    write(out / 'COMPLETED.json', dict(status='passed-independent-original-MEME-site-matrix-score-and-tiling-audit',
          families=families, source_pins=sources, auditor=pin(Path(__file__)), contributing_sites=total_sites,
          scanned_hits=total_scans, original_families=len(families), runtime_admitted=False, implementation_credit=False,
          scope='Independent complete-word integer polynomial distributions on printed centibit matrices, exact rational rounded backgrounds, independently reconstructed site counts/prior scores, both-strand best scoring and complete nonoverlap tilings. Scientific EM fit, motif significance and original report/process admission remain separate.'))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original scientific audit checks')
    main()
