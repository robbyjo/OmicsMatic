from pathlib import Path
import concurrent.futures,csv,hashlib,json,math,os,random,shutil,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';O=B/'worker-probe-1';O.mkdir();RS=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe')
libraries=(R/'build/literature-review/monocle-runtime/idep244-adopted-provider-4/libraries-requested.txt').read_text().splitlines()
profile='limma-3.68.5-edgeR-4.10.7-statmod-1.5.2-R4.6.1';rng=random.Random(20261121);G=180;N=12
features=['001','NA','gene_é']+['g'+str(i+1) for i in range(3,G)];samples=['001','NA']+['sample '+str(i+1) for i in range(2,N)]
counts=[[float(4+rng.randrange(1,100)+int((i%4==0)*(j%2)*30)) for j in range(N)] for i in range(G)]
expression=[[math.log2(v+1)+rng.gauss(0,.1) for v in row] for row in counts]
design=[[1,j%2] for j in range(N)]
def tsv(path,header,rows):
    with path.open('x',encoding='utf-8',newline='') as f:
        w=csv.writer(f,delimiter='\t',lineterminator='\n',quoting=csv.QUOTE_NONE,escapechar=None);w.writerow(header);w.writerows(rows)
env={k:v for k,v in os.environ.items() if not k.startswith('R_LIBS') and k not in ['R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS','LANG','LANGUAGE'] and not k.startswith('LC_')};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
def run(op):
    root=O/op;worker=root/'worker';worker.mkdir(parents=True);(root/'results').mkdir();(root/'source/reference').mkdir(parents=True)
    shutil.copyfile(T/'src/main/resources/org/jlinalg/omics/limma_workflow.R',root/'source/limma_workflow.R');shutil.copyfile(T/'src/main/resources/org/jlinalg/omics/limma-workflow/source.zip',root/'source/reference/source.zip')
    (worker/'runtime-paths.txt').write_text('\n'.join(libraries)+'\n',encoding='utf-8',newline='\n')
    p=dict(profile=profile,operation=op,features=G,samples=N,design_columns=2,sets=0,seed=1,has_design=op not in ['NORMALIZE','CAMERAPR'],has_weights=False,has_block=False,has_libraries=False,has_norm_factors=False,has_covariates=False,has_batch=op=='BATCH',has_batch2=False,moderation=dict(method='EBAYES',trend='none',legacy='auto',robust=False,proportion=.01,stdev_coef_lim=[.1,4]),correlation=dict(mode='none'),contrasts=[dict(id='case minus control',values=[0,1])],adjust_method='BH',adjustment_scope='per-contrast')
    if op=='CAMERAPR':
        p.update(sets=3,camera=dict(use_ranks=False,inter_gene_cor=.01,directional=True,sort=True))
        tsv(worker/'statistics.tsv',['feature_id','statistic'],[(features[i],rng.gauss(0,1)) for i in range(G)])
        tsv(worker/'gene_sets.tsv',['set_id','feature_id'],[(name,features[i]) for name,indices in [('set 1',range(15)),('set_é',range(20,40)),('NA',range(50,60))] for i in indices])
    else:
        x=counts if op in ['VOOM','EDGER_VOOM_LMFIT'] else expression
        tsv(worker/'matrix.tsv',['feature_id',*samples],[(features[i],*x[i]) for i in range(G)])
        if p['has_design']:tsv(worker/'design.tsv',['sample_id','Intercept','group'],[(samples[j],*design[j]) for j in range(N)])
        if op in ['VOOM','EDGER_VOOM_LMFIT']:p.update(normalization=dict(method='TMMwsp',logratio_trim=.3,sum_trim=.05,do_weighting=True,a_cutoff=-1e10),voom=dict(span=.5,adaptive_span=True,normalize_method='none',sample_weights=False))
        if op=='BATCH':p['batch_levels']=['A','B'];tsv(worker/'batch.tsv',['sample_id','batch'],[(samples[j],'A' if j<6 else 'B') for j in range(N)])
        if op=='NORMALIZE':p['normalize_method']='quantile'
    (worker/'parameters.json').write_text(json.dumps(p,indent=2)+'\n',encoding='utf-8',newline='\n')
    command=[str(RS),'--vanilla','../source/limma_workflow.R']
    with (worker/'stdout.log').open('xb') as stdout,(worker/'stderr.log').open('xb') as stderr:proc=subprocess.run(command,cwd=worker,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    report=json.loads((worker/'report.json').read_bytes()) if (worker/'report.json').exists() else {}
    (root/'PROCESS.json').write_text(json.dumps(dict(command=command,exit_code=proc.returncode,report=report),indent=2)+'\n',encoding='utf-8',newline='\n')
    print(op,proc.returncode,report.get('error'),report.get('object_snapshots'),flush=True);return op,dict(exit_code=proc.returncode,error=report.get('error'))
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(run,['LM','VOOM','EDGER_VOOM_LMFIT','BATCH','CAMERAPR','NORMALIZE']))
(O/'COMPLETED.json').write_text(json.dumps(dict(status='PROTOTYPE_ORIGINAL_WORKER_CONTROLS',results=results,fully_qualified=False),indent=2)+'\n',encoding='utf-8',newline='\n')
