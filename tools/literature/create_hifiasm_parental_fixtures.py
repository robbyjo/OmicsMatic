# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build independent integer-hash controls and observe original parental readers/trio workers without yak installation."""
import argparse,collections,hashlib,itertools,json,math,random,struct,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

MASK=(1<<64)-1
NT={**dict(zip(b'ACGT',range(4))),**dict(zip(b'acgt',range(4))),**dict(zip(range(4),range(4))),ord('U'):3,ord('u'):3}


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def integer_hash(key,mask):
    key=(key*((1<<21)-1)-1)&mask;key^=key>>24;key=(key*265)&mask;key^=key>>14;key=(key*21)&mask;key^=key>>28;return (key*((1<<31)+1))&mask


def exact_hash(sequence):
    codes=[NT[b] for b in sequence];k=len(codes);reverse=[3-c for c in reversed(codes)]
    if k<32:
        encode=lambda values:sum(c<<(2*(k-i-1)) for i,c in enumerate(values));return integer_hash(min(encode(codes),encode(reverse)),(1<<(2*k))-1)
    planes=lambda values:(sum((c&1)<<(k-i-1) for i,c in enumerate(values)),sum((c>>1)<<(k-i-1) for i,c in enumerate(values)))
    forward=planes(codes);backward=planes(reverse);chosen=forward if forward[1]<backward[1] else backward;return (integer_hash(chosen[0],MASK)+integer_hash(chosen[1],MASK))&MASK


def count(sequences,k):
    result=collections.Counter();windows=ambiguous=0
    for sequence in sequences:
        ambiguous+=sum(c not in NT for c in sequence)
        for i in range(max(0,len(sequence)-k+1)):
            window=sequence[i:i+k]
            if all(c in NT for c in window):result[exact_hash(window)]+=1;windows+=1
    saturated=sum(max(0,n-1023) for n in result.values());return dict(entries=[dict(hash=str(h),count=min(n,1023)) for h,n in sorted(result.items())],windows=windows,ambiguous_bases=ambiguous,saturated_windows=saturated)


