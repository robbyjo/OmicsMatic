from pathlib import Path,PurePosixPath
import hashlib,json,os,subprocess,zipfile
R=Path('D:/projects/JLinAlg');I=R/'build/twosamplemr-historical-intake-20261121-1';O=R/'build/twosamplemr-historical-runtime-20261121-1';V=O/'versions';assert (O/'COMPLETED.txt').exists() and not V.exists();V.mkdir();results={}
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
for version in ['0.4.9','0.5.6','0.5.7','0.6.8']:
    row=V/version;row.mkdir();lib=row/'library';lib.mkdir();source=row/'upstream';source.mkdir();meta=json.loads((I/version/'RESULT.json').read_bytes());assert pin(I/version/'source.zip')=={k:meta['archive'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(I/version/'source.zip') as archive:
        prefix=archive.namelist()[0].split('/')[0]+'/'
        for entry in archive.infolist():
            name=entry.filename[len(prefix):];p=PurePosixPath(name);assert entry.filename.startswith(prefix) and not p.is_absolute() and '..' not in p.parts and '\\' not in name
            target=source.joinpath(*p.parts)
            if entry.is_dir():target.mkdir(parents=True,exist_ok=True)
            else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.read(entry))
    command=['C:/Program Files/R/R-4.6.1/bin/R.exe','CMD','INSTALL','--no-multiarch','--library='+str(lib),str(source)]
    environment=dict(os.environ,LANG='C',LC_ALL='C',R_LIBS=os.pathsep.join([str(O/'library'),str(R/'build/twosamplemr-runtime-intake-20261121-1/library')]),R_LIBS_USER=str(lib),R_PROFILE_USER=str(row/'no-user-profile'),R_ENVIRON_USER=str(row/'no-user-environ'))
    (row/'COMMAND.json').write_text(json.dumps(dict(command=command,environment={k:environment[k] for k in ['LANG','LC_ALL','R_LIBS','R_LIBS_USER','R_PROFILE_USER','R_ENVIRON_USER']},source=meta,source_modified=False),indent=2)+'\n',encoding='utf-8',newline='\n')
    with (row/'stdout').open('xb') as stdout,(row/'stderr').open('xb') as stderr:
        p=subprocess.Popen(command,cwd=row,env=environment,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW);(row/'PROCESS.json').write_text(json.dumps(dict(pid=p.pid))+'\n');print('Started original',version,p.pid,flush=True);code=p.wait()
    results[version]=dict(exit_code=code,version=version,commit=meta['commit'],installed=(lib/'TwoSampleMR/DESCRIPTION').exists(),source_modified=False)
    (row/'TERMINAL.json').write_text(json.dumps(results[version],indent=2)+'\n',encoding='utf-8',newline='\n');print(version,results[version],flush=True)
(V/'RESULT.json').write_text(json.dumps(dict(versions=results,scientific_execution_qualified=False,new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
