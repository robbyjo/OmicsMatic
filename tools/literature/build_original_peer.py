"""Download and privately build the pinned original dense PEER with its owned observer."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,re,shutil,subprocess,sys,urllib.request,zipfile

ROOT=Path(__file__).resolve().parents[2]
RESOURCE=ROOT/'src/main/resources/org/jlinalg/omics/peer-original'
ACTION=('SKIP3: DEPENDENT ANALYSIS SKIPPED; supply the named permitted source/compiler/transport '
        'or choose a writable NEW output and retry. Downloads/private installation are authorized '
        'where the environment permits; a missing prerequisite is not an installation prohibition. '
        'FAIL2: NO COMPLETE RESULT; retain sources, partial builds and logs, resolve the named '
        'integrity/compiler/build cause and retry NEW output. This setup does not run a scientific analysis.')

def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--archive',type=Path);parser.add_argument('--wsl-distribution');parser.add_argument('--jobs',type=int,default=2);args=parser.parse_args();owned=False
    try:
        if not __debug__ or sys.version_info<(3,11):raise ValueError('Use Python 3.11+ without -O')
        if not 1<=args.jobs<=16:raise ValueError('--jobs must be 1..16')
        if args.wsl_distribution and (os.name!='nt' or not re.fullmatch('[A-Za-z0-9_.-]{1,128}',args.wsl_distribution)):raise ValueError('WSL selection requires Windows and an explicit valid distribution name')
        out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);owned=True
        profile=json.loads((RESOURCE/'SOURCE-PROFILE.json').read_bytes())
        if profile['profile']!='PEER-1.3-40bc4b2-original-fp32' or profile['original_core_modified']:raise ValueError('Different packaged source profile')
        for name,wanted in profile['observer_sources'].items():
            if pin(RESOURCE/name)!=wanted:raise ValueError('Packaged observer/source resource changed: '+name)
        prefix=['wsl.exe','-d',args.wsl_distribution,'--'] if args.wsl_distribution else []
        def mapped(path):
            raw=str(Path(path).resolve()).replace('\\','/')
            if not args.wsl_distribution:return raw
            if not re.fullmatch('[A-Za-z]:/.*',raw):raise ValueError('WSL build output must use a Windows drive-backed path')
            return '/mnt/'+raw[0].lower()+raw[2:]
        for name in ['cmake','g++']+(['python3'] if args.wsl_distribution else []):
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:process=subprocess.run(prefix+[name,'--version'],stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=30)
            if process.returncode!=0:raise FileNotFoundError('Required compiler/transport command cannot execute: '+name)
        archive=out/'source.zip'
        if args.archive:
            if not args.archive.is_file():raise FileNotFoundError('Exact permitted source archive is unavailable: '+str(args.archive))
            shutil.copyfile(args.archive,archive)
        else:
            request=urllib.request.Request(profile['source_archive']['url'],headers={'User-Agent':'OmicsMatic-original-PEER-builder'})
            try:
                with urllib.request.urlopen(request,timeout=120) as response,archive.open('xb') as stream:
                    total=0
                    while True:
                        chunk=response.read(65536)
                        if not chunk:break
                        total+=len(chunk)
                        if total>64*1024*1024:raise ValueError('Original source download exceeds capacity')
                        stream.write(chunk)
            except OSError as error:raise FileNotFoundError('Permitted source download unavailable; supply --archive with the exact pinned archive') from error
        expected={key:profile['source_archive'][key] for key in ['bytes','sha256']}
        if pin(archive)!=expected:raise ValueError('Original source archive SHA-256/length differs; no source substitution')
        source=out/'source';source.mkdir();members={};total=0;prefix_name='peer-'+profile['source_commit']
        with zipfile.ZipFile(archive) as zipped:
            if len(zipped.namelist())!=len(set(zipped.namelist())) or zipped.testzip() is not None:raise ValueError('Duplicate/corrupt source archive')
            for item in zipped.infolist():
                path=PurePosixPath(item.filename)
                if path.is_absolute() or '..' in path.parts or path.parts[0]!=prefix_name:raise ValueError('Unsafe source archive member')
                if item.is_dir():continue
                total+=item.file_size
                if item.file_size>32*1024*1024 or total>128*1024*1024:raise ValueError('Source extraction exceeds capacity')
                name='/'.join(path.parts[1:]);target=source.joinpath(*path.parts[1:]);target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as stream:stream.write(zipped.read(item))
                members[name]=pin(target)
        if members!=profile['source_files']:raise ValueError('Exact upstream source file population/content differs')
        driver=out/'observer';driver.mkdir()
        for name in ['peer_capture.cpp','CMakeLists.txt','peer_owned_worker.py','SOURCE-PROFILE.json','PEER-LICENSE.txt']:shutil.copyfile(RESOURCE/name,driver/name)
        commands=[prefix+['cmake','-S',mapped(driver),'-B',mapped(out/'build'),'-DPEER_SOURCE='+mapped(source),'-DCMAKE_BUILD_TYPE=Release'],prefix+['cmake','--build',mapped(out/'build'),'--parallel',str(args.jobs)]]
        for index,command in enumerate(commands,1):
            with (out/(str(index)+'.stdout')).open('xb') as stdout,(out/(str(index)+'.stderr')).open('xb') as stderr:process=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1800)
            save(out/(str(index)+'.PROCESS.json'),dict(command=command,exit_code=process.returncode))
            if process.returncode!=0:raise ValueError('Original compile/build failed; inspect step '+str(index)+' stdout/stderr')
        for name,wanted in members.items():
            if pin(source/name)!=wanted:raise ValueError('Original source changed during build: '+name)
        executable=out/'build'/('peer_capture.exe' if os.name=='nt' and not args.wsl_distribution else 'peer_capture')
        if not executable.is_file():raise ValueError('Build did not produce the declared observer executable')
        runtime=dict(executable=str(executable),sha256=pin(executable)['sha256'])
        if args.wsl_distribution:runtime['wsl_distribution']=args.wsl_distribution
        save(out/'runtime.json',runtime)
        save(out/'SETUP.json',dict(status='PASS_ORIGINAL_DENSE_PEER_PRIVATE_BUILD',profile=profile['profile'],source_commit=profile['source_commit'],archive=pin(archive),source_files=len(members),original_core_modified=False,observer_sources=profile['observer_sources'],runtime=runtime,system_packages_installed=False,scientific_analysis_executed=False,whole_goal_complete=False))
        (out/'WARNINGS.md').write_text('# Original PEER setup warnings\n\n'+ACTION+'\n\nThe compiler and C library affect runtime bytes and the C rand stream. Keep this build and runtime.json together. The supplied executable hash binds this local build; it is not a source signature. The original core uses FP32. Legacy native Java Peer has separate precision/initialization behavior and is not a fallback.\n',encoding='utf-8',newline='\n')
        print('Built pinned original dense PEER. Runtime binding: '+str(out/'runtime.json'));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if owned:
            save(out/'FAILURE.json',dict(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED' if code==3 else 'FAIL2_NO_COMPLETE_RESULT',error=repr(error),required_action=ACTION,complete_result=False))
            (out/'WARNINGS.md').write_text('# Setup failed or skipped\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
        print(('SKIP3: DEPENDENT ANALYSIS SKIPPED; ' if code==3 else 'FAIL2: NO COMPLETE RESULT; ')+str(error)+'\n'+ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
