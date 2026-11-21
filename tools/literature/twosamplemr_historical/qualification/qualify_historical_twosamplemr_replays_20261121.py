from pathlib import Path
import copy,hashlib,json,os,shutil,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-6';B=T/'build/twosamplemr-historical-qualification-1';P=R/'build/mr-5/build/twosamplemr-historical-1';O=B/'replays-examples-1';O.mkdir();E=T/'examples/twosamplemr-historical';E.mkdir();(E/'data').mkdir();(E/'recipes').mkdir();J=P/'normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar';results={}
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
def pins(root):return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
def execute(folder,label,command,env=None):
    save(folder/(label+'-COMMAND.json'),dict(command=command))
    with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,env=env,stdout=stdout,stderr=stderr,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW)
    save(folder/(label+'-PROCESS.json'),dict(exit_code=p.returncode));assert p.returncode==0,(label,(folder/(label+'.stderr')).read_text(encoding='utf-8',errors='replace')[-1200:]);return p.returncode
cases=[('format-0.4.9','0.4.9/format-exposure'),('mr-0.5.6','0.5.6/mr-default'),('pipeline-0.5.7','0.5.7/pipeline'),('multivariable-0.6.8','0.6.8/multivariable')]
assert json.loads((B/'normal-analyses-1/RESULT.json').read_bytes())['successes']==45
for name,key in cases:
    parent=B/'normal-analyses-1'/key/'analysis';request=json.loads((B/'normal-analyses-1'/key/'request.json').read_bytes());r=copy.deepcopy(request);r.update(out='../outputs/'+name,rscript='REPLACE_WITH_EXACT_R_4.6.1_RSCRIPT_PATH',r_libraries=['REPLACE_WITH_SELECTED_VERSION_LIBRARY','REPLACE_WITH_HISTORICAL_EXTRAS_LIBRARY','REPLACE_WITH_PROTECTED_PRECEDING_LIBRARY'])
    for field in ['data','exposure','outcome','harmonised']:
        if field in r:
            filename=name+'.'+field+'.tsv';shutil.copyfile(Path(request[field]),E/'data'/filename);r[field]='../data/'+filename
    r['preparation_note']='Unmodified synthetic qualification input; explicit tagged source, present-day dependencies and source defaults with declared overrides; not a paper replication'
    save(E/'recipes'/(name+'.json'),r)
for kind in ['replay','example']:
    for name,key in cases:
        folder=O/(kind+'-'+name);folder.mkdir();java='C:/Program Files/Java/jdk-25/bin/java.exe' if name=='mr-0.5.6' else 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
        if kind=='replay':
            parent=B/'normal-analyses-1'/key/'analysis';before=pins(parent);capture=folder/'analysis';r=dict(schema_version=1,mode='REPLAY',analysis=str(parent),out=str(capture));request=folder/'request.json'
        else:
            shutil.copytree(E/'data',folder/'data');(folder/'recipes').mkdir();r=json.loads((E/'recipes'/(name+'.json')).read_bytes());binding=json.loads((B/'normal-analyses-1'/key/'analysis/worker/runtime-bindings.json').read_bytes());r.update(rscript=binding['rscript'],r_libraries=binding['libraries']);request=folder/'recipes'/(name+'.json');capture=(request.parent/r['out']).resolve()
        save(request,r);execute(folder,'run',[java,'-Xmx1g','-cp',str(J),'org.jlinalg.cli.TwoSampleMrHistoricalCli','--request',str(request)]);execute(folder,'verify',[java,'-Xmx1g','-cp',str(J),'org.jlinalg.cli.TwoSampleMrHistoricalCli','--verify',str(capture)])
        proof=folder/'direct-original';execute(folder,'compare',['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(T/'tools/literature/twosamplemr_historical/compare_original.R'),str(capture),str(proof)],dict(os.environ,LANG='C',LC_ALL='C'));direct=json.loads((proof/'RESULT.json').read_bytes());result=dict(exit_code=0,full_original_returns=direct['full_original_returns'],original_errors=direct['original_errors'],RNG_classes_attributes_identical=True,Java='25' if name=='mr-0.5.6' else '21')
        if kind=='replay':
            assert pins(parent)==before;assert pins(parent/'results')==pins(capture/'results');result.update(parent_files_unchanged=len(before),exports_identical=True)
        else:result['only_runtime_paths_substituted']=True
        results[kind+'-'+name]=result;print(kind,name,result,flush=True)
def linux(p):return '/mnt/'+str(p)[0].lower()+'/'+str(p)[3:].replace('\\','/')
verifies={}
for key in ['0.4.9/format-exposure','0.4.9/multivariable-latin','0.5.6/mr-default','0.5.7/pipeline','0.6.8/multivariable','0.6.8/mr-modes','0.6.8/format-all-missing']:
    label=key.replace('/','-');capture=B/'normal-analyses-1'/key/'analysis';folder=O/('linux-'+label);folder.mkdir();command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','java','-Xmx1g','-cp',linux(J),'org.jlinalg.cli.TwoSampleMrHistoricalCli','--verify',linux(capture)];execute(folder,'verify',command);verifies[key]=dict(exit_code=0,R_executed=False)
save(O/'RESULT.json',dict(status='PASS_4_REAL_REPLAYS_4_PUBLIC_EXAMPLES_7_LINUX_OFFLINE_VERIFICATIONS',results=results,Linux_R_free_verifications=verifies,full_original_returns=sum(x['full_original_returns'] for x in results.values()),example_source_inputs={p.relative_to(T).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for part in ['data','recipes'] for p in (E/part).rglob('*') if p.is_file()},new_catalog_credit=0,whole_goal_complete=False))
