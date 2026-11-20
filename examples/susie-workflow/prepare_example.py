"""Create a NEW synthetic SuSiE example and an explicit original-package request."""
import argparse
import csv
import json
import math
from pathlib import Path


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--profile',required=True,choices=['SuSiE-0.11.92-R4.6.1','SuSiE-0.12.35-R4.6.1','SuSiE-0.14.2-R4.6.1'])
    parser.add_argument('--model',default='RSS_Z',choices=['INDIVIDUAL','RSS_Z','RSS_BETA_SE','SUFFICIENT','SUFFICIENT_BETA_SE'])
    parser.add_argument('--rscript',type=Path,required=True)
    parser.add_argument('--library',type=Path,action='append',required=True)
    args=parser.parse_args();legacy='0.11.92' in args.profile
    if (args.model=='RSS_BETA_SE' and legacy) or (args.model=='SUFFICIENT_BETA_SE' and not legacy):
        parser.error('Use SUFFICIENT_BETA_SE for 0.11.92 and RSS_BETA_SE for the later editions; no interface is silently substituted.')
    root=args.out.resolve();root.mkdir(parents=True,exist_ok=False)
    n,p=60,8;ids=[f'variant-{j+1}' for j in range(p)];samples=[f'sample-{i+1}' for i in range(n)]
    X=[[math.sin((i+1)*(j+1)*.373)+math.cos((i+1)*(j+2)*.217) for j in range(p)] for i in range(n)]
    y=[1.4*row[0]-.9*row[3]+.4*math.sin(i*.719) for i,row in enumerate(X)]
    xm=[sum(row[j] for row in X)/n for j in range(p)];ym=sum(y)/n
    centered=[[row[j]-xm[j] for j in range(p)] for row in X];yc=[v-ym for v in y]
    xx=[[sum(row[i]*row[j] for row in centered) for j in range(p)] for i in range(p)]
    xy=[sum(row[j]*v for row,v in zip(centered,yc)) for j in range(p)];yy=sum(v*v for v in yc)
    beta=[xy[j]/xx[j][j] for j in range(p)];se=[math.sqrt((yy-xy[j]*beta[j])/(n-2)/xx[j][j]) for j in range(p)]
    def token(v):return format(v,'.17g')
    def table(name,header,rows):
        with (root/name).open('x',encoding='utf-8',newline='') as f:
            writer=csv.writer(f,delimiter='\t',lineterminator='\n',quoting=csv.QUOTE_NONE)
            writer.writerow(header);writer.writerows(rows)
    request=dict(schema_version=1,mode='RUN',profile=args.profile,model=args.model,out='analysis',effects=3,
        maximum_iterations=300,tolerance=1e-6,seed=7,rscript=str(args.rscript.resolve()),
        r_libraries=[str(path.resolve()) for path in args.library],
        preparation_note='Synthetic 60-sample, eight-variable example. No biological validation or allele harmonization is claimed.')
    if args.model=='INDIVIDUAL':
        table('design.tsv',['sample_id',*ids],[[s,*map(token,row)] for s,row in zip(samples,X)])
        table('phenotype.tsv',['sample_id','response'],[[s,token(v)] for s,v in zip(samples,y)])
        request.update(design='design.tsv',phenotype='phenotype.tsv')
    else:
        if args.model=='RSS_Z':table('variants.tsv',['variant_id','z'],[[ids[j],token(beta[j]/se[j])] for j in range(p)])
        elif args.model=='SUFFICIENT':table('variants.tsv',['variant_id','xty'],[[ids[j],token(xy[j])] for j in range(p)])
        else:table('variants.tsv',['variant_id','beta','standard_error'],[[ids[j],token(beta[j]),token(se[j])] for j in range(p)])
        matrix=xx if args.model=='SUFFICIENT' else [[xx[i][j]/math.sqrt(xx[i][i]*xx[j][j]) if i!=j else 1.0 for j in range(p)] for i in range(p)]
        table('matrix.tsv',['variant_id',*ids],[[id,*map(token,row)] for id,row in zip(ids,matrix)])
        request.update(variants='variants.tsv',matrix='matrix.tsv')
        if args.model!='SUFFICIENT':request['ld_origin']='in-sample'
        if not(legacy and args.model=='RSS_Z'):request['sample_size']=n
        if args.model=='SUFFICIENT':request['yty']=yy
        if args.model.endswith('BETA_SE'):request['variance_y']=yy/(n-1)
    (root/'request.json').write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8')
    (root/'PREPARED.json').write_text(json.dumps(dict(status='PREPARED_SYNTHETIC_INPUTS_NOT_ANALYSIS_RESULTS',
        samples=n,variables=p,profile=args.profile,model=args.model,
        legacy_rss_does_not_use_sample_size=legacy and args.model=='RSS_Z',runtimes_installed=False),indent=2)+'\n',encoding='utf-8')
    print(root/'request.json')


if __name__=='__main__':main()
