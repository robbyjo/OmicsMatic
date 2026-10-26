"""Actual packaged native threshold peaks against independently calculated geometry and warnings."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[2]

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--jar',type=Path,required=True);args=ap.parse_args()
    out=ROOT/'build/literature-review'/('track-peaks-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True)
    reference=out/'reference.sizes';reference.write_text('chr1\t100\n',newline='\n')
    signal=out/'score.bedGraph';signal.write_text('chr1\t0\t10\t0\nchr1\t10\t30\t3\nchr1\t30\t40\t0\nchr1\t40\t70\t3\nchr1\t70\t100\t0\n',newline='\n')
    attempts=[]
    def run(name,track=signal,sizes=reference,missing='error',precision='float32',cutoff='3',minimum='60',gap='10',expected=0):
        destination=out/name;cmd=['java','-jar',str(args.jar.resolve()),'track-peaks','--input',str(track),'--sizes',str(sizes),'--genome-build','independent-fixture','--missing',missing,'--precision',precision,'--cutoff',cutoff,'--min-length',minimum,'--max-gap',gap,'--out',str(destination),'--no-config']
        p=subprocess.run(cmd,capture_output=True,text=True,timeout=90);(out/(name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8');attempts.append({'name':name,'exit_code':p.returncode,'expected_exit':expected,'command':cmd})
        assert p.returncode==expected,(name,p.stdout,p.stderr)
        if expected==0:assert(destination/'results.tsv').is_file() and(destination/'peaks.bed').is_file()
        else:assert not(destination/'results.tsv').exists() and not(destination/'peaks.bed').exists() and 'Required action' in(destination/'WARNINGS.md').read_text()
        return destination
    def rows(folder):
        lines=(folder/'results.tsv').read_text().splitlines();header=lines[0].split('\t');return[dict(zip(header,line.split('\t'))) for line in lines[1:]]
    status,error='failed',None
    try:
        with zipfile.ZipFile(args.jar) as archive:assert archive.read('META-INF/LICENSE-MACS3.txt')==(ROOT/'src/main/resources/META-INF/LICENSE-MACS3.txt').read_bytes()
        basic=run('known-gap-ties');r=rows(basic);assert len(r)==1 and r[0]['start']=='10' and r[0]['end']=='70' and r[0]['summit']=='20' and r[0]['qualifying_bases']=='50' and r[0]['maximum_signal']=='3.0'
        assert(basic/'peaks.bed').read_text()=='chr1\t10\t70\tpeak_1\n' and not(basic/'WARNINGS.md').exists()
        empty=run('minimum-too-long',minimum='61');assert rows(empty)==[] and 'NO THRESHOLD PEAKS' in(empty/'WARNINGS.md').read_text()
        split=run('gap-too-small',minimum='1',gap='9');assert[(r['start'],r['end'],r['summit']) for r in rows(split)]==[('10','30','20'),('40','70','55')]
        boundary=out/'boundary.bdg';boundary.write_text('chr1\t0\t10\t1.00000001\nchr1\t10\t20\t0\nchr1\t20\t30\t1.0000001\nchr1\t30\t100\t0\n',newline='\n')
        single=run('float32-boundary',track=boundary,cutoff='1.00000002',minimum='1',gap='0');assert len(rows(single))==2 and 'FLOAT32 ROUNDING' in(single/'WARNINGS.md').read_text()
        double=run('float64-boundary',track=boundary,precision='float64',cutoff='1.00000002',minimum='1',gap='0');assert len(rows(double))==1 and rows(double)[0]['start']=='20'
        sparse=out/'sparse.bdg';sparse.write_text('chr1\t10\t30\t3\n',newline='\n')
        zeros=run('sparse-background',track=sparse,missing='zero',cutoff='0',minimum='1',gap='0');assert rows(zeros)[0]['start']=='0' and rows(zeros)[0]['end']=='100';assert all(s in(zeros/'WARNINGS.md').read_text() for s in ['ZERO-FILLED SIGNAL','NONPOSITIVE CUTOFF'])
        run('reject-missing',track=sparse,expected=2)
        overlap=out/'overlap.bdg';overlap.write_text('chr1\t0\t100\t3\nchr1\t20\t30\t4\n',newline='\n');run('reject-overlap',track=overlap,expected=2)
        huge=out/'huge.bdg';huge.write_text('chr1\t0\t100\t1e300\n',newline='\n');run('reject-float32-overflow',track=huge,expected=2)
        longref=out/'long.sizes';longref.write_text('chr1\t9223372036854775807\n',newline='\n');longtrack=out/'long.bdg';longtrack.write_text('chr1\t9223372036854775796\t9223372036854775807\t1e300\n',newline='\n')
        longpeak=run('long-coordinates',track=longtrack,sizes=longref,missing='zero',precision='float64',minimum='11');assert rows(longpeak)[0]['summit']=='9223372036854775801'
        signed=out/'signed.bdg';signed.write_text('chr1\t0\t50\t0\nchr1\t50\t100\t-1\n',newline='\n');signedpeak=run('signed-maximum',track=signed,cutoff='-2',minimum='1');assert rows(signedpeak)[0]['maximum_signal']=='0.0' and rows(signedpeak)[0]['summit']=='25' and 'NONPOSITIVE CUTOFF' in(signedpeak/'WARNINGS.md').read_text()
        status='passed'
    except Exception as failure:error=str(failure)
    report={'status':status,'error':error,'jar':str(args.jar.resolve()),'jar_sha256':hashlib.sha256(args.jar.read_bytes()).hexdigest(),'checks':len(attempts),'license_notice_checked':True,'fixture_directory':str(out),'attempts':attempts,'scope':'Actual native executable independent cutoff/gap/span/tie/precision, complete/sparse/reference geometry, 64-bit bounds, no-hit warnings and rejected inputs. Positive float32 API has 129 actual upstream references separately. No alignment-based MACS caller, HMMRATAC, old-version or whole-study equivalence.'}
    target=ROOT/'docs/literature/omics-2023-2026/native-track-peaks-packaged-validation.json'
    if target.exists():
        old=json.loads(target.read_text(encoding='utf-8'));report['history']=old.get('history',[])+[{k:v for k,v in old.items() if k!='history'}]
    target.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');(out/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k not in ['attempts','history']}));return 0 if status=='passed' else 1

if __name__=='__main__':raise SystemExit(main())
