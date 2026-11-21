from pathlib import Path
import concurrent.futures,copy,csv,json,math,random,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';O=B/'java-variants-1';O.mkdir();J=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe');prior=R/'build/lm-2/build/limma-native-1/normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar';cp=';'.join(map(str,[B/'compile-probe-4/classes',T/'src/main/resources',prior]))
base={op:json.loads((B/'java-probe-2'/op/'request.json').read_bytes()) for op in ['LM','VOOM','EDGER_VOOM_LMFIT','BATCH','CAMERAPR','NORMALIZE']}
def rows(path):
    with Path(path).open(encoding='utf-8') as f:return list(csv.reader(f,delimiter='\t'))
matrix=rows(base['LM']['matrix']);design=rows(base['LM']['design']);G=len(matrix)-1;N=len(matrix[0])-1;features=[x[0] for x in matrix[1:]];samples=matrix[0][1:];rng=random.Random(2026112101)
specs=[]
def case(name,op,edit):specs.append((name,op,edit))
for method in ['BY','holm','hochberg','hommel','bonferroni','none']:case('ADJUST_'+method,'LM',dict(adjust_method=method,adjustment_scope='all-contrasts',contrasts=[dict(id='effect_é',values=[0,1]),dict(id='NA',values=[1,0])]))
case('MULTI_DEPENDENT','LM',dict(contrasts=[dict(id='a',values=[0,1]),dict(id='b',values=[1,0]),dict(id='c',values=[1,-1])]))
case('BORROW_ZERO_DF','LM',dict(_zero_df=True))
case('BORROW_ZERO_DF_TREAT','LM',dict(_zero_df=True,moderation=dict(method='TREAT',trend='none',legacy='auto',robust=False,lfc=.2630344058337938)))
case('MODERN_TREND_SPAN','LM',dict(moderation=dict(method='EBAYES',trend='Amean',legacy='false',span=.6,robust=True,winsor_tail_p=[.05,.1],proportion=.02,stdev_coef_lim=[.1,4])))
case('PROVIDED_TREND','LM',dict(_trend=True,moderation=dict(method='EBAYES',trend='provided',legacy='true',robust=False,proportion=.01,stdev_coef_lim=[.1,4])))
case('TREAT_ROBUST','LM',dict(moderation=dict(method='TREAT',trend='Amean',legacy='true',robust=True,winsor_tail_p=[.05,.1],lfc=.2630344058337938)))
case('SMALL_P','LM',dict(_small_p=True))
case('UTF8_CHUNKS','LM',dict(_large=True,contrasts=[dict(id='contrast_é',values=[0,1])]))
case('FIXED_CORRELATION','LM',dict(_block=True,correlation=dict(mode='fixed',value=.15)))
for method in ['TMM','RLE','upperquartile','none']:case('NORM_'+method,'VOOM',dict(normalization=dict(method=method)))
case('PROVIDED_LIBRARIES_FACTORS','VOOM',dict(normalization=dict(method='provided'),_provided=True))
case('VOOM_FIXED_QUANTILE','VOOM',dict(_block=True,correlation=dict(mode='fixed',value=.1),voom=dict(adaptive_span=False,span=.7,normalize_method='quantile')))
case('EDGER_SAMPLE_WEIGHTS','EDGER_VOOM_LMFIT',dict(voom=dict(adaptive_span=False,span=.5,normalize_method='none',sample_weights=True)))
case('BATCH_COVARIATES','BATCH',dict(_covariates=True,_remove_batch=True))
case('BATCH_TWO_AND_COVARIATE','BATCH',dict(_covariates=True,_batch2=True))
case('CAMERA_RANKS','CAMERAPR',dict(camera=dict(use_ranks=True,inter_gene_cor=.01,directional=True,sort=False)))
case('CAMERA_NONDIRECTIONAL','CAMERAPR',dict(camera=dict(use_ranks=False,inter_gene_cor=.01,directional=False,sort=True)))
case('CAMERA_COR_VECTOR','CAMERAPR',dict(camera=dict(use_ranks=False,inter_gene_cor=[.01,.1,-.02],directional=True,sort=False)))
case('CAMERA_ONE_SET','CAMERAPR',dict(_one_set=True,camera=dict(use_ranks=False,inter_gene_cor=[.01],directional=True,sort=True)))
for method in ['none','scale','cyclicloess']:case('BETWEEN_'+method,'NORMALIZE',dict(normalize_method=method))
def tsv(path,data):
    with path.open('x',encoding='utf-8',newline='') as f:csv.writer(f,delimiter='\t',lineterminator='\n',quoting=csv.QUOTE_NONE).writerows(data)
