"""Direct unmodified MAGMA calls on frozen synthetic data, independent of the Java adapter."""
import argparse,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--home',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);ap.add_argument('--fixtures',type=Path,default=ROOT/'src/test/resources/magma');a=ap.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);data=a.fixtures.resolve();exe=(a.home/'magma.exe').resolve();assert sha(exe)=='e05e3d963215a973064852ec33640118d65745884cc800a5119ca7cffe6cb859';before={p:sha(p) for p in data.iterdir() if p.is_file()};calls=[]
    gene_locations=out/'genes.loc';gene_locations.write_text('\n'.join((data/'genes.tsv').read_text().splitlines()[1:])+'\n')
    def run(name,args):
        command=[str(exe),*map(str,args),'--out',str(out/name)];p=subprocess.run(command,capture_output=True,text=True,timeout=180);log=out/(name+'.console.log');log.write_text(p.stdout+p.stderr,encoding='utf-8');assert p.returncode==0,(name,p.stdout,p.stderr);calls.append(dict(name=name,command=command,exit_code=p.returncode,console_sha256=sha(log)))
    run('annotation',['--annotate','window=0','--snp-loc',data/'reference.bim','--gene-loc',gene_locations])
    for model in ['mean','top','multi']:run('summary-'+model,['--bfile',data/'reference','--gene-annot',out/'annotation.genes.annot.txt','--pval',data/'pvalues.tsv','ncol=N','duplicate=error','--gene-model','snp-wise='+model,'--seed','42'])
    run('raw-linreg',['--bfile',data/'reference','--gene-annot',out/'annotation.genes.annot.txt','--gene-model','linreg','--gene-settings','prune=.999','--seed','42'])
    run('sets',['--gene-results',out/'summary-mean.genes.raw','--set-annot',data/'sets.annot','--model','direction=pos','correct=all','self-contained','--settings','gene-info','abbreviate=0','--seed','42'])
    run('properties',['--gene-results',out/'summary-mean.genes.raw','--gene-covar',data/'properties.tsv','missing-values=drop','max-miss=0','--model','direction=pos','correct=all','--settings','gene-info','abbreviate=0','--seed','42'])
    names=['annotation.genes.annot.txt',*[x+'.genes.out.txt' for x in ['summary-mean','summary-top','summary-multi','raw-linreg']],'sets.gsa.out.txt','sets.gsa.self.out.txt','properties.gsa.out.txt']
    for name in names:assert (out/name).read_text().splitlines()==(data/name).read_text().splitlines(),name
    assert all(sha(p)==h for p,h in before.items());record=dict(status='passed',calls=calls,exact_reference_files=names,executable_sha256=sha(exe),reproducer_sha256=sha(Path(__file__)),scope='Eight independently reproduced original-program annotation/association/set/property files; frozen synthetic source bytes unchanged. Program remains private and unmodified.');(out/'reference-reproduction.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(dict(status='passed',calls=len(calls),exact_reference_files=len(names))))
if __name__=='__main__':main()
