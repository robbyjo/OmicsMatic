from pathlib import Path
import concurrent.futures,hashlib,json,math,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1';F=B/'synthetic-inputs-1';O=B/'original-1.94.1-1';O.mkdir(exist_ok=False)
UP=R/'build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1';fixture=json.loads((F/'FIXTURE.json').read_bytes())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def linux(p):return '/mnt/d/'+p.relative_to(Path('D:/')).as_posix()
def task(host):
    root=O/host;root.mkdir();exe=UP/('linux/download' if host=='linux' else 'windows/files/gcta-1.94.1-win-x86_64/gcta-win-1.94.1.exe');expected='dbb2b5e5d51e278328ff297a403d3dd8ee5f5280f0b6d3d8db74b3e7fead7b49' if host=='linux' else '2529bccb2f685332f115726c3cb2bf8cab91f12ae3344a991e3a52d751bdb07e';assert pin(exe)['sha256']==expected
    results=[]
    for count in ['single','multi']:
        for estimator in ['supplied','GSMR']:
            for name,w in fixture['files'].items():assert pin(F/name)==w,name
            name=count+'-'+estimator;folder=root/name;folder.mkdir();output=folder/'result'
            args=['--bfile','reference','--mtcojo-file',count+'.traits','--ref-ld-chr','ref-ld','--w-ld-chr','weights-ld']
            if estimator=='supplied':args+=['--mtcojo-bxy',count+'.bxy']
            args+=['--thread-num','1','--out',str(output) if host=='windows' else linux(output)]
            command=[str(exe),*args] if host=='windows' else ['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--cd',linux(F),'--exec',linux(exe),*args]
            save(folder/'COMMAND.json',dict(command=command,cwd=str(F),runtime=pin(exe),defaults=dict(gwas_thresh=5e-8,gsmr_snp_min=10,clump_kb=10000,clump_r2=.05,diff_freq=.2,heidi_thresh=.01),edition='GCTA-1.94.1',declared_estimator=estimator))
            with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=F,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,env=dict(os.environ,OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',LANG='C',LC_ALL='C'),timeout=300)
            log=(folder/'stdout').read_text(errors='replace');data=folder/'result.mtcojo.cma';row=dict(name=name,exit_code=p.returncode,table_present=data.is_file(),scientific_result_admitted=False,diagnostic_tail=log[-1700:])
            if data.is_file():
                lines=[line.split() for line in data.read_text().splitlines()];row['header']=lines[0];row['rows']=len(lines)-1;row['all_fields_finite']=all(math.isfinite(float(x)) for values in lines[1:] for x in values[3:]);row['positive_SE_and_bounded_P']=all(float(values[9])>0 and 0<=float(values[10])<=1 for values in lines[1:]);row['has_source_completion']='mtCOJO analysis completed.' in log and 'Analysis finished at' in log
            results.append(row);save(folder/'RESULT.json',row);print(host,name,'exit',p.returncode,'table',row['table_present'],row.get('rows',''),flush=True)
    assert pin(exe)['sha256']==expected
    for name,w in fixture['files'].items():assert pin(F/name)==w,name
    return host,results
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(task,['windows','linux']))
save(O/'COMPLETED.json',dict(status='ACTUAL_ORIGINAL_MTCOJO_OBSERVATIONS_REVIEW_REQUIRED',results=results,inputs_unchanged=True,not_a_project_workflow_yet=True,new_catalog_credit=0))
