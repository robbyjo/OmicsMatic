# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create full source-reader summaries, including a27M-entry database that need not fit a Java table."""
import argparse,array,hashlib,json,struct,subprocess,sys,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()
def xor_to(n):return [n,1,n+1,0][n%4]


def small(path):
    raw=path.read_bytes();assert raw[:4]==b'YAK\2';k,pre,counter=struct.unpack_from('<III',raw,4);assert counter==10;pos=16;hashes=[];hist=[0]*1024;occupied=maximum=0
    for bucket in range(1<<pre):
        capacity,n=struct.unpack_from('<II',raw,pos);pos+=8
        if n:occupied+=1
        maximum=max(maximum,n)
        for _ in range(n):
            word=struct.unpack_from('<Q',raw,pos)[0];pos+=8;hashes.append(((word>>10)<<pre)|bucket);hist[word&1023]+=1
    assert pos==len(raw) and len(hashes)==len(set(hashes));xors=0
    for h in hashes:xors^=h
    return dict(k=k,prefix_bits=pre,entries=len(hashes),occupied_buckets=occupied,maximum_bucket_entries=maximum,hash_sum=str(sum(hashes)%(1<<64)),hash_xor=str(xors),histogram=hist)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--references',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_STREAMING_YAK_FIXTURES',analysis_admitted=False,implementation_credit=False)
    try:
        inputs=out/'inputs';inputs.mkdir();cases=[]
        for file in sorted((ROOT/'build/literature-review/hifiasm-runtime/parental-native-2/windows21/native').glob('table-*.yak')):
            cases.append(dict(id=file.stem,path=file.relative_to(ROOT).as_posix(),pin=pin(file),expected=small(file)))
        assert len(cases)==16;n=1000001;buckets=27;large=inputs/'synthetic-27000027.yak';assert sys.byteorder=='little';words=array.array('Q',((i<<10)|(i&1023) for i in range(1,n+1))).tobytes()
        with large.open('xb') as stream:
            stream.write(b'YAK\2'+struct.pack('<III',31,10,10))
            for bucket in range(1024):
                stream.write(struct.pack('<II',2097152 if bucket<buckets else 0,n if bucket<buckets else 0))
                if bucket<buckets:stream.write(words)
        hist=[((n//1024)+(1 if 0<i<=n%1024 else 0))*buckets for i in range(1024)];expected=dict(k=31,prefix_bits=10,entries=n*buckets,occupied_buckets=buckets,maximum_bucket_entries=n,hash_sum=str(((n*(n+1)//2)*1024*buckets+n*buckets*(buckets-1)//2)%(1<<64)),hash_xor=str((xor_to(n)<<10)^xor_to(buckets-1)),histogram=hist);assert sum(hist)==n*buckets>25000000;cases.append(dict(id='beyond-native-table-limit',path=large.relative_to(ROOT).as_posix(),pin=pin(large),expected=expected))
        empty=inputs/'empty.yak';empty.write_bytes(b'YAK\2'+struct.pack('<III',31,10,10)+b'\0'*(1024*8));cases.append(dict(id='empty',path=empty.relative_to(ROOT).as_posix(),pin=pin(empty),expected=small(empty)))
        source=a.references.resolve()/'0.19.5-r587/source';profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/hifiasm-source/profiles.json').read_bytes())['editions']['0.19.5-r587'];assert all(pin(source/name)==v for name,v in profile['members'].items());objects=sorted(q for q in source.glob('*.o') if q.name not in ['main.o','Trio.o']);helper=ROOT/'tools/literature/hifiasm_yak_reference.cpp';command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/g++','-O2','-msse4.2','-mpopcnt','-I',linux(source),linux(helper),*map(linux,objects),'-lz','-lpthread','-lm','-o',linux(out/'reference')];run=subprocess.run(command,capture_output=True,timeout=300);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode,original_objects={q.name:pin(q) for q in objects}));assert run.returncode==0,run.stderr.decode(errors='replace')
        for case in cases:
            command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',linux(out/'reference'),linux(ROOT/case['path'])];run=subprocess.run(command,capture_output=True,timeout=300);(out/(case['id']+'.stdout')).write_bytes(run.stdout);(out/(case['id']+'.stderr')).write_bytes(run.stderr);assert run.returncode==0;lines=run.stdout.decode().splitlines();head=lines[0].split('\t');want=case['expected'];assert list(map(int,head[:5]))==[want[k] for k in ['k','prefix_bits','entries','occupied_buckets','maximum_bucket_entries']] and head[5:]==[want['hash_sum'],want['hash_xor']] and list(map(int,lines[1].split('\t')))==want['histogram'];print(case['id'],want['entries'],'complete original entries',flush=True)
        assert all(pin(source/name)==v for name,v in profile['members'].items());save(out/'cases.json',dict(cases=cases));state.update(status='prepared-eighteen-original-reader-and-independent-streaming-YAK-controls',cases=pin(out/'cases.json'),case_count=len(cases),large_entries=27000027,large_pin=pin(large),reference=pin(out/'reference'),source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [helper,Path(__file__).resolve()]},native_workflow_uses_streaming=False,original_reader_observer_edition='0.19.5-r587')
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('prepared-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original-reader checks')
    raise SystemExit(main())
