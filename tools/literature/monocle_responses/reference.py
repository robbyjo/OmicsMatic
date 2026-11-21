"""Evaluate the exact official Monocle arithmetic branch and original spdep controls."""
from pathlib import Path
import argparse,json,shutil,subprocess,tarfile
ROOT=Path(__file__).resolve().parents[3]
def mount(path):return '/mnt/d/'+path.resolve().relative_to(Path('D:/')).as_posix()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    for name in ['home','tmp','cache']:(out/name).mkdir()
    source=ROOT/'src/main/resources/org/jlinalg/omics/monocle100-neighbors/reference/monocle3/R/graph_test.R';shutil.copyfile(source,out/'OFFICIAL-graph_test.R');archive=ROOT/'docs/literature/handover-2026-11-21-neighbors/upstream/spdep_1.4-1.tar.gz'
    with tarfile.open(archive) as stream:
        members=[item for item in stream.getmembers() if item.name.endswith('/R/lm.morantest.R')];assert len(members)==1;raw=stream.extractfile(members[0]).read()
    (out/'OFFICIAL-lm.morantest.R').write_bytes(raw);shutil.copyfile(Path(__file__).with_name('reference.R'),out/'EXECUTED-reference.R')
    R=ROOT.parents[1];previous=R/'build/monocle100-neighbor-diagnostics-20261121-1';command=json.loads((previous/'COMMAND.json').read_bytes());command=command[:command.index('--vanilla')+1];command=[value.replace(mount(previous),mount(out)).replace('LANG=C','LANG=C.UTF-8').replace('LC_ALL=C','LC_ALL=C.UTF-8') for value in command];libraries=json.loads((ROOT.parent/'mo-1/build/monocle100-1/install-1/RESULT.json').read_bytes())['libraries'];command +=[mount(out/'EXECUTED-reference.R'),mount(out),*libraries,mount(out/'OFFICIAL-graph_test.R'),mount(out/'OFFICIAL-lm.morantest.R')];save(out/'COMMAND.json',command)
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=700)
    save(out/'PROCESS.json',dict(exit_code=p.returncode));print('Official response/spdep reference exit',p.returncode,flush=True)
    if p.returncode:print((out/'stdout').read_text()[-2000:],(out/'stderr').read_text()[-3000:]);return p.returncode
    result=json.loads((out/'RESULT.json').read_bytes());print('PASS',len(result['cases']),'complete cases;',sum(len(rows) for case in result['cases'].values() for rows in case['tests'].values()),'original residual Moran gene tests',flush=True);return 0
if __name__=='__main__':raise SystemExit(main())
