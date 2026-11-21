from pathlib import Path
import hashlib,json,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/mr-7';B=T/'build/twosamplemr-historical-clump-1';O=B/'fixture-1';O.mkdir();pilot=B/'pilot-1';result=json.loads((pilot/'RESULT.json').read_bytes());assert result['all_pass'];target=T/'src/test/resources/org/jlinalg/cli/twosamplemr-historical-clump-0.6.8.zip';pins={};cases=[]
with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for name,row in sorted(result['results'].items()):
        if row['exit_code']!=0:continue
        cases.append(name)
        for p in sorted((pilot/name/'analysis').rglob('*')):
            if not p.is_file():continue
            member=name+'/'+p.relative_to(pilot/name/'analysis').as_posix();raw=p.read_bytes();pins[member]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());info=zipfile.ZipInfo(member,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
assert len(cases)==21 and len(pins)<3000
(O/'INVENTORY.json').write_text(json.dumps(pins,indent=2)+'\n',encoding='utf-8',newline='\n');(O/'PROFILE.json').write_text(json.dumps(dict(scope='21 genuine original0.6.8 quick-compiled Java/R/PLINK captures; normal-JAR qualification separate',cases=cases,files=len(pins),archive_bytes=target.stat().st_size,archive_sha256=hashlib.sha256(target.read_bytes()).hexdigest(),new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
test=(T/'src/test/java/org/jlinalg/cli/TwoSampleMrClumpTest.java').read_text(encoding='utf-8').replace('TwoSampleMrClump','TwoSampleMrHistoricalClump').replace('twosamplemr-clump-0.7.14.zip','twosamplemr-historical-clump-0.6.8.zip')
at=test.index('    private static int run(')
extra='''    @Test void all149TaggedFunctionsAreRequired()throws IOException {
        Path root=fixture("basic");TwoSampleMrHistoricalClumpCli.verify(root);var report=read(root.resolve("worker/report.json"));assertEquals(149,((Number)report.get("TwoSampleMR_source_functions_guarded")).intValue());report.put("TwoSampleMR_source_functions_guarded",152);json(root.resolve("worker/report.json"),report);TwoSampleMrHistoricalClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrHistoricalClumpCli.verify(root));
    }
    @Test void modernEditionCannotBeRelabeledAsHistorical()throws IOException {
        Path root=fixture("basic");var profile=read(root.resolve("worker/runtime-profile.json"));assertEquals("0.6.8",profile.get("TwoSampleMR"));profile.put("TwoSampleMR","0.7.14");json(root.resolve("worker/runtime-profile.json"),profile);TwoSampleMrHistoricalClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrHistoricalClumpCli.verify(root));
    }
    @Test void ineffectiveSourceP2IsNotOfferedAsAnIndependentThreshold()throws IOException {
        Path root=fixture("basic");var request=read(root.resolve("request.json"));request.put("clump_p2",0.05);assertThrows(IOException.class,()->TwoSampleMrHistoricalClumpContracts.validate(request));
    }
'''
test=test[:at]+extra+test[at:];(T/'src/test/java/org/jlinalg/cli/TwoSampleMrHistoricalClumpTest.java').write_text(test,encoding='utf-8',newline='\n')
s=(R/'build/mr-5/build/twosamplemr-historical-1/run-normal-gradle.py').read_text(encoding='utf-8').replace("T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1'","T=R/'build/mr-7';B=T/'build/twosamplemr-historical-clump-1'")
s=s.replace("'--tests','org.jlinalg.cli.TwoSampleMrHistoricalTest']","'--tests','org.jlinalg.cli.TwoSampleMrHistoricalTest','--tests','org.jlinalg.cli.TwoSampleMrHistoricalClumpTest']")
s=s.replace('for n in names:',"names+=[p.relative_to(T).as_posix() for p in (T/'src/main/resources/org/jlinalg/omics/twosamplemr-historical-clump').iterdir() if p.is_file()]\nnames+=['src/test/java/org/jlinalg/cli/TwoSampleMrHistoricalClumpTest.java','src/test/resources/org/jlinalg/cli/twosamplemr-historical-clump-0.6.8.zip']\nfor n in names:")
helper=B/'run-normal-gradle.py';helper.write_text(s,encoding='utf-8',newline='\n');print('Fixture21 genuine captures',len(pins),'files; starting normal812-test build',flush=True)
raise SystemExit(subprocess.run(['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-X','utf8','-B',str(helper),'1'],cwd=T).returncode)
