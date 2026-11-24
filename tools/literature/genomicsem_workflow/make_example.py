"""Create NEW synthetic full-covariance inputs and explicit Java workflow requests."""
from pathlib import Path
import argparse,json,sys

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--runtime',required=True,type=Path);a=p.parse_args(argv)
    root=a.out.absolute()
    if root.exists() or not root.parent.is_dir() or root.parent.resolve()!=root.parent:raise ValueError('Use a NEW output with an existing physical parent')
    root.mkdir();traits=['T1','T2','T3','T4'];l=[.5,.6,.7,.8];res=[.4,.35,.3,.25]
    pairs=[traits[i]+':'+traits[j] for i in range(4)for j in range(i,4)]
    def matrix(name,labels,values):
        with(root/name).open('x',encoding='utf-8',newline='\n')as f:
            f.write('id\t'+'\t'.join(labels)+'\n')
            for label,row in zip(labels,values):f.write(label+'\t'+'\t'.join(format(x,'.17g')for x in row)+'\n')
    matrix('S.tsv',traits,[[l[i]*l[j]+(res[i]if i==j else 0)for j in range(4)]for i in range(4)])
    u=[.001+.002*i/9 for i in range(10)]
    matrix('V.tsv',pairs,[[u[i]*u[j]+(.0001+.00009*i/9 if i==j else 0)for j in range(10)]for i in range(10)])
    matrix('I.tsv',traits,[[(1.2 if i==0 else 1)if i==j else .05 for j in range(4)]for i in range(4)])
    with(root/'associations.tsv').open('x',encoding='utf-8',newline='\n')as f:
        f.write('\t'.join(['SNP','CHR','BP','MAF','A1','A2']+[x for t in traits for x in ('beta.'+t,'se.'+t)])+'\n')
        for i,b in enumerate([.01,-.02,.03,-.01]):f.write('\t'.join(['rsSynthetic'+str(i+1),'1',str(1001+i),'.2','A','G']+[x for j in range(4)for x in (format(b*l[j],'.17g'),'.003')])+'\n')
    (root/'model.txt').write_text('F =~ NA*T1 + T2 + T3 + T4\nF ~~ 1*F\n',encoding='utf-8',newline='\n')
    (root/'GWAS-model.txt').write_text('F =~ T1 + T2 + T3 + T4\nF ~ SNP\n',encoding='utf-8',newline='\n')
    shared=dict(estimation='DWLS',seed=73,covariance_scale='observed',sampling_order='lower-column-including-diagonal',input_reviewed=True,smoothing_policy='FAIL',warning_policy='RETAIN_AND_FLAG')
    for op,name in [('USERMODEL','model'),('USER_GWAS','gwas')]:
        inputs=dict(covariance='S.tsv',sampling_covariance='V.tsv',model='model.txt'if name=='model'else'GWAS-model.txt');o=dict(shared,std_lv=False)
        if name=='model':o.update(CFIcalc=True,fix_resid=False,tolerance=None,Q_Factor=False)
        else:inputs.update(intercepts='I.tsv',associations='associations.tsv');o.update(cores=1,parallel=False,GC='standard',SNPSE=False,tolerance=False,effects_aligned=True,fix_measurement=True,Q_SNP=True,sub=['F ~ SNP'])
        q=dict(schema_version=1,mode='RUN',profile='genomicsem-da95d43-r461-win64-lavaan0619-v1',operation=op,runtime_home=a.runtime.absolute().as_posix(),out=name+'-run',inputs=inputs,options=o)
        (root/(name+'-request.json')).write_text(json.dumps(q,indent=2)+'\n',encoding='utf-8',newline='\n')
    (root/'README.txt').write_text('SYNTHETIC INPUTS ONLY. V is generated as a complete positive-definite sampling covariance for this example. Real analyses require genuine full multivariable sampling dependencies;never assemble them from pairwise LDSC fits. Runtimes and scientific inputs are not downloaded by an analysis command. Output directories model-run/gwas-run must be NEW. See docs/GENOMICSEM_WORKFLOW.md.\n',encoding='utf-8')
    print(json.dumps(dict(status='SYNTHETIC_EXAMPLES_CREATED',out=str(root),real_paper_analysis=False)))
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError)as e:print('FAIL2: '+str(e)+'. Preserve existing inputs and retry a NEW output.',file=sys.stderr);raise SystemExit(2)
