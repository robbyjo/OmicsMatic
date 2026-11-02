#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fresh original-v14 controls. Linux/Perl, exact source, NEW output; no installation.

Run privately with --source ORIGINAL --out NEW_DIRECTORY. The original script can
exit zero on invalid input: every output/error stream remains evidence, not an
admission certificate. Native safety departures are explicitly labelled.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

SOURCE_SHA256 = '2b419417e304e9a63129056d61fa513a4c5834257a7b162cc4389c3ac53e23f2'
TABLES = [1,2,3,4,5,6,9,10,11,12,13,14,15,16,21,22,23]
BASES = 'TCAG'


def patterns(source):
    text = source.decode('ascii').split('sub pn2codon', 1)[1]
    result = {}
    for match in re.finditer(r'(?:if|elsif)\s*\(\$codontable == (\d+)\).*?%p2c = \((.*?)\n\s*\);', text, re.S):
        rows = re.findall(r'"([^"\n]+)"\s*=>\s*"([^"\n]+)"', match[2])
        assert len(rows) == 24
        result[int(match[1])] = dict(rows)
    assert sorted(result) == TABLES
    return result


def definitions(codes):
    cases = []
    def add(name, peptide, dna, table=1, flags=(), selection=None, comparison='exact-rows', pairing='EXACT_ID'):
        cases.append(dict(name=name, protein=peptide, dna=dna, table=table, flags=list(flags),
                          selection=selection, comparison=comparison, pairing=pairing))
    codons = [a+b+c for a in BASES for b in BASES for c in BASES]
    for table in TABLES:
        peptide = ''
        for codon in codons:
            aa = [aa for aa in 'ACDEFGHIKLMNPQRSTVWY*' if re.fullmatch(codes[table][aa], codon)]
            assert len(aa) == 1, (table, codon, aa)
            peptide += aa[0]
        dna = ''.join(codons)
        for suffix, flags in [('all', ()), ('nogap', ('-nogap',))]:
            add(f'table-{table}-{suffix}', [('p1', peptide), ('p2', peptide)], [('p1', dna), ('p2', dna)], table, flags)
        starts = [c for c in codons if re.fullmatch(codes[table]['B'], c)]
        start = next((c for c in starts if c != 'ATG'), 'ATG')
        add(f'table-{table}-initiation', [('p1', 'M'+'A'*11)], [('p1', start+'GCT'*11)], table)
    for digit in range(1,10):
        add(f'frameshift-{digit}', [('p1', f'MA{digit}K'), ('p2', 'MA-K')],
            [('p1', 'ATGGCT'+'C'*digit+'AAA'), ('p2', 'ATGGCTAAA')])
    add('gaps', [('p1','M-AK'),('p2','MA-K')], [('p1','CCCATGGCTAAATTT'),('p2','GGGATGGCTAAACCC')])
    add('lowercase-stop-kept-v14', [('p1','MA*')], [('p1','atggcttaa')], flags=('-nogap',))
    add('uppercase-stop-dropped-v14', [('p1','MA*')], [('p1','ATGGCTTAA')], flags=('-nogap',))
    add('rna-upper', [('p1','MA*')], [('p1','AUGGCUUAA')])
    add('rna-lower', [('p1','MA*')], [('p1','auggcuuaa')])
    add('reordered-ids', [('p1','MAK'),('p2','MAL')], [('p2','ATGGCTCTG'),('p1','ATGGCTAAA')])
    add('explicit-input-order', [('p1','MAK'),('p2','MAL')], [('n1','ATGGCTAAA'),('n2','ATGGCTCTG')], pairing='INPUT_ORDER')
    add('block-selection', [('p1','MAKQLR'),('p2','MAKQLR')], [('p1','ATGGCTAAACAACTGCGT'),('p2','ATGGCTAAACAACTGCGT')],
        flags=('-blockonly',), selection='# ## #')
    peptide = 'MAKQLRTACDEFGHIKLMNPQRSTVWY'
    encoding = {aa:next(c for c in codons if re.fullmatch(codes[1][aa],c)) for aa in set(peptide)}
    dna = ''.join(encoding[aa] for aa in peptide)
    bad = dna[:45]+'CCC'+dna[48:]
    for name, flags, mask in [('relaxed-mismatch',(),None),('remove-mismatch',('-nomismatch',),None),
                              ('masked-mismatch',('-nomismatch','-blockonly'),'#'*14+' '+'#'*(len(peptide)-15))]:
        add(name,[('p1',peptide),('p2',peptide)],[('p1','GGG'+dna+'CCC'),('p2','GGG'+bad+'CCC')],flags=flags,selection=mask)
    add('short-fallback-failure',[('p1','MAKQLRT')],[('p1','ATGGCTAAAGGGCTGCGTACT')],comparison='native-refusal')
    add('weak-relaxed-success',[('p1','M'*12)],[('p1','GCT'*12)])
    add('table-10-cys-disagreement',[('p1','M'+'C'*11)],[('p1','ATG'+'TGT'*10+'TGA')],table=10)
    add('underscore-native-correction',[('p1','MA_K')],[('p1','ATGGCTTAAAAA')],comparison='documented-normalization-departure')
    add('dot-native-correction',[('p1','M.AK')],[('p1','ATGGCTAAA')],comparison='documented-normalization-departure')
    add('ragged-refusal',[('p1','MAK'),('p2','MA')],[('p1','ATGGCTAAA'),('p2','ATGGCT')],comparison='native-refusal')
    add('duplicate-refusal',[('p1','MAK'),('p1','MAK')],[('p1','ATGGCTAAA'),('p2','ATGGCTAAA')],comparison='native-refusal')
    add('selenocysteine-refusal',[('p1','MAUK')],[('p1','ATGGCTTGAAAA')],comparison='native-refusal')
    add('unknown-residue-refusal',[('p1','MAZK')],[('p1','ATGGCTGGTAAA')],comparison='native-refusal')
    add('empty-dna-refusal',[('p1','MAK')],[('p1','')],comparison='native-refusal')
    add('leading-X-initiation',[('p1','XMAK')],[('p1','CCCCTGGCTAAA')])
    add('tfastx-backslash',[('p1','MA\\K')],[('p1','ATGGCTCAAA')])
    add('tfastx-slash',[('p1','MA/K')],[('p1','ATGGCTCC')])
    add('all-gap-refusal',[('p1','---')],[('p1','ATGGCTAAA')],comparison='native-refusal')
    return cases


