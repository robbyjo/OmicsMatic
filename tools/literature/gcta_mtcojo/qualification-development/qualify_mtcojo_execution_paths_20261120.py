from pathlib import Path
import concurrent.futures,copy,gzip,hashlib,json,math,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/gc-6';B=T/'build/mtcojo-qualification-1';B.mkdir(exist_ok=False);O=B/'execution-paths-1';O.mkdir();OLD=R/'build/gc-5/build/gcta-mtcojo-1';F=OLD/'synthetic-inputs-1';UP=R/'build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1';JAR=OLD/'normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar'
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
def linux(p):return '/mnt/d/'+Path(p).relative_to(Path('D:/')).as_posix()
def transport(v):
    if isinstance(v,dict):return {k:transport(x) for k,x in v.items()}
    if isinstance(v,list):return [transport(x) for x in v]
    return linux(str(v).replace('\\','/')) if isinstance(v,str) and (v.startswith('D:/') or v.startswith('D:\\')) else v
assert pin(JAR)==dict(bytes=196022241,sha256='3de49eb979a01116320a9ebc0d2c76174ae058cad8e45c68814dedce1c2044e2')
basepins={str(p):pin(p) for p in F.rglob('*') if p.is_file()};save(O/'ORIGINAL-INPUT-PINS.json',basepins)
variants=O/'derived-inputs';variants.mkdir();gz={}
for p in F.rglob('*'):
    if p.is_file() and (p.suffix=='.ma' or p.name.endswith('.l2.ldscore')):
        target=variants/(p.relative_to(F).as_posix()+'.gz');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(gzip.compress(p.read_bytes(),mtime=0));gz[str(p)]=str(target)
