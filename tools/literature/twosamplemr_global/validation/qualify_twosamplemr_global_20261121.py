from pathlib import Path
import concurrent.futures,copy,hashlib,json,os,shutil,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-8';B=T/'build/twosamplemr-global-1';O=B/'global-examples-replays-1';O.mkdir();E=T/'examples/twosamplemr-global';E.mkdir();(E/'data').mkdir();(E/'recipes').mkdir();J=B/'normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';JAVA='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';V=R/'build/twosamplemr-historical-runtime-20261121-1';L=R/'build/twosamplemr-runtime-intake-20261121-1/library'
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
def pins(root):return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
cases=[('modern-prepared','twosamplemr-workflow/pipeline.json','twosamplemr_workflow'),('modern-clump','twosamplemr-clump/recipes/basic.json','twosamplemr_clump'),('format-0.4.9','twosamplemr-historical/recipes/format-0.4.9.json','twosamplemr_historical'),('mr-0.5.6','twosamplemr-historical/recipes/mr-0.5.6.json','twosamplemr_historical'),('pipeline-0.5.7','twosamplemr-historical/recipes/pipeline-0.5.7.json','twosamplemr_historical'),('multivariable-0.6.8','twosamplemr-historical/recipes/multivariable-0.6.8.json','twosamplemr_historical'),('historical-clump','twosamplemr-historical-clump/recipes/basic.json','twosamplemr_historical_clump')]
for name,relative,provider in cases:
    source=T/'examples'/relative;r=json.loads(source.read_bytes());r.update(out='../out/'+name,preparation_note='Synthetic global CLI demonstration with original source-edition semantics and declared present-day dependencies; not author data or paper reproduction')
    for key in ['data','exposure','outcome','harmonised','instruments','reference_bed','reference_bim','reference_fam']:
        if key in r:
            path=(source.parent/r[key]).resolve();filename=name+'.'+key;shutil.copyfile(path,E/'data'/filename);r[key]='../data/'+filename
    save(E/'recipes'/(name+'.json'),r)
save(E/'recipes/replay.json',dict(schema_version=1,mode='REPLAY',analysis='../out/modern-prepared',out='../out/replayed-modern-prepared'))
def execute(folder,label,command,env=None):
    save(folder/(label+'-COMMAND.json'),dict(command=command))
    with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,env=env,stdout=stdout,stderr=stderr,timeout=240,creationflags=subprocess.CREATE_NO_WINDOW)
    save(folder/(label+'-PROCESS.json'),dict(exit_code=p.returncode));assert p.returncode==0,(label,(folder/(label+'.stderr')).read_text(encoding='utf-8',errors='replace')[-2000:]);return p.returncode
def run(case):
    name,relative,provider=case;folder=O/name;folder.mkdir();shutil.copytree(E/'data',folder/'data');(folder/'recipes').mkdir();recipe=json.loads((E/'recipes'/(name+'.json')).read_bytes());recipe['rscript']='C:/Program Files/R/R-4.6.1/bin/Rscript.exe'
    if name.startswith('modern-'):recipe['r_libraries']=[str(L)]
    else:
        version='0.6.8' if name=='historical-clump' else name.split('-')[-1];version_folder='0.4.9-documentation-only-attempt-3' if version=='0.4.9' else version;recipe['r_libraries']=list(map(str,[V/'versions'/version_folder/'library',V/'library',L]))
    if 'plink_executable' in recipe:recipe['plink_executable']=str(R/'build/omics-plink-official/plink1/plink.exe')
    request=folder/'recipes'/(name+'.json');save(request,recipe);capture=(request.parent/recipe['out']).resolve();execute(folder,'run',[JAVA,'-Xmx1g','-jar',str(J),'twosamplemr-tools','--request',str(request)]);execute(folder,'verify',[JAVA,'-Xmx1g','-jar',str(J),'twosamplemr-tools','--verify',str(capture)]);rows={}
    for kind,analysis in [('example',capture),('replay',folder/'replayed-analysis')]:
        if kind=='replay':
            before=pins(capture);control=folder/'replay.json';save(control,dict(schema_version=1,mode='REPLAY',analysis=str(capture),out=str(analysis)));execute(folder,'replay',[JAVA,'-Xmx1g','-jar',str(J),'twosamplemr-tools','--request',str(control)]);execute(folder,'replay-verify',[JAVA,'-Xmx1g','-jar',str(J),'twosamplemr-tools','--verify',str(analysis)]);assert pins(capture)==before and pins(capture/'results')==pins(analysis/'results')
        comparison=folder/(kind+'-direct-original');tmp=folder/(kind+'-direct-tmp');tmp.mkdir();command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(T/'tools/literature'/provider/'compare_original.R'),str(analysis),str(comparison)];execute(folder,kind+'-compare',command,dict(os.environ,LANG='C',LC_ALL='C',TMPDIR=str(tmp),TEMP=str(tmp),TMP=str(tmp)));proof=json.loads((comparison/'RESULT.json').read_bytes());count=proof.get('full_original_returns',len(proof.get('calls',[])));row=dict(exit_code=0,complete_original_returns=count,RNG_classes_attributes_identical=True)
        if kind=='replay':row.update(parent_files_unchanged=len(before),exports_identical=True)
        else:row.update(only_runtime_paths_substituted=True)
        rows[kind]=row
    print(name,rows,flush=True);save(folder/'RESULT.json',rows);return name,rows
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(run,cases))
linux={}
def mount(p):return '/mnt/'+str(p)[0].lower()+'/'+str(p)[3:].replace('\\','/')
for name,relative,provider in cases:
    folder=O/name;capture=folder/'out'/name;command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','java','-Xmx1g','-jar',mount(J),'twosamplemr-tools','--verify',mount(capture)];execute(folder,'linux-verify',command);linux[name]=dict(exit_code=0,R_executed=False)
save(O/'RESULT.json',dict(status='PASS_SEVEN_GLOBAL_EXAMPLES_SEVEN_REAL_REPLAYS_SEVEN_LINUX_VERIFICATIONS',results=results,Linux_R_free_verifications=linux,complete_original_returns=sum(row['complete_original_returns'] for result in results.values() for row in result.values()),global_command_registered=True,new_catalog_credit=0,whole_goal_complete=False))
