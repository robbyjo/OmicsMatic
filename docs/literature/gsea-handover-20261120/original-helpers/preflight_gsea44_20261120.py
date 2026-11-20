from pathlib import Path
import hashlib,json,os,shutil,subprocess,traceback
R=Path('D:/projects/JLinAlg');G=R/'build/gs-1';B=G/'build/gsea-high-impact-1';O=B/'preflight-4.4.0-1';O.mkdir(exist_ok=False)
S=B/'source-intake-1/4.4.0/source';F=B/'runtime-source-4.4.0-1';F.mkdir();M=F/'modules';M.mkdir()
JAVA='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
def enc(v):return (json.dumps(v,indent=2)+'\n').encode()
def save(n,v):(O/n).write_bytes(enc(v))
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
try:
    for p in (S/'modules').glob('*.jar'):shutil.copyfile(p,M/p.name)
    jar=B/'original-build-4.4.0-1/project-build/libs/gsea-minimal-4.4.0.jar';shutil.copyfile(jar,M/jar.name)
    for n in ['LICENSE.txt','LICENSE-3RD-PARTY.txt']:shutil.copyfile(S/n,F/n)
    shutil.copyfile(S/'scripts/logging.properties',F/'logging.properties')
    record=dict(schema_version=1,kind='original-GSEA-unmodified-source-build',source_tag='v4.4.0',source_commit='2cfcbd9856cd8e47b39994063ff717708631cfcf',
        program_version_build_property='4.4.0',vendor_binary=False,compiler='JDK21',build_tool='Gradle9.7.1',
        files={p.relative_to(F).as_posix():pin(p) for p in F.rglob('*') if p.is_file()},qualified_statistical_execution=False)
    (F/'PROFILE.json').write_bytes(enc(record))
    results=[]
    for operation in ['GSEA','GSEAPreranked']:
        folder=O/operation;folder.mkdir();home=folder/'home';home.mkdir()
        command=[JAVA,'-Xmx1g','-Djava.awt.headless=true','-Duser.home='+str(home),'-Djava.util.logging.config.file='+str(F/'logging.properties'),
            '--module-path',str(M),'--module','org.gsea_msigdb.gsea/xapps.gsea.CLI',operation,'-help']
        p=subprocess.run(command,cwd=folder,stdin=subprocess.DEVNULL,capture_output=True,timeout=90)
        (folder/'stdout').write_bytes(p.stdout);(folder/'stderr').write_bytes(p.stderr)
        (folder/'PROCESS.json').write_bytes(enc(dict(command=command,exit_code=p.returncode)))
        results.append(dict(operation=operation,exit_code=p.returncode,stdout_bytes=len(p.stdout),stderr_bytes=len(p.stderr)))
        print(operation,'help status',p.returncode,'output',len(p.stdout),len(p.stderr),flush=True)
    for n,w in record['files'].items():assert pin(F/n)==w,n
    save('COMPLETED.json',dict(status='OBSERVED_ORIGINAL_GSEA44_MODULE_CLI_HELP_BOTH_OPERATIONS',runtime=str(F),results=results,
        statistical_analysis_run=False,source_or_original_runtime_files_modified=False))
except Exception as error:
    save('FAILURE.json',dict(error=repr(error),detail=traceback.format_exc()));raise
