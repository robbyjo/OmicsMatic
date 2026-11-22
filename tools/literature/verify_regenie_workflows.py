"""Exercise the executable JAR against the pinned real REGENIE runtime; retain failures."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def rows(path):
    lines=[line for line in path.read_text().splitlines() if not line.startswith('##')]
    header=lines[0].split();records=[dict(zip(header,line.split(),strict=True)) for line in lines[1:]]
    result={(r['ID'],r['TEST']):r for r in records};assert len(result)==len(records),'Duplicate ID/test in fixture output';return result


def same(expected,actual):
    left,right=rows(expected),rows(actual);assert left.keys()==right.keys()
    for key,a in left.items():
        b=right[key]
        for name in ['CHROM','GENPOS','ALLELE0','ALLELE1','N','TEST']:assert a[name]==b[name],(key,name,a[name],b[name])
        for name in ['BETA','SE','LOG10P']:
            if a[name]=='NA' or b[name]=='NA':assert a[name]==b[name],(key,name)
            else:assert abs(float(a[name])-float(b[name]))<=2e-5*max(1,abs(float(a[name]))),(key,name,a[name],b[name])


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--runtime',type=Path,default=ROOT/'build/omics-regenie');args=parser.parse_args()
    jar=args.jar.resolve();runtime=args.runtime.resolve();fixture=ROOT/'src/test/resources/regenie';out=ROOT/'build/literature-review'/('regenie-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True);attempts=[]
    def run(name,method,format='bed',genotype=None,extra=(),expected=0,runtime_=None,build='b37',alleles=None):
        dest=out/name;command=['java','-jar',str(jar),'regenie-tools','--method',method,'--runtime',str(runtime_ or runtime),'--format',format,'--genotypes',str(genotype or fixture/'example'),'--allele-order',alleles or ('pvar' if format=='pgen' else 'last'),'--genome-build',build,'--block-size','100','--out',str(dest),'--no-config',*map(str,extra)]
        p=subprocess.run(command,capture_output=True,text=True,timeout=300,cwd=ROOT);(out/(name+'.console.log')).write_text(p.stdout+p.stderr)
        attempts.append({'name':name,'command':command,'exit_code':p.returncode,'expected_exit':expected,'output':str(dest),'console_sha256':digest(out/(name+'.console.log'))})
        assert p.returncode==expected,(name,p.stdout,p.stderr,(dest/'WARNINGS.md').read_text() if(dest/'WARNINGS.md').exists() else '')
        if expected==0:assert(dest/'results.tsv').is_file()
        else:assert not(dest/'results.tsv').exists() and 'Required action:' in(dest/'WARNINGS.md').read_text()
        assert json.loads((dest/'analysis-status.json').read_text())['status'].startswith('completed' if expected==0 else 'skipped' if expected==3 else 'failed')
        return dest
    def fit(name,binary=False,genotype=None,phenotype=None):
        extra=['--phenotypes',phenotype or fixture/('phenotype_bin.txt' if binary else 'phenotype.txt'),'--phenotype-columns','Y1','--trait','binary' if binary else 'quantitative','--rint','false','--missing','per-trait','--covariates',fixture/'covariates.txt','--covariate-columns','V1,V2,V3']
        if binary:extra+=['--binary-coding','0/1']
        return run(name,'fit',genotype=genotype,extra=extra)
    def test(model,binary=False):
        return ['--model',model,'--test','additive','--min-mac','1','--correction','firth-approx' if binary else 'none',*(['--p-threshold','0.999'] if binary else [])]
    status,error='failed',None;reference_counts={}
    try:
        unavailable=run('dependency-forbidden-unavailable','fit',runtime_=out/'absent-runtime',extra=['--phenotypes','absent','--phenotype-columns','Y1','--trait','quantitative','--rint','false','--missing','complete'],expected=3)
        assert 'SKIPPED' in(out/'dependency-forbidden-unavailable.console.log').read_text() and '--allow-source-build' in(unavailable/'WARNINGS.md').read_text()
        for binary in [False,True]:
            label='binary' if binary else 'qt';model=fit(label+' model with spaces',binary);sealed={p:digest(p) for p in model.rglob('*') if p.is_file()}
            for format in ['bed','bgen','pgen']:
                result=run(label+'-'+format,'test',format,fixture/('example.bgen' if format=='bgen' else 'example'),extra=test(model,binary));same(fixture/('reference-binary.regenie' if binary else 'reference-qt.regenie'),result/'upstream/assoc_Y1.regenie')
                assert b'\r' not in(result/'upstream/predictions.list').read_bytes();reference_counts[label+'-'+format]=len(rows(result/'upstream/assoc_Y1.regenie'))
            assert all(digest(p)==sha for p,sha in sealed.items()),'Fit bundle changed during tests'
            if not binary:
                reversed_=run('qt-reference-first','test',extra=test(model),alleles='first');flipped=rows(reversed_/'upstream/assoc_Y1.regenie');original=rows(fixture/'reference-qt.regenie')
                for key,a in original.items():
                    b=flipped[key];assert a['ALLELE0']==b['ALLELE1'] and a['ALLELE1']==b['ALLELE0'];assert abs(float(a['BETA'])+float(b['BETA']))<2e-5
                run('wrong-build','test',extra=test(model),build='b38',expected=2)
                broken=out/'tampered-fit';shutil.copytree(model,broken);original_phi=(broken/'phenotypes.tsv').read_bytes();(broken/'phenotypes.tsv').write_bytes(original_phi+b' ')
                run('tampered-model','test',extra=test(broken),expected=2);assert(broken/'phenotypes.tsv').read_bytes()==original_phi+b' '
        model=fit('gene-model',genotype=fixture/'example_3chr');extra=['--model',model,'--test','additive','--min-mac','5','--correction','none','--annotations',fixture/'example_3chr.annotations','--set-list',fixture/'example_3chr.setlist','--masks',fixture/'example_3chr.masks','--mask-rule','max','--aaf-bins','0.01,0.05,0.2','--vc-max-aaf','0.2','--gene-tests','rgc','--sbat-draws','10','--sample',fixture/'example_3chr.sample']
        result=run('gene-rgc','genes','bgen',fixture/'example_3chr.bgen',extra=extra);same(fixture/'reference-genes.regenie',result/'upstream/assoc_Y1.regenie');reference_counts['genes']=len(rows(result/'upstream/assoc_Y1.regenie'))
        tests={r['TEST'] for r in rows(result/'upstream/assoc_Y1.regenie').values()};assert all(any(name in t for t in tests) for name in ['ACAT','SKAT','SBAT','GENE_P']);assert(result/'upstream/assoc_Y1.regenie').read_text().startswith('##MASKS=')
        forced=fit('declared-quantitative-01',phenotype=fixture/'phenotype_bin.txt');assert 'FEW QUANTITATIVE VALUES' in(forced/'WARNINGS.md').read_text();assert '--bt' not in(forced/'upstream/analysis.log').read_text()
        status='passed'
    except Exception as failure:error=repr(failure)
    record={'status':status,'error':error,'jar_sha256':digest(jar),'jar':str(jar),'runtime_executable_sha256':digest(runtime/'bin'/('regenie.exe' if (runtime/'bin/regenie.exe').exists() else 'regenie')),'runtime_marker_sha256':digest(runtime/'.jlinalg-regenie.json'),'checks':len(attempts),'steps':attempts,'reference_rows':reference_counts,'fixture_directory':str(out),'verifier_sha256':digest(Path(__file__)),'fixture_provenance_sha256':digest(fixture/'provenance.json'),'scope':'Actual executable JAR and real REGENIE 4.1.3 private Cygwin profile. Quantitative/binary BED/BGEN/PGEN, frozen direct-upstream statistics, allele reversal, gene RGC metadata/tests, explicit quantitative 0/1 choice, changed-model/build rejection and dependency skip. No full-package, old-version or whole-paper equivalence. Separate JUnit generated-signal test supplies independent effect truth.'}
    report=DATA/'regenie-packaged-validation.json'
    if report.exists():
        prior=json.loads(report.read_text());record['validation_history']=prior.get('validation_history',[])+[{k:v for k,v in prior.items() if k!='validation_history'}]
    (out/'validation.json').write_text(json.dumps(record,indent=2)+'\n');report.write_text(json.dumps(record,indent=2)+'\n');print(json.dumps({'status':status,'checks':len(attempts),'reference_rows':reference_counts,'error':error}));return 0 if status=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
