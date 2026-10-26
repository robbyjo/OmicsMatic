"""Retain actual executable checks of native sequence operations and failure recovery."""
import argparse
import datetime as dt
import gzip
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args()
    out=ROOT/'build/literature-review'/('native-sequence-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True)
    fixture=ROOT/'src/test/resources/seqtk/reference.json';ref=json.loads(fixture.read_text());attempts=[]

    def run(name,method,*extra,expected=0):
        dest=out/name;command=['java','-jar',str(args.jar.resolve()),'sequence-tools','--method',method,'--out',str(dest),'--no-config',*extra]
        p=subprocess.run(command,capture_output=True,text=True,timeout=90)
        (out/(name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
        attempts.append({'name':name,'command':command,'exit_code':p.returncode,'expected_exit':expected,'output':str(dest)})
        assert p.returncode==expected,(name,p.stdout,p.stderr)
        if expected==0:assert (dest/'results.tsv').is_file()
        else:assert not (dest/'results.tsv').exists() and 'Required action' in (dest/'WARNINGS.md').read_text()
        return dest

    def fastq(path):
        with gzip.open(path,'rt') as stream:
            lines=stream.read().splitlines()
        assert len(lines)%4==0
        return [lines[i:i+4] for i in range(0,len(lines),4)]

    first,second=out/'reads1.fq.gz',out/'reads2.fq.gz'
    for mate,path in [(1,first),(2,second)]:
        with gzip.open(path,'wt',newline='\n') as stream:
            for i in range(2049):stream.write('@read'+str(i)+'/'+str(mate)+' original comment\nACGTN\n+\nIHFED\n')
    status,error='failed',None
    try:
        for mode,value in [('count',7),('fraction',.25)]:
            dest=run('paired-'+mode,'sample','--reads',str(first),'--reads2',str(second),'--'+mode,str(value),'--seed','100')
            expected=next(x['indices'] for x in ref['sampling'] if x['mode']==mode and x['value']==value and x['seed']==100)
            for mate in [1,2]:
                reads=fastq(dest/('reads_R'+str(mate)+'.fastq.gz'))
                assert [int(x[0].split('/',1)[0][5:]) for x in reads]==expected
                assert all(x[1]=='ACGTN' and x[3]=='IHFED' and x[0].endswith('original comment') for x in reads)
        empty=run('empty-sample','sample','--reads',str(first),'--fraction','0')
        assert not fastq(empty/'reads_R1.fastq.gz') and 'No reads/pairs retained' in (empty/'WARNINGS.md').read_text()
        long=out/'75bp.fq';long.write_text('@simulated comment\n'+'ACG'*25+'\n+\n'+'I'*75+'\n',newline='\n')
        trim=run('length-50','trimfq','--reads',str(long),'--length','50')
        row=fastq(trim/'reads_R1.fastq.gz')[0];assert row[1]==('ACG'*25)[:50] and row[3]=='I'*50
        poor=out/'poor.fq';poor.write_text('@poor\n'+'A'*80+'\n+\n'+'!'*80+'\n',newline='\n')
        trim=run('quality-fallback','trimfq','--reads',str(poor));assert fastq(trim/'reads_R1.fastq.gz')[0][3]=='!'*30
        assert 'minimum-length fallback' in (trim/'WARNINGS.md').read_text()
        trim=run('zero-length','trimfq','--reads',str(long),'--trim-left','100');assert fastq(trim/'reads_R1.fastq.gz')[0][1]==''
        assert 'zero-length' in (trim/'WARNINGS.md').read_text()
        fasta=out/'reference.fa'
        fasta.write_text(''.join('>'+name+'\r\n'+'\r\n'.join(s[i:i+57] for i in range(0,len(s),57))+'\r\n' for name,s in ref['chromosomes'].items()),newline='')
        old=out/'reference.fa.fai';old.write_text('user-owned index must remain unchanged\n');bed=out/'regions.bed';bed.write_text(ref['subsequence']['bed3'],newline='\n')
        dest=run('subsequence','subseq','--reference',str(fasta),'--regions',str(bed))
        assert (dest/'sequences.fasta').read_text()==ref['subsequence']['expected_fasta']
        assert old.read_text()=='user-owned index must remain unchanged\n'
        dest=run('telomeres','telo','--reference',str(fasta))
        expected=''.join('\t'.join(line.split('\t')[:3])+'\n' for line in ref['telomeres'][0]['bed4'].splitlines())
        assert (dest/'telomeres.bed').read_text()==expected
        dest=run('no-telomere-hits','telo','--reference',str(fasta),'--minimum-score','100000')
        assert 'No terminal motif segment' in (dest/'WARNINGS.md').read_text()
        bad=out/'mismatched.fq';bad.write_text('@wrong/2\nACGTN\n+\nIHFED\n',newline='\n')
        run('bad-pair','sample','--reads',str(first),'--reads2',str(bad),'--fraction','0',expected=2)
        bed.write_text('chrA\t10\t999999\n',newline='\n');run('bad-bounds','subseq','--reference',str(fasta),'--regions',str(bed),expected=2)
        compressed=out/'reference.fa.gz'
        with gzip.open(compressed,'wb') as stream:stream.write(fasta.read_bytes())
        run('compressed-reference','telo','--reference',str(compressed),expected=2)
        # Include the permissive upstream notice in the actual delivered executable.
        import zipfile
        with zipfile.ZipFile(args.jar) as jar:assert b'The MIT License' in jar.read('META-INF/LICENSE-seqtk.txt')
        status='passed'
    except Exception as failure:error=str(failure)
    report={'status':status,'error':error,'jar':str(args.jar.resolve()),'jar_sha256':digest(args.jar),'attempts':attempts,
        'passed':len(attempts) if status=='passed' else None,'fixture_directory':str(out),'frozen_reference_sha256':digest(fixture),
        'scope':'Actual native packaged workflows, paired sampling against pinned reference membership, exact length/quality/fallback, CRLF indexed extraction, terminal coordinates and explicit input-error paths. No whole seqtk/dirty-paper-version or biological-pipeline parity claim.'}
    path=DATA/'native-sequence-packaged-validation.json'
    if path.exists():
        previous=json.loads(path.read_text());report['history']=previous.get('history',[])+[{k:v for k,v in previous.items() if k!='history'}]
    path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k not in ['attempts','history']}))
    return 0 if status=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