raw=(F/'Y.ma').read_bytes();assert raw.endswith(b'\n')
(variants/'truncated-record.ma.gz').write_bytes(gzip.compress(raw[:-1],mtime=0))
bad=bytearray(gzip.compress(raw,mtime=0));bad[-8]^=1;(variants/'crc.ma.gz').write_bytes(bad)
first=raw.splitlines(keepends=True);first[1]=first[1].rstrip(b'\r\n')+b' '*1024+b'\n';(variants/'long-record.ma.gz').write_bytes(gzip.compress(b''.join(first),mtime=0))
ld=(F/'ref-ld/1.l2.ldscore').read_bytes();(variants/'duplicate.ldscore').write_bytes(ld+ld.splitlines(keepends=True)[1])
(variants/'not-gcta.bin').write_bytes(b'Explicit invalid-runtime identity control; never execute this file.\n')
def prefix(host):
    if host=='linux21':return ['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--cd',linux(T),'--exec','/usr/bin/java','-cp',linux(JAR),'org.jlinalg.cli.GctaWorkflowCli']
    return ['C:/Program Files/Java/jdk-25/bin/java.exe' if host=='windows25' else 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-cp',str(JAR),'org.jlinalg.cli.GctaWorkflowCli']
def source_request(count,mode):return json.loads((OLD/'normal-workflow-runs-1/windows'/(count+'-'+mode+'.request.json')).read_bytes())
def gzip_request(r):
    for item in r['traits']:item.update(summary=gz[item['summary']],compression='gzip')
    for key in ['reference_ld','weight_ld']:
        for item in r[key]:item.update(scores=gz[item['scores']],compression='gzip')
def cases():
    items=[]
    for name,count,mode in [('single-supplied-zero','single','supplied'),('multi-prevalence','multi','supplied'),('multi-prevalence-swapped','multi','supplied'),('single-supplied-low-IV','single','supplied'),('single-GSMR-gzip','single','GSMR'),('multi-supplied-gzip','multi','supplied')]:
        r=source_request(count,mode)
        if name.endswith('-zero'):r['causal_effects']['X1']=0
        if 'prevalence' in name:
            for i,trait in enumerate(r['traits']):
                sample,population=[(.25,.01),(.4,.05),(.3,.08)][i]
                if name.endswith('-swapped'):sample,population=population,sample
                trait.update(sample_prevalence=sample,population_prevalence=population)
        if name.endswith('-low-IV'):r['gwas_p']=1e-100
        if name.endswith('-gzip'):gzip_request(r)
        reference=OLD/'original-behavior-controls-1'/'{host}'/name/'result.mtcojo.cma' if not name.endswith('-gzip') else OLD/'original-1.94.1-1'/'{host}'/(count+'-'+mode)/'result.mtcojo.cma'
        items.append((name,r,0,None,str(reference)))
    for count in ['single','multi']:
        r=source_request(count,'GSMR');r['gwas_p']=1e-100;items.append((count+'-GSMR-low-IV',r,2,'fallback',None))
    for name,mode in [('supplied-sentinel','supplied'),('fractional-LD-FDR','GSMR'),('missing-runtime','supplied'),('wrong-runtime','supplied'),('missing-LD','supplied'),('gzip-CRC','supplied'),('gzip-final-LF','supplied'),('gzip-long-record','supplied'),('duplicate-LD','supplied'),('original-timeout','GSMR'),('original-output-cap','GSMR')]:
        r=source_request('single',mode);expected=2;needle=None
        if name=='supplied-sentinel':r['causal_effects']['X1']=100;needle='abs(bxy)>=100'
        elif name=='fractional-LD-FDR':r['ld_fdr']=.01;needle='integer conversion'
        elif name=='missing-runtime':r['runtime']=str(variants/'absent-runtime');expected=3;needle='runtime'
        elif name=='wrong-runtime':r['runtime']=str(variants/'not-gcta.bin');expected=3;needle='runtime'
        elif name=='missing-LD':r['reference_ld'][0]['scores']=str(variants/'absent-LD');expected=3;needle='Required permitted input is unavailable'
        elif name.startswith('gzip-'):
            file={'gzip-CRC':'crc.ma.gz','gzip-final-LF':'truncated-record.ma.gz','gzip-long-record':'long-record.ma.gz'}[name];r['traits'][0].update(summary=str(variants/file),compression='gzip');needle={'gzip-CRC':'Corrupt GZIP trailer','gzip-final-LF':'final LF','gzip-long-record':'1023'}[name]
        elif name=='duplicate-LD':r['reference_ld'][0]['scores']=str(variants/'duplicate.ldscore');needle='Duplicate'
        elif name=='original-timeout':r['timeout_seconds']=1;needle='timed out'
        elif name=='original-output-cap':r['maximum_output_bytes']=128;needle='maximum_output_bytes'
        items.append((name,r,expected,needle,None))
    assert len(items)==19
    return items
def task(host):
    folder=O/host;folder.mkdir();rows=[];actualhost='linux' if host=='linux21' else 'windows'
    for name,original,expected,needle,reference in cases():
        r=copy.deepcopy(original);r['out']=str(folder/(name+'-bundle'))
        if name not in ['missing-runtime','wrong-runtime']:r['runtime']=str(UP/('linux/download' if host=='linux21' else 'windows/files/gcta-1.94.1-win-x86_64/gcta-win-1.94.1.exe'))
        if host=='linux21':r=transport(r)
        path=folder/(name+'.request.json');save(path,r);command=prefix(host)+['--request',linux(path) if host=='linux21' else str(path)]
        with (folder/(name+'.stdout')).open('xb') as stdout,(folder/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        error=(folder/(name+'.stderr')).read_text(errors='replace');bundle=folder/(name+'-bundle');state=json.loads((bundle/'analysis-status.json').read_bytes()) if bundle.is_dir() else None
        row=dict(name=name,command=command,exit_code=p.returncode,expected_exit=expected,cause_seen=needle is None or needle in error,expected_cause=needle,bundle_created=bundle.is_dir(),state=state)
        if p.returncode==0:
            baseline=Path(reference.format(host=actualhost));table=bundle/'worker/result.mtcojo.cma';row['all_original_table_tokens_equal']=[s.split() for s in table.read_text().splitlines()]==[s.split() for s in baseline.read_text().splitlines()];row['original_reference']=str(baseline)
            command2=prefix(host)+['--verify',linux(bundle) if host=='linux21' else str(bundle)]
            with (folder/(name+'.verify.stdout')).open('xb') as stdout,(folder/(name+'.verify.stderr')).open('xb') as stderr:v=subprocess.run(command2,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
            row['verify_exit']=v.returncode
        elif bundle.is_dir():
            assert state['results_admitted'] is False and (bundle/'WARNINGS.md').is_file() and (bundle/'capture-files.json').is_file()
            command2=prefix(host)+['--verify',linux(bundle) if host=='linux21' else str(bundle)]
            with (folder/(name+'.verify.stdout')).open('xb') as stdout,(folder/(name+'.verify.stderr')).open('xb') as stderr:v=subprocess.run(command2,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
            row['incomplete_verify_exit']=v.returncode
        if name in ['single-GSMR-low-IV','multi-GSMR-low-IV'] and bundle.is_dir():
            source=json.loads((bundle/'worker/analysis-command.json').read_bytes());table=bundle/'worker/result.mtcojo.cma';row['original_exit_code']=source.get('exit_code');row['source_NaN_table_retained']=table.is_file() and any(not math.isfinite(float(x)) for line in table.read_text().splitlines()[1:] for x in line.split()[8:11])
        if name in ['original-timeout','original-output-cap'] and bundle.is_dir():
            record=json.loads((bundle/'worker/analysis-command.json').read_bytes());row['owned_process_cleanup']=record.get('owned_process_cleanup',[]);pid=int(record['pid']);probe=['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--exec','/usr/bin/kill','-0',str(pid)] if host=='linux21' else ['C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-NonInteractive','-Command',f'if(Get-Process -Id {pid} -ErrorAction SilentlyContinue){{exit 1}}else{{exit 0}}'];observed=subprocess.run(probe,capture_output=True,timeout=30);row['terminal_probe']=dict(command=probe,exit_code=observed.returncode,observed_terminal=observed.returncode==(1 if host=='linux21' else 0))
        rows.append(row);(folder/'PROGRESS.json').write_text(json.dumps(rows,indent=2)+'\n',encoding='utf-8');print('MTCOJO execution',host,name,'exit',p.returncode,'expected',expected,'cause',row['cause_seen'],flush=True)
        if p.returncode!=expected or not row['cause_seen']:row['review_required']=error[-3000:]
    save(folder/'RESULTS.json',rows);return host,rows
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(task,['windows21','windows25','linux21']))
for name,w in basepins.items():assert pin(Path(name))==w,name
save(O/'COMPLETED.json',dict(status='EXECUTION_OBSERVATIONS_REVIEW_REQUIRED',producer=pin(JAR),results=results,original_inputs_unchanged=True,new_catalog_credit=0))
print('Saved19 actual execution observations per JVM for review',flush=True)
