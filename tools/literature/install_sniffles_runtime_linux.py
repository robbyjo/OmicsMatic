# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Provision a NEW exact private runtime using bundled sources/binary and pinned wheels.

Invoke with /usr/bin/python3.12 -I -B -S and a NEW cache prefix. Requires
--allow-download --allow-install. Never installs system packages or admits SVs.
"""
import argparse,hashlib,io,json,os,signal,stat,subprocess,sys,tarfile,time,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath

ROOT=Path(__file__).resolve().parents[2]
ACTION=('Dependent Sniffles calling is SKIPPED until an exact permitted runtime is installed and separately bound. '
    'Read the named error and retained logs. Where permitted, correct the host/dependency/network/permission problem '
    'and retry with --allow-download --allow-install and a NEW output. If installation is forbidden, retain the skip '
    'and do not run dependent calling. Do not replace global Python/libraries or refresh known hashes to accept '
    'an untested host. This installer supports only the packaged tested host profile; another host requires a separately validated profile.')


def fingerprint(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):
    with path.open('rb') as stream:
        before=os.fstat(stream.fileno());digest=hashlib.file_digest(stream,'sha256').hexdigest();after=os.fstat(stream.fileno())
    assert (before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns,before.st_ctime_ns)==(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns,after.st_ctime_ns)
    return dict(bytes=after.st_size,sha256=digest)
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def safe(name):
    path=PurePosixPath(name);assert name and not path.is_absolute() and '..' not in path.parts and '\\' not in name and ':' not in name
    return path


def inventory(root,ignore_bytecode=False,links=False):
    result={};pending=[root];nodes=0
    while pending:
        parent=pending.pop()
        with os.scandir(parent) as stream:
            for item in stream:
                nodes+=1;assert nodes<=20000,'Runtime inventory node capacity exceeded'
                path=Path(item.path);name=path.relative_to(root).as_posix();info=item.stat(follow_symlinks=False)
                if ignore_bytecode and '__pycache__' in path.parts:continue
                if stat.S_ISDIR(info.st_mode):pending.append(path)
                elif stat.S_ISREG(info.st_mode):result[name]=pin(path)
                elif stat.S_ISLNK(info.st_mode) and links:
                    target=path.resolve(strict=True);assert target.is_file(),'Directory links are outside the standard-library profile'
                    result[name]=dict(link=os.readlink(path),resolved=str(target),**pin(target))
                else:raise RuntimeError('Linked or nonregular runtime member: '+str(path))
    return result


def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            raw=(path/'stat').read_text();fields=raw[raw.rindex(')')+2:].split()
            if int(fields[2])==group:result.append(dict(pid=int(path.name),state=fields[0],start_ticks=int(fields[19])))
        except (FileNotFoundError,ProcessLookupError):pass
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--allow-download',action='store_true');parser.add_argument('--allow-install',action='store_true');args=parser.parse_args()
    out=args.out
    try:
        assert out.is_absolute() and out==out.resolve() and not out.exists() and out.parent.is_dir() and out.parent==out.parent.resolve(), 'Require a NEW canonical absolute output with an existing canonical parent'
        assert out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime'), 'Installer output must remain in this checkout\'s build/literature-review/sniffles-runtime directory'
    except Exception as failure:
        print('FAIL2_SNIFFLES_INSTALLATION_REQUEST: '+repr(failure)+'\nACTION: preserve existing paths; create the named parent inside the checkout if needed, then supply a NEW canonical absolute output. '+ACTION,file=sys.stderr)
        return 2
    out.mkdir();state=dict(status='SKIP3_SNIFFLES_INSTALLATION_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,
        required_action=ACTION,processes=[],downloads=[],source_namespace_patched=False,system_packages_installed=False)
    shared=None
    try:
        assert args.allow_download and args.allow_install,'Download/install permission absent; no download, runtime copy or child process performed'
        assert sys.platform=='linux' and os.uname().machine=='x86_64','This exact runtime profile requires Linux x86_64'
        assert sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode,'Invoke /usr/bin/python3.12 -I -B -S -X pycache_prefix=NEW_NONEXISTENT_PATH'
        assert sys.pycache_prefix and not Path(sys.pycache_prefix).exists(),'A fresh nonexistent interpreter cache prefix is required'
        profile_path=ROOT/'src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json';profile=json.loads(profile_path.read_bytes())
        distribution=ROOT/'docs/literature/sniffles-runtime-distribution';manifest=json.loads((distribution/'DISTRIBUTION.json').read_bytes())
        assert manifest['status']=='PREPARED_EXACT_UPSTREAM_RUNTIME_DISTRIBUTION_ONLY' and pin(profile_path)==manifest['profile']
        source_paths=[Path(__file__).resolve(),profile_path,distribution/'DISTRIBUTION.json',distribution/'assets.zip',
            ROOT/'tools/literature/sniffles_native_evidence.py',ROOT/'tools/literature/setup_deseq2_reference.py',
            ROOT/'docs/literature/sniffles-vcf-development/VALIDATION.json',ROOT/'docs/literature/sniffles-vcf-development/controls.zip',
            ROOT/'docs/literature/sniffles-vcf-development/sniffles-vcf-development.jar',ROOT/'docs/literature/sniffles-vcf-development/sniffles-vcf-javac25-development.jar']
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in source_paths};save(out/'preparation-inputs-before.json',source_pins)
        assert tuple(sys.version_info[:3])==tuple(profile['python_version']) and Path(sys.executable).resolve()==Path(profile['python_executable'])
        assert pin(Path(profile['python_executable']))==profile['python'],'Base Python binary differs from the tested profile'
        assert not Path(profile['stdlib_zip']).exists(),'Unexpected standard-library ZIP could shadow tested sources'
        shared=inventory(Path(profile['stdlib_root']),True,True);assert shared==profile['stdlib_members'],'Standard-library source population differs from the tested profile'
        save(out/'shared-before.json',shared)
        # Only published repository evidence is used. No ignored reference-setup,
        # intake, Hail bootstrap, preinstalled private package or network source checkout.
        sys.path.insert(0,str(ROOT/'tools/literature'))
        from sniffles_native_evidence import verify
        _,frozen=verify(True)
        runtime=out/'runtime';runtime.mkdir();sources=runtime/'sources';sources.mkdir()
        for tag,row in profile['editions'].items():
            source=sources/tag;source.mkdir();archive_info=manifest['source_archives'][tag];raw=frozen[archive_info['path']]
            assert fingerprint(raw)=={k:archive_info[k] for k in ['bytes','sha256']}
            seen=set();casefold=set();total=0
            with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as archive:
                for member in archive:
                    path=safe(member.name);assert path.parts[0]=='Sniffles-'+row['commit']
                    if member.isdir():continue
                    assert member.isfile() and len(path.parts)>1,'Nonregular original archive member'
                    name=PurePosixPath(*path.parts[1:]).as_posix();assert name in row['members'] and name not in seen and name.casefold() not in casefold
                    seen.add(name);casefold.add(name.casefold());wanted=row['members'][name];assert member.size==wanted['bytes']<=134217728
                    data=archive.extractfile(member).read(wanted['bytes']+1);assert fingerprint(data)==wanted
                    total+=len(data);assert total<=134217728
                    target=source/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
            assert seen==set(row['members']) and inventory(source)==row['members']
        assets=distribution/'assets.zip';assert pin(assets)==manifest['assets']
        with zipfile.ZipFile(assets) as archive:
            assert len(archive.namelist())==len(set(archive.namelist())) and set(archive.namelist())==set(manifest['asset_members'])
            for name,wanted in manifest['asset_members'].items():
                safe(name);data=archive.read(name);assert fingerprint(data)==wanted
                target=runtime/'legacy-sniffles' if name=='legacy-sniffles' else out/name
                target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        native=runtime/'legacy-sniffles';assert pin(native)==profile['legacy_binary'];native.chmod(0o755)
        python=runtime/'python';python.write_bytes(Path(profile['python_executable']).read_bytes());python.chmod(0o755);assert pin(python)==profile['python']
        archives=out/'archives';archives.mkdir();temporary=out/'private-temp';temporary.mkdir()
        def download(name,row):
            safe(name);assert row['url'].startswith('https://files.pythonhosted.org/packages/') and row['bytes']<=33554432
            target=archives/name;request=urllib.request.Request(row['url'],headers={'User-Agent':'OmicsMatic-exact-runtime-installer'})
            with urllib.request.urlopen(request,timeout=60) as response,target.open('xb') as stream:
                assert response.geturl().startswith('https://files.pythonhosted.org/packages/')
                remaining=row['bytes']
                while remaining:
                    chunk=response.read(min(1048576,remaining));assert chunk,'Truncated official wheel download'
                    stream.write(chunk);remaining-=len(chunk)
                assert not response.read(1),'Official wheel exceeds its declared complete length'
            wanted={k:row[k] for k in ['bytes','sha256']};assert pin(target)==wanted,'Official wheel digest differs from the pinned release'
            state['downloads'].append(dict(name=name,url=row['url'],**wanted));print('Captured pinned official wheel '+name,flush=True)
        bootstrap=manifest['pip'];download(bootstrap['filename'],bootstrap)
        for name,row in manifest['wheels'].items():download(name,row)
        # Pip is run from its exact official wheel; target installation creates
        # genuine INSTALLER/REQUESTED/RECORD files, never forged pip metadata.
        bridge=out/'pip-bootstrap.py'
        bridge.write_text('import runpy,sys\nwheel=sys.argv.pop(1)\nsys.path.insert(0,wheel)\nrunpy.run_module("pip",run_name="__main__")\n',encoding='utf8',newline='\n')
        site=runtime/'site'
        command=[str(python),'-I','-B','-S','-X','pycache_prefix='+str(out/'never-created-pip-cache'),str(bridge),str(archives/bootstrap['filename']),
            '--isolated','install','--no-index','--find-links',str(archives),'--no-deps','--no-compile','--target',str(site),'--report',str(out/'pip-report.json'),
            *[row['package']+'=='+row['version'] for row in manifest['wheels'].values()]]
        env=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',HOME=str(out),TMPDIR=str(temporary),
            PIP_CONFIG_FILE='/dev/null',PIP_DISABLE_PIP_VERSION_CHECK='1',PIP_NO_INPUT='1',PIP_CACHE_DIR=str(out/'private-pip-cache'))
        save(out/'install-command.json',dict(command=command,environment=env))
        import resource
        def limits():
            for kind,value in [(resource.RLIMIT_AS,8589934592),(resource.RLIMIT_CPU,180),(resource.RLIMIT_FSIZE,536870912),(resource.RLIMIT_NOFILE,256),(resource.RLIMIT_CORE,0)]:resource.setrlimit(kind,(value,value))
        process=None;record=dict(status='RUNNING_INSTALL_PROCESS_ONLY',command=command);started=time.monotonic()
        try:
            with (out/'install.stdout').open('xb') as stdout,(out/'install.stderr').open('xb') as stderr:
                process=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True,preexec_fn=limits)
                record['owned_process_group']=process.pid
                while process.poll() is None:
                    assert time.monotonic()-started<=240,'Private pip installation exceeded wall-time capacity'
                    time.sleep(.05)
                record['exit_code']=process.returncode;assert process.returncode==0,'Private pip install failed; read retained install.stderr/stdout'
                assert not any(row['state']!='Z' for row in members(process.pid)),'Private pip process left live owned workers'
                record['status']='completed-private-install-process-only'
        finally:
            if process is not None:
                record['owned_before_cleanup']=members(process.pid)
                for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                    if not any(row['state']!='Z' for row in members(process.pid)):break
                    try:os.killpg(process.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+seconds
                    while any(row['state']!='Z' for row in members(process.pid)) and time.monotonic()<deadline:time.sleep(.05)
                process.wait(timeout=10);record['owned_live_after_cleanup']=[row for row in members(process.pid) if row['state']!='Z']
                assert not record['owned_live_after_cleanup'],'Owned installer workers remain alive'
            record['elapsed_seconds']=time.monotonic()-started;save(out/'install-process.json',record);state['processes'].append(record)
        actual=inventory(site);save(out/'installed-site-files.json',actual)
        assert actual==profile['private_site_members'],'Actual pip-installed private site differs from the frozen profile; retain the discrepancy, do not rewrite installed metadata or known hashes'
        expected={'python':profile['python'],'legacy-sniffles':profile['legacy_binary']}
        expected.update({'site/'+name:value for name,value in profile['private_site_members'].items()})
        for tag,row in profile['editions'].items():expected.update({'sources/'+tag+'/'+name:value for name,value in row['members'].items()})
        assert inventory(runtime)==expected,'Complete prepared runtime differs from the closed known file population'
        assert source_pins=={name:pin(ROOT/name) for name in source_pins},'Installer or published source/artifact inputs changed during preparation'
        assert shared==inventory(Path(profile['stdlib_root']),True,True) and pin(Path(profile['python_executable']))==profile['python']
        save(out/'shared-after.json',shared);save(out/'runtime-files.json',expected)
        state.update(status='PASS_INSTALLED_EXACT_PRIVATE_SNIFFLES_RUNTIME_ONLY',runtime_root=str(runtime),files=pin(out/'runtime-files.json'),
            packages=profile['packages'],profile=pin(profile_path),distribution=pin(distribution/'DISTRIBUTION.json'),
            shared_python_and_standard_library_unchanged=True,original_runtime_health_and_binding_still_required=True,
            source_pins=source_pins,preparation_inputs_unchanged=True)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        if shared is not None:
            try:
                after=inventory(Path('/usr/lib/python3.12'),True,True);save(out/'shared-after.json',after)
                if shared!=after:state.update(status='FAIL2_SHARED_STANDARD_LIBRARY_CHANGED',error='Shared standard-library population changed; no runtime admitted')
                if pin(Path('/usr/bin/python3.12'))!=profile['python']:state.update(status='FAIL2_SHARED_PYTHON_CHANGED',error='Shared Python executable changed; no runtime admitted')
            except Exception as failure:state.update(status='FAIL2_SHARED_STANDARD_LIBRARY_CHECK_UNAVAILABLE',error=repr(failure))
        save(out/'COMPLETED.json',state)
        message='# '+state['status']+'\n\n'+state.get('error','Exact runtime prepared; original health/binding and complete scientific calling validation remain required.')+'\n\n**ACTION REQUIRED:** '+ACTION+'\n'
        (out/'WARNINGS.md').write_text(message,encoding='utf8',newline='\n')
        print(state['status']+' '+state.get('error','')+'\nACTION: '+ACTION,flush=True)
    return 0 if state['status'].startswith('PASS_') else 2 if state['status'].startswith('FAIL2_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact installation/source/shared-runtime checks')
    raise SystemExit(main())
