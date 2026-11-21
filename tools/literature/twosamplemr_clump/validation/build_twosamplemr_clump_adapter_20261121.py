from pathlib import Path
import hashlib,json,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/mr-4';B=T/'build/twosamplemr-clump-adapter-1';O=B/'fixture-1';O.mkdir()
source=B/'pilot-2';result=json.loads((source/'RESULT.json').read_bytes());assert result['all_pass']
target=T/'src/test/resources/org/jlinalg/cli/twosamplemr-clump-0.7.14.zip';assert not target.exists();pins={};cases=[]
with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for name,row in sorted(result['results'].items()):
        if row['exit_code']!=0:continue
        cases.append(name)
        for path in sorted((source/name).rglob('*')):
            if not path.is_file():continue
            member=name+'/'+path.relative_to(source/name).as_posix();raw=path.read_bytes();pins[member]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());info=zipfile.ZipInfo(member,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,raw)
assert len(cases)==21 and len(pins)<3000
(O/'INVENTORY.json').write_text(json.dumps(pins,indent=2)+'\n',encoding='utf-8',newline='\n');(O/'PROFILE.json').write_text(json.dumps(dict(original_source='pilot-2 actual R/PLINK captures; quick-compiled Java adapter, not normal JAR qualification',cases=cases,files=len(pins),archive_bytes=target.stat().st_size,archive_sha256=hashlib.sha256(target.read_bytes()).hexdigest(),new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
s=(R/'build/run_twosamplemr_workflow_gradle_20261121.py').read_text().replace("T=R/'build/mr-2';B=T/'build/twosamplemr-workflow-1'","T=R/'build/mr-4';B=T/'build/twosamplemr-clump-adapter-1'")
s=s.replace("names=[p.relative_to(T).as_posix()", "command+=['--tests','org.jlinalg.cli.TwoSampleMrClumpTest']\nnames=[p.relative_to(T).as_posix()",1)
s=s.replace('for n in names:', "names+=[p.relative_to(T).as_posix() for p in (T/'src/main/java/org/jlinalg/cli').glob('TwoSampleMrClump*.java')]\nnames+=[p.relative_to(T).as_posix() for p in (T/'src/main/resources/org/jlinalg/omics/twosamplemr-clump').iterdir() if p.is_file()]\nnames+=['src/test/java/org/jlinalg/cli/TwoSampleMrClumpTest.java','src/test/resources/org/jlinalg/cli/twosamplemr-clump-0.7.14.zip']\nfor n in names:")
path=B/'run-normal-gradle.py';path.write_text(s,encoding='utf-8',newline='\n');print('Saved fixture',len(pins),'files',target.stat().st_size,'bytes',flush=True)
raise SystemExit(subprocess.run(['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-X','utf8','-B',str(path),'1'],cwd=T).returncode)
