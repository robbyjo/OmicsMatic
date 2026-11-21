from pathlib import Path
import json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';O=B/'java-variants-recovery-1';O.mkdir();source=B/'java-variants-1/PROVIDED_LIBRARIES_FACTORS/request.json';r=json.loads(source.read_bytes());r['out']=str(O/'analysis');request=O/'request.json';request.write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8',newline='\n')
prior=R/'build/lm-2/build/limma-native-1/normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar';cp=';'.join(map(str,[B/'compile-probe-5/classes',T/'src/main/resources',prior]));results={}
for mode,args in [('RUN',['--request',str(request)]),('VERIFY',['--verify',r['out']])]:
    command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-cp',cp,'org.jlinalg.cli.LimmaWorkflowCli',*args]
    with (O/(mode+'.stdout')).open('xb') as stdout,(O/(mode+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    results[mode]=dict(exit_code=p.returncode,command=command);print('Provided factor recovery',mode,p.returncode,flush=True)
    if p.returncode:print((O/(mode+'.stderr')).read_text(errors='replace')[:600],flush=True);break
(O/'PROCESS.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8',newline='\n')
