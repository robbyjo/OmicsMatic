# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run a real typed peak call and audit its capture on three JVMs."""
import argparse,csv,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_deseq2_inference import JAVA21,JAVA25,linux


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--case',choices=['pooled','grouped','bedpe','broad','empty'],default='pooled');args=parser.parse_args();out=args.out.resolve();base=ROOT/'build/literature-review/signac-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir()
    jar=base/'peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar';artifact=pin(jar);reference=base/'peaks-macs-original-1';input=reference/'inputs';target=out/'analysis-结果'
    r=dict(schema_version=1,mode='GROUPED' if args.case=='grouped' else 'POOLED',out=linux(target),source_profile='Signac-1.9.0',macs2_version='2.2.7.1',macs2_home=linux(base/'peaks-reference-setup-5/2.2.7.1'),genome_build='synthetic',effective_genome_size=1000000,sources=[dict(fragments=linux(input/'a.tsv')),dict(fragments=linux(input/'b.tsv'))],timeout_seconds=120,maximum_output_bytes=100000000)
    if args.case=='grouped':
        cells=out/'groups.tsv';cells.write_text('cell\tgroup\ncell1\tA\ncell2\tA\ncell3\tB\ncell4\tB\n');r['cell_groups']=linux(cells)
    else:r['name']='fixture'
    if args.case=='bedpe':r['format']='BEDPE'
    if args.case=='broad':r['broad']=True
    if args.case=='empty':r.update(sources=[dict(fragments=linux(input/'low.tsv'))],qvalue=1e-20)
    save(out/'request.json',r)
    def run(label,argv,expected=0):
        argv=list(map(str,argv))
        with (out/(label+'.stdout')).open('xb') as a,(out/(label+'.stderr')).open('xb') as b:p=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=240)
        save(out/(label+'.process.json'),dict(command=argv,exit_code=p.returncode,expected_exit_code=expected));assert p.returncode==expected,label+' failed; inspect logs'
    try:
        run('call',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx768m','-cp',linux(jar),'org.jlinalg.cli.SignacPeaksCli','--request',linux(out/'request.json')])
        state=json.loads((target/'analysis-status.json').read_bytes());assert state['results_admitted']
        with (target/'peaks.tsv').open(encoding='utf8',newline='') as f:actual=list(csv.DictReader(f,delimiter='\t'))
        expected=[]
        cases=['group-A','group-B'] if args.case=='grouped' else [dict(pooled='pooled-BED',bedpe='pooled-BEDPE',broad='pooled-broad',empty='no-significant-peaks')[args.case]]
        for index,name in enumerate(cases):
            broad=args.case=='broad';path=reference/'2.2.7.1'/name/('fixture_peaks.'+('broadPeak' if broad else 'narrowPeak'))
            for line in path.read_text().splitlines():
                values=line.split('\t');group=('A' if index==0 else 'B') if args.case=='grouped' else 'fixture';expected.append(dict(group=group,chromosome=values[0],start=str(int(values[1])+1),end=values[2],strand='*',name=values[3].replace('fixture_peak_',group+'_peak_',1),score=values[4],fold_change=values[6],negative_log10_pvalue=values[7],negative_log10_qvalue=values[8],relative_summit_position='' if broad else values[9]))
        assert actual==expected,(actual,expected)
        save(out/'verify.json',dict(schema_version=1,mode='VERIFY',analysis=target.name))
        for label,java in [('windows-java21',JAVA21),('windows-java25',JAVA25)]:run('verify-'+label,[java,'-Xmx768m','-cp',jar,'org.jlinalg.cli.SignacPeaksCli','--request',out/'verify.json'])
        run('verify-linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx768m','-cp',linux(jar),'org.jlinalg.cli.SignacPeaksCli','--request',linux(out/'verify.json')])
        assert pin(jar)==artifact;save(out/'COMPLETED.json',dict(status='passed-real-typed-original-Signac-peak-smoke',case=args.case,artifact=artifact,rows=len(actual),portable_verifications=3,files={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()},source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))}));print('Real typed '+args.case+' matched originals and passed3 portable verifications')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_TYPED_PEAK_SMOKE',error=str(e)));raise


if __name__=='__main__':main()