def run(spec):
    name,op,options=spec;folder=O/name;folder.mkdir();r=copy.deepcopy(base[op]);r['out']=str(folder/'analysis');r.update({k:v for k,v in options.items() if not k.startswith('_')})
    if options.get('_block'):
        path=folder/'block.tsv';tsv(path,[['sample_id','block']]+[[samples[j],'b'+str(j//2+1)] for j in range(N)]);r.update(block=str(path),block_levels=['b'+str(i+1) for i in range(N//2)])
    if options.get('_zero_df'):
        path=folder/'weights.tsv';tsv(path,[['feature_id',*samples]]+[[features[i],*[0 if i==0 and j>1 else 1 for j in range(N)]] for i in range(G)]);r['weights']=str(path)
    if options.get('_trend'):
        path=folder/'trend.tsv';tsv(path,[['feature_id','covariate']]+[[features[i],i/10] for i in range(G)]);r['trend_covariate']=str(path)
    if options.get('_small_p') or options.get('_large'):
        count=2051 if options.get('_large') else G;local=random.Random(731);data=[matrix[0]]
        for i in range(count):
            id=features[i] if i<G else 'g_more_'+str(i)
            if i==1023:id='gene_<U+00E9>'
            if i==1024:id='gene_e\u0301'
            data.append([id,*[5+(3 if i%4==0 and j%2 else 0)+local.gauss(0,.1 if options.get('_small_p') else .5) for j in range(N)]])
        path=folder/'expression.tsv';tsv(path,data);r['matrix']=str(path)
    if options.get('_provided'):
        counts=rows(r['matrix']);libs=folder/'libraries.tsv';factors=folder/'factors.tsv';tsv(libs,[['sample_id','library_size']]+[[samples[j],sum(float(x[j+1]) for x in counts[1:])*1.2] for j in range(N)]);tsv(factors,[['sample_id','norm_factor']]+[[s,2] for s in samples]);r.update(libraries=str(libs),norm_factors=str(factors))
    if options.get('_covariates'):
        path=folder/'covariates.tsv';tsv(path,[['sample_id','age']]+[[samples[j],j/10] for j in range(N)]);r['covariates']=str(path)
    if options.get('_remove_batch'):r.pop('batch');r.pop('batch_levels')
    if options.get('_batch2'):
        path=folder/'batch2.tsv';tsv(path,[['sample_id','batch2']]+[[samples[j],'q'+str(j%3+1)] for j in range(N)]);r.update(batch2=str(path),batch2_levels=['q1','q2','q3'])
    if options.get('_one_set'):
        data=rows(r['gene_sets']);name0=data[1][0];path=folder/'one-set.tsv';tsv(path,[data[0]]+[x for x in data[1:] if x[0]==name0]);r['gene_sets']=str(path)
    request=folder/'request.json';request.write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8',newline='\n');results={}
    for mode,args in [('RUN',['--request',str(request)]),('VERIFY',['--verify',r['out']])]:
        command=[str(J),'-cp',cp,'org.jlinalg.cli.LimmaWorkflowCli',*args]
        with (folder/(mode+'.stdout')).open('xb') as stdout,(folder/(mode+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        results[mode]=dict(exit_code=p.returncode,command=command);print(name,mode,p.returncode,(folder/(mode+'.stderr')).read_text(errors='replace')[:250] if p.returncode else '',flush=True)
        if p.returncode:break
    (folder/'PROCESS.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8',newline='\n');return name,results
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(run,specs))
(O/'RESULTS.json').write_text(json.dumps(dict(status='DEVELOPMENT_VARIANT_CONTROLS',results=results),indent=2)+'\n',encoding='utf-8',newline='\n')
