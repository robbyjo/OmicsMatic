from pathlib import Path
import hashlib,json,os,shutil,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-7';B=T/'build/twosamplemr-historical-clump-1';O=B/'replays-examples-1';O.mkdir();E=T/'examples/twosamplemr-historical-clump';J=B/'normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar';results={};parents={}
def pins(root):return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
for kind in ['replay','example']:
    for name in ['basic','multiple-groups','numeric-looking-ids']:
        out=O/(kind+'-'+name);out.mkdir();java='C:/Program Files/Java/jdk-25/bin/java.exe' if name=='multiple-groups' else 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
        if kind=='replay':
            parent=B/'normal-analyses-1'/name/'analysis';parents[name]=pins(parent);analysis=out/'analysis';r=dict(schema_version=1,mode='REPLAY',analysis=str(parent),out=str(analysis));request=out/'request.json'
        else:
            shutil.copytree(E/'data',out/'data');(out/'recipes').mkdir();r=json.loads((E/'recipes'/(name+'.json')).read_bytes());r.update(rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_libraries=[str(R/'build/twosamplemr-historical-runtime-20261121-1/versions/0.6.8/library'),str(R/'build/twosamplemr-historical-runtime-20261121-1/library'),str(R/'build/twosamplemr-runtime-intake-20261121-1/library')],plink_executable=str(R/'build/omics-plink-official/plink1/plink.exe'));request=out/'recipes'/(name+'.json');analysis=(request.parent/r['out']).resolve()
        save(request,r);command=[java,'-Xmx1g','-cp',str(J),'org.jlinalg.cli.TwoSampleMrHistoricalClumpCli','--request',str(request)]
        with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=180)
        assert p.returncode==0,(name,(out/'stderr').read_text()[-1500:])
        proof=out/'independent-original';tmp=out/'original-temporary';tmp.mkdir();env=dict(os.environ,LANG='C',LC_ALL='C',TMPDIR=str(tmp),TEMP=str(tmp),TMP=str(tmp));compare=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(T/'tools/literature/twosamplemr_historical_clump/compare_original.R'),str(analysis),str(proof)]
        with (out/'compare.stdout').open('xb') as stdout,(out/'compare.stderr').open('xb') as stderr:q=subprocess.run(compare,cwd=T,env=env,stdout=stdout,stderr=stderr,timeout=180)
        assert q.returncode==0,(name,(out/'compare.stderr').read_text()[-1500:]);count=len(json.loads((proof/'RESULT.json').read_bytes())['calls']);result=dict(exit_code=p.returncode,full_original_returns_and_RNG_identical=count,Java='25' if name=='multiple-groups' else '21')
        if kind=='replay':
            assert pins(parent)==parents[name];assert pins(parent/'results')==pins(analysis/'results');result.update(parent_files_unchanged=len(parents[name]),exports_identical=True)
        results[kind+'-'+name]=result;print(kind,name,result,flush=True)
linux={}
for name in ['basic','multiple-groups','numeric-looking-ids','empty-input','single-absent-above-p']:
    analysis=B/'normal-analyses-1'/name/'analysis';jar='/mnt/d/'+J.relative_to('D:/').as_posix();capture='/mnt/d/'+analysis.relative_to('D:/').as_posix();command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','java','-Xmx1g','-cp',jar,'org.jlinalg.cli.TwoSampleMrHistoricalClumpCli','--verify',capture]
    with (O/(name+'.linux.stdout')).open('xb') as stdout,(O/(name+'.linux.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=90)
    assert p.returncode==0,name;linux[name]=dict(exit_code=0,R_executed=False)
save(O/'RESULT.json',dict(status='PASS_THREE_REAL_REPLAYS_THREE_PUBLIC_EXAMPLES_FIVE_LINUX_OFFLINE_VERIFICATIONS',results=results,Linux_R_free_verifications=linux,source_inputs={p.relative_to(T).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for part in ['data','recipes'] for p in (E/part).rglob('*') if p.is_file()},whole_goal_complete=False))
