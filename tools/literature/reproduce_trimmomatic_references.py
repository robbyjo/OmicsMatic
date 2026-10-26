"""Re-run frozen synthetic cases in original JARs, independently of JLinAlg."""
import argparse,datetime as dt,hashlib,json,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];FIXTURE=ROOT/'src/test/resources/trimmomatic'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--java',type=Path,default=ROOT/'build/omics-java25/bin/java.exe');ap.add_argument('--javac',default=shutil.which('javac'));ap.add_argument('--home',type=Path,default=ROOT/'build/omics-trimmomatic');ap.add_argument('--out',type=Path,required=True);a=ap.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);steps=[];error=None;status='failed';provenance=json.loads((FIXTURE/'reference-provenance.json').read_text());writer=Path(__file__).with_name('TrimmomaticReference.java')
    def run(name,command):
        p=subprocess.run(list(map(str,command)),capture_output=True,text=True,timeout=180);log=out/(name+'.log');log.write_text(p.stdout+p.stderr,encoding='utf-8');steps.append(dict(name=name,command=list(map(str,command)),exit_code=p.returncode,log_sha256=sha(log)));assert p.returncode==0,(name,p.stderr)
    try:
        assert sha(writer)==provenance['reproducer_sha256'];assert all(sha(FIXTURE/n)==h for n,h in provenance['files'].items());assert a.javac,'A permitted JDK javac is required; JRE alone cannot compile the reference writer'
        classes=out/'classes';classes.mkdir();run('compile',[a.javac,'--release','17','-d',classes,writer]);results={}
        for v in ['0.39','0.41']:
            jar=a.home/v/('trimmomatic-'+v+'.jar');before=sha(jar);assert before==provenance['original_versions'][v]['jar_sha256'];dest=out/('reference-'+v+'.tsv');run(v,[a.java,'-cp',classes,'TrimmomaticReference',jar,FIXTURE/'cases.tsv',dest]);assert sha(jar)==before;assert sha(dest)==sha(FIXTURE/dest.name);results[v]=dict(jar_sha256=before,case_count=len(dest.read_text().splitlines())-1,output_sha256=sha(dest))
        assert sum(d['case_count'] for d in results.values())==4236;status='passed'
    except Exception as e:error=repr(e);results={}
    record=dict(status=status,error=error,steps=steps,results=results,writer_sha256=sha(writer),java_executable=str(a.java.resolve()),java_executable_sha256=sha(a.java) if a.java.is_file() else None,recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),scope='Actual original step objects and frozen synthetic cases; no JLinAlg expected-output formula, adapters or patient data. A missing runtime/compiler skips reproduction until supplied; no scientific success inferred.')
    (out/'reference-reproduction.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(dict(status=status,error=error,observed_cases=sum(d['case_count'] for d in results.values()))));return 0 if status=='passed' else 1
if __name__=='__main__':raise SystemExit(main())
