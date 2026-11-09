# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare literal valid read fields with all six original kseq readers; declare stricter admission rejections separately."""
import argparse,gzip,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--references',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_READ_REFERENCE',analysis_admitted=False,implementation_credit=False)
    try:
        home=out/'inputs';home.mkdir();valid=[];invalid=[]
        def add(id,raw,fastq=False,compress=False,good=True,limits=None):
            file=home/(id+'.data');file.write_bytes(gzip.compress(raw,mtime=0) if compress else raw);record=dict(id=id,path=file.relative_to(ROOT).as_posix(),pin=pin(file),require_fastq=fastq,limits=limits or {});(valid if good else invalid).append(record);return record
        sequences=['ACGT','acgtuNRYswKMBDHV','A','N'*97,'ACGT'*257]
        for n,s in enumerate(sequences):
            for fastq in [False,True]:
                for compressed in [False,True]:
                    id=f'valid-{n}-{fastq}-{compressed}';header='read-'+str(n)+'  literal\tcomment';raw=('@'+header+'\n'+s[:max(1,len(s)//2)]+'\n'+s[max(1,len(s)//2):]+'\n+read-'+str(n)+'\n'+'I'*len(s)+'\n') if fastq and len(s)>1 else ('@'+header+'\n'+s+'\n+\n'+'@'*len(s)+'\n') if fastq else '>'+header+'\n'+s+'\n';add(id,raw.encode(),fastq,compressed)
        add('crlf',b'>literal/1 note\r\nacGT\r\nNN\r\n>literal2\r\nACTU\r\n');add('quality-wrap',b'@read\nACGT\n+read comment\n@+\nII\n',True);add('no-final-newline',b'>read\nACGT')
        bad=[('empty',b'',False),('duplicate',b'>r\nAC\n>r other\nGT\n',False),('alphabet',b'>r\nACX\n',False),('header',b'r\nAC\n',False),('empty-read',b'>r\n',False),('quality-short',b'@r\nACGT\n+\nIII\n',True),('quality-long',b'@r\nACGT\n+\nIIIII\n',True),('header-nonascii',b'>r\xff\nAC\n',False),('blank-line',b'>r\nAC\n\n',False),('mixed',b'>r\nAC\n@q\nGT\n+\nII\n',False),('plus-identity',b'@r\nAC\n+q\nII\n',True),('ont-fasta',b'>r\nAC\n',True),('truncated-gzip',gzip.compress(b'>r\nAC\n',mtime=0)[:-4],False)]
        for id,raw,fastq in bad:add(id,raw,fastq,good=False)
        for key,value in [('encoded_bytes',3),('decoded_bytes',3),('line_bytes',2),('read_bases',3),('records',1),('bases',3),('header_bytes',1)]:add('capacity-'+key,b'>read\nACGT\n>other\nAC\n',good=False,limits={key:value})
        pairs=[]
        for id,first,second,good in [('plain',b'>r/1 note\nAC\n>s/1\nGT\n',b'>r/2\nAC\n>s/2\nGT\n',True),('identical-id',b'@r\nAC\n+\nII\n',b'@r\nGT\n+\nII\n',True),('count',b'>r\nAC\n>s\nGT\n',b'>r\nAC\n',False),('order',b'>r\nAC\n>s\nGT\n',b'>s\nGT\n>r\nAC\n',False),('suffix',b'>r/2\nAC\n',b'>r/1\nGT\n',False),('duplicate-canonical',b'>r\nAC\n>r/1\nAC\n',b'>r\nAC\n>r/2\nAC\n',False)]:
            paths=[]
            for mate,raw in enumerate([first,second]):file=home/(id+'-mate'+str(mate));file.write_bytes(raw);paths.append(dict(path=file.relative_to(ROOT).as_posix(),pin=pin(file)))
            pairs.append(dict(id=id,first=paths[0],second=paths[1],valid=good))
        original={};helper=ROOT/'tools/literature/hifiasm_read_reference.cpp';profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/hifiasm-source/profiles.json').read_bytes())
        for edition,spec in profile['editions'].items():
            source=a.references.resolve()/edition/'source';assert all(pin(source/n)==v for n,v in spec['members'].items());base=out/edition;base.mkdir();command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/g++','-O2','-I',linux(source),linux(helper),'-lz','-o',linux(base/'reference')];run=subprocess.run(command,capture_output=True,timeout=120);(base/'compile.stderr').write_bytes(run.stderr);save(base/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode();rows={}
            for case in valid:
                command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',linux(base/'reference'),linux(ROOT/case['path'])];run=subprocess.run(command,capture_output=True,timeout=60);(base/(case['id']+'.stdout')).write_bytes(run.stdout);(base/(case['id']+'.stderr')).write_bytes(run.stderr);assert run.returncode==0 and run.stderr==b'kseq_terminal_status=-1\n';records=[]
                for line in run.stdout.decode().splitlines():
                    fields=['' if v=='-' else bytes.fromhex(v).decode('ascii') for v in line.split('\t')];assert len(fields)==4;records.append(dict(id=fields[0],comment=fields[1],sequence=fields[2],quality=fields[3] or None))
                rows[case['id']]=records
            original[edition]=rows;print('Original kseq',edition,len(rows),'complete inputs',flush=True)
        assert all(v==next(iter(original.values())) for v in original.values());expected=next(iter(original.values()))
        for case in valid:case['expected']=expected[case['id']]
        save(out/'cases.json',dict(valid=valid,invalid=invalid,pairs=pairs));state.update(status='prepared-six-original-kseq-read-field-controls-and-explicit-admission-rejections',cases=pin(out/'cases.json'),valid_cases=len(valid),invalid_cases=len(invalid),pair_cases=len(pairs),original_editions=list(original),original_source_members_unchanged=True,source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [helper,Path(__file__).resolve()]})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('prepared-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source controls')
    raise SystemExit(main())
