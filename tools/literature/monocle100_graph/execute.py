"""Exercise an explicit original1.0.0 graph operation from complete synthetic CDS input."""
from pathlib import Path
import argparse,json,subprocess,time
ROOT=Path(__file__).resolve().parents[3]
def mount(path):return '/mnt/d/'+path.resolve().relative_to(Path('D:/')).as_posix()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--classes',type=Path);parser.add_argument('--operation',choices=['LEARN','ORDER','RUN'],required=True);parser.add_argument('--source',type=Path);parser.add_argument('--settings',type=Path);args=parser.parse_args()
    out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);operation=args.operation
    runtime=json.loads((ROOT.parent/'mo-2/build/monocle100-neighbors-1/pilot-inputs-1/runtime.json').read_bytes());runtime['profile']='monocle100-original-graph-r43-v1';save(out/'runtime.json',runtime)
    source=args.source.resolve() if args.source else ROOT/'build/monocle100-graph-1/probe-1'/('learned-cds.rds' if operation=='ORDER' else 'input-cds.rds')
    request=dict(schema_version=1,source_cds=mount(source),runtime=mount(out/'runtime.json'),operation=operation,reduction_slot='UMAP',seed=100022,dataset_provenance='Complete27-cell/three-gene synthetic original prepared input; no author pipeline or biological root direction',out=mount(out/'capture'))
    if operation!='ORDER':request.update(use_partition=True,close_loop=False,learn_graph_control={'ncenter':5,'minimal_branch_len':2,'rann.k':6,'maxiter':10},convergence_policy='require')
    else:request['graph_provenance']='Complete caller-supplied original1.0.0 learned synthetic control'
    if operation!='LEARN':request['root_cells']=['cell-001']
    if args.settings:request.update(json.loads(args.settings.read_bytes()))
    save(out/'request.json',request);cp=mount(args.jar)
    if args.classes:cp=mount(args.classes)+':'+mount(ROOT/'src/main/resources')+':'+cp
    command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','/usr/bin/timeout','--signal=TERM','--kill-after=20','720','java','-Xmx2g','-cp',cp,'org.jlinalg.cli.Monocle100GraphCli','RUN','--request',mount(out/'request.json')]
    save(out/'STARTED.json',dict(command=command,started_unix=time.time(),quick_compiled_only=args.classes is not None))
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
    save(out/'PROCESS.json',dict(exit_code=process.returncode));print(operation,'graph CLI exit',process.returncode,flush=True)
    if process.returncode:print((out/'stderr').read_text(errors='replace')[-2000:]);print((out/'capture/worker/error.json').read_text(errors='replace')[-2000:] if (out/'capture/worker/error.json').exists() else '')
    else:print((out/'capture/results/audit.json').read_text())
    return process.returncode
if __name__=='__main__':raise SystemExit(main())
