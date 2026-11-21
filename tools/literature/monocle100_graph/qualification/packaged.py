"""Run the actual normal executable on three JVMs and compare all preceding JAR payloads."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,shutil,subprocess,zipfile
from build import ROOT,pin,save
def mount(path):
    path=path.resolve();assert path.drive.lower() in {'c:','d:'};return '/mnt/'+path.drive[0].lower()+'/'+path.relative_to(Path(path.drive+'/')).as_posix()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    build=args.build.resolve();result=json.loads((build/'RESULT.json').read_bytes());assert result['status']=='PASS_NORMAL_JAR_MONOCLE100_GRAPH_CONTROLS';expected=result['tests'];assert expected>=700
    out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);jar=build/'project-build/cli/omicsmatic-0.4.0.jar';assert pin(jar)==result['producer']
    for name,wanted in json.loads((build/'STARTED.json').read_bytes())['source_inputs'].items():assert pin(ROOT/name)==wanted,name
    dependencies=[Path(path) for path in result['test_classpath'].split(';') if path.endswith('.jar')]
    guardians=list(Path('C:/Users/User/.gradle/caches/modules-2/files-2.1/org.apiguardian/apiguardian-api/1.1.2').glob('*/*.jar'));assert len(guardians)==1
    if guardians[0] not in dependencies:dependencies.append(guardians[0])
    classes=out/'test-classes';classes.mkdir();tests=['Monocle100GraphContractsTest','Monocle100GraphCaptureTest','Monocle100NeighborsContractsTest','Monocle100NeighborsCaptureTest','Monocle100WeightsTest','Monocle100WeightsCliTest','RegressionMoranTest','RegressionMoranCliTest','MonocleRootCliTest','JLinAlgCliTest','Monocle3PseudotimeTest']
    sources=[ROOT/'src/test/java/org/jlinalg/cli'/(name+'.java') for name in tests]+[Path(__file__).with_name('PackagedMonocle100GraphTests.java')]
    command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',';'.join(map(str,[jar,*dependencies])),'-d',str(classes),*map(str,sources)]
    with (out/'compile.stdout').open('xb') as stdout,(out/'compile.stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    save(out/'compile.PROCESS.json',dict(command=command,exit_code=process.returncode));assert process.returncode==0,'Inspect retained strict compilation diagnostics'
    for name in ['org/jlinalg/cli/monocle100-graph.zip','org/jlinalg/cli/monocle100-neighbors.zip','org/jlinalg/singlecell/monocle100/weights.json','org/jlinalg/singlecell/monocle3-pseudotime/original-controls-v1.json.gz','org/jlinalg/spatial/regression-moran/original-spdep-1.4-1.json.gz']:
        target=classes/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'src/test/resources'/name,target)
    def payloads(path):
        with zipfile.ZipFile(path) as archive:
            names=[name for name in archive.namelist() if not name.endswith('/')];assert len(names)==len(set(names));return {name:hashlib.sha256(archive.read(name)).hexdigest() for name in names}
    previous=ROOT.parent/'mo-3/build/monocle100-neighbors-qualification-1/normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar';old=payloads(previous);current=payloads(jar);changed=[name for name,wanted in old.items() if current.get(name)!=wanted];assert set(changed)=={'org/jlinalg/cli/Monocle100NeighborsRuntime.class','org/jlinalg/cli/Monocle100NeighborsRuntime$Unavailable.class'},changed
    added=sorted(set(current)-set(old));assert added and all(name.startswith(('org/jlinalg/cli/Monocle100Graph','org/jlinalg/omics/monocle100-graph/')) for name in added),added
    save(out/'JAR-COMPARISON.json',dict(preceding_payloads=len(old),preceding_payloads_unchanged=len(old)-len(changed),changed_preceding_payloads=changed,new_payloads=added))
    print('PASS',len(old)-len(changed),'preceding JAR payloads exact;',len(changed),'explicit shared runtime class changes;',len(added),'new class/resources',flush=True)
    def host(name):
        folder=out/name;folder.mkdir();paths=[jar,classes,*dependencies]
        if name=='linux21':command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','java','-Xmx2g','-cp',':'.join(map(mount,paths)),'org.jlinalg.validation.PackagedMonocle100GraphTests',mount(jar),str(expected)]
        else:
            java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if name=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe';command=[java,'-Xmx2g','-cp',';'.join(map(str,paths)),'org.jlinalg.validation.PackagedMonocle100GraphTests',str(jar),str(expected)]
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=360)
        record=dict(command=command,exit_code=process.returncode,tests=expected);save(folder/'PROCESS.json',record);print(name,process.returncode,flush=True);return name,record
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:hosts=dict(pool.map(host,['windows21','windows25','linux21']))
    save(out/'RESULT.json',dict(status='PASS_PACKAGED_GRAPH_AND_NEIGHBOR_CONTROLS_THREE_JVMS' if all(row['exit_code']==0 for row in hosts.values()) else 'FAILED_PACKAGED_CONTROLS',JAR=pin(jar),tests_each_host=expected,hosts=hosts,new_catalog_credit=0,whole_goal_complete=False));assert all(row['exit_code']==0 for row in hosts.values());return 0
if __name__=='__main__':raise SystemExit(main())
