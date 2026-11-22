"""Test the normal QTLtools JAR on three JVMs;never run a stub or scientific upstream model."""
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
        names=[name for name in source.namelist() if not name.endswith('/')];assert len(names)==len(set(names))
        return {name:hashlib.sha256(source.read(name)).hexdigest() for name in names}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--previous-jar',type=Path,required=True);p.add_argument('--wsl',default='JLinAlg-Omics-PyMOL-20261026');args=p.parse_args();build=args.build.resolve();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    result=json.loads((build/'RESULT.json').read_bytes());assert result['exit_code']==0 and result['failures']==0;expected=result['tests'];jar=build/'project-build/cli/omicsmatic-0.4.0.jar';assert pin(jar)==result['producer']
    dependencies=[Path(value) for value in (build/'CLASSPATH.txt').read_text().split(';') if value.endswith('.jar')]
    guardian=list(Path('C:/Users/User/.gradle/caches/modules-2/files-2.1/org.apiguardian/apiguardian-api/1.1.2').glob('*/*.jar'));assert len(guardian)==1
    if guardian[0] not in dependencies:dependencies.append(guardian[0])
    classes=out/'test-classes';classes.mkdir();sources=[ROOT/'src/test/java/org/jlinalg/cli'/(name+'.java') for name in ['QtltoolsWorkflowCliTest','QtltoolsOriginalExportsTest']]+[ROOT/'src/test/java/org/jlinalg/genetics'/(name+'.java') for name in ['QtltoolsFunctionalDensityTest','GenomicIntervalsTest']]+[Path(__file__).with_name('PackagedQtltoolsWorkflowTests.java')]
    compile=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',';'.join(map(str,[jar,*dependencies])),'-d',str(classes),*map(str,sources)]
    save(out/'STARTED.json',dict(command=compile,source_inputs={path.relative_to(ROOT).as_posix():pin(path) for path in [*sources,Path(__file__)]},normal_producer=pin(jar),previous_producer=pin(args.previous_jar)))
    with (out/'compile.stdout').open('xb') as stdout,(out/'compile.stderr').open('xb') as stderr:process=subprocess.run(compile,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    save(out/'compile.PROCESS.json',dict(exit_code=process.returncode));assert process.returncode==0,'Inspect retained strict compilation diagnostics'
    for name in ['org/jlinalg/genetics/qtltools-fdensity','org/jlinalg/cli/qtltools-workflow']:
        target=classes/name;target.mkdir(parents=True);source=ROOT/'src/test/resources'/name
        for path in source.iterdir():shutil.copyfile(path,target/path.name)
    previous=payloads(args.previous_jar);current=payloads(jar);changed=[name for name,wanted in previous.items() if current.get(name)!=wanted];assert changed==['org/jlinalg/cli/JLinAlgCli.class'],changed
    added=sorted(set(current)-set(previous));assert added and all(name.startswith('org/jlinalg/cli/QtltoolsWorkflow') and name.endswith('.class') or name.startswith('org/jlinalg/omics/qtltools-workflow/') for name in added),added
    comparison=dict(previous_producer=pin(args.previous_jar),current_producer=pin(jar),preceding_payloads=len(previous),unchanged_preceding_payloads=len(previous)-len(changed),changed_preceding_payloads=changed,new_payloads=added);save(out/'JAR-COMPARISON.json',comparison)
    def host(label):
        folder=out/label;folder.mkdir();paths=[jar,classes,*dependencies]
        if label=='linux21':command=['wsl.exe','-d',args.wsl,'-u','root','--','java','-Xmx2g','-cp',':'.join(map(mount,paths)),'org.jlinalg.validation.PackagedQtltoolsWorkflowTests',mount(jar),str(expected)]
        else:
            executable='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if label=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe';command=[executable,'-Xmx2g','-cp',';'.join(map(str,paths)),'org.jlinalg.validation.PackagedQtltoolsWorkflowTests',str(jar),str(expected)]
        save(folder/'STARTED.json',dict(command=command))
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
        value=dict(exit_code=process.returncode,tests=expected);save(folder/'PROCESS.json',value);print(label,'exit',process.returncode,flush=True);return label,value
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:hosts=dict(pool.map(host,['windows21','windows25','linux21']))
    assert all(row['exit_code']==0 for row in hosts.values()),'Inspect retained packaged-JVM failures'
    save(out/'RESULT.json',dict(status='PASS_NORMAL_QTLTOOLS_JAR_FOCUSED_TESTS_EACH_OF_THREE_JVMS',producer=pin(jar),tests_each_JVM=expected,hosts=hosts,jar_comparison=comparison,global_native_command_actually_executed_in_tests=True,original_models_refit_by_driver=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS',expected,'tests per packaged JVM;',comparison['unchanged_preceding_payloads'],'old payloads unchanged;only dispatcher changed',flush=True);return 0
if __name__=='__main__':raise SystemExit(main())
