from pathlib import Path
import copy,json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-8';B=T/'build/twosamplemr-global-1';O=B/'cli-controls-1';O.mkdir();J=B/'normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';OLD=R/'build/mr-7/build/twosamplemr-historical-clump-1/normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar';JAVA='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';basefolder=B/'global-examples-replays-1/format-0.4.9';base=json.loads((basefolder/'recipes/format-0.4.9.json').read_bytes());results={}
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
for name,expected in [('unknown-profile',3),('invalid-operation',2),('malformed-json',2),('incomplete-capture',2),('foreign-engine',2),('engine-profile-conflict',2),('missing-R',3),('missing-input',3),('replay-profile-override',2),('unknown-mode',2)]:
    folder=O/name;folder.mkdir();r=copy.deepcopy(base);r['data']=str((basefolder/'recipes'/base['data']).resolve());r['out']=str(folder/'analysis');request=folder/'request.json'
    if name=='unknown-profile':r['profile']='TwoSampleMR-UNQUALIFIED'
    elif name=='invalid-operation':r['operation']='CHOOSE_ESTIMATOR_FOR_ME'
    elif name=='unknown-mode':r['mode']='AUTO'
    elif name=='missing-R':r['rscript']=str(folder/'missing.exe')
    elif name=='missing-input':r['data']=str(folder/'missing.tsv')
    elif name=='replay-profile-override':r=dict(schema_version=1,mode='REPLAY',analysis=str(basefolder/'out/format-0.4.9'),out=str(folder/'analysis'),profile='TwoSampleMR-UNQUALIFIED')
    elif name in ['incomplete-capture','foreign-engine','engine-profile-conflict']:
        capture=folder/'capture';capture.mkdir();manifest=dict(engine='original-twosamplemr-historical-workflows',profile=base['profile'])
        if name=='foreign-engine':manifest['engine']='some-other-tool'
        if name=='engine-profile-conflict':manifest['profile']='TwoSampleMR0.7.14-c08ab8a6-R4.6.1'
        save(capture/'manifest.json',manifest);r=dict(schema_version=1,mode='VERIFY',analysis=str(capture))
    if name=='malformed-json':request.write_text('{',encoding='utf-8',newline='\n')
    else:save(request,r)
    command=[JAVA,'-Xmx1g','-jar',str(J),'twosamplemr-tools','--request',str(request)];save(folder/'COMMAND.json',dict(command=command,expected_exit=expected))
    with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=90)
    state=json.loads((folder/'analysis/analysis-status.json').read_bytes()) if (folder/'analysis/analysis-status.json').exists() else {};err=(folder/'stderr').read_text(encoding='utf-8',errors='replace');result=dict(exit_code=p.returncode,expected_exit=expected,results_admitted=state.get('results_admitted',False),prominent_recovery=('DEPENDENT ANALYSIS SKIPPED' if expected==3 else 'NO COMPLETE RESULT') in err)
    assert p.returncode==expected and not result['results_admitted'] and result['prominent_recovery'],(name,result);results[name]=result;print(name,result,flush=True)
neighbors={}
for name,args in [('peer',['peer-tools','--help']),('limma',['limma-tools','--help']),('gcta',['gcta-tools','--help']),('gsea',['broad-gsea-tools','--help']),('edgeR',['edger-tools','--help']),('ldsc',['ldsc-tools','--help'])]:
    before=subprocess.run([JAVA,'-jar',str(OLD),*args],cwd=T,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60);after=subprocess.run([JAVA,'-jar',str(J),*args],cwd=T,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60);assert (before.returncode,before.stdout,before.stderr)==(after.returncode,after.stdout,after.stderr) and after.returncode==0,name
    (O/(name+'-before.stdout')).write_bytes(before.stdout);(O/(name+'-after.stdout')).write_bytes(after.stdout);(O/(name+'-before.stderr')).write_bytes(before.stderr);(O/(name+'-after.stderr')).write_bytes(after.stderr);neighbors[name]=dict(exit_code=0,stdout_stderr_identical=True,arguments=args)
save(O/'RESULT.json',dict(status='PASS_TEN_GLOBAL_REFUSALS_SIX_NEIGHBOR_HELP_IDENTITIES',results=results,neighbor_help=neighbors,new_catalog_credit=0,whole_goal_complete=False))
