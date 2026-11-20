#!/usr/bin/env python3
"""Create a NEW synthetic native-GRM example; no external packages or runtimes."""
from pathlib import Path
import argparse,json

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();root=args.out.resolve();root.mkdir(parents=True,exist_ok=False)
    calls=[[2,1,0,2],[0,0,1,2],[1,2,1,0],[2,0,2,1],[None,1,2,0],[1,1,0,2]]
    code={2:0,1:2,0:3,None:1}
    (root/'input.bed').write_bytes(bytes([0x6c,0x1b,0x01]+[sum(code[x]<<(2*i) for i,x in enumerate(row)) for row in calls]))
    (root/'input.fam').write_text(''.join(f'F{i}\tI{i}\t0\t0\t0\t-9\n' for i in range(1,5)),encoding='utf-8',newline='\n')
    (root/'input.bim').write_text(''.join(f'1\trs{i}\t0\t{i*100}\tA\tG\n' for i in range(1,7)),encoding='utf-8',newline='\n')
    request=dict(schema_version=1,mode='RUN',profile='GCTA-1.94.1',engine='native',model='GRM',
        out='native-grm-result',preparation_note='Synthetic four-subject six-marker diploid example, A1 hard calls, one explicit missing genotype; no participant data',
        genotypes={k:'input.'+k for k in ['bed','bim','fam']},grm_algorithm=0)
    (root/'request.json').write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(root/'request.json')

if __name__=='__main__':main()
