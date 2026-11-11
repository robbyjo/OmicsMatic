# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete original Salmon quantifications for native/output contract development."""
import argparse
import concurrent.futures
import gzip
import hashlib
import json
import math
import os
from pathlib import Path
import random
import resource
import shutil
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
VERSIONS = ('1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0')


def write(path, value): path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8', newline='\n')
def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
def rc(sequence): return sequence.translate(str.maketrans('ACGT','TGCA'))[::-1]
def limits():
    resource.setrlimit(resource.RLIMIT_CPU, (180, 185))
    resource.setrlimit(resource.RLIMIT_AS, (8*1024**3, 8*1024**3))
    resource.setrlimit(resource.RLIMIT_FSIZE, (4*1024**3, 4*1024**3))
    resource.setrlimit(resource.RLIMIT_NOFILE, (1024, 1024))


def inspect_quant(folder, targets, bootstraps):
    lines = (folder/'quant.sf').read_text().splitlines()
    assert lines[0] == 'Name\tLength\tEffectiveLength\tTPM\tNumReads'
    rows = [line.split('\t') for line in lines[1:]]
    assert len(rows) == len(targets) and {r[0] for r in rows} == set(targets)
    for row in rows:
        assert len(row) == 5 and int(row[1]) == len(targets[row[0]])
        assert all(math.isfinite(float(v)) and float(v) >= 0 for v in row[2:]) and float(row[2]) > 0
    meta = json.loads((folder/'aux_info/meta_info.json').read_bytes())
    assert meta['num_bootstraps'] == bootstraps
    binary_values = 0
    if bootstraps:
        names = gzip.decompress((folder/'aux_info/bootstrap/names.tsv.gz').read_bytes()).decode().rstrip('\n').split('\t')
        assert len(names) == len(targets) and names == [r[0] for r in rows]
        raw = gzip.decompress((folder/'aux_info/bootstrap/bootstraps.gz').read_bytes())
        assert len(raw) == bootstraps * len(names) * 8
        values = struct.unpack('<' + str(bootstraps * len(names)) + 'd', raw)
        assert all(math.isfinite(v) and v >= 0 for v in values)
        binary_values = len(values)
    return dict(rows=len(rows), numeric_fields=4*len(rows), bootstrap_values=binary_values,
        estimated_fragments=sum(float(row[4]) for row in rows), tpm_sum=sum(float(row[3]) for row in rows), meta_info=meta)


