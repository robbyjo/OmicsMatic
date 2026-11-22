"""Copy checked synthetic inputs and prepare native response/Moran requests in NEW output."""
from pathlib import Path
import argparse,hashlib,json,shutil

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();source=Path(__file__).resolve().parent
    provenance=json.loads((source/'PROVENANCE.json').read_bytes())
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    for name,wanted in provenance['files'].items():
        raw=(source/name).read_bytes()
        if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:
            raise ValueError('Changed synthetic example input: '+name)
        shutil.copyfile(source/name,out/name)
    preparation=dict(schema_version=1,transform='size-factor-log10-plus-0.1',
        expression='expression.tsv',size_factors='size_factors.tsv',
        input_provenance='Complete deterministic official Monocle1.0.0 response-branch control; supplied counts and factors.',
        population_declaration='All48 prespecified synthetic cells and all4 prespecified genes.',
        detected_genes='detected_genes.tsv',
        detected_genes_provenance='Explicit pre-filter nFeature_RNA values; not recomputed from the selected four genes.',
        base_design='base_design.tsv',
        design_provenance='Explicit intercept, BatchB/BatchC contrasts, SexM and numeric PMI; add natural log of supplied nFeature_RNA.',
        out='preparation')
    moran=dict(schema_version=1,design='preparation/design.tsv',weights='weights.tsv',
        expression='preparation/responses.tsv',response_units='prepared-linear-response',
        response_provenance='Explicit native log10(count/supplied_size_factor+0.1); independently compared to original-source control.',
        population_declaration='Complete same48-cell/four-gene synthetic population; supplied reciprocal ring weights.',
        zero_policy=True,alternative='greater',out='statistics')
    for name,value in [('preparation-request.json',preparation),('moran-request.json',moran)]:
        with (out/name).open('x',encoding='utf-8',newline='\n') as stream:
            json.dump(value,stream,indent=2,ensure_ascii=False);stream.write('\n')
    print('Prepared complete synthetic inputs and two requests in',out)

if __name__=='__main__':main()
