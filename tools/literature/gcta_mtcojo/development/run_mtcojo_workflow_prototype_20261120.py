from pathlib import Path
import concurrent.futures,hashlib,json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1';F=B/'synthetic-inputs-1';O=B/'prototype-workflow-1';O.mkdir(exist_ok=False)
C=B/'compile-1';jar=R/'build/gc-4/build/gcta-qualification-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';UP=R/'build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1'
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
compiled=json.loads((C/'COMPLETED.json').read_bytes());assert compiled['exit_code']==0 and pin(jar)==compiled['dependency_producer']
for name,w in compiled['inputs'].items():assert pin(T/name)==w,name
def linux(p):return '/mnt/d/'+Path(p).relative_to(Path('D:/')).as_posix()
def transport(v):
    if isinstance(v,dict):return {k:transport(x) for k,x in v.items()}
    if isinstance(v,list):return [transport(x) for x in v]
    return linux(str(v).replace('\\','/')) if isinstance(v,str) and (v.startswith('D:/') or v.startswith('D:\\')) else v
def task(host):
    root=O/host;root.mkdir();results=[]
    prefix=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-cp',str(C/'classes')+';'+str(jar),'org.jlinalg.cli.GctaWorkflowCli'] if host=='windows' else ['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--cd',linux(T),'--exec','/usr/bin/java','-cp',linux(C/'classes')+':'+linux(jar),'org.jlinalg.cli.GctaWorkflowCli']
    for count,names in [('single',['Y','X1']),('multi',['Y','X1','X2'])]:
        for mode in ['supplied','GSMR']:
            name=count+'-'+mode;request=dict(schema_version=1,mode='RUN',profile='GCTA-1.94.1',engine='original',model='MTCOJO',out=str(root/name),runtime=str(UP/('linux/download' if host=='linux' else 'windows/files/gcta-1.94.1-win-x86_64/gcta-win-1.94.1.exe')),
                preparation_note='Synthetic Gaussian GWAS summaries and empirical chromosome LD from the recorded512-subject reference; no participant data. Source accuracy warnings retained.',effect_scale='as_supplied',causal_effect_mode=mode,
                genotypes={s:str(F/('reference.'+s)) for s in ['bed','bim','fam']},traits=[dict(name=n,summary=str(F/(n+'.ma'))) for n in names],
                reference_ld=[dict(chromosome=c,scores=str(F/'ref-ld'/(str(c)+'.l2.ldscore')),markers=str(F/'ref-ld'/(str(c)+'.l2.M_5_50'))) for c in range(1,23)],weight_ld=[dict(chromosome=c,scores=str(F/'weights-ld'/(str(c)+'.l2.ldscore'))) for c in range(1,23)])
            if mode=='supplied':request['causal_effects']={n:.35 if n=='X1' else -.2 for n in names[1:]}
            if host=='linux':request=transport(request)
            file=root/(name+'.request.json');save(file,request);command=prefix+['--request',linux(file) if host=='linux' else str(file)]
            with (root/(name+'.stdout')).open('xb') as stdout,(root/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
            folder=root/name;status=json.loads((folder/'analysis-status.json').read_bytes()) if (folder/'analysis-status.json').is_file() else {};row=dict(name=name,command=command,exit_code=p.returncode,analysis_status=status)
            if p.returncode==0:
                command=prefix+['--verify',linux(folder) if host=='linux' else str(folder)]
                with (root/(name+'.verify.stdout')).open('xb') as stdout,(root/(name+'.verify.stderr')).open('xb') as stderr:v=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
                row['verify_exit']=v.returncode
                actual=folder/'worker/result.mtcojo.cma';original=B/'original-1.94.1-1'/host/name/'result.mtcojo.cma';row['all_original_table_tokens_equal']=[line.split() for line in actual.read_text().splitlines()]==[line.split() for line in original.read_text().splitlines()]
            results.append(row);save(root/'PROGRESS.json',results);print('Prototype mtCOJO',host,name,'exit',p.returncode,status.get('error',''),row.get('all_original_table_tokens_equal',''),flush=True)
    save(root/'RESULTS.json',results);return host,results
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(task,['windows','linux']))
save(O/'COMPLETED.json',dict(status='DEVELOPMENT_COMPOSITIONS_REVIEW_REQUIRED_NOT_NORMAL_PRODUCER',results=results,source_owner=str(C),new_catalog_credit=0))