def fasta(rows):
    return ''.join('>'+name+'\n'+sequence+'\n' for name,sequence in rows)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',required=True,type=Path);parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args()
    if sys.platform!='linux' or not shutil.which('perl'):
        parser.exit(3,'SKIP3: Linux and an existing Perl runtime are required; no analysis was started.\n')
    if shutil.which('bl2seq'):
        parser.exit(3,'SKIP3: original diagnostics may invoke bl2seq; use a controlled host without that optional executable.\n')
    source=args.source.resolve();raw=source.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=SOURCE_SHA256:
        parser.exit(2,'FAIL2: source bytes differ from the audited original v14 archive.\n')
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    cases=definitions(patterns(raw));results=[]
    (out/'cases.json').write_text(json.dumps(cases,indent=2)+'\n')
    (out/'source.pl').write_bytes(raw)
    for case in cases:
        home=out/case['name'];home.mkdir()
        if case['selection'] is None:protein=fasta(case['protein'])
        else:
            protein='CLUSTAL W multiple sequence alignment\n\n'+''.join(f'{name:<12}  {seq}\n' for name,seq in case['protein'])+' '*14+case['selection']+'\n\n'
        (home/'protein.aln').write_text(protein)
        (home/'dna.fasta').write_text(fasta(case['dna']))
        command=['perl',str(source),'protein.aln','dna.fasta','-output','fasta','-codontable',str(case['table']),*case['flags']]
        started=time.monotonic()
        with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:
            process=subprocess.Popen(command,cwd=home,stdout=stdout,stderr=stderr,start_new_session=True)
            try:code=process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                # Preserve ownership and ask the caller to observe/terminate this exact process group.
                (home/'live-process.json').write_text(json.dumps(dict(pid=process.pid,process_group=process.pid,command=command))+'\n')
                raise RuntimeError('Original control still live; do not restart this case: '+str(home))
        row=dict(name=case['name'],command=command,exit_code=code,elapsed_seconds=time.monotonic()-started,
                 stdout_sha256=hashlib.sha256((home/'stdout').read_bytes()).hexdigest(),stderr_sha256=hashlib.sha256((home/'stderr').read_bytes()).hexdigest())
        (home/'observed.json').write_text(json.dumps(row,indent=2)+'\n');results.append(row)
    record=dict(status='captured-original-output-not-an-admission-certificate',source_sha256=SOURCE_SHA256,
                collector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),protocols=len(results),
                results=results,all_owned_processes_terminal=True)
    (out/'summary.json').write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps(dict(status=record['status'],protocols=len(results),all_owned_processes_terminal=True)))


if __name__=='__main__':main()
