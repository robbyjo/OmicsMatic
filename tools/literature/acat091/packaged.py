"""Run focused tests against the normal ACAT JAR on three JVMs;retain all attempts."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[3]
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def mount(path):
    path=path.resolve();return '/mnt/'+path.drive[0].lower()+'/'+path.as_posix()[3:]
def payloads(path):
    with zipfile.ZipFile(path) as source:
        names=[n for n in source.namelist() if not n.endswith('/')];assert len(names)==len(set(names));return {n:hashlib.sha256(source.read(n)).hexdigest() for n in names}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--previous-jar',required=True,type=Path);p.add_argument('--wsl',default='JLinAlg-Omics-PyMOL-20261026');args=p.parse_args();build=args.build.resolve();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    result=json.loads((build/'RESULT.json').read_bytes());assert result['exit_code']==0 and result['failures']==0;expected=result['tests'];jar=build/'project-build/cli/omicsmatic-0.4.0.jar';assert pin(jar)==result['producer']
    dependencies=[Path(value) for value in (build/'CLASSPATH.txt').read_text().split(';') if value.endswith('.jar')];guardian=list(Path('C:/Users/User/.gradle/caches/modules-2/files-2.1/org.apiguardian/apiguardian-api/1.1.2').glob('*/*.jar'));assert len(guardian)==1
    if guardian[0] not in dependencies:dependencies.append(guardian[0])
    classes=out/'test-classes';classes.mkdir();sources=[ROOT/'src/test/java/org/jlinalg/cli'/(n+'.java') for n in ['Acat091ReferenceTest','AcatToolsCliTest']]+[ROOT/'src/test/java/org/jlinalg/settest'/(n+'.java') for n in ['RLongDoubleSumTest','Acat091Test','AcatTestsTest','AcatLogTest']]+[Path(__file__).with_name('PackagedAcatTests.java')]
    command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',';'.join(map(str,[jar,*dependencies])),'-d',str(classes),*map(str,sources)]
    save(out/'STARTED.json',dict(command=command,source_inputs={path.relative_to(ROOT).as_posix():pin(path) for path in [*sources,Path(__file__)]},normal_producer=pin(jar),previous_producer=pin(args.previous_jar)))
    with (out/'compile.stdout').open('xb') as stdout,(out/'compile.stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    save(out/'compile.PROCESS.json',dict(exit_code=process.returncode));assert process.returncode==0,'Inspect retained strict test/driver compilation'
    for name in ['org/jlinalg/settest/acat091','acat']:
        source=ROOT/'src/test/resources'/name;target=classes/name;target.mkdir(parents=True)
        for path in source.iterdir():
            if path.is_file():shutil.copyfile(path,target/path.name)
    previous=payloads(args.previous_jar);current=payloads(jar);changed=[n for n,w in previous.items() if current.get(n)!=w];assert changed==['org/jlinalg/cli/JLinAlgCli.class'],changed;added=sorted(set(current)-set(previous))
    assert added and all(n.startswith('org/jlinalg/settest/Acat091') or n=='org/jlinalg/settest/RLongDoubleSum.class' or n=='org/jlinalg/cli/AcatToolsCli.class' or n.startswith('org/jlinalg/omics/acat091/') for n in added),added
    comparison=dict(previous_producer=pin(args.previous_jar),current_producer=pin(jar),preceding_payloads=len(previous),unchanged_preceding_payloads=len(previous)-1,changed_preceding_payloads=changed,new_payloads=added);save(out/'JAR-COMPARISON.json',comparison)
    def host(label):
        folder=out/label;folder.mkdir();paths=[jar,classes,*dependencies]
        if label=='linux21':command=['wsl.exe','-d',args.wsl,'-u','root','--','java','-Xmx2g','-cp',':'.join(map(mount,paths)),'org.jlinalg.validation.PackagedAcatTests',mount(jar),str(expected)]
        else:
            exe='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if label=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe';command=[exe,'-Xmx2g','-cp',';'.join(map(str,paths)),'org.jlinalg.validation.PackagedAcatTests',str(jar),str(expected)]
        save(folder/'STARTED.json',dict(command=command))
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
        value=dict(exit_code=process.returncode,tests=expected);save(folder/'PROCESS.json',value);print(label,process.returncode,flush=True);return label,value
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:hosts=dict(pool.map(host,['windows21','windows25','linux21']))
    assert all(v['exit_code']==0 for v in hosts.values()),'Inspect retained packaged-JVM failure evidence'
    save(out/'RESULT.json',dict(status='PASS_NORMAL_ACAT091_JAR_FOCUSED_TESTS_EACH_OF_THREE_JVMS',producer=pin(jar),tests_each_JVM=expected,hosts=hosts,jar_comparison=comparison,global_native_command_actually_executed=True,original_R_refit_by_driver=False,new_catalog_credit=0,whole_goal_complete=False))
    print('PASS',expected,'tests per packaged JVM;',comparison['unchanged_preceding_payloads'],'preceding payloads exact;legacy methods unchanged');return 0
if __name__=='__main__':raise SystemExit(main())
