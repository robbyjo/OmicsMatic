from pathlib import Path
import concurrent.futures,hashlib,json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';O=B/'java-probe-1';O.mkdir();J=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')
prior=R/'build/lm-2/build/limma-native-1/normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar';cp=';'.join(map(str,[B/'compile-probe-3/classes',T/'src/main/resources',prior]));libraries=(R/'build/literature-review/monocle-runtime/idep244-adopted-provider-4/libraries-requested.txt').read_text().splitlines()
def run(op):
    folder=O/op;folder.mkdir();src=B/'worker-probe-3'/op/'worker';p=json.loads((src/'parameters.json').read_bytes());out=folder/'analysis'
    r=dict(schema_version=1,mode='RUN',profile=p['profile'],operation=op,out=str(out),rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_libraries=libraries,units='ranked-statistic' if op=='CAMERAPR' else 'raw-counts' if op in ['VOOM','EDGER_VOOM_LMFIT'] else 'log2-expression')
    if op=='CAMERAPR':r.update(statistics=str(src/'statistics.tsv'),gene_sets=str(src/'gene_sets.tsv'),camera=p['camera'])
    else:
        r['matrix']=str(src/'matrix.tsv')
        if p['has_design']:r['design']=str(src/'design.tsv')
        if op in ['LM','VOOM','EDGER_VOOM_LMFIT']:r.update(contrasts=p['contrasts'],moderation=p['moderation'],adjust_method='BH',adjustment_scope='per-contrast')
        if op in ['LM','VOOM']:r['correlation']=p['correlation']
        if op in ['VOOM','EDGER_VOOM_LMFIT']:r.update(normalization=p['normalization'],voom={k:v for k,v in p['voom'].items() if k!='span' and (op=='EDGER_VOOM_LMFIT' or k!='sample_weights')})
        if op=='BATCH':r.update(batch=str(src/'batch.tsv'),batch_levels=p['batch_levels'])
        if op=='NORMALIZE':r['normalize_method']=p['normalize_method']
    request=folder/'request.json';request.write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8',newline='\n');rows={}
    for mode,args in [('RUN',['--request',str(request)]),('VERIFY',['--verify',str(out)])]:
        command=[str(J),'-cp',cp,'org.jlinalg.cli.LimmaWorkflowCli',*args]
        with (folder/(mode+'.stdout')).open('xb') as stdout,(folder/(mode+'.stderr')).open('xb') as stderr:p2=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        rows[mode]=dict(exit_code=p2.returncode,command=command)
        print(op,mode,p2.returncode,(folder/(mode+'.stderr')).read_text(errors='replace')[:300] if p2.returncode else '',flush=True)
        if p2.returncode:break
    (folder/'PROCESS.json').write_text(json.dumps(rows,indent=2)+'\n',encoding='utf-8',newline='\n');return op,rows
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(run,['LM','VOOM','EDGER_VOOM_LMFIT','BATCH','CAMERAPR','NORMALIZE']))
(O/'COMPLETED.json').write_text(json.dumps(dict(status='DEVELOPMENT_JAVA_WORKFLOW_CONTROLS',results=results,normal_JAR_qualified=False),indent=2)+'\n',encoding='utf-8',newline='\n')
