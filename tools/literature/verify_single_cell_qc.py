"""Executable-JAR checks against independent original-package references and known counts."""
import argparse
import csv
import datetime as dt
import hashlib
import json
import math
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2];DATA=ROOT/'docs/literature/omics-2023-2026';FIXTURE=ROOT/'src/test/resources/single-cell-qc'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def table(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t',quoting=csv.QUOTE_NONE))
def matrix(p):
    lines=p.read_text(encoding='utf-8').splitlines();lines=[s for s in lines if s and not s.startswith('%')];n,m,z=map(int,lines[0].split());values={}
    for s in lines[1:]:
        a,b,v=s.split();key=int(a)-1,int(b)-1;values[key]=values.get(key,0)+float(v)
    assert len(lines)-1==z;return n,m,values
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--rscript',type=Path,default=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe'));ap.add_argument('--r-library',type=Path,default=ROOT/'build/omics-sctransform-r');ap.add_argument('--python',type=Path,default=ROOT/'build/omics-scrublet-env-locked/Scripts/python.exe');a=ap.parse_args();jar=a.jar.resolve()
    out=ROOT/'build/literature-review'/('single-cell-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'));out.mkdir(parents=True);steps=[];checks=[];before={p:sha(p) for p in [jar,*FIXTURE.glob('*')] if p.is_file()}
    def run(name,method,options,expected=0):
        dest=out/name;command=['java','-jar',str(jar),'single-cell-qc','--method',method,*map(str,options),'--out',str(dest),'--no-config'];p=subprocess.run(command,cwd=ROOT,capture_output=True,text=True,timeout=360);log=out/(name+'.console.log');log.write_text(p.stdout+p.stderr,encoding='utf-8');steps.append(dict(name=name,command=command,exit_code=p.returncode,expected_exit=expected,console_sha256=sha(log),output=str(dest)))
        assert p.returncode==expected,(name,p.returncode,p.stdout,p.stderr,(dest/'WARNINGS.md').read_text(encoding='utf-8') if (dest/'WARNINGS.md').exists() else '')
        status=json.loads((dest/'analysis-status.json').read_text(encoding='utf-8'))['status'];assert status.startswith('completed') if expected==0 else status==('skipped-dependency-unavailable' if expected==3 else 'failed');assert (dest/'results.tsv').exists() if expected==0 else not(dest/'results.tsv').exists()
        if expected:assert 'Required action:' in (dest/'WARNINGS.md').read_text(encoding='utf-8') and 'partial' in (dest/'WARNINGS.md').read_text(encoding='utf-8')
        return dest
    base=['--dataset','LIB1','--seed','42','--matrix',FIXTURE/'counts.mtx','--features',FIXTURE/'features.tsv','--barcodes',FIXTURE/'barcodes.tsv'];scr=['--python',a.python,'--components','10','--neighbors','10'];sct=['--rscript',a.rscript,'--r-library',a.r_library,'--fit-cells','200'];status='failed';error=None
    try:
        d=run('counts','counts',base[4:]);observed=table(d/'cell-counts.tsv');n,m,values=matrix(FIXTURE/'counts.mtx');totals=[0.]*m;detected=[0]*m
        for (i,j),v in values.items():totals[j]+=v;detected[j]+=v>0
        for j,row in enumerate(observed):assert row['cell_id']=='C'+str(j) and float(row['selected_feature_total'])==totals[j] and int(row['detected_features'])==detected[j]
        checks.append('native canonical totals/detected genes independently summed from every source coordinate')
        f=out/'scores.tsv';f.write_text('id\tsimulated_neighbors\tneighbors\tsimulation_ratio\texpected_rate\trate_sd\nzero\t0\t10\t2\t0.1\t0.02\nfull\t10\t10\t2\t0.1\t0.02\n');d=run('native-score','doublet-score',['--input',f,'--threshold','.2']);r=table(d/'results.tsv');assert abs(float(r[0]['score'])-1/199)<1e-15 and r[1]['predicted_doublet']=='true';checks.append('native Bayesian score known independent endpoint value and strict calls')
        f=out/'nb.tsv';f.write_text('id\tcount\tmean\ttheta\tminimum_variance\tclip_low\tclip_high\np\t4\t2\tInf\t0\t-Inf\tInf\nfloor\t4\t2\t1\t100\t-1\t1\n');d=run('native-residual','nb-residual',['--input',f]);r=table(d/'results.tsv');assert abs(float(r[0]['pearson_residual'])-math.sqrt(2))<1e-15 and float(r[1]['pearson_residual'])==.2;checks.append('native Poisson and NB variance-floor residual known truth')
        d=run('scrublet-exact','scrublet',base+scr+['--approximate','false']);r=table(d/'observed.tsv');ref=table(FIXTURE/'scrublet-neighbor-scores.tsv');assert len(r)==252 and len(table(d/'simulated.tsv'))==504
        for i,row in enumerate(r):
            assert row['cell_id']=='C'+str(i)
            for key in ['score','standard_error']:assert abs(float(row[key])-float(ref[i][key]))<2e-11
        assert 'invalid value encountered in sqrt' in (d/'WARNINGS.md').read_text(encoding='utf-8');checks.append('original exact Scrublet all252 scores/errors,504 parents and retained variance warning')
        d=run('scrublet-approximate','scrublet',base+scr+['--metric','cosine','--approximate','true','--threshold','1']);assert all(r['predicted_doublet']=='false' for r in table(d/'observed.tsv'));assert 'NO DETECTABLE SIMULATED DOUBLETS' in (d/'WARNINGS.md').read_text(encoding='utf-8');checks.append('actual Annoy cosine path and explicit threshold with undefined overall-rate warning')
        for engine in ['vst','seurat']:
            for flavor in ['v1','v2']:
                options=['--fit-genes','300'] if engine=='vst' else ['--metadata',FIXTURE/'phase-covariates.tsv','--regress','S.Score,G2M.Score','--variable-features','100']
                d=run(engine+'-'+flavor,'sct-'+engine,base+sct+['--flavor',flavor]+options);genes=(d/'residual-features.tsv').read_text().splitlines();cells=(d/'barcodes.tsv').read_text().splitlines();assert cells==['C'+str(i) for i in range(252)];n,m,values=matrix(d/'residuals.mtx');assert n==len(genes) and m==252
                for row in table(FIXTURE/('sctransform-residuals.tsv' if engine=='vst' else 'seurat-regressed-residuals.tsv')):
                    if row['flavor']==flavor:assert abs(values.get((genes.index(row['gene']),cells.index(row['cell'])),0)-float(row['expected']))<(2e-8 if engine=='vst' else 3e-7)
                assert (d/('vst-model.rds' if engine=='vst' else 'seurat-model.rds')).is_file();assert (d/'corrected-counts.mtx').is_file();assert (d/'sct-logdata.mtx').exists()==(engine=='seurat');checks.append('actual '+engine+' '+flavor+' independent frozen model/phase-regression residuals and distinct layers')
        # Preserve and explicitly label a processed estimate, rather than claiming raw-UMI input.
        f=out/'estimates.mtx';n,m,values=matrix(FIXTURE/'counts.mtx');f.write_text('%%MatrixMarket matrix coordinate real general\n'+f'{n} {m} {len(values)}\n'+''.join(f'{i+1} {j+1} {v+.25}\n' for (i,j),v in values.items()));estimate=base.copy();estimate[estimate.index('--matrix')+1]=f
        d=run('estimated-vst','sct-vst',estimate+sct+['--flavor','v1','--count-kind','estimate','--fit-genes','300']);assert 'FRACTIONAL/PROCESSED INPUT' in (d/'WARNINGS.md').read_text(encoding='utf-8');checks.append('actual fractional-estimate VST preserves prominent non-UMI assumption warning')
        run('failed-components','scrublet',base+['--python',a.python,'--components','200','--neighbors','10'],2);checks.append('invalid model geometry fails with retained partial counts and actionable recovery')
        for method,options in [('scrublet',['--python',out/'absent']),('sct-vst',['--rscript',a.rscript,'--r-library',out/'absent-library'])]:run('missing-'+method,method,base+options,3)
        checks.append('both dependency skips publish clear cause/action without successful result')
        assert all(sha(p)==h for p,h in before.items());status='passed'
    except Exception as failure:error=repr(failure)
    record=dict(status=status,error=error,jar=str(jar),jar_sha256=sha(jar),steps=steps,checks=checks,check_count=len(checks),fixture_directory=str(out),verifier_sha256=sha(Path(__file__)),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),scope='Actual executable-JAR resources and independent synthetic/direct-upstream references; no whole-paper or historical-version equivalence')
    report=DATA/'single-cell-qc-packaged-validation.json'
    if report.exists():
        old=json.loads(report.read_text(encoding='utf-8'));record['validation_history']=old.get('validation_history',[])+[{k:v for k,v in old.items() if k!='validation_history'}]
    report.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');(out/'validation.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(dict(status=status,steps=len(steps),checks=len(checks),error=error)));return 0 if status=='passed' else 1

if __name__=='__main__':raise SystemExit(main())
