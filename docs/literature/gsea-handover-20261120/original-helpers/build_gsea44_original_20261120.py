from pathlib import Path
import hashlib,json,os,subprocess,traceback,zipfile
R=Path('D:/projects/JLinAlg');G=R/'build/gs-1';B=G/'build/gsea-high-impact-1';P=B/'source-intake-1/4.4.0'
O=B/'original-build-4.4.0-1';O.mkdir(exist_ok=False)
S=P/'source';OUT=O/'project-build';OUT.mkdir()
def enc(v):return (json.dumps(v,indent=2)+'\n').encode()
def save(n,v):(O/n).write_bytes(enc(v))
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
try:
    source=json.loads((P/'COMPLETED.json').read_bytes());assert source['commit']=='2cfcbd9856cd8e47b39994063ff717708631cfcf'
    for n,w in source['files'].items():assert pin(S/n)==w,n
    save('SOURCE-BEFORE.json',source['files'])
    init=O/'isolated-build.gradle';init.write_text("gradle.beforeProject { p -> p.layout.buildDirectory.set(new File('"+OUT.as_posix()+"')) }\n",encoding='utf-8',newline='\n')
    gradle=next(Path('C:/Users/User/.gradle/wrapper/dists').glob('gradle-9.7.1-bin/*/gradle-9.7.1/bin/gradle.bat'))
    cmd=[str(gradle),'--no-daemon','--offline','--console=plain','--max-workers=2','--gradle-user-home',str(O/'gradle-home'),
        '--project-cache-dir',str(O/'project-cache'),'--init-script',str(init),'-Pversion=4.4.0','-PversionMajor=4','-PversionMinor=4','-PversionPatch=0',
        '-PbuildNumber=0','-Ptimestamp=unmodified-source-tag-2cfcbd9856cd-local-JDK21','jar']
    env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot')
    save('COMMAND.json',dict(command=cmd,cwd=str(S),JAVA_HOME=env['JAVA_HOME'],source_tag='v4.4.0',source_commit=source['commit'],
        original_source_modified=False,build_properties_explicit=True,production_vendor_binary=False,offline=True))
    with (O/'gradle.stdout').open('xb') as out,(O/'gradle.stderr').open('xb') as err:
        p=subprocess.Popen(cmd,cwd=S,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save('PROCESS.json',dict(pid=p.pid,command=cmd));code=p.wait()
    save('PROCESS-EXIT.json',dict(exit_code=code))
    print((O/'gradle.stdout').read_text(errors='replace')[-3500:]);print((O/'gradle.stderr').read_text(errors='replace')[-2500:],flush=True)
    for n,w in source['files'].items():assert pin(S/n)==w,n
    save('SOURCE-AFTER.json',source['files'])
    assert code==0,'Original-source build failed; retain this attempt and named build prerequisite diagnostics.'
    jar=OUT/'libs/gsea-minimal-4.4.0.jar';assert jar.is_file()
    with zipfile.ZipFile(jar) as z:members={n:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for n in z.namelist() for raw in [z.read(n)]}
    save('PRODUCER.json',dict(jar=str(jar),jar_pin=pin(jar),members=members,source_commit=source['commit'],source_tag='v4.4.0',vendor_distribution=False))
    save('COMPLETED.json',dict(status='PASS_UNMODIFIED_ORIGINAL_GSEA_4_4_0_SOURCE_JAR_BUILD',producer=pin(jar),source_files_unchanged=len(source['files']),source_commit=source['commit'],
        host='Windows JDK21 / Gradle9.7.1',original_Gradle_build_script_unchanged=True,source_edition='4.4.0',production_vendor_binary=False))
    print('PASS unmodified original GSEA4.4.0 source build',jar,flush=True)
except Exception as error:
    save('FAILURE.json',dict(error=repr(error),detail=traceback.format_exc()));raise
