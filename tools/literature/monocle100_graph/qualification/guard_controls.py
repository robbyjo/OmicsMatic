"""Require the packaged graph R worker to reject actual body/formal namespace mutations."""
from pathlib import Path
import argparse,json,shutil,subprocess,zipfile
from build import ROOT,pin,save
from packaged import mount

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);jar=args.jar.resolve(strict=True);runtime=json.loads((ROOT.parent/'mo-2/build/monocle100-neighbors-1/pilot-inputs-1/runtime.json').read_bytes());runtime['profile']='monocle100-original-graph-r43-v1'
    base=json.loads((ROOT.parents[1]/'build/monocle100-neighbor-diagnostics-20261121-1/COMMAND.json').read_bytes());base=base[:base.index('--vanilla')+1];results={}
    for kind in ['body','formals']:
        root=out/kind;(root/'worker').mkdir(parents=True);(root/'inputs').mkdir();save(root/'plan.json',dict(kind=kind));(root/'source').mkdir()
        with zipfile.ZipFile(jar) as archive:
            prefix='org/jlinalg/omics/monocle100-graph/';profile=json.loads(archive.read(prefix+'PROFILE.json'));names=['PROFILE.json','worker.R','NOTICE.txt',*profile['files']]
            for name in names:
                assert not Path(name).is_absolute() and '..' not in Path(name).parts;target=root/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.read(prefix+name))
        for name in ['home','tmp','cache']:(root/name).mkdir()
        shutil.copyfile(Path(__file__).with_name('guard_controls.R'),root/'EXECUTED-guard_controls.R');old='/mnt/d/projects/JLinAlg/build/monocle100-neighbor-diagnostics-20261121-1';command=[value.replace(old,mount(root)).replace('LANG=C','LANG=C.UTF-8').replace('LC_ALL=C','LC_ALL=C.UTF-8') for value in base]+[mount(root/'EXECUTED-guard_controls.R'),mount(root)]
        (root/'worker/runtime-paths.txt').write_text('\n'.join(runtime['r_libraries'])+'\n',encoding='utf-8',newline='\n');save(root/'worker/runtime.json',runtime);save(root/'COMMAND.json',command)
        with (root/'stdout').open('xb') as stdout,(root/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(root/'PROCESS.json',dict(exit_code=p.returncode));assert p.returncode==2,(root/'stderr').read_text(errors='replace')[-1500:];error=json.loads((root/'worker/error.json').read_bytes());assert error['stage']=='runtime' and error['message']=='Changed original graph function: learn_graph' and error['graph_admitted'] is False and error['pseudotime_admitted'] is False and error['scientific_test_admitted'] is False and not (root/'worker/learned-cds.rds').exists()
        row=dict(status='PASS_PACKAGED_GRAPH_WORKER_CHANGED_NAMESPACE_REFUSAL',kind=kind,original_graph_executed=False,installed_source_modified=False,error=error);save(root/'RESULT.json',row);results[kind]=row;print('PASS packaged graph worker rejects changed',kind,flush=True)
    save(out/'RESULT.json',dict(status='PASS_BOTH_PACKAGED_GRAPH_CHANGED_NAMESPACE_REFUSALS',producer=pin(jar),controls=results,scientific_results_admitted=0));return 0
if __name__=='__main__':raise SystemExit(main())
