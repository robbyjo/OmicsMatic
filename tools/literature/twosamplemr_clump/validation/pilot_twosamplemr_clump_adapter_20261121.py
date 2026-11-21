from pathlib import Path
import hashlib,json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-4';B=T/'build/twosamplemr-clump-adapter-1';O=B/'pilot-1';O.mkdir()
old=R/'build/mr-3/build/twosamplemr-clump-1/original-controls-5';jar=R/'build/mr-2/build/twosamplemr-workflow-1/normal-gradle-4/project-build/cli/omicsmatic-0.4.0.jar';cp=';'.join(str(p) for p in [B/'compile-1/classes',T/'src/main/resources',jar]);java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
cases=json.loads((old/'RESULT.json').read_bytes())['results'];results={}
profile='TwoSampleMR0.7.14-ieugwasr1.2.1-PLINK1.9-20260927-R4.6.1-Windows64'
for name,expected in cases.items():
    source=old/name;settings=json.loads((source/'effective-request.json').read_bytes());out=O/name
    request=dict(schema_version=1,mode='RUN',profile=profile,operation='CLUMP',out=str(out),instruments=str(source/'inputs/instruments'),reference_bed=str(source/'inputs/reference.bed'),reference_bim=str(source/'inputs/reference.bim'),reference_fam=str(source/'inputs/reference.fam'),rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_libraries=[str(R/'build/twosamplemr-runtime-intake-20261121-1/library')],plink_executable=str(R/'build/omics-plink-official/plink1/plink.exe'),plink_sha256='efc55270aba7216e4fcebdf781ee04e9fa556c2fe5be1f71c836ba73930a6b6b',reference_population='Synthetic 36-sample panel; not ancestry inferred',source_resources='original-defaults',allow_source_defaults=dict(generated_ids=True,pvalues=True),preparation_note='Synthetic saved original-controls-5 inputs copied without repair; not a real cohort',**settings)
    path=O/(name+'.request.json');path.write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8',newline='\n');command=[java,'-Xmx2g','-cp',cp,'org.jlinalg.cli.TwoSampleMrClumpCli','--request',str(path)]
    with (O/(name+'.stdout')).open('xb') as stdout,(O/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=180)
    manifest=json.loads((out/'manifest.json').read_bytes()) if (out/'manifest.json').exists() else {};result=dict(exit_code=p.returncode,expected_exit=expected['exit_code'],error=manifest.get('error'),results_admitted=manifest.get('results_admitted'))
    if p.returncode==0:
        with (O/(name+'.verify.stdout')).open('xb') as stdout,(O/(name+'.verify.stderr')).open('xb') as stderr:v=subprocess.run([java,'-cp',cp,'org.jlinalg.cli.TwoSampleMrClumpCli','--verify',str(out)],cwd=T,stdout=stdout,stderr=stderr,timeout=90)
        result['verify_exit']=v.returncode
    results[name]=result;print(name,json.dumps(result),flush=True)
(O/'RESULT.json').write_text(json.dumps(dict(results=results,all_pass=all(v['exit_code']==v['expected_exit'] and (v['exit_code']!=0 or v.get('verify_exit')==0) for v in results.values()),normal_JAR=False,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
