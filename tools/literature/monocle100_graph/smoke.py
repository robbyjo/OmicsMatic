"""Direct production-worker smoke check; no claim of normal-JAR qualification."""
from pathlib import Path
import argparse,json,os,shutil,subprocess,sys
ROOT=Path(__file__).resolve().parents[3]
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();assert sys.platform=='linux';out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    runtime=json.loads((ROOT.parent/'mo-2/build/monocle100-neighbors-1/pilot-inputs-1/runtime.json').read_bytes());runtime['profile']='monocle100-original-graph-r43-v1';results={}
    for operation in ['LEARN','ORDER','RUN']:
        root=out/operation;(root/'worker').mkdir(parents=True);(root/'inputs').mkdir();shutil.copytree(ROOT/'src/main/resources/org/jlinalg/omics/monocle100-graph',root/'source');shutil.copyfile(ROOT/'src/main/resources/org/jlinalg/omics/monocle100-neighbors/supervisor.py',root/'source/supervisor.py')
        input=ROOT/'build/monocle100-graph-1/probe-1'/('learned-cds.rds' if operation=='ORDER' else 'input-cds.rds');shutil.copyfile(input,root/'inputs/source-cds.rds')
        parameters=dict(schema_version=1,profile=runtime['profile'],operation=operation,reduction_slot='UMAP',seed=100022,dataset_provenance='Complete original27-cell/three-gene synthetic prepared input; direct worker development smoke',maximum_cells=1000000,maximum_output_bytes=2147483648,maximum_files=10000,maximum_address_space_bytes=8589934592,maximum_sampled_rss_bytes=8589934592,timeout_seconds=600)
        if operation!='ORDER':parameters.update(use_partition=True,close_loop=False,learn_graph_control={'ncenter':5,'minimal_branch_len':2,'rann.k':6,'maxiter':10},convergence_policy='require')
        else:parameters['graph_provenance']='Exact unchanged original1.0.0 prepared graph probe'
        if operation!='LEARN':parameters['root_cells']=['cell-001']
        save(root/'worker/parameters.json',parameters);save(root/'worker/runtime.json',runtime);(root/'worker/runtime-paths.txt').write_text('\n'.join(runtime['r_libraries'])+'\n',encoding='utf-8',newline='\n')
        command=[runtime['python'],'-I','-B',str(root/'source/supervisor.py'),str(root),str(os.getpid())];save(root/'COMMAND.json',command)
        with (root/'stdout').open('xb') as stdout,(root/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=660)
        save(root/'PROCESS.json',dict(exit_code=process.returncode));print(operation,'worker exit',process.returncode,flush=True)
        if process.returncode:print((root/'worker/error.json').read_text() if (root/'worker/error.json').exists() else (root/'stderr').read_text());return process.returncode
        results[operation]=json.loads((root/'worker/report.json').read_bytes())
    save(out/'RESULT.json',dict(status='PASS_THREE_DIRECT_ORIGINAL_GRAPH_WORKER_OPERATIONS',cases=results,normal_JAR_qualification=False));return 0
if __name__=='__main__':raise SystemExit(main())
