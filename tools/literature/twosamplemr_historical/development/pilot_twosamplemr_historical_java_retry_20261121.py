from pathlib import Path
import json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';O=B/'java-pilot-2';O.mkdir();old=B/'original-pilot-2';V=R/'build/twosamplemr-historical-runtime-20261121-1';J=R/'build/mr-4/build/twosamplemr-clump-adapter-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';cp=';'.join(map(str,[B/'compile-2/classes',T/'src/main/resources',J]));java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';results={}
for key,prior in json.loads((old/'RESULT.json').read_bytes())['results'].items():
    version,name=key.split('/');source=old/version/name;proto=json.loads((source/'effective-request.json').read_bytes());out=O/version/name;out.parent.mkdir(exist_ok=True);request=out.parent/(name+'.json');lib=V/'versions'/('0.4.9-documentation-only-attempt-3' if version=='0.4.9' else version)/'library'
    r=dict(schema_version=1,mode='RUN',profile=proto['profile'],operation=proto['operation'],out=str(out),rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_libraries=list(map(str,[lib,V/'library',R/'build/twosamplemr-runtime-intake-20261121-1/library'])),seed=proto['seed'],preparation_note='Synthetic original-pilot-2 literal inputs; historical source under declared present-day dependencies, not paper/runtime-era reproduction')
    if proto['operation']=='FORMAT':r.update(data=str(source/'inputs/data'),format_policy='source-defaults-with-overrides',format_options=proto['format_options'])
    else:
        for field in ['exposure','outcome','harmonised']:
            if (source/'inputs'/field).exists():r[field]=str(source/'inputs'/field)
        if 'harmonise_action' in proto:r['harmonise_action']=proto['harmonise_action']
        if proto['operation'] in ['MR','HARMONISE_AND_MR']:r.update(methods=proto['methods'],parameters=proto['parameters'],sensitivity=proto['sensitivity'],parameter_policy='source-defaults-with-overrides')
    request.write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8',newline='\n');command=[java,'-Xmx2g','-cp',cp,'org.jlinalg.cli.TwoSampleMrHistoricalCli','--request',str(request)]
    with (out.parent/(name+'.stdout')).open('xb') as stdout,(out.parent/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=180)
    manifest=json.loads((out/'manifest.json').read_bytes()) if (out/'manifest.json').exists() else {};result=dict(exit_code=p.returncode,expected_exit=prior['exit_code'],error=manifest.get('error'),verification_error=manifest.get('verification_error'),results_admitted=manifest.get('results_admitted'))
    if p.returncode==0:
        with (out.parent/(name+'.verify.stdout')).open('xb') as stdout,(out.parent/(name+'.verify.stderr')).open('xb') as stderr:q=subprocess.run([java,'-cp',cp,'org.jlinalg.cli.TwoSampleMrHistoricalCli','--verify',str(out)],cwd=T,stdout=stdout,stderr=stderr,timeout=90)
        result['verify_exit']=q.returncode
    results[key]=result;print(key,json.dumps(result),flush=True)
(O/'RESULT.json').write_text(json.dumps(dict(results=results,all_pass=all(v['exit_code']==v['expected_exit'] and (v['exit_code']!=0 or v.get('verify_exit')==0) for v in results.values()),normal_JAR=False,new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
