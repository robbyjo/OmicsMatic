"""Real executable-JAR MAGMA checks with independently frozen upstream scientific outputs."""
import argparse,datetime as dt,hashlib,json,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];DATA=ROOT/'docs/literature/omics-2023-2026';FIXTURE=ROOT/'src/test/resources/magma'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def table(p):
    lines=[s.split() for s in p.read_text(encoding='utf-8').splitlines() if s.strip() and not s.startswith('#')];return [dict(zip(lines[0],r)) for r in lines[1:]]
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--home',type=Path,default=ROOT/'build/omics-magma');a=ap.parse_args();jar=a.jar.resolve();out=ROOT/'build/literature-review'/('magma-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'));out.mkdir();steps=[];checks=[];before={p:sha(p) for p in [jar,*FIXTURE.iterdir()] if p.is_file()};status='failed';error=None
    def run(name,method,options,expected=0,build='GRCh38'):
        dest=out/name;command=['java','-jar',str(jar),'magma-tools','--method',method,'--build',build,*map(str,options),'--out',str(dest),'--no-config'];p=subprocess.run(command,cwd=ROOT,capture_output=True,text=True,timeout=360);log=out/(name+'.console.log');log.write_text(p.stdout+p.stderr,encoding='utf-8');steps.append(dict(name=name,command=command,exit_code=p.returncode,expected_exit=expected,output=str(dest),console_sha256=sha(log)));assert p.returncode==expected,(name,p.returncode,p.stdout,p.stderr,(dest/'WARNINGS.md').read_text() if (dest/'WARNINGS.md').exists() else '')
        state=json.loads((dest/'analysis-status.json').read_text())['status'];assert state.startswith('completed') if expected==0 else state==('failed' if expected==2 else 'skipped-dependency-unavailable');assert (dest/'results.tsv').exists() if expected==0 else not(dest/'results.tsv').exists()
        if expected:assert 'Required action:' in (dest/'WARNINGS.md').read_text()
        return dest
    def same(actual,reference):assert table(actual)==table(FIXTURE/reference),reference
    annotation_args=['--genes',FIXTURE/'genes.tsv','--snps',FIXTURE/'snps.tsv'];gene_args=['--home',a.home,'--reference-panel',FIXTURE/'reference','--ancestry','SYNTHETIC','--input-mode','summary','--pvalues',FIXTURE/'gwas.tsv','--seed','42']
    try:
        with zipfile.ZipFile(jar) as z:assert not any(n.endswith(('magma.exe','manual_v1.10.pdf','magma-1.10-windows.zip')) for n in z.namelist())
        checks.append('No restricted upstream MAGMA binary/manual/archive redistributed in the executable JAR')
        annotation=run('native-annotation','annotate-native',annotation_args);assert len(table(annotation/'results.tsv'))==202 and 'EMPTY ANNOTATION' in (annotation/'WARNINGS.md').read_text();original=run('original-annotation','annotate',annotation_args+['--home',a.home]);assert (annotation/'genes.annot').read_text().splitlines()==(original/'genes.annot').read_text().splitlines();checks.append('all995variant/202gene memberships,expanded bounds and empty status agree with original annotation')
        gene=None
        for model in ['mean','top','multi']:
            d=run('summary-'+model,'gene',['--annotation',annotation,*gene_args,'--model',model]);same(d/'results.tsv','summary-'+model+'.genes.out.txt');assert len(table(d/'gene-status.tsv'))==202
            if model=='mean':gene=d
        checks.append('all201gene results for mean/top/multi agree with independently frozen original runs')
        d=run('raw-pc','gene',['--home',a.home,'--annotation',annotation,'--reference-panel',FIXTURE/'reference','--ancestry','SYNTHETIC','--input-mode','raw','--phenotype-kind','quantitative','--model','linreg','--prune','.999','--seed','42']);same(d/'results.tsv','raw-linreg.genes.out.txt');assert all(r['N']=='160' for r in table(d/'results.tsv'));checks.append('raw phenotype PC/F-test and99.9percent pruning match original full-gene results')
        d=run('sets','sets',['--home',a.home,'--gene-bundle',gene,'--sets',FIXTURE/'sets.annot','--self-contained','true','--seed','42']);same(d/'results.tsv','sets.gsa.out.txt');same(d/'self-contained.tsv','sets.gsa.self.out.txt');assert float(table(d/'results.tsv')[0]['P'])<1e-50;checks.append('competitive and self-contained gene-set models recover independent known pathway signal and exact references')
        d=run('properties','properties',['--home',a.home,'--gene-bundle',gene,'--properties',FIXTURE/'properties.tsv','--property-direction','positive','--seed','42']);same(d/'results.tsv','properties.gsa.out.txt');checks.append('continuous gene-property model matches frozen reference')
        d=run('conditional','gsa',['--home',a.home,'--gene-bundle',gene,'--sets',FIXTURE/'sets.annot','--properties',FIXTURE/'properties.tsv','--condition','STRONG','--analyse','Brain','--property-direction','positive','--seed','42']);assert float(next(r['P'] for r in table(d/'results.tsv') if r['VARIABLE']=='Brain'))>.001;checks.append('conditional model removes the planted shared set/property signal instead of reporting the marginal P-value')
        joint=out/'joint.tsv';joint.write_text('Brain Liver\n');d=run('joint','gsa',['--home',a.home,'--gene-bundle',gene,'--properties',FIXTURE/'properties.tsv','--joint',joint,'--seed','42']);assert {r['VARIABLE'] for r in table(d/'results.tsv')}=={'Brain','Liver'};checks.append('joint gene-property model has exact requested variable identities')
        for kind in ['binary-zero-one','binary-one-two','quantitative']:
            prefix=out/kind
            for ext in ['bed','bim']:Path(str(prefix)+'.'+ext).write_bytes((FIXTURE/('reference.'+ext)).read_bytes())
            fam=[]
            for i,line in enumerate((FIXTURE/'reference.fam').read_text().splitlines()):
                f=line.split();f[5]=str(i%2+(1 if kind=='binary-one-two' else 0));fam.append('\t'.join(f))
            Path(str(prefix)+'.fam').write_text('\n'.join(fam)+'\n');d=run('coded-'+kind,'gene',['--home',a.home,'--annotation',annotation,'--reference-panel',prefix,'--ancestry','SYNTHETIC','--input-mode','raw','--phenotype-kind',kind,'--model','linreg','--seed','42']);assert all(r['N']=='160' for r in table(d/'results.tsv'))
            if kind=='quantitative':assert 'shifted by+3' in (d/'WARNINGS.md').read_text()
        assert table(out/'coded-binary-zero-one/results.tsv')==table(out/'coded-binary-one-two/results.tsv');checks.append('binary0/1 and1/2 preserve all controls and identical statistics; quantitative0/1 retains its declared model')
        uniform=out/'uniform-gwas.tsv';uniform.write_text('\n'.join('\t'.join(s.split('\t')[:-1]) for s in (FIXTURE/'gwas.tsv').read_text().splitlines())+'\n');args=gene_args.copy();args[args.index('--pvalues')+1]=uniform;d=run('uniform-N','gene',['--annotation',annotation,*args,'--sample-size','10000']);assert all(r['N']=='10000' for r in table(d/'results.tsv'));checks.append('explicit uniform total GWAS sample size differs from per-variant N without silent substitution')
        bad=out/'bad-coordinate.tsv';bad.write_text((FIXTURE/'gwas.tsv').read_text().replace('10025','10026',1));args=gene_args.copy();args[args.index('--pvalues')+1]=bad;run('wrong-coordinate','gene',['--annotation',annotation,*args],2);run('wrong-build','gene',['--annotation',annotation,*gene_args],2,build='GRCh37');checks.append('same-ID coordinate and sealed-build mismatches fail with retained actionable diagnostics')
        run('missing-runtime','gene',['--home',out/'absent','--annotation','missing','--reference-panel','missing','--ancestry','DECLARED','--input-mode','summary','--pvalues','missing'],3);checks.append('missing private application skips before opening data and gives permitted download/recovery')
        assert all(sha(p)==h for p,h in before.items());status='passed'
    except Exception as e:error=repr(e)
    record=dict(status=status,error=error,jar=str(jar),jar_sha256=sha(jar),upstream_executable_sha256=sha(a.home/'magma.exe'),steps=steps,checks=checks,check_count=len(checks),fixture_directory=str(out),verifier_sha256=sha(Path(__file__)),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),scope='Unmodified upstream scientific fixtures through executable JAR, including exact804association/reference rows, gene sets/properties, phenotype coding, model/bundle failures and dependency recovery. No FUMA/R wrapper/PoPS/scDRS/historical or full-paper parity.')
    report=DATA/'magma-packaged-validation.json'
    if report.exists():
        old=json.loads(report.read_text(encoding='utf-8'));record['validation_history']=old.get('validation_history',[])+[{k:v for k,v in old.items() if k!='validation_history'}]
    report.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');(out/'validation.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(dict(status=status,steps=len(steps),checks=len(checks),error=error)));return 0 if status=='passed' else 1
if __name__=='__main__':raise SystemExit(main())
