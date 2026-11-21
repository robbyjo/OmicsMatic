"""Copy byte-checked complete synthetic inputs and create two relative-path requests."""
from pathlib import Path
import argparse,hashlib,json,shutil

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    source=Path(__file__).resolve().parent;provenance=json.loads((source/'PROVENANCE.json').read_bytes());out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    for name,wanted in provenance['files'].items():
        raw=(source/name).read_bytes()
        if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:raise ValueError('Changed example input: '+name)
        shutil.copyfile(source/name,out/name)
    weights=dict(schema_version=1,neighbor_input='neighbors.json',out='weights',maximum_operations=50000000)
    moran=dict(schema_version=1,design='design.tsv',weights='weights/weights.tsv',expression='expression.tsv',response_units='prepared-linear-response',response_provenance='Complete three-gene synthetic prepared responses from the original-spdep bridge control; no biological count transformation is inferred.',population_declaration='All27 prespecified synthetic source-control cells and all three prespecified genes.',zero_policy=True,alternative='greater',rank_tolerance=1e-7,maximum_operations=500000000,out='statistics')
    for name,value in [('weights-request.json',weights),('moran-request.json',moran)]:
        with (out/name).open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
    print('Prepared complete synthetic inputs in',out)

if __name__=='__main__':main()
