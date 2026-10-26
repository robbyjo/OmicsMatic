"""Verify warning promotion through the actual packaged Cutadapt/FastQC/MultiQC paths."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args()
    out=ROOT/'build/literature-review'/('qc-warning-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True)
    python=ROOT/'build/omics-sequence-tools-env/Scripts/python.exe';home=ROOT/'build/omics-fastqc/FastQC';attempts=[]
    def run(name,method,*extra):
        command=['java','-jar',str(args.jar.resolve()),'sequence-qc','--method',method,'--out',str(out/name),'--no-config',*extra]
        p=subprocess.run(command,capture_output=True,text=True,timeout=120);(out/(name+'.log')).write_text(p.stdout+p.stderr)
        attempts.append({'name':name,'command':command,'exit_code':p.returncode});assert p.returncode==0,(name,p.stdout,p.stderr)
        assert 'QC WARNINGS' in p.stderr and 'Required action' in(out/name/'WARNINGS.md').read_text()
        assert json.loads((out/name/'analysis-status.json').read_text())['status']=='completed-with-qc-warnings'
        return out/name
    reads=out/'poor.fastq';reads.write_text(''.join('@poor'+str(i)+'\n'+'A'*50+'\n+\n'+'!'*50+'\n' for i in range(40)),newline='\n')
    status,error='failed',None
    try:
        trim=run('trim','cutadapt','--python',str(python),'--reads',str(reads),'--adapter3','AGATCGGAAGAGC','--minimum-length','100')
        assert 'All 40 input reads/pairs were filtered' in(trim/'WARNINGS.md').read_text()
        run('qc','fastqc','--fastqc-home',str(home),'--reads',str(reads))
        reports=out/'reports.tsv';reports.write_text('sample_id\tfastqc_zip\tcutadapt_json\npatient_qc\tqc/poor_fastqc.zip\ttrim/cutadapt.json\n',newline='\n')
        aggregated=run('aggregate','multiqc','--python',str(python),'--reports',str(reports))
        warning=(aggregated/'WARNINGS.md').read_text();assert 'patient_qc: FAIL' in warning and 'Cutadapt retained no reads/pairs' in warning
        assert 'FAIL' in(aggregated/'source-qc.tsv').read_text();assert(aggregated/'multiqc_report.html').is_file();status='passed'
    except Exception as failure:error=str(failure)
    record={'status':status,'error':error,'jar_sha256':hashlib.sha256(args.jar.read_bytes()).hexdigest(),'attempts':attempts,'fixture_directory':str(out),
        'scope':'Actual 5.2/0.13.0/1.35 executables, retained zero reads and source FAIL findings remain prominent in console/status/warning files after successful aggregation.'}
    path=ROOT/'docs/literature/omics-2023-2026/qc-warning-packaged-validation.json'
    if path.exists():
        old=json.loads(path.read_text());record['history']=old.get('history',[])+[{k:v for k,v in old.items() if k!='history'}]
    path.write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record));return 0 if status=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
