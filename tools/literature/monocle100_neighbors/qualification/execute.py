"""Run one complete synthetic CDS through an explicit Linux Java original/Java engine."""
from pathlib import Path
import argparse,json,subprocess,time
ROOT=Path(__file__).resolve().parents[4]

def mount(path):return '/mnt/d/'+path.resolve().relative_to(Path('D:/')).as_posix()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--classes',type=Path)
    parser.add_argument('--case',required=True);parser.add_argument('--engine',choices=['original','java'],required=True)
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    inputs=ROOT.parent/'mo-2/build/monocle100-neighbors-1/worker-smoke-2/reference-inputs';records=json.loads((inputs/'RESULT.json').read_bytes())['records'];case=records[args.case]
    runtime=ROOT.parent/'mo-2/build/monocle100-neighbors-1/pilot-inputs-1/runtime.json';(out/'runtime.json').write_bytes(runtime.read_bytes())
    request=dict(schema_version=1,source_cds=mount(inputs/args.case/'cds.rds'),runtime=mount(out/'runtime.json'),engine=args.engine,neighbor_graph=case['neighbor_graph'],k=case['k'],reduction_slot=case['reduction_slot'],seed=100021,dataset_provenance='All original synthetic cells/genes and complete prepared graph/embedding for '+args.case+'; no author pipeline.',out=mount(out/'capture'))
    save(out/'request.json',request)
    cp=mount(args.jar)
    if args.classes:cp=mount(args.classes)+':'+mount(ROOT/'src/main/resources')+':'+cp
    command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','/usr/bin/timeout','--signal=TERM','--kill-after=20','720','java','-Xmx2g','-cp',cp,'org.jlinalg.cli.Monocle100NeighborsCli','RUN','--request',mount(out/'request.json')]
    save(out/'STARTED.json',dict(command=command,started_unix=time.time(),quick_compile_only=args.classes is not None))
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
    save(out/'PROCESS.json',dict(exit_code=process.returncode))
    print(args.case,args.engine,'exit',process.returncode,flush=True)
    if process.returncode:print((out/'stderr').read_text(errors='replace')[-2000:]);print((out/'capture/worker/error.json').read_text(errors='replace')[-2000:] if (out/'capture/worker/error.json').exists() else '')
    else:print((out/'capture/results/audit.json').read_text())
    return process.returncode

if __name__=='__main__':raise SystemExit(main())
