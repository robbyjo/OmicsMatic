"""Prepare a complete synthetic CDS/response bridge for an explicit permitted Linux runtime."""
from pathlib import Path
import argparse,hashlib,json,shutil,sys

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--engine',choices=['original','java'],required=True);args=parser.parse_args()
    if not sys.platform.startswith('linux'):raise RuntimeError('Run this preparer and RUN/REPLAY inside Linux/WSL so request paths select the Linux runtime. VERIFY also works on Windows.')
    runtime=args.runtime.resolve(strict=True);source=Path(__file__).resolve().parent;provenance=json.loads((source/'PROVENANCE.json').read_bytes());out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    for name,wanted in provenance['files'].items():
        raw=(source/name).read_bytes()
        if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:raise ValueError('Changed complete synthetic input: '+name)
        shutil.copyfile(source/name,out/name)
    shutil.copyfile(runtime,out/'runtime.json')
    neighbors=dict(schema_version=1,source_cds='complete-cds.rds',runtime='runtime.json',engine=args.engine,neighbor_graph='principal_graph',k=5,reduction_slot='UMAP',seed=100021,dataset_provenance='Complete27-cell synthetic original1.0.0 control27-5-principal_graph; all source counts and prepared graph/embedding retained. This example slot is UMAP; no P021 UMAT substitution.',out='neighbors')
    moran=dict(schema_version=1,design='design.tsv',weights='neighbors/results/weights.tsv',expression='expression.tsv',response_units='prepared-linear-response',response_provenance='Complete three-gene synthetic prepared responses from the prior official-spdep bridge, separate from the CDS count assay; no count transformation is inferred.',population_declaration='All27 prespecified synthetic cells and all three prespecified response genes.',zero_policy=True,alternative='greater',rank_tolerance=1e-7,maximum_operations=500000000,out='statistics')
    for name,value in [('neighbors-request.json',neighbors),('moran-request.json',moran)]:
        with (out/name).open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
    print('Prepared all synthetic cells/genes with explicit engine='+args.engine+' in '+str(out))
if __name__=='__main__':main()
