#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fresh original PLINK1.9 binary LD/frequency and table-window controls."""
import argparse,hashlib,json,random,struct,subprocess,sys,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import pin,write
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/fuma-runtime/ld-reference-development-1'
PRIOR=ROOT/'build/literature-review/plink-runtime/downloaded-reference-1'


def bed(prefix,rows,positions,founders,children=0,keep=None):
    total=founders+children;assert all(len(r)==total for r in rows)
    prefix.with_suffix('.fam').write_text(''.join('F S'+str(i)+' '+('0 0' if i<founders else 'S0 S1')+' '+str(i%3)+' -9\n' for i in range(total)),encoding='utf-8',newline='\n')
    prefix.with_suffix('.bim').write_text(''.join(str(chrom)+' rs'+str(i)+' 0 '+str(pos)+' '+('AT A' if i%7==6 else 'A G')+'\n' for i,(chrom,pos) in enumerate(positions)),encoding='utf-8',newline='\n')
    raw=bytearray([108,27,1]);codes={0:3,1:2,2:0,-1:1}
    for row in rows:
        b=bytearray((total+3)//4)
        for i,value in enumerate(row):b[i//4]|=codes[value]<<(2*(i%4))
        raw.extend(b)
    prefix.with_suffix('.bed').write_bytes(raw)
    if keep is not None:prefix.with_suffix('.keep').write_text(''.join('F S'+str(i)+'\n' for i in reversed(keep)),encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        receipt=json.loads((PRIOR/'verification.json').read_text(encoding='utf-8'));source=next(x for x in receipt['entries'] if x['name']=='plink1');exe=PRIOR/'plink1/plink.exe';archive=PRIOR/'plink_win64_20260927.zip'
        assert pin(archive)==dict(bytes=source['archive_bytes'],sha256=source['archive_sha256']) and pin(exe)['sha256']==source['members']['plink.exe']
        with zipfile.ZipFile(archive) as z:
            assert hashlib.sha256(z.read('plink.exe')).hexdigest()==pin(exe)['sha256'];(out/'PLINK-LICENSE').write_bytes(z.read('LICENSE'))
        write(out/'original-source-receipt.json',dict(official_page=source['official_page'],url=source['url'],archive=pin(archive),binary=pin(exe),version=source['version_stdout'].strip(),original_binary_unmodified=True,download_performed=False,license_sha256=source['members']['LICENSE']))
        bound={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))};cases=[];rng=random.Random(20261102)
        definitions=[]
        rows=[[((value>>(2*i))&3)-1 for i in range(3)] for value in range(64)]
        definitions.append(('exhaustive-three',rows,[(1,100+i*17) for i in range(64)],3,0,None,None))
        for founders in [63,64,65,129,2504]:
            n=founders+3;rows=[[rng.randrange(4)-1 for _ in range(n)] for _ in range(24)]
            rows[0]=[i%3 for i in range(n)];rows[1]=[2-v for v in rows[0]];rows[2]=[1]*n;rows[3]=[-1]*n;rows[4]=[2]*n;rows[4][0]=1
            definitions.append(('word-'+str(founders),rows,[(1,100+i*137) for i in range(24)],founders,3,None,None))
        n=74;rows=[[rng.randrange(4)-1 for _ in range(n)] for _ in range(20)];rows[0]=[i%3 for i in range(n)]
        definitions.append(('explicit-keep',rows,[(1,100+i*137) for i in range(20)],71,3,[i for i in range(n) if i%3==0],None))
        positions=[(1,100),(1,101),(1,1100),(1,1101),(1,2100),(2,100),(2,1100),(2,1101)]
        for count in [2,3,99999]:
            definitions.append(('window-'+str(count),[[0,1,2,1,0] for _ in positions],positions,5,0,None,dict(bp=1000,maximum_index_gap=count-1,plink_ld_window=count,r2_floor=1.0)))
        for name,rows,positions,founders,children,keep,window in definitions:
            folder=out/name;folder.mkdir();prefix=folder/'input';bed(prefix,rows,positions,founders,children,keep)
            actual_founders=[i for i in range(founders) if keep is None or i in keep]
            meta=dict(name=name,variants=len(rows),total_samples=founders+children,selected_founder_indices=actual_founders,keep_requested=keep is not None,window=window,synthetic_genotypes=rows)
            write(folder/'input.json',meta)
            args0=[str(exe),'--bfile',str(prefix),'--keep-allele-order','--allow-no-sex','--memory','128','--threads','1']
            if keep is not None:args0+=['--keep',str(prefix.with_suffix('.keep'))]
            argv=[*args0,'--freqx','--r2','square','bin','--out',str(folder/'original')]
            def call(argv,label):
                with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(argv,cwd=folder,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=60,check=False)
                record=dict(argv=argv,exit_code=result.returncode);write(folder/(label+'.process.json'),record);assert result.returncode==0,name+': inspect '+label+'.stderr';return record
            process=call(argv,'original');raw=(folder/'original.ld.bin').read_bytes();assert len(raw)==len(rows)**2*8
            # Preserve every original double including NaN; never impute undefined LD.
            values=struct.unpack('<'+str(len(rows)**2)+'d',raw);assert len(values)==len(rows)**2
            window_process=None
            if window is not None:
                window_process=call([*args0,'--r2','--ld-window',str(window['plink_ld_window']),'--ld-window-kb','1','--ld-window-r2','1','--out',str(folder/'window')],'window')
                assert (folder/'window.ld').is_file()
            cases.append(dict(name=name,variants=len(rows),founders=len(actual_founders),matrix_values=len(values),process=process,window_process=window_process))
        assert all(pin(ROOT/n)==v for n,v in bound.items()) and pin(exe)['sha256']==source['members']['plink.exe']
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-original-PLINK19-hardcall-LD-controls',cases=cases,matrix_values=sum(c['matrix_values'] for c in cases),original_version=source['version_stdout'].strip(),original_binary=pin(exe),source_pins=bound,native_execution=False,synthetic=True,full_FUMA_reproduced=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));print(json.dumps(dict(status='passed-original-PLINK19-hardcall-LD-controls',cases=len(cases),matrix_values=sum(c['matrix_values'] for c in cases))));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_ORIGINAL_LD_CONTROLS',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_ORIGINAL_LD_CONTROLS: '+str(failure)+'\nRequired action: preserve the failed attempt and inspect the named original command; do not replace undefined values.',file=sys.stderr);sys.exit(2)
