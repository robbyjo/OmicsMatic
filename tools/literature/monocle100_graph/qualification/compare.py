"""Independently compare original-reference and adapter complete scientific objects."""
from pathlib import Path
import argparse,json,shutil,subprocess
ROOT=Path(__file__).resolve().parents[4]

def mount(path):return '/mnt/d/'+path.resolve().relative_to(Path('D:/')).as_posix()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--matrix',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);reference=args.reference.resolve();matrix=args.matrix.resolve();result=json.loads((matrix/'RESULT.json').read_bytes());original=json.loads((reference/'RESULT.json').read_bytes());assert result['total']==len(original['cases'])
    profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/monocle100-graph/PROFILE.json').read_bytes())
    plan=dict(cases=[dict(name=name,capture=mount(matrix/name/'capture'),reference=mount(reference/name)) for name in original['cases']],guarded_functions=profile['guarded_functions'],reference_source_signatures=original['source_function_signatures']);save(out/'PLAN.json',plan)
    for name in ['home','tmp','cache']:(out/name).mkdir()
    R=ROOT.parents[1];previous=R/'build/monocle100-neighbor-diagnostics-20261121-1';command=json.loads((previous/'COMMAND.json').read_bytes());i=command.index('--vanilla');command=command[:i+1];command=[value.replace(mount(previous),mount(out)).replace('LANG=C','LANG=C.UTF-8').replace('LC_ALL=C','LC_ALL=C.UTF-8') for value in command]
    libraries=json.loads((ROOT.parent/'mo-1/build/monocle100-1/install-1/RESULT.json').read_bytes())['libraries'];shutil.copyfile(Path(__file__).with_name('compare_original.R'),out/'EXECUTED-compare_original.R');command +=[mount(out/'EXECUTED-compare_original.R'),mount(out),*libraries,mount(out/'PLAN.json'),mount(R/'build/literature-review/monocle-runtime/source-intake-1/1.0.0/source')];save(out/'COMMAND.json',command)
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=700)
    save(out/'PROCESS.json',dict(exit_code=p.returncode));print('Complete original comparison exit',p.returncode,flush=True)
    if p.returncode:print((out/'stdout').read_text()[-3000:],(out/'stderr').read_text()[-3000:]);return p.returncode
    print((out/'RESULT.json').read_text()[-1000:]);return 0
if __name__=='__main__':raise SystemExit(main())