def main():
    if not __debug__: raise RuntimeError('Never disable complete-population assertions')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--probe', type=Path, required=True); parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); probe = args.probe.resolve(); out = args.out.resolve()
    base = (ROOT/'build/literature-review/salmon-runtime').resolve()
    assert os.uname().sysname == 'Linux' and probe.is_relative_to(base) and out.is_relative_to(base) and not out.exists()
    startup = json.loads((probe/'COMPLETED.json').read_bytes()); assert startup['status'] == 'passed-five-unmodified-Salmon-release-startup-probes'
    owned = Path(startup['owned_linux_root']); assert owned.parent == Path('/var/tmp') and owned.name.startswith('omicsmatic-salmon-reference-')
    out.mkdir(); inputs = out/'inputs'; inputs.mkdir(); rng = random.Random(720231)
    sequence = lambda n: ''.join(rng.choice('ACGT') for _ in range(n))
    shared = sequence(600); a = shared + sequence(600); b = shared + sequence(800)
    targets = {'txA.1':a, 'txB.2':b, 'txC.3':b, 'txZero.4':sequence(900)}
    # Populate every positional length class. The earlier four-target control
    # exposes an original positional-model finalization/serialization failure;
    # it is retained separately and must not be relabeled a successful run.
    additional = {'posClass'+str(i)+'.1':sequence(650+71*i) for i in range(36)}
    targets.update(additional)
    decoy = sequence(2000); reference = dict(targets, genome_decoy=decoy)
    def fasta(path, values): path.write_text(''.join('>'+name+'\n'+seq+'\n' for name,seq in values.items()), encoding='ascii', newline='\n')
    fasta(inputs/'transcripts.fa', targets); fasta(inputs/'gentrome.fa', reference)
    (inputs/'decoys.txt').write_text('genome_decoy\n', encoding='ascii', newline='\n')
    population = [('txA.1',i) for i in range(2400)] + [('txB.2',i) for i in range(3600)] + [('genome_decoy',i) for i in range(1000)] + [('unmapped',i) for i in range(500)] + [(name,i) for name in additional for i in range(70)]
    rng.shuffle(population); read1=[]; read2=[]; sams=[]
    for order,(name, _) in enumerate(population):
        template = reference.get(name); length = rng.randrange(210,291)
        start = rng.randrange(0,len(template)-length+1) if template is not None else 0
        fragment = template[start:start+length] if template is not None else sequence(length)
        first, second = rc(fragment[-100:]), fragment[:100]; identity = 'read'+str(order)
        read1.append('@'+identity+'/1\n'+first+'\n+\n'+'I'*100+'\n');read2.append('@'+identity+'/2\n'+second+'\n+\n'+'I'*100+'\n')
        if name not in targets: continue
        alternatives = [name, 'txC.3'] if name == 'txB.2' else [name]
        for k,target in enumerate(alternatives):
            secondary = 256 if k else 0; p1=start+length-100+1; p2=start+1
            sams.append('\t'.join(map(str,[identity,83+secondary,target,p1,255,'100M','=',p2,-length,first,'I'*100])))
            sams.append('\t'.join(map(str,[identity,163+secondary,target,p2,255,'100M','=',p1,length,second,'I'*100])))
    for name,content in [('reads_1.fastq',''.join(read1)),('reads_2.fastq',''.join(read2))]:
        (inputs/name).write_text(content,encoding='ascii',newline='\n')
    sam = '@HD\tVN:1.6\tSO:unsorted\n'+''.join('@SQ\tSN:'+name+'\tLN:'+str(len(seq))+'\n' for name,seq in targets.items())+'\n'.join(sams)+'\n'
    (inputs/'alignments.sam').write_text(sam,encoding='ascii',newline='\n')
    write(inputs/'population.json',dict(seed=720231,paired_fragments=len(population),generated_transcript_origins=dict({'txA.1':2400,'txB.2':3600}, **{name:70 for name in additional}),decoy_fragments=1000,unmapped_fragments=500,
        reads_per_mate=len(population),reference_lengths={k:len(v) for k,v in reference.items()},identical_sequence_transcripts=['txB.2','txC.3'],alignment_groups=6000+70*len(additional),
        claim='Known synthetic origins do not identify unique transcripts in shared/duplicate sequence and do not certify biological estimation accuracy.'))
    input_pins = {p.name:pin(p) for p in inputs.iterdir()}; source_pin = pin(Path(__file__))

    def family(release):
        version=release['version']; folder=out/version;folder.mkdir(); home=Path(release['home']); area=owned/version/out.name;assert not area.exists();area.mkdir()
        for name,row in release['payload'].items():
            if row['kind']=='file': assert pin(home/name)=={k:row[k] for k in ('bytes','sha256')}
        env=dict(PATH='/usr/bin:/bin', HOME=str(area), TMPDIR=str(area), LANG='C', LC_ALL='C', LD_LIBRARY_PATH=str(home/'lib'), SALMON_NO_VERSION_CHECK='1')
        def command(label,arguments,expected=0):
            cmd=['/usr/bin/prlimit','--cpu=180:185','--as=8589934592:8589934592','--fsize=4294967296:4294967296','--nofile=1024:1024','--core=0:0','--',str(home/'bin/salmon'),'--no-version-check',*map(str,arguments)]
            with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:
                result=subprocess.run(cmd,cwd=area,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
            record=dict(command=cmd,exit_code=result.returncode,expected_exit=expected,environment=env);write(folder/(label+'.json'),record)
            if result.returncode != expected and '-o' in cmd:
                partial=Path(cmd[cmd.index('-o')+1])
                if partial.is_dir():shutil.copytree(partial,folder/(label+'-FAILED'))
            assert result.returncode==expected,(version,label,result.returncode);return record
        command('help-reads',['quant','--help-reads']);command('help-alignment',['quant','--help-alignment'])
        index=area/'index'; index_call=command('index',['index','-t',inputs/'gentrome.fa','-d',inputs/'decoys.txt','-i',index,'-k','31','-p','2','--keepDuplicates'])
        shutil.copytree(index,folder/'index')
        modes=[('paired-em-bias-50',50,['-i',index,'-l','A','-1',inputs/'reads_1.fastq','-2',inputs/'reads_2.fastq','--useEM','--seqBias','--gcBias','--posBias']),
               ('paired-vb-pos-100',100,['-i',index,'-l','ISR','-1',inputs/'reads_1.fastq','-2',inputs/'reads_2.fastq','--useVBOpt','--seqBias','--gcBias','--posBias']),
               ('single-em-5',5,['-i',index,'-l','SR','-r',inputs/'reads_1.fastq','--useEM','--seqBias','--fldMean','250','--fldSD','25']),
               ('paired-vb-no-replicates',0,['-i',index,'-l','A','-1',inputs/'reads_1.fastq','-2',inputs/'reads_2.fastq','--useVBOpt']),
               ('alignment-em-3',3,['-t',inputs/'transcripts.fa','-l','ISR','-a',inputs/'alignments.sam','--useEM'])]
        records=[]
        for label,bootstraps,options in modes:
            quant=area/label;call=command(label,['quant',*options,'--numBootstraps',str(bootstraps),'-p','2','-o',quant])
            shutil.copytree(quant,folder/label);audit=inspect_quant(folder/label,targets,bootstraps)
            assert audit['meta_info']['salmon_version']==version
            record=dict(mode=label,bootstraps=bootstraps,call=call,audit=audit);write(folder/(label+'-AUDITED.json'),record);records.append(record)
            print(json.dumps(dict(version=version,mode=label,rows=audit['rows'],bootstrap_values=audit['bootstrap_values'])),flush=True)
        for name,row in release['payload'].items():
            if row['kind']=='file': assert pin(home/name)=={k:row[k] for k in ('bytes','sha256')}
        result=dict(version=version,index_call=index_call,quantifications=records);write(folder/'COMPLETED.json',result);return result
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool: runs=list(pool.map(family,startup['releases']))
    assert pin(Path(__file__))==source_pin and all(pin(inputs/name)==row for name,row in input_pins.items())
    write(out/'COMPLETED.json',dict(status='passed-25-original-Salmon-mapping-alignment-bias-and-inferential-protocols',runs=runs,source_pin=source_pin,input_pins=input_pins,
        original_quantifications=25,complete_quant_rows=25*len(targets),complete_bootstrap_values=5*158*len(targets),public_adapter_available=False,scientific_runtime_admitted=False,whole_paper_reproduced=False))


if __name__=='__main__':
    try:main()
    except Exception as error:
        print('SALMON REFERENCE CONTROLS FAIL2: '+str(error)+'\nRequired action: retain every original output/log and inspect the named process, metadata, identity, bootstrap or capacity discrepancy. No passing prefix admits a runtime or user analysis. Use NEW paths after correction.',file=sys.stderr)
        raise