def wire(path,k,prefix,entries):
    buckets=collections.defaultdict(list)
    for e in entries:buckets[int(e['hash'])&((1<<prefix)-1)].append(e)
    with path.open('xb') as file:
        file.write(b'YAK\2'+struct.pack('<III',k,prefix,10))
        for bucket in range(1<<prefix):
            values=buckets[bucket];size=len(values);capacity=0 if size==0 else 1<<max(2,((4*size+2)//3-1).bit_length());file.write(struct.pack('<II',capacity,size))
            for e in values:file.write(struct.pack('<Q',((int(e['hash'])>>prefix)<<10)|e['count']))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_PARENTAL_REFERENCE_CONTROLS',implementation_credit=False,analysis_admitted=False,whole_goal_complete=False)
    try:
        reference=a.reference.resolve();built=json.loads((reference/'COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-original-parental-source-observer-and-six-edition-equations-only' and pin(reference/'reference')==built['binary'];assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items());rng=random.Random(98317)
        def call(name,mode,data,args=()):
            command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',linux(reference/'reference'),mode,*args];run=subprocess.run(command,input=data,capture_output=True,timeout=180);(out/(name+'.input')).write_bytes(data);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,(name,run.stderr.decode(errors='replace'));return run.stdout.decode().splitlines()
        sequences=[bytes(s) for k in range(1,7) for s in itertools.product(b'ACGT',repeat=k)]
        for k in range(1,64):
            sequences.extend([bytes([base])*k for base in b'ACGTUacgtu'])
            for _ in range(40):sequences.append(bytes(rng.choice(b'ACGTacgtUu\0\1\2\3') for i in range(k)))
        hashes=[dict(hex=s.hex(),expected=str(exact_hash(s))) for s in sequences];observed=call('hashes','hash',''.join(str(len(s))+'\t'+s.hex()+'\n' for s in sequences).encode());assert observed==[r['expected'] for r in hashes]
        classifications=[]
        for k in [1,2,4,5,21,31,32,51,63]:
            for _ in range(900):
                values=[0,1,max(0,k-4),k,k+1,19,20,21,100,1000];c=[0]*16;c[2]=rng.choice(values);c[8]=rng.choice(values);c[rng.randrange(16)]+=rng.randrange(5);sc=[rng.choice(values),rng.choice(values)];ratio=rng.choice([0,0.05,math.nextafter(0.05,0),math.nextafter(0.05,1),0.5,1,math.nextafter(1,2),2,20]);classifications.append(dict(k=k,counts=c,runs=sc,ratio=ratio))
        lines=''.join(' '.join(map(str,[r['k'],r['ratio'],*r['runs'],*r['counts']]))+'\n' for r in classifications).encode();observed=call('classifications','classify-counts',lines)
        for row,kind in zip(classifications,observed):assert kind in ['0','p','m','a'];row['expected']=kind
        assert len(observed)==len(classifications);tables=[];read_checks=[]
        for index,k in enumerate([1,2,7,21,31,32,51,63]):
            genome=bytes(rng.choice(b'ACGT') for _ in range(320));other=bytearray(genome)
            for i in range(0,len(other),7):other[i]=b'ACGT'[(b'ACGT'.index(other[i])+1)%4]
            family=[]
            for parent,sequence in enumerate([genome,bytes(other)]):
                sequences=[sequence]*6+[sequence.lower(),b'A'*1500,b'ACGTNNacgtUu'+sequence[:k+10]];expected=count(sequences,k);name='table-'+str(index)+'-'+str(parent);row=dict(id=name,k=k,prefix=10,sequences_hex=[s.hex() for s in sequences],**expected);path=out/(name+'.yak');wire(path,k,10,expected['entries']);dump=call(name+'-source-reader','dump',b'',(linux(path),));assert dump[0]==str(k)+'\t10';entries=[dict(hash=h,count=int(c)) for h,c in (line.split('\t') for line in dump[1:])];assert sorted(entries,key=lambda e:int(e['hash']))==expected['entries'];tables.append(row);family.append((row,path))
            raw_reads=[genome[:180],bytes(other[:180]),genome[130:],bytes(other[130:]),b'A'*250,b'N'*80,b'',b'ACGTUuacgtNN'+genome[:90],genome[:90]+bytes(other[90:180])]
            for minimum,repeat in [(2,5),(0,0),(5,2)]:
                source=call('read-'+str(index)+'-'+str(minimum)+'-'+str(repeat),'classify-read',''.join('1.0\t'+s.hex()+'\n' for s in raw_reads).encode(),(linux(family[0][1]),linux(family[1][1]),str(minimum),str(repeat)));assert len(source)==len(raw_reads)
                for raw,line in zip(raw_reads,source):label,recovered=line.split('\t');read_checks.append(dict(paternal=family[0][0]['id'],maternal=family[1][0]['id'],raw_hex=raw.hex(),source_recovered_hex=recovered,source_preprocessing_changed=raw.hex()!=recovered,minimum=minimum,repeat=repeat,ratio=1.0,expected_source_label=int(label)))
        fixture=dict(schema_version=1,hashes=hashes,classifications=classifications,tables=tables,read_checks=read_checks);save(out/'cases.json',fixture);state.update(status='passed-original-parental-hash-reader-and-trio-worker-controls-only',hash_cases=len(hashes),independent_exact_integer_hashes=len(hashes),classification_cases=len(classifications),table_families=len(tables),source_trio_reads=len(read_checks),source_preprocessing_changes=sum(r['source_preprocessing_changed'] for r in read_checks),cases=pin(out/'cases.json'),reference_binary=built['binary'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},namespace_patched=False,yak_runtime_used=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/independent controls')
    raise SystemExit(main())
