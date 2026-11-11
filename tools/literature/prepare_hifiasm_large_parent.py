# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a NEW large-parent regression input; preserve every original marker and declare synthetic weak-marker additions."""
import argparse,array,json,struct,subprocess,sys,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from create_hifiasm_yak_fixtures import small,xor_to


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_LARGE_PARENT_PREPARATION',analysis_admitted=False,implementation_credit=False)
    try:
        base=ROOT/'build/literature-review/hifiasm-runtime';source=base/'paper-modes-1/0.19.5-r587/original/parents/parent1.yak';before=pin(source);old=small(source);raw=source.read_bytes();assert old['k']==31 and old['prefix_bits']==10;pos=16;families=[];n=1000001;occupied_noise=27;assert sys.byteorder=='little'
        for bucket in range(1024):
            capacity,count=struct.unpack_from('<II',raw,pos);pos+=8;words=raw[pos:pos+count*8];pos+=count*8
            if bucket<occupied_noise:
                for (key,) in struct.iter_unpack('<Q',words):assert not 1<=(key>>10)<=n,'Synthetic noise would collide with an original marker; no input admitted'
            families.append((count,words))
        assert pos==len(raw);noise=array.array('Q',((i<<10)|1 for i in range(1,n+1))).tobytes();target=out/'augmented-parent.yak';maximum=0
        with target.open('xb') as stream:
            stream.write(b'YAK\2'+struct.pack('<III',31,10,10))
            for bucket,(count,words) in enumerate(families):
                combined=count+(n if bucket<occupied_noise else 0);capacity=0 if combined==0 else 4
                while capacity-(capacity>>2)<combined:capacity*=2
                maximum=max(maximum,combined);stream.write(struct.pack('<II',capacity,combined));stream.write(words)
                if bucket<occupied_noise:stream.write(noise)
        expected=dict(old);expected['entries']+=n*occupied_noise;expected['maximum_bucket_entries']=maximum;expected['occupied_buckets']=sum(count>0 or bucket<occupied_noise for bucket,(count,_) in enumerate(families));expected['histogram']=old['histogram'].copy();expected['histogram'][1]+=n*occupied_noise;expected['hash_sum']=str((int(old['hash_sum'])+(n*(n+1)//2)*1024*occupied_noise+n*occupied_noise*(occupied_noise-1)//2)%(1<<64));expected['hash_xor']=str(int(old['hash_xor'])^(xor_to(n)<<10)^xor_to(occupied_noise-1));assert expected['entries']>25000000 and target.stat().st_size==16+1024*8+expected['entries']*8
        reference=base/'yak-stream-fixtures-1/reference';proof=json.loads((reference.parent/'COMPLETED.json').read_bytes());assert pin(reference)==proof['reference'];command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',linux(reference),linux(target)];run=subprocess.run(command,capture_output=True,timeout=300);(out/'original-reader.stdout').write_bytes(run.stdout);(out/'original-reader.stderr').write_bytes(run.stderr);save(out/'original-reader.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0;lines=run.stdout.decode().splitlines();head=lines[0].split('\t');assert list(map(int,head[:5]))==[expected[k] for k in ['k','prefix_bits','entries','occupied_buckets','maximum_bucket_entries']] and head[5:]==[expected['hash_sum'],expected['hash_xor']] and list(map(int,lines[1].split('\t')))==expected['histogram'];assert pin(source)==before
        save(out/'expected.json',expected);state.update(status='prepared-complete-source-checked-large-parent-with-all-original-markers',original_parent=dict(path=source.relative_to(ROOT).as_posix(),**before),augmented_parent=dict(path=target.relative_to(ROOT).as_posix(),**pin(target)),expected=pin(out/'expected.json'),synthetic_added_entries=n*occupied_noise,synthetic_added_count=1,all_original_marker_words_retained=True,duplicates_introduced=False,biological_parent_database=False,assembly_output_equivalence_not_yet_checked=True,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('prepared-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source-marker and synthetic-input provenance checks')
    raise SystemExit(main())
