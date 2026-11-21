"""Prepare explicit synthetic LEARN, ORDER and RUN requests; perform no analysis."""
from pathlib import Path
import argparse,json,shutil,sys

HERE=Path(__file__).resolve().parent

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--root-cell',required=True);args=parser.parse_args()
    if sys.platform!='linux':parser.error('Use Linux/WSL Python with Linux paths; actual RUN/REPLAY requires Linux Java and the selected Linux R runtime.')
    runtime_file=args.runtime.resolve(strict=True);runtime=json.loads(runtime_file.read_bytes());required={'schema_version','profile','python','rscript','r_home','native_library_path','r_libraries'}
    if set(runtime)!=required or runtime['schema_version']!=1 or runtime['profile']!='monocle100-original-graph-r43-v1':parser.error('Supply the exact declared graph runtime profile; read runtime-template.json and README.md.')
    if not isinstance(runtime['r_libraries'],list) or not runtime['r_libraries']:parser.error('Declare the complete ordered private and base R library paths.')
    if args.root_cell not in {'cell-'+str(i).zfill(3) for i in range(1,28)}:parser.error('The supplied synthetic CDS has literal cell-001 through cell-027; choose an explicit synthetic root.')
    out=args.out.resolve();protected=[runtime_file,HERE/'complete-cds.rds']
    for value in [runtime['python'],runtime['rscript'],runtime['r_home'],runtime['native_library_path'],*runtime['r_libraries']]:
        path=Path(value)
        if not path.is_absolute():parser.error('Use absolute Linux runtime paths in this example.')
        protected.append(path.resolve())
    if any(out==p or out in p.parents or p in out.parents for p in protected):parser.error('Use NEW output outside all protected inputs and runtime paths.')
    out.mkdir(parents=True,exist_ok=False);shutil.copyfile(HERE/'complete-cds.rds',out/'complete-cds.rds');save(out/'runtime.json',runtime)
    common=dict(schema_version=1,runtime=str(out/'runtime.json'),reduction_slot='UMAP',seed=100022,dataset_provenance='Complete synthetic27-cell/three-gene original1.0.0 CDS with explicit prepared UMAP, cluster and partition factors; no paper data, embedding learning or biological root claim')
    learning=dict(use_partition=True,close_loop=False,learn_graph_control={'ncenter':5,'minimal_branch_len':2,'rann.k':6,'maxiter':100},convergence_policy='require')
    save(out/'learn-request.json',dict(**common,**learning,operation='LEARN',source_cds=str(out/'complete-cds.rds'),out=str(out/'learn')))
    save(out/'order-request.json',dict(**common,operation='ORDER',source_cds=str(out/'learn/worker/output-cds.rds'),graph_provenance='Caller-declared complete graph expected from this example LEARN; actual source RDS is captured when ORDER executes',root_cells=[args.root_cell],out=str(out/'order')))
    save(out/'run-request.json',dict(**common,**learning,operation='RUN',source_cds=str(out/'complete-cds.rds'),root_cells=[args.root_cell],out=str(out/'run')))
    print('Prepared three explicit requests only. Execute LEARN before ORDER, or use combined RUN. Read README.md and the CLI warnings. No analysis was performed.');return 0
if __name__=='__main__':raise SystemExit(main())
