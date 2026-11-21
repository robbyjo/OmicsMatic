"""Exercise the new original-package worker before building the Java adapter."""
from pathlib import Path
import argparse,json,shutil,subprocess
ROOT=Path(__file__).resolve().parents[3]

def mounted(path):return '/mnt/d/'+path.resolve().relative_to(Path('D:/')).as_posix()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    previous=ROOT.parent/'mo-1/build/monocle100-1';record=json.loads((previous/'install-1/RESULT.json').read_bytes());command=json.loads((previous/'original-weights-5/STARTED.json').read_bytes())['command'];old=mounted(previous/'original-weights-5');command=[value.replace(old,mounted(out)) for value in command];index=command.index('--vanilla')
    for name in ['home','tmp','cache']:(out/name).mkdir()
    def run(label,cmd):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:process=subprocess.run(cmd,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
        save(out/(label+'.PROCESS.json'),dict(command=cmd,exit_code=process.returncode));print(label,process.returncode,flush=True)
        if process.returncode:print((out/(label+'.stderr')).read_text(errors='replace')[-2500:])
        return process.returncode
    extracted=out/'reference-inputs';extracted.mkdir()
    cmd=command[:index+1]+[mounted(Path(__file__).with_name('prepare_reference_inputs.R')),mounted(extracted),*record['libraries'],mounted(previous/'original-weights-5')]
    assert run('extract',cmd)==0
    home=out/'worker-smoke';(home/'worker').mkdir(parents=True);(home/'inputs').mkdir();shutil.copytree(ROOT/'src/main/resources/org/jlinalg/omics/monocle100-neighbors',home/'source')
    shutil.copyfile(extracted/'27-5-principal_graph/cds.rds',home/'inputs/source-cds.rds')
    (home/'worker/runtime-paths.txt').write_text('\n'.join(record['libraries'])+'\n',encoding='utf-8',newline='\n')
    save(home/'worker/runtime.json',dict(r_libraries=record['libraries']));save(home/'worker/parameters.json',dict(engine='original',k=5,neighbor_graph='principal_graph',reduction_slot='UMAP',seed=100021,maximum_cells=1000000,maximum_rank_entries=16000000))
    cmd=command[:index+1]+[mounted(home/'source/worker.R'),mounted(home)];code=run('worker',cmd)
    if (home/'worker/terminal.tsv').exists():print((home/'worker/terminal.tsv').read_text(errors='replace'))
    return code

if __name__=='__main__':raise SystemExit(main())
