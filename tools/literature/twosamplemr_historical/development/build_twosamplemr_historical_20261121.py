from pathlib import Path
import hashlib,json,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';O=B/'fixture-1';O.mkdir();source=B/'java-pilot-2';result=json.loads((source/'RESULT.json').read_bytes());assert result['all_pass'];target=T/'src/test/resources/org/jlinalg/cli/twosamplemr-historical.zip';assert not target.exists();pins={};cases=[]
with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for key,row in sorted(result['results'].items()):
        if row['exit_code']!=0:continue
        cases.append(key);root=source/key
        for path in sorted(root.rglob('*')):
            if not path.is_file():continue
            name=key+'/'+path.relative_to(root).as_posix();raw=path.read_bytes();pins[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());info=zipfile.ZipInfo(name,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,raw)
assert len(cases)==44 and len(pins)<4000
(O/'INVENTORY.json').write_text(json.dumps(pins,indent=2)+'\n',encoding='utf-8',newline='\n');(O/'PROFILE.json').write_text(json.dumps(dict(scope='Actual quick-compiled Java pilot-2 captures of original tagged package execution, not normal-JAR qualification',cases=cases,files=len(pins),archive_bytes=target.stat().st_size,archive_sha256=hashlib.sha256(target.read_bytes()).hexdigest(),new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
s=(R/'build/run_twosamplemr_workflow_gradle_20261121.py').read_text().replace("T=R/'build/mr-2';B=T/'build/twosamplemr-workflow-1'","T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1'")
s=s.replace("names=[p.relative_to(T).as_posix()", "command+=['--tests','org.jlinalg.cli.TwoSampleMrClumpTest','--tests','org.jlinalg.cli.TwoSampleMrHistoricalTest']\nnames=[p.relative_to(T).as_posix()",1)
needle='for n in names:';assert needle in s;s=s.replace(needle,"names+=[p.relative_to(T).as_posix() for stem in ['TwoSampleMrClump','TwoSampleMrHistorical'] for p in (T/'src/main/java/org/jlinalg/cli').glob(stem+'*.java')]\nnames+=[p.relative_to(T).as_posix() for sub in ['twosamplemr-clump','twosamplemr-historical'] for p in (T/'src/main/resources/org/jlinalg/omics'/sub).rglob('*') if p.is_file()]\nnames+=['src/test/java/org/jlinalg/cli/TwoSampleMrClumpTest.java','src/test/resources/org/jlinalg/cli/twosamplemr-clump-0.7.14.zip','src/test/java/org/jlinalg/cli/TwoSampleMrHistoricalTest.java','src/test/resources/org/jlinalg/cli/twosamplemr-historical.zip']\nfor n in names:")
path=B/'run-normal-gradle.py';path.write_text(s,encoding='utf-8',newline='\n');print('Fixture',len(cases),'cases',len(pins),'files',target.stat().st_size,'bytes',flush=True)
raise SystemExit(subprocess.run(['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-X','utf8','-B',str(path),'1'],cwd=T).returncode)
