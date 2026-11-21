from pathlib import Path
import concurrent.futures,hashlib,json,math,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-6';B=T/'build/mtcojo-qualification-1';O=B/'examples-1';O.mkdir();OLD=R/'build/gc-5/build/gcta-mtcojo-1';F=OLD/'synthetic-inputs-1';JAR=OLD/'normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';UP=R/'build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1';J=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
def linux(p):return '/mnt/d/'+Path(p).relative_to(Path('D:/')).as_posix()
def run(command,folder,name,expected=0):
    with (folder/(name+'.stdout')).open('xb') as stdout,(folder/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    save(folder/(name+'.PROCESS.json'),dict(command=command,exit_code=p.returncode,expected_exit=expected));assert p.returncode==expected,(name,(folder/(name+'.stderr')).read_text(errors='replace')[-2500:]);return p
archive=T/'examples/mtcojo/synthetic-inputs.zip'
members=[p for p in F.rglob('*') if p.is_file() and (p.name in ['reference.bed','reference.bim','reference.fam','Y.ma','X1.ma','X2.ma','FIXTURE.json'] or p.parent.name in ['ref-ld','weights-ld'])]
with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as zipped:
    for p in sorted(members):
        info=zipfile.ZipInfo(p.relative_to(F).as_posix(),(2026,11,20,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;zipped.writestr(info,p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
classes=O/'native-classes';classes.mkdir();command=[str(J/'javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',str(JAR),'-d',str(classes),str(T/'examples/mtcojo/MtcojoConditionalExample.java')];run(command,O,'native-javac')
def native(host):
    folder=O/host;folder.mkdir();command=['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--exec','/usr/bin/java','-cp',linux(JAR)+':'+linux(classes),'MtcojoConditionalExample'] if host=='linux21' else [str(J/'java.exe') if host=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe','-cp',str(JAR)+';'+str(classes),'MtcojoConditionalExample'];run(command,folder,'native-example');lines=(folder/'native-example.stdout').read_text().splitlines();values=[line.split('\t') for line in lines[1:] if not line.startswith('#')];assert len(values)==4
    # Independent closed two-covariate inverse and scalar erfc equations.
    d1,d2=math.sqrt(.4),math.sqrt(.6);g1=(d1*.4-.2*d2*(-.25))/(.96*d1);g2=(d2*(-.25)-.2*d1*.4)/(.96*d2);expected=[]
    for y,sy,x,sx in [(.1,.02,[.05,-.02],[.01,.015]),(-.2,.03,[.1,.03],[.02,.012])]:
        beta=y-x[0]*.4;variance=sy*sy+.4*.4*sx[0]*sx[0]-2*.4*.1*sx[0]*sy;se=math.sqrt(variance);expected.append([beta,se,math.erfc(abs(beta)/se/math.sqrt(2))])
    for y,sy,x,sx in [(.1,.02,[.05,-.02],[.01,.015]),(-.2,.03,[.1,.03],[.02,.012])]:
        u1,u2=g1*sx[0],g2*sx[1];beta=y-x[0]*g1-x[1]*g2;variance=sy*sy+u1*u1+u2*u2+2*.15*u1*u2-2*sy*(.1*u1+.05*u2);se=math.sqrt(variance);expected.append([beta,se,math.erfc(abs(beta)/se/math.sqrt(2))])
    for line,wanted in zip(values,expected):
        for actual,value in zip(map(float,line[2:]),wanted):assert math.isclose(actual,value,rel_tol=3e-13,abs_tol=1e-300),(host,line,value)
    return host,dict(exit_code=0,independent_fields=12,command=command)
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:native_results=dict(pool.map(native,['windows21','windows25','linux21']))
def original(host):
    folder=O/host;inputs=folder/'extracted inputs with spaces';inputs.mkdir()
    with zipfile.ZipFile(archive) as zipped:zipped.extractall(inputs)
    for p in members:assert pin(inputs/p.relative_to(F))==pin(p)
    get=lambda p:linux(p) if host=='linux21' else str(p)
    py=['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--cd',linux(T),'--exec','/usr/bin/python3','-B'] if host=='linux21' else ['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-X','utf8','-B']
    runtime=UP/('linux/download' if host=='linux21' else 'windows/files/gcta-1.94.1-win-x86_64/gcta-win-1.94.1.exe');request=folder/'generated-request.json';output=folder/'original-example-new';command=py+[get(T/'examples/mtcojo/make_request.py'),'--target','Y='+get(inputs/'Y.ma'),'--covariate','X1='+get(inputs/'X1.ma'),'--covariate','X2='+get(inputs/'X2.ma'),'--genotype-prefix',get(inputs/'reference'),'--reference-ld-prefix',get(inputs/'ref-ld')+'/','--weight-ld-prefix',get(inputs/'weights-ld')+'/','--causal-effect-mode','supplied','--causal-effect','X1=0.35','--causal-effect','X2=-0.2','--effect-scale','as_supplied','--runtime',get(runtime),'--preparation-note','Synthetic Gaussian GWAS and matching empirical LD; supplied scale; no participant data.','--analysis-out',get(output),'--request-out',get(request)];run(command,folder,'make-request')
    prefix=['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--exec','/usr/bin/java','-cp',linux(JAR),'org.jlinalg.cli.GctaWorkflowCli'] if host=='linux21' else [str(J/'java.exe'),'-cp',str(JAR),'org.jlinalg.cli.GctaWorkflowCli'];run(prefix+['--request',get(request)],folder,'original-example');run(prefix+['--verify',get(output)],folder,'original-example-verify')
    source=OLD/'original-1.94.1-1'/('linux' if host=='linux21' else 'windows')/'multi-supplied/result.mtcojo.cma';assert [s.split() for s in (output/'worker/result.mtcojo.cma').read_text().splitlines()]==[s.split() for s in source.read_text().splitlines()]
    before=pin(request);run(command,folder,'refuse-existing-request',2);assert pin(request)==before
    assert 'NEW --request-out' in (folder/'refuse-existing-request.stderr').read_text() or 'NEW --analysis-out' in (folder/'refuse-existing-request.stderr').read_text()
    return host,dict(exit_code=0,verify_exit=0,all_original_table_tokens_equal=True,existing_request_preserved=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:original_results=dict(pool.map(original,['windows21','linux21']))
save(O/'COMPLETED.json',dict(status='PASS_NATIVE_AND_ORIGINAL_EXAMPLES',producer=pin(JAR),synthetic_archive=pin(archive),synthetic_files=len(members),sources={n:pin(T/n) for n in ['examples/mtcojo/MtcojoConditionalExample.java','examples/mtcojo/make_request.py','examples/mtcojo/README.md']},native_results=native_results,original_results=original_results,independent_Linux_compilation='SKIP3: Linux javac absent; transferred Java17-target classes executed',new_catalog_credit=0))
print('PASS native examples on3 JVMs and actual builder/original/VERIFY on2 hosts',flush=True)
