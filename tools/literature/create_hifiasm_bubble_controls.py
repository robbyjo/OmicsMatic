# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate declared synthetic diploid reads for every paper-relevant hifiasm mode; never infer paper inputs."""
import argparse,json,random
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()
def rc(sequence):return sequence.translate(str.maketrans('ACGT','TGCA'))[::-1]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);rng=random.Random(471911);length=300000;first=''.join(rng.choice('ACGT') for _ in range(length));second=list(first)
    for i in range(100,length,97):
        if i%50000<20000:second[i]=rng.choice([c for c in 'ACGT' if c!=second[i]])
    second=''.join(second);genomes=[first,second];inputs={}
    def write(name,records):
        file=out/(name+'.fa')
        with file.open('x',encoding='ascii',newline='\n') as stream:
            for id,sequence in records:stream.write('>'+id+'\n'+sequence+'\n')
        inputs[name]=dict(path=linux(file),**pin(file))
    def long_reads(label,read_length,coverage,errors):
        records=[]
        for hap,genome in enumerate(genomes):
            starts=[0,length-read_length]+[rng.randrange(length-read_length+1) for _ in range(round(length*coverage/read_length))]
            for i,start in enumerate(starts):
                sequence=list(genome[start:start+read_length])
                for j in range(len(sequence)):
                    if errors and rng.random()<errors:sequence[j]=rng.choice([b for b in 'ACGT' if b!=sequence[j]])
                sequence=''.join(sequence)
                if i%2:sequence=rc(sequence)
                records.append((label+'-hap'+str(hap)+'-read'+str(i),sequence))
        return records
    write('hifi',long_reads('hifi',12000,25,0));write('ont',long_reads('ont',25000,30,0.005));write('ul',long_reads('ul',140000,8,0.01));hic1=[];hic2=[]
    for hap,genome in enumerate(genomes):
        for i in range(1200):
            first_pos=rng.randrange(length-150);second_pos=rng.randrange(length-150);name='hic-hap'+str(hap)+'-pair'+str(i);hic1.append((name+'/1',genome[first_pos:first_pos+150]));hic2.append((name+'/2',rc(genome[second_pos:second_pos+150])))
    write('hic1',hic1);write('hic2',hic2);editions=['0.14.2-r315','0.15.1-r328','0.16.1-r375','0.19.5-r587','0.19.5-r592','0.22.0-r689'];cases=[]
    for edition in editions:
        for mode in ['hifi','trio','hic']:
            cases.append(dict(id=edition+'-'+mode,edition=edition,assembly_mode=mode,purge_level=None,disable_post_join=False))
        if edition in editions[3:]:
            for mode in ['hifi-ul','trio-ul','hic-ul']:cases.append(dict(id=edition+'-'+mode,edition=edition,assembly_mode=mode,purge_level=None,disable_post_join=False))
    cases.append(dict(id='0.22.0-r689-ont',edition='0.22.0-r689',assembly_mode='ont',purge_level=None,disable_post_join=False));cases.append(dict(id='0.14.2-r315-paper-l2-u',edition=editions[0],assembly_mode='hifi',purge_level=2,disable_post_join=True));assert len(cases)==29
    request=dict(schema_version=1,mode='ORIGINAL_ASSEMBLY_CONTROL_ONLY',reference_builds=linux(ROOT/'build/literature-review/hifiasm-runtime/reference-builds-2'),inputs=inputs,parental_sequences=genomes,cases=cases);save(out/'request.json',request);save(out/'COMPLETED.json',dict(status='prepared-twenty-nine-explicit-original-hifiasm-assembly-controls',request=pin(out/'request.json'),synthetic_genome_length=length,haplotypes=2,HiFi_errors=0,ONT_substitution_rate=0.005,ultralong_substitution_rate=0.01,genome_sha256=[__import__('hashlib').sha256(s.encode()).hexdigest() for s in genomes],actual_case_count=29,synthetic_graph_design='Alternating20kb divergent blocks and30kb shared homozygous anchors; original assembler settings unchanged',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},paper_data_inferred=False,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Prepared29 original controls: all six editions, trio/Hi-C, three UL combinations, ONT-only and reported-l2-u options')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable explicit control checks')
    main()
