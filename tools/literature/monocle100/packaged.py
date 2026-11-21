"""Check the normal producer on Windows21/25 and Linux21, without original R execution."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,shutil,subprocess,zipfile
from setup import ROOT,linux,pin,require,save

def mounted(path):
    path=path.resolve();require(path.drive.lower() in {'c:','d:'},'Use an exposed C: or D: dependency path')
    return '/mnt/'+path.drive[0].lower()+'/'+path.relative_to(Path(path.drive+'/')).as_posix()

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    build=args.build.resolve();result=json.loads((build/'RESULT.json').read_bytes());require(result['status']=='PASS_NORMAL_JAR_MONOCLE100_SOURCE_CONTROLS' and result['tests']==636,'Complete normal636 controls required')
    out=args.out.resolve();require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW build output');out.mkdir(parents=True,exist_ok=False)
    jar=build/'project-build/cli/omicsmatic-0.4.0.jar';require(pin(jar)==result['producer'],'Changed normal producer')
    for name,wanted in json.loads((build/'STARTED.json').read_bytes())['source_inputs'].items():require(pin(ROOT/name)==wanted,'Changed qualified input: '+name)
    dependencies=[Path(name) for name in result['test_classpath'].split(';') if name.endswith('.jar')]
    guardians=list(Path('C:/Users/User/.gradle/caches/modules-2/files-2.1/org.apiguardian/apiguardian-api/1.1.2').glob('*/*.jar'));require(len(guardians)==1,'Missing exact compile annotation dependency')
    if guardians[0] not in dependencies:dependencies.append(guardians[0])
    classes=out/'test-classes';classes.mkdir()
    tests=['Monocle100WeightsTest','Monocle100WeightsCliTest','RegressionMoranTest','RegressionMoranCliTest','MonocleRootCliTest','JLinAlgCliTest','Monocle3PseudotimeTest']
    sources=[ROOT/'src/test/java/org/jlinalg/cli'/(name+'.java') for name in tests]+[Path(__file__).with_name('PackagedMonocle100Tests.java')]
    command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',';'.join(map(str,[jar,*dependencies])),'-d',str(classes),*map(str,sources)]
    with (out/'compile.stdout').open('xb') as stdout,(out/'compile.stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    save(out/'compile.PROCESS.json',dict(command=command,exit_code=process.returncode));require(process.returncode==0,'Inspect retained strict compilation diagnostics')
    for name in ['org/jlinalg/singlecell/monocle100/weights.json','org/jlinalg/singlecell/monocle3-pseudotime/original-controls-v1.json.gz','org/jlinalg/spatial/regression-moran/original-spdep-1.4-1.json.gz']:
        target=classes/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'src/test/resources'/name,target)
    def payloads(path):
        with zipfile.ZipFile(path) as archive:
            names=[name for name in archive.namelist() if not name.endswith('/')];require(len(names)==len(set(names)),'Duplicate JAR payload');return {name:hashlib.sha256(archive.read(name)).hexdigest() for name in names}
    previous=ROOT.parent/'mr-8/build/twosamplemr-global-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar'
    old=payloads(previous);current=payloads(jar);changed=[name for name,wanted in old.items() if current.get(name)!=wanted]
    require(not changed,'Preceding JAR payload changed: '+repr(changed))
    added=sorted(set(current)-set(old));require(len(added)==5 and all(name.startswith(('org/jlinalg/singlecell/Monocle100Weights','org/jlinalg/cli/Monocle100WeightsCli')) for name in added),'Unexpected added JAR payload')
    save(out/'JAR-COMPARISON.json',dict(preceding_payloads=len(old),preceding_payloads_unchanged=len(old),changed_preceding_payloads=changed,new_payloads=added,all_preceding_scientific_classes_resources_unchanged=True))
    print('PASS all',len(old),'preceding JAR payloads exact; five new classes',flush=True)
    def host(name):
        folder=out/name;folder.mkdir();paths=[jar,classes,*dependencies]
        if name=='linux21':command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','java','-Xmx2g','-cp',':'.join(map(mounted,paths)),'org.jlinalg.validation.PackagedMonocle100Tests',mounted(jar),'636']
        else:
            java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if name=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe'
            command=[java,'-Xmx2g','-cp',';'.join(map(str,paths)),'org.jlinalg.validation.PackagedMonocle100Tests',str(jar),'636']
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
        row=dict(command=command,exit_code=process.returncode,tests=636);save(folder/'PROCESS.json',row);print(name,process.returncode,flush=True);return name,row
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:hosts=dict(pool.map(host,['windows21','windows25','linux21']))
    save(out/'RESULT.json',dict(status='PASS_PACKAGED_MONOCLE100_636_TESTS_THREE_JVMS' if all(row['exit_code']==0 for row in hosts.values()) else 'FAILED_PACKAGED_CONTROLS',hosts=hosts,JAR=pin(jar),new_catalog_credit=0,whole_goal_complete=False))
    require(all(row['exit_code']==0 for row in hosts.values()),'Inspect retained host failure transcripts');return 0

if __name__=='__main__':raise SystemExit(main())
