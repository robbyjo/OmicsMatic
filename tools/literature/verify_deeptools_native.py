"""Executable-JAR supplied-track fixtures and frozen original arithmetic references."""
import argparse,csv,datetime as dt,hashlib,json,math,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def table(path):
    with path.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--report',type=Path,required=True);a=ap.parse_args();jar=a.jar.resolve();initial=sha(jar);out=ROOT/'build/literature-review'/('deeptools-native-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'));out.mkdir();steps=[];checks=[];sources={};status='failed';error=None;reference_rows=0
    def file(name,body):
        p=out/name;p.write_text(body,encoding='utf-8');sources[p]=sha(p);return p
    def run(name,method,first,sizes,options=(),expected=0,bin_size=5):
        dest=out/name;cmd=['java','-jar',str(jar),'deep-tools','--method',method,'--input',str(first),'--sizes',str(sizes),'--genome-build','SYNTHETIC','--bin-size',str(bin_size),'--out',str(dest),'--no-config',*map(str,options)];p=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,timeout=150);log=out/(name+'.console.log');log.write_text(p.stdout+p.stderr,encoding='utf-8');steps.append(dict(name=name,command=cmd,exit_code=p.returncode,expected_exit=expected,output=str(dest),console_sha256=sha(log)));assert p.returncode==expected,(name,p.returncode,p.stdout,p.stderr,(dest/'WARNINGS.md').read_text() if (dest/'WARNINGS.md').exists() else '')
        state=json.loads((dest/'analysis-status.json').read_text())['status'];assert state.startswith('completed') if expected==0 else state=='failed';assert (dest/'results.tsv').exists() if expected==0 else not(dest/'results.tsv').exists()
        if expected:assert 'Required action:' in (dest/'WARNINGS.md').read_text()
        return dest
    def compare_args(second,operation='subtract',missing='zero',invalid='error'):
        return ['--input2',second,'--operation',operation,'--missing',missing,'--invalid',invalid]
    try:
        with zipfile.ZipFile(jar) as z:
            assert 'org/jlinalg/genetics/DeepToolsSignals.class' in z.namelist() and 'org/jlinalg/cli/DeepToolsCli.class' in z.namelist();assert z.read('META-INF/NOTICE-deepTools.txt')==(ROOT/'src/main/resources/META-INF/NOTICE-deepTools.txt').read_bytes();assert z.read('META-INF/LICENSE-deepTools.txt')==(ROOT/'src/main/resources/META-INF/LICENSE-deepTools.txt').read_bytes()
        checks.append('native source CLI and MIT attribution/full original license are packaged')
        # Each operation is tested on independent original-function rows with one shared parameter set.
        frozen=ROOT/'src/test/resources/deeptools/comparison-reference.tsv';before=sha(frozen);golden=table(frozen);ops=['ratio','log2','reciprocal_ratio','subtract','add','first','second','mean']
        for op in ops:
            rows=[r for r in golden if r['operation']==op and float(r['first_scale'])==1 and float(r['second_scale'])==.5 and float(r['first_pseudo'])==2 and float(r['second_pseudo'])==.5 and all(math.isnan(float(r[k])) or abs(float(r[k]))<1e300 for k in ['first','second'])];assert len(rows)>5
            sizes=file(op+'.sizes','oracle\t'+str(5*len(rows))+'\n');paths=[]
            for k in ['first','second']:
                paths.append(file(op+'-'+k+'.bedgraph',''.join(f'oracle\t{5*i}\t{5*(i+1)}\t{r[k]}\n' for i,r in enumerate(rows) if not math.isnan(float(r[k])))))
            d=run('oracle-'+op,'compare-native',paths[0],sizes,compare_args(paths[1],op,'covered','retain')+['--scale1','1','--scale2','.5','--pseudo1','2','--pseudo2','.5']);observed=table(d/'results.tsv');assert len(observed)==len(rows)
            for i,(actual,expected) in enumerate(zip(observed,rows)):
                assert actual['status']==expected['status'],(op,i,actual,expected)
                if expected['status']=='FINITE':assert math.isclose(float(actual['value']),float(expected['expected']),rel_tol=3e-13,abs_tol=1e-12),(op,i,actual,expected)
                else:assert actual['value']=='NA'
            reference_rows+=len(rows)
        assert sha(frozen)==before;checks.append('all eight packaged comparison operations agree with'+str(reference_rows)+'frozen original3.5.6 calculation cases,including missing/nonfinite distinctions')
        sizes=file('weighted.sizes','chrA\t11\nchrAbsent\t4\n');first=file('weighted-first.bdg','chrA\t0\t2\t4\nchrA\t2\t5\t2\nchrA\t5\t10\t6\n');second=file('weighted-second.bdg','chrA\t0\t5\t1\nchrA\t5\t11\t3\n')
        d=run('weighted-zero','compare-native',first,sizes,compare_args(second));rows=table(d/'results.tsv');assert len(rows)==4 and math.isclose(float(rows[0]['value']),1.8) and [float(r['value']) for r in rows[1:]]==[3,-3,0];assert 'UNREPRESENTED BASES' in (d/'WARNINGS.md').read_text();checks.append('independent exact overlap weighting,short terminal bin and absent-reference-contig zero policy')
        d=run('weighted-covered','compare-native',first,sizes,compare_args(second,missing='covered',invalid='retain'));rows=table(d/'results.tsv');assert rows[2]['status']==rows[3]['status']=='MISSING_INPUT' and len((d/'signal.bedgraph').read_text().splitlines())==2;checks.append('covered-base estimand retains missing rows and omits finite-track output without inventing zeros')
        run('missing-rejected','compare-native',first,sizes,compare_args(second,missing='error'),expected=2);checks.append('strict missing policy fails with retained partials and actionable recovery')
        simple=file('simple.sizes','c\t11\n');counts=file('counts.bdg','c\t0\t5\t10\nc\t5\t10\t20\nc\t10\t11\t2\n')
        for mode,factor in [('NONE',2),('CPM',1),('RPKM',200),('BPM',1),('RPGC',.01)]:
            options=['--normalization',mode,'--count-kind','raw']
            if mode=='NONE':options+=['--scale-factor','2']
            else:options+=['--mapped-alignments','1000000']
            if mode=='RPGC':options+=['--effective-genome-size','1000000','--represented-length','100']
            d=run('scale-'+mode,'scale-native',counts,simple,options);rows=table(d/'results.tsv');assert [float(r['input_bin_count']) for r in rows]==[10,20,2];assert all(math.isclose(float(r['value']),c*factor,rel_tol=1e-14) for r,c in zip(rows,[10,20,2]));assert 'SUPPLIED NORMALIZATION CONTRACT' in (d/'WARNINGS.md').read_text()
        checks.append('independent NONE/CPM/RPKM/BPM/RPGC supplied-depth units and nominal-width terminal-bin behavior')
        bad=file('fraction.bdg','c\t0\t5\t1.5\n');run('raw-fraction-rejected','scale-native',bad,simple,['--normalization','NONE','--count-kind','raw'],expected=2);d=run('estimated-count','scale-native',bad,simple,['--normalization','NONE','--count-kind','estimate']);assert float(table(d/'results.tsv')[0]['value'])==1.5 and 'PROCESSED/WEIGHTED' in (d/'WARNINGS.md').read_text();checks.append('fractional inputs cannot silently claim raw counts; explicit estimate warning retained')
        run('depth-zero-rejected','scale-native',counts,simple,['--normalization','CPM','--count-kind','raw','--mapped-alignments','0'],expected=2);checks.append('zero normalized depth fails rather than inventing a library denominator')
        zero=file('zero.bdg','c\t0\t5\t0\nc\t5\t11\t0\n');d=run('zero-filter','compare-native',zero,simple,compare_args(zero,'ratio',invalid='retain')+['--pseudo1','1','--pseudo2','2','--skip-zero-zero','true']);assert all(r['status']=='omitted-zero-over-zero' and r['value']=='NA' for r in table(d/'results.tsv'));assert (d/'signal.bedgraph').stat().st_size==0 and 'NO FINITE' in (d/'WARNINGS.md').read_text();checks.append('zero filtering precedes pseudocounts and all-omitted output remains prominently warned')
        huge=file('long.sizes','long\t5000000001\n');x=file('long-first.bdg','long\t0\t5000000001\t1\n');y=file('long-second.bdg','long\t0\t5000000001\t2\n');d=run('long-bounds','compare-native',x,huge,compare_args(y,'ratio',missing='error'),bin_size=5000000000);rows=table(d/'results.tsv');assert len(rows)==2 and rows[-1]['end']=='5000000001' and all(float(r['value'])==.5 for r in rows);checks.append('64bit coordinates and final reference endpoint survive executable CLI without narrowing')
        assert all(sha(p)==h for p,h in sources.items()) and sha(jar)==initial;status='passed'
    except Exception as failure:error=repr(failure)
    record=dict(status=status,error=error,jar=str(jar),jar_sha256=sha(jar),steps=steps,checks=checks,check_count=len(checks),original_function_rows=reference_rows,fixture_directory=str(out),verifier_sha256=sha(Path(__file__)),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),scope='Actual executable-JAR native supplied-bedGraph/declared-count methods only. Frozen original arithmetic references plus independent geometry/unit/error truth. No upstream BAM/BigWig/matrix/plot runtime,published defaults or full-paper parity.')
    a.report.parent.mkdir(parents=True,exist_ok=True)
    if a.report.exists():
        old=json.loads(a.report.read_text());record['validation_history']=old.get('validation_history',[])+[{k:v for k,v in old.items() if k!='validation_history'}]
    a.report.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');(out/'validation.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(dict(status=status,runs=len(steps),checks=len(checks),original_function_rows=reference_rows,error=error)));return 0 if status=='passed' else 1
if __name__=='__main__':raise SystemExit(main())
