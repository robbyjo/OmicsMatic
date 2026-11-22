"""Check actual normal-JAR fdensity tests on three JVMs and all prior payloads."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,shutil,subprocess,zipfile
from build import ROOT,pin,save
def mount(path):
    path=path.resolve();assert path.drive.lower() in {'c:','d:'};return '/mnt/'+path.drive[0].lower()+'/'+path.relative_to(Path(path.drive+'/')).as_posix()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args();build=args.build.resolve();result=json.loads((build/'RESULT.json').read_bytes());assert result['status']=='PASS_NORMAL_JAR_NATIVE_QTLTOOLS_FDENSITY_API';expected=result['tests'];out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);jar=build/'project-build/cli/omicsmatic-0.4.0.jar';assert pin(jar)==result['producer']
    for name,wanted in json.loads((build/'STARTED.json').read_bytes())['source_inputs'].items():assert pin(ROOT/name)==wanted,name
    dependencies=[Path(path) for path in result['test_classpath'].split(';') if path.endswith('.jar')];guardians=list(Path('C:/Users/User/.gradle/caches/modules-2/files-2.1/org.apiguardian/apiguardian-api/1.1.2').glob('*/*.jar'));assert len(guardians)==1
    if guardians[0] not in dependencies:dependencies.append(guardians[0])
    classes=out/'test-classes';classes.mkdir();sources=[ROOT/'src/test/java/org/jlinalg/genetics'/(name+'.java') for name in ['QtltoolsFunctionalDensityTest','GenomicIntervalsTest']]+[Path(__file__).with_name('PackagedQtltoolsFdensityTests.java')]
    command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',';'.join(map(str,[jar,*dependencies])),'-d',str(classes),*map(str,sources)]
    with (out/'compile.stdout').open('xb') as stdout,(out/'compile.stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    save(out/'compile.PROCESS.json',dict(command=command,exit_code=process.returncode));assert process.returncode==0,'Inspect retained strict compiler diagnostics'
    name='org/jlinalg/genetics/qtltools-fdensity/original-controls.tsv.gz';target=classes/name;target.parent.mkdir(parents=True);shutil.copyfile(ROOT/'src/test/resources'/name,target)
    def payloads(path):
        with zipfile.ZipFile(path) as z:
            names=[name for name in z.namelist() if not name.endswith('/')];assert len(names)==len(set(names));return {name:hashlib.sha256(z.read(name)).hexdigest() for name in names}
    previous=ROOT.parent/'mo-7/build/monocle-response-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';old=payloads(previous);current=payloads(jar);changed=[name for name,wanted in old.items() if current.get(name)!=wanted];assert not changed,changed;added=sorted(set(current)-set(old));assert added and all(name.startswith('org/jlinalg/genetics/QtltoolsFunctionalDensity') and name.endswith('.class') for name in added),added
    save(out/'JAR-COMPARISON.json',dict(previous_producer=pin(previous),preceding_payloads=len(old),preceding_payloads_unchanged=len(old),changed_preceding_payloads=changed,new_payloads=added))
    def host(label):
        folder=out/label;folder.mkdir();paths=[jar,classes,*dependencies]
        if label=='linux21':command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','java','-Xmx2g','-cp',':'.join(map(mount,paths)),'org.jlinalg.validation.PackagedQtltoolsFdensityTests',mount(jar),str(expected)]
        else:
            java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if label=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe';command=[java,'-Xmx2g','-cp',';'.join(map(str,paths)),'org.jlinalg.validation.PackagedQtltoolsFdensityTests',str(jar),str(expected)]
        save(folder/'STARTED.json',dict(command=command))
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        value=dict(exit_code=process.returncode,tests=expected);save(folder/'PROCESS.json',value);print(label,process.returncode,flush=True);return label,value
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:hosts=dict(pool.map(host,['windows21','windows25','linux21']))
    assert all(row['exit_code']==0 for row in hosts.values());save(out/'RESULT.json',dict(status='PASS_NORMAL_JAR_NATIVE_FDENSITY_API_THREE_JVMS',producer=pin(jar),tests_each_host=expected,hosts=hosts,preceding_payloads_unchanged=len(old),added_payloads=added,API_only=True,CLI_implemented=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS',expected,'tests per packaged JVM;all',len(old),'preceding payloads exact',flush=True);return 0
if __name__=='__main__':raise SystemExit(main())
