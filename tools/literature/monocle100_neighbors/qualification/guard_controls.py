"""Require the exact production R worker to reject actual in-process body/formal mutations."""
from pathlib import Path
import argparse,json,shutil,subprocess
from build import ROOT,save
from packaged import mount
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    runtime=json.loads((ROOT.parent/'mo-2/build/monocle100-neighbors-1/pilot-inputs-1/runtime.json').read_bytes());base=json.loads((ROOT.parents[1]/'build/monocle100-neighbor-diagnostics-20261121-1/COMMAND.json').read_bytes());index=base.index('--vanilla');base=base[:index+1];results={}
    for kind in ['body','formals']:
        root=out/kind;(root/'worker').mkdir(parents=True);(root/'inputs').mkdir();shutil.copytree(ROOT/'src/main/resources/org/jlinalg/omics/monocle100-neighbors',root/'source');save(root/'plan.json',dict(kind=kind))
        for name in ['home','tmp','cache']:(root/name).mkdir()
        old='/mnt/d/projects/JLinAlg/build/monocle100-neighbor-diagnostics-20261121-1'
        command=[value.replace(old,mount(root)).replace('LANG=C','LANG=C.UTF-8').replace('LC_ALL=C','LC_ALL=C.UTF-8') for value in base]+[mount(Path(__file__).with_name('guard_controls.R')),mount(root)]
        (root/'worker/runtime-paths.txt').write_text('\n'.join(runtime['r_libraries'])+'\n',encoding='utf-8',newline='\n');save(root/'worker/runtime.json',runtime)
        save(root/'COMMAND.json',command)
        with (root/'stdout').open('xb') as stdout,(root/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(root/'PROCESS.json',dict(exit_code=process.returncode));assert process.returncode==2,(root/'stderr').read_text(errors='replace')[-1000:]
        error=json.loads((root/'worker/error.json').read_bytes());assert error['stage']=='runtime' and error['message']=='Changed canonical original function: slam rowapply_simple_triplet_matrix'
        assert error['weights_admitted'] is False and error['scientific_test_admitted'] is False and not (root/'worker/original-nearest.rds').exists()
        results[kind]=dict(status='PASS_ACTUAL_PRODUCTION_WORKER_CHANGED_NAMESPACE_REFUSAL',original_RANN_executed=False,installed_source_modified=False,error=error);save(root/'RESULT.json',results[kind]);print('PASS production worker rejects changed',kind,flush=True)
    save(out/'RESULT.json',dict(status='PASS_BOTH_ACTUAL_CHANGED_NAMESPACE_REFUSALS',controls=results,scientific_analyses_admitted=0));return 0
if __name__=='__main__':raise SystemExit(main())
