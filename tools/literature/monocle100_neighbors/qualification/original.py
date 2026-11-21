"""Compare every actual capture with fresh unchanged original RANN/calculateLW calls."""
from pathlib import Path
import argparse,json,shutil,subprocess
ROOT=Path(__file__).resolve().parents[4]
def mount(path):return '/mnt/d/'+path.resolve().relative_to(Path('D:/')).as_posix()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();matrix=args.matrix.resolve();result=json.loads((matrix/'RESULT.json').read_bytes());assert result['status']=='PASS_ALL66_ACTUAL_NEIGHBOR_EXPECTATIONS'
    out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    for name in ['home','tmp','cache']:(out/name).mkdir()
    plan=[]
    for name,row in result['results'].items():plan.append(dict(name=name,capture=mount(matrix/(row['engine']+'-'+row['case'])/'capture'),expected_success=row['expected_exit_code']==0))
    assert len(plan)==66;save(out/'PLAN.json',dict(cases=plan))
    R=ROOT.parents[1];previous=R/'build/monocle100-neighbor-diagnostics-20261121-1';command=json.loads((previous/'COMMAND.json').read_bytes());i=command.index('--vanilla');command=command[:i+1]
    command=[value.replace(mount(previous),mount(out)).replace('LANG=C','LANG=C.UTF-8').replace('LC_ALL=C','LC_ALL=C.UTF-8') for value in command]
    libraries=json.loads((ROOT.parent/'mo-1/build/monocle100-1/install-1/RESULT.json').read_bytes())['libraries']
    shutil.copyfile(Path(__file__).with_name('compare_original.R'),out/'compare_original.R');command +=[mount(out/'compare_original.R'),mount(out),*libraries,mount(out/'PLAN.json')]
    save(out/'COMMAND.json',command)
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=700)
    save(out/'PROCESS.json',dict(exit_code=process.returncode));print('Independent original comparison exit',process.returncode,flush=True)
    if process.returncode:print((out/'stdout').read_text(errors='replace')[-2000:],(out/'stderr').read_text(errors='replace')[-3000:]);return process.returncode
    print((out/'RESULT.json').read_text()[:700]);return 0
if __name__=='__main__':raise SystemExit(main())
