#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install and qualify a private selected Snakemake runtime where installation is permitted."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,platform,shutil,signal,subprocess,sys,tarfile,time,traceback,urllib.error,urllib.request,zipfile
if not __debug__:raise RuntimeError('Setup qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
PIXI='50571451e96f4a82cf675cd83baaf66fff9b718f0aaf153900b9faf4c29bd401'
WHEEL='cb8b9045c68d2d561520a92d78c8acb0e7383cd7958adde1efb68d97a0601a92'
ASSETS={'pixi.toml':(216,'137f15cc42b20ec06c1584352cfcb93a969c29fe629fff50bca85f080aab52a4'),'pixi.lock':(50586,'ebc39cd3f2d04a7ae915554e8291bd0e84fe7fe6b9f0edf17dc4bb137c44080a'),'setup-controls.zip':(6903,'4f942198d2cb31c134b77fededb594555eee25792102eebc003c943658821607')}
WHEEL_URL='https://files.pythonhosted.org/packages/d4/24/08e5bd06a94770441f844611fa50626d06f2170f037bd37019b2b8b3e489/snakemake-9.27.1-py3-none-any.whl'
PIXI_URL='https://github.com/prefix-dev/pixi/releases/download/v0.83.0/pixi-x86_64-unknown-linux-musl.tar.gz'
LICENSE_URL='https://raw.githubusercontent.com/prefix-dev/pixi/v0.83.0/LICENSE'
ACTION='SKIP3: if download, installation or execution is missing/forbidden, skip dependent analysis. Use a permitted Linux x86-64 host and a NEW private ASCII path without whitespace on a case-sensitive filesystem supporting POSIX modes and symlinks, with at least 2 GiB free, then explicitly enable permitted installation. FAIL2: retain all inputs, partial installations, logs and failed controls; resolve the named integrity/source discrepancy and retry NEW paths. Never overwrite/relabel an old setup or refresh its hashes. Runtime setup and synthetic controls admit no user analysis, paper reproduction or catalog integration.'
def pin(path):
    digest=hashlib.sha256()
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):digest.update(raw)
    return dict(bytes=path.stat().st_size,sha256=digest.hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def download(url,target,limit):
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-permitted-private-runtime-setup/1'})
    with urllib.request.urlopen(request,timeout=60) as response,target.open('xb') as stream:
        if not response.url.startswith('https://'):raise ValueError('Refuse a non-HTTPS software download redirect')
        count=0
        while True:
            raw=response.read(1048576)
            if not raw:break
            count+=len(raw)
            if count>limit:raise ValueError('Software download exceeds complete artifact capacity')
            stream.write(raw)
    write(target.with_name(target.name+'.SOURCE.json'),dict(requested_url=url,**pin(target)))
def run(out,name,command,environment,timeout):
    start=time.monotonic();timed_out=False
    with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:
        p=subprocess.Popen(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
        try:
            try:p.wait(timeout=timeout)
            except subprocess.TimeoutExpired:timed_out=True;os.killpg(p.pid,signal.SIGTERM);p.wait(timeout=15)
        finally:
            if p.poll() is None:os.killpg(p.pid,signal.SIGKILL);p.wait(timeout=10)
    row=dict(command=command,exit_code=p.returncode,timed_out=timed_out,seconds=round(time.monotonic()-start,3));write(out/(name+'.PROCESS.json'),row)
    if timed_out or p.returncode!=0:
        message='Permitted setup step failed: '+name+'. Inspect '+str(out/(name+'.stderr'))+'; no dependent analysis is run.'
        if name=='install' or p.returncode==3:raise FileNotFoundError(message)
        raise RuntimeError(message)
    return row
def export_records(out,target):
    if target is None:return
    target=target.absolute()
    if target!=target.resolve() or not target.parent.is_dir() or target.is_relative_to(out) or out.is_relative_to(target):raise ValueError('Require a NEW canonical record export path separate from the private installation')
    target.mkdir(exist_ok=False)
    for source in sorted(out.iterdir()):
        if source.is_file():shutil.copyfile(source,target/source.name)
    for name in ['adoption','build','control-source','tools','src']:
        if (out/name).is_dir():shutil.copytree(out/name,target/name,symlinks=True)
    filesystem=out/'filesystem-probe'
    if filesystem.is_dir():
        # Case-distinct source names cannot be copied loose onto a case-insensitive
        # record filesystem. Preserve their bytes and link spelling inside a ZIP.
        entries={}
        with zipfile.ZipFile(target/'FILESYSTEM-PROBE.zip','x',compression=zipfile.ZIP_DEFLATED) as archive:
            for source in sorted(filesystem.iterdir()):
                info=source.lstat();link=source.is_symlink();raw=os.readlink(source).encode('utf-8') if link else source.read_bytes();member=zipfile.ZipInfo(source.name,date_time=(2026,11,16,0,0,0));member.external_attr=info.st_mode<<16;member.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(member,raw)
                entries[source.name]=dict(kind='symlink' if link else 'file',mode=info.st_mode&0o777,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
        write(target/'FILESYSTEM-PROBE.json',dict(scope='Exact source file bytes/link spelling and POSIX modes inside ZIP; never infer case preservation from a loose copy to another filesystem',members=entries))
    for name in ['pixi.toml','pixi.lock']:
        source=out/'environment'/name
        if source.is_file():path=target/'environment'/name;path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,path)
    for source in (out/'bootstrap').glob('*') if (out/'bootstrap').exists() else []:
        if source.is_file() and source.name!='pixi' and source.name!='pixi.tar.gz':path=target/'bootstrap'/source.name;path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,path)
    members={p.relative_to(target).as_posix():pin(p) for p in sorted(target.rglob('*')) if p.is_file()}
    shutil.copyfile(Path(__file__),target/'as-run-exporter.py');members['as-run-exporter.py']=pin(target/'as-run-exporter.py')
    write(target/'EXPORTED.json',dict(status='RETAINED_PRIVATE_SETUP_RECORDS_NOT_USER_ANALYSIS',installed_prefix=str(out),files=members,installation_or_cache_binaries_exported=False,user_analysis_admitted=False))
def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--records-out',type=Path,help='Optional NEW separate path for complete setup/control logs and metadata; retain this on persistent storage');parser.add_argument('--allow-install',action='store_true',help='Use only when software download/installation/execution is permitted; otherwise write SKIP3 and launch nothing');parser.add_argument('--pixi',type=Path,help='Optional existing exact pinned Pixi 0.83.0 Linux binary; otherwise download it privately');a=parser.parse_args();out=None
    try:
        target=a.out.absolute()
        if target!=target.resolve() or target.parent==target or not target.parent.is_dir() or any(c.isspace() or ord(c)>127 for c in str(target)):raise ValueError('Require a NEW canonical private ASCII output path without whitespace and an existing parent')
        if a.records_out is not None:
            records=a.records_out.absolute()
            if records!=records.resolve() or records.exists() or not records.parent.is_dir() or records.is_relative_to(target) or target.is_relative_to(records):raise ValueError('Require a NEW canonical separate record export path with an existing parent')
        target.mkdir(exist_ok=False);out=target;(out/'WARNINGS.md').write_text('# Original Snakemake runtime setup only\n\n'+ACTION+'\n\nUse a persistent native Linux installation prefix. Temporary directories may be cleared between sessions. Each new installation is adopted independently; the shared package lock does not establish whole-environment byte identity or an author environment.\n',encoding='utf-8',newline='\n');shutil.copyfile(Path(__file__),out/'as-run-setup_runtime.py')
        if not a.allow_install:raise PermissionError('Software installation is not enabled. No download, install, control workflow or user analysis was launched.')
        if sys.platform!='linux' or platform.machine() not in ['x86_64','amd64']:raise FileNotFoundError('This selected software setup requires permitted Linux x86-64 execution')
        if shutil.disk_usage(out).free<2147483648:raise FileNotFoundError('At least 2 GiB of permitted free workspace is required for the private environment/cache')
        filesystem=out/'filesystem-probe';filesystem.mkdir()
        try:
            with (filesystem/'Case').open('xb') as f:f.write(b'upper\n')
            with (filesystem/'case').open('xb') as f:f.write(b'lower\n')
            (filesystem/'Case').chmod(0o700);(filesystem/'alias').symlink_to('Case')
            if (filesystem/'Case').read_bytes()!=b'upper\n' or (filesystem/'case').read_bytes()!=b'lower\n' or (filesystem/'Case').stat().st_mode&0o777!=0o700 or (filesystem/'alias').resolve()!=filesystem/'Case':raise OSError('Case, POSIX mode or symlink preservation differs')
        except OSError as unavailable:raise PermissionError('Selected prefix cannot preserve case-distinct package names, POSIX modes and symlinks. Use a permitted native Linux filesystem and NEW setup paths; no installation has started.') from unavailable
        assets=Path(__file__).with_name('runtime');source=json.loads((assets/'SOURCE.json').read_bytes());resource_pins={}
        for name,(size,digest) in ASSETS.items():
            expected=dict(bytes=size,sha256=digest);assert pin(assets/name)==source['resources'][name]==expected,'Pinned setup asset differs: '+name;resource_pins[name]=expected
        envroot=out/'environment';envroot.mkdir()
        for name in ['pixi.toml','pixi.lock']:shutil.copyfile(assets/name,envroot/name)
        fixtures=out/'control-source';fixtures.mkdir()
        with zipfile.ZipFile(assets/'setup-controls.zip') as archive:
            assert len(archive.namelist())==len(set(archive.namelist())) and set(archive.namelist())==set(source['fixture_files']) and archive.testzip() is None
            for name in archive.namelist():
                member=PurePosixPath(name);assert not member.is_absolute() and '..' not in member.parts and '\\' not in name
                raw=archive.read(name);expected=source['fixture_files'][name];assert len(raw)==expected['bytes'] and hashlib.sha256(raw).hexdigest()==expected['sha256'];path=fixtures/name;path.parent.mkdir(parents=True,exist_ok=True)
                with path.open('xb') as f:f.write(raw)
        deployed={}
        for name in ['prototype_driver.py','adopt_runtime.py']:
            original=Path(__file__).with_name(name);copy=out/'tools/literature/snakemake_original'/name;copy.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(original,copy);assert pin(original)==pin(copy);deployed[str(original.relative_to(ROOT))]=pin(copy)
        helpers=ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1'
        for name in ['driver.py','snakemake_logger_plugin_omicsmatic/__init__.py','official-package.json']:
            path=helpers/name;copy=out/'src/main/resources/org/jlinalg/omics/snakemake-original-v1'/name;copy.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,copy);assert pin(path)==pin(copy);deployed[str(path.relative_to(ROOT))]=pin(copy)
        write(out/'SOURCE.json',dict(status='NEW_PRIVATE_SETUP_SOURCE_SNAPSHOT_NOT_ADOPTED',producer=pin(Path(__file__)),resources=resource_pins,deployed_project_sources=deployed,selected_engine='9.27.1',selected_pixi='0.83.0',author_runtime_established=False))
        for name in ['home','cache','pixi-home','bootstrap']:(out/name).mkdir()
        environment=dict(PATH='/usr/bin:/bin',HOME=str(out/'home'),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',PIXI_HOME=str(out/'pixi-home'),PIXI_CACHE_DIR=str(out/'cache/pixi'),XDG_CACHE_HOME=str(out/'cache'),PIXI_NO_CONFIG='true',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',PYPI_KEYRING_PROVIDER='disabled')
        pixi=out/'bootstrap/pixi'
        if a.pixi is not None:
            supplied=a.pixi.resolve(strict=True)
            if pin(supplied)['sha256']!=PIXI:raise ValueError('Existing Pixi differs from the selected pinned binary')
            shutil.copyfile(supplied,pixi)
        else:
            archive=out/'bootstrap/pixi.tar.gz';download(PIXI_URL,archive,104857600)
            with tarfile.open(archive,'r:gz') as bundle:
                candidates=[m for m in bundle.getmembers() if m.name.removeprefix('./')=='pixi'];assert len(candidates)==1 and candidates[0].isfile() and candidates[0].size<=268435456
                with bundle.extractfile(candidates[0]) as src,pixi.open('xb') as dst:shutil.copyfileobj(src,dst)
        if pin(pixi)['sha256']!=PIXI:raise ValueError('Downloaded Pixi differs from the selected pinned binary')
        pixi.chmod(0o755);download(LICENSE_URL,out/'bootstrap/Pixi-BSD-3-Clause.txt',65536)
        run(out,'install',[str(pixi),'install','--manifest-path',str(envroot/'pixi.toml'),'--locked','--no-config','--pypi-keyring-provider','disabled'],environment,1800)
        assert all(pin(envroot/name)==resource_pins[name] for name in ['pixi.toml','pixi.lock']),'Installer changed the selected manifest or lock'
        wheel=out/'snakemake-9.27.1-py3-none-any.whl';download(WHEEL_URL,wheel,8388608)
        if pin(wheel)['sha256']!=WHEEL:raise ValueError('Selected official Snakemake wheel bytes differ')
        python=envroot/'.pixi/envs/default/bin/python';controls=out/'build/driver-controls';controls.parent.mkdir();environment['PATH']=str(python.parent)+':/usr/bin:/bin'
        run(out,'driver-controls',[str(python),'-I','-B',str(out/'tools/literature/snakemake_original/prototype_driver.py'),'--original-controls',str(fixtures),'--out',str(controls)],environment,600)
        adoption=out/'adoption';run(out,'adopt',[str(python),'-I','-B',str(out/'tools/literature/snakemake_original/adopt_runtime.py'),'--environment',str(python.parent.parent),'--pixi-lock',str(envroot/'pixi.lock'),'--wheel',str(wheel),'--driver-controls',str(controls),'--out',str(adoption)],environment,600)
        ready=json.loads((adoption/'READY.json').read_bytes());assert not ready['user_analysis_admitted'] and not ready['project_java_adapter_qualified']
        write(out/'READY-SETUP.json',dict(status='READY_PRIVATE_SELECTED_RUNTIME_NOT_USER_ANALYSIS',profile=ready['profile'],adoption=str(adoption),adoption_marker=pin(adoption/'READY.json'),environment=str(python.parent.parent),source=pin(out/'SOURCE.json'),source_controls=pin(controls/'COMPLETED.json'),user_analysis_admitted=False,whole_goal_complete=False,required_action=ACTION));export_records(out,a.records_out);print('READY_PRIVATE_SELECTED_RUNTIME_NOT_USER_ANALYSIS '+str(adoption),flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError,urllib.error.URLError)) else 2
        if out is not None:write(out/'FAILURE.json',dict(status='SKIP3-analysis-skipped' if code==3 else 'FAIL2-no-result-admitted',exit_code=code,error=repr(error),traceback=traceback.format_exc(),required_action=ACTION,user_analysis_admitted=False))
        if out is not None and a.records_out is not None and not a.records_out.exists():
            try:export_records(out,a.records_out)
            except Exception as export_failure:print('FAIL2: setup diagnostic export failed: '+repr(export_failure)+'; retain '+str(out),file=sys.stderr,flush=True)
        print(('SKIP3' if code==3 else 'FAIL2')+': '+repr(error)+'\n'+ACTION,file=sys.stderr,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
