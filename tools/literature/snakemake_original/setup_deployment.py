#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install pinned private deployment managers when permitted; no workflow or user analysis is run."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,os,platform,shutil,sys,traceback,urllib.error,zipfile
if not __debug__:raise RuntimeError('Setup assertions must remain enabled')
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_runtime import download,pin,write,run
ARTIFACTS={
    'conda':dict(name='Miniforge3-26.9.4-2-Linux-x86_64.sh',url='https://github.com/conda-forge/miniforge/releases/download/26.9.4-2/Miniforge3-26.9.4-2-Linux-x86_64.sh',bytes=128272241,sha256='bbaf16b3897da24c4be50315934ba74848f617b4495982841d6a0a60e0edbbdf',license='https://raw.githubusercontent.com/conda-forge/miniforge/26.9.4-2/LICENSE'),
    'apptainer':dict(name='apptainer_1.5.5_amd64.deb',url='https://github.com/apptainer/apptainer/releases/download/v1.5.5/apptainer_1.5.5_amd64.deb',bytes=32997476,sha256='25095d3711504b3e8657dbb2e172ef8db9db43c4503d940c9c36b105db8dce77',license='https://raw.githubusercontent.com/apptainer/apptainer/v1.5.5/LICENSE.md')}
ACTION='SKIP3: missing/forbidden download, installation or execution skips the dependent analysis. Restore the named prerequisite on a permitted Linux x86-64 host, enable installation only where allowed and retry NEW persistent native Linux paths. FAIL2: preserve the complete partial installation, source artifacts and logs; resolve the named integrity discrepancy and retry NEW paths. This setup does not execute a workflow, qualify a deployment adapter, reproduce a paper or add catalog credit.'
LIBSUBID=dict(name='libsubid4_4.13+dfsg1-4ubuntu3.2_amd64.deb',url='https://archive.ubuntu.com/ubuntu/pool/main/s/shadow/libsubid4_4.13+dfsg1-4ubuntu3.2_amd64.deb',bytes=23442,sha256='ba97fd28c53560a8d2a2261e8f75a7ab4112535b12f9fe1d50970c30051da0da')
APPTAINER_INSTALLER=dict(name='install-unprivileged-1.5.5.sh',url='https://raw.githubusercontent.com/apptainer/apptainer/v1.5.5/tools/install-unprivileged.sh',bytes=15560,sha256='a097956eafa6ab3dd843ee429d0b774ed029753bfadf013b8046556228fac6f2')
APPTAINER_LIBRARIES=[LIBSUBID,
    dict(name='libtalloc2_2.4.2-1build2_amd64.deb',url='https://archive.ubuntu.com/ubuntu/pool/main/t/talloc/libtalloc2_2.4.2-1build2_amd64.deb',bytes=27260,sha256='c9728388084bdccdbcfb3e97358535431855f4ce57992c2a775534dc1bd4935f'),
    dict(name='libprotobuf-c1_1.4.1-1ubuntu4_amd64.deb',url='https://archive.ubuntu.com/ubuntu/pool/main/p/protobuf-c/libprotobuf-c1_1.4.1-1ubuntu4_amd64.deb',bytes=20790,sha256='577b713651a61e82226e46c9c9e3c23cd16df9ee3c2b27b22b49c5abb2ef8f61')]

def downloaded(out,selected,cache):
    artifact=out/'downloads'/selected['name']
    if cache is not None and (cache/selected['name']).is_file():
        source=cache/selected['name']
        if pin(source)!={k:selected[k] for k in ['bytes','sha256']}:raise ValueError('Supplied cached artifact differs from official selected bytes: '+str(source))
        shutil.copyfile(source,artifact);write(artifact.with_name(artifact.name+'.SOURCE.json'),dict(requested_url=selected['url'],cached_source=str(source),**pin(artifact)))
    else:download(selected['url'],artifact,selected['bytes'])
    if pin(artifact)!={key:selected[key] for key in ['bytes','sha256']}:raise ValueError('Official selected deployment artifact bytes differ: '+selected['name'])
    return artifact

def manager_tree(prefix):
    # Preserve dangling links in package-cache payloads. A link awaiting another
    # package's file is not a missing executable and must never be silently fixed.
    def members():
        rows={}
        for parent,dirs,files in os.walk(prefix,followlinks=False):
            for name in dirs+files:
                path=Path(parent)/name;info=path.lstat();rows[path.relative_to(prefix).as_posix()]=(info.st_mode,info.st_size,info.st_mtime_ns,info.st_ino)
        return rows
    before=members()
    def item(name):
        path=prefix/name;mode=path.lstat().st_mode&0o7777
        if path.is_symlink():
            target=os.readlink(path);resolved=path.resolve(strict=False);kind='file' if path.is_file() else 'directory' if path.is_dir() else 'missing'
            return name,dict(kind='symlink',mode=mode,target=target,resolved=str(resolved),target_kind=kind,pin=pin(path) if kind=='file' else None)
        if path.is_dir():return name,dict(kind='directory',mode=mode)
        if not path.is_file():raise ValueError('Unexpected special installed manager object: '+str(path))
        return name,dict(kind='file',mode=mode,pin=pin(path))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:rows=dict(pool.map(item,sorted(before)))
    if members()!=before:raise ValueError('Private installed manager changed during full fingerprinting')
    return rows

def export(out,target):
    if target is None:return
    target.mkdir(exist_ok=False)
    for path in sorted(out.iterdir()):
        if path.is_file():shutil.copyfile(path,target/path.name)
    for name in ['downloads','licenses','as-run']:
        if (out/name).exists():shutil.copytree(out/name,target/name)
    probe=out/'filesystem-probe'
    if probe.exists():
        with zipfile.ZipFile(target/'FILESYSTEM-PROBE.zip','x',compression=zipfile.ZIP_DEFLATED) as z:
            for path in sorted(probe.iterdir()):
                raw=os.readlink(path).encode() if path.is_symlink() else path.read_bytes();info=zipfile.ZipInfo(path.name);info.external_attr=path.lstat().st_mode<<16;z.writestr(info,raw)
    write(target/'EXPORTED.json',dict(status='PRIVATE_DEPLOYMENT_BOOTSTRAP_RECORDS_NOT_ANALYSIS',installed_prefix=str(out),files={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()},installed_manager_binaries_copied=False))

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--records-out',type=Path);p.add_argument('--components',nargs='+',choices=list(ARTIFACTS),default=list(ARTIFACTS));p.add_argument('--artifact-cache',type=Path,help='Optional existing exact pinned archives; cached bytes are verified before use');p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=None
    try:
        target=a.out.absolute()
        if target!=target.resolve() or not target.parent.is_dir() or any(c.isspace() or ord(c)>127 for c in str(target)):raise ValueError('Require a NEW canonical private ASCII prefix without whitespace and an existing parent')
        if a.records_out is not None:
            record=a.records_out.absolute()
            if record!=record.resolve() or record.exists() or not record.parent.is_dir() or record.is_relative_to(target) or target.is_relative_to(record):raise ValueError('Require a NEW separate persistent record-export directory')
        target.mkdir(exist_ok=False);out=target;(out/'WARNINGS.md').write_text('# Deployment manager setup only\n\n'+ACTION+'\n',encoding='utf-8');snapshot=out/'as-run';snapshot.mkdir()
        for source in [Path(__file__),HERE/'setup_runtime.py',HERE/'adopt_runtime.py']:shutil.copyfile(source,snapshot/source.name)
        if not a.allow_install:raise PermissionError('Installation is not enabled. No download, package manager, installation or workflow was launched.')
        if sys.platform!='linux' or platform.machine() not in ['x86_64','amd64']:raise FileNotFoundError('Selected deployment managers require Linux x86-64')
        if shutil.disk_usage(out).free<3221225472:raise FileNotFoundError('At least 3 GiB free in the private prefix is required')
        probe=out/'filesystem-probe';probe.mkdir()
        try:
            (probe/'Case').write_bytes(b'upper\n');(probe/'case').write_bytes(b'lower\n');(probe/'Case').chmod(0o700);(probe/'alias').symlink_to('Case')
            if (probe/'Case').read_bytes()!=b'upper\n' or (probe/'case').read_bytes()!=b'lower\n' or (probe/'Case').stat().st_mode&0o777!=0o700 or (probe/'alias').resolve()!=probe/'Case':raise OSError('Case/mode/link preservation differs')
        except OSError as error:raise PermissionError('Use a native case-sensitive Linux filesystem preserving POSIX modes and symlinks; no installation has started') from error
        for name in ['home','cache','downloads','licenses']:(out/name).mkdir()
        condarc=out/'condarc';condarc.write_text('channels:\n  - conda-forge\nchannel_priority: strict\nauto_activate_base: false\n',encoding='utf-8')
        env=dict(PATH='/usr/bin:/bin',HOME=str(out/'home'),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',XDG_CACHE_HOME=str(out/'cache'),CONDARC=str(condarc),CONDA_PKGS_DIRS=str(out/'cache/conda-pkgs'),CONDA_ENVS_PATH=str(out/'cache/conda-envs'),CONDA_NO_PLUGINS='true',CONDA_SOLVER='classic')
        installed={};skipped={}
        for component in dict.fromkeys(a.components):
            selected=ARTIFACTS[component];artifact=out/'downloads'/selected['name']
            phase='download-selected-manager'
            try:
                if component=='apptainer':
                    libc,version=platform.libc_ver()
                    if libc!='glibc' or tuple(int(x) for x in version.split('.')[:2])<(2,38):raise FileNotFoundError('This pinned private Apptainer library profile requires glibc >= 2.38; found '+libc+' '+version+'. Use a compatible permitted host or skip Apptainer-dependent analysis.')
                artifact=downloaded(out,selected,a.artifact_cache)
                phase='download-license'
                license=out/'licenses'/(component+'-LICENSE.txt');download(selected['license'],license,262144)
                if component=='conda':
                    prefix=out/'conda';command=['/usr/bin/bash',str(artifact),'-b','-p',str(prefix)]
                else:
                    executable=shutil.which('dpkg-deb',path=env['PATH'])
                    if executable is None:raise FileNotFoundError('Private Debian archive extraction requires dpkg-deb; install it only where permitted or skip Apptainer-dependent analysis')
                    prefix=out/'apptainer';command=[executable,'--extract',str(artifact),str(prefix)]
                phase='install-private-manager'
                try:run(out,'install-'+component,command,env,900)
                except RuntimeError as error:raise FileNotFoundError('Private '+component+' installation failed; inspect retained install logs and skip dependent analysis') from error
                binary=prefix/('bin/conda' if component=='conda' else 'usr/bin/apptainer')
                if component=='apptainer':
                    phase='install-private-apptainer-dependencies'
                    for selected_dependency in APPTAINER_LIBRARIES:
                        dependency=downloaded(out,selected_dependency,a.artifact_cache);run(out,'private-'+selected_dependency['name'],[executable,'--extract',str(dependency),str(prefix/'dependencies')],env,120)
                    (prefix/'bin').mkdir();binary=prefix/'bin/apptainer'
                    # Upstream buildcfg relocates only binaries whose basename is
                    # exactly apptainer or starter. Flatten the Debian FHS tree
                    # into a private prefix; retain the original unpacked tree.
                    # Copy executable directories physically so /proc/self/exe
                    # does not resolve a link back into the Debian usr prefix.
                    payload=prefix/'payload';payload.mkdir()
                    for name in ['bin','libexec','share']:shutil.copytree(prefix/'usr'/name,payload/name,symlinks=True)
                    for name in ['etc','var']:shutil.copytree(prefix/name,payload/name,symlinks=True)
                    # Use the exact upstream helper wrapper. Its _WRAPPER_EXEC_CMD
                    # and _WRAPPER_ARG0 handling is needed by the runtime parent
                    # when re-executing after clearing loader environment values.
                    script=downloaded(out,APPTAINER_INSTALLER,a.artifact_cache);text=script.read_text();anchor="cat >libexec/apptainer/bin/.wrapper <<'!EOF!'\n";assert text.count(anchor)==1;wrapper=text.split(anchor,1)[1].split('\n!EOF!\n',1)[0]+'\n'
                    utils=payload/'utils';utils.mkdir();(utils/'bin').mkdir();(utils/'lib').symlink_to('../../dependencies/usr/lib/x86_64-linux-gnu',target_is_directory=True)
                    helper_bin=payload/'libexec/apptainer/bin';original_helpers=list(helper_bin.iterdir());real_helpers=helper_bin.parent/'libexec';real_helpers.mkdir()
                    for helper in original_helpers:
                        if not helper.is_file() or helper.is_symlink():raise ValueError('Unexpected selected Apptainer helper shape: '+str(helper))
                        helper.rename(real_helpers/helper.name);helper.symlink_to('.wrapper')
                    (helper_bin/'.wrapper').write_text(wrapper,encoding='utf-8');(helper_bin/'.wrapper').chmod(0o755)
                    binary.write_text('#!/usr/bin/bash\nself="$(/usr/bin/readlink -f -- "$0")"\nprefix="${self%/bin/*}"\nexport LD_LIBRARY_PATH="$prefix/dependencies/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"\nexec "$prefix/payload/bin/apptainer" "$@"\n',encoding='utf-8');binary.chmod(0o755)
                phase='execute-version-probe'
                try:run(out,'version-'+component,[str(binary),'--version'],env,120)
                except RuntimeError as error:raise FileNotFoundError('Installed '+component+' cannot execute on this host; retain the private prefix and skip dependent analysis') from error
                phase='capture-complete-manager-tree'
                rows=manager_tree(prefix);manifest=out/(component+'-files.json');write(manifest,dict(schema_version=1,root=str(prefix),scope='Complete installed manager tree including unresolved package-cache links; no kernel/whole-OS claim',nodes=rows))
                value=dict(status='INSTALLED_AND_VERSION_PROBED_NOT_SOURCE_WORKFLOW_QUALIFIED',component=component,prefix=str(prefix),executable=str(binary),artifact=pin(artifact),license=pin(license),manifest=pin(manifest),entries=len(rows),version=(out/('version-'+component+'.stdout')).read_text(),user_analysis_admitted=False)
                write(out/(component.upper()+'-INSTALLED.json'),value);installed[component]=value;print(component+' INSTALLED_NOT_WORKFLOW_QUALIFIED',flush=True)
            except (FileNotFoundError,PermissionError,urllib.error.URLError) as error:
                value=dict(status='SKIP3-dependent-analysis-skipped',component=component,step=phase,error=str(error),error_type=type(error).__name__,filename=getattr(error,'filename',None),traceback=traceback.format_exc(),required_action=ACTION);write(out/(component.upper()+'-SKIP.json'),value);skipped[component]=value
                with (out/'WARNINGS.md').open('a',encoding='utf-8') as f:f.write('\n## SKIP3: '+component+' dependent analysis skipped\n\nStep: '+phase+'\n\nReason: '+str(error)+'\n\n'+ACTION+'\n')
                print('SKIP3 '+component+' at '+phase+': '+str(error)+'; inspect '+str(out/'WARNINGS.md'),flush=True)
        write(out/'SETUP.json',dict(status='PARTIAL_DEPLOYMENT_BOOTSTRAP_WITH_SKIPS' if skipped else 'INSTALLED_SELECTED_DEPLOYMENT_MANAGERS_NOT_SOURCE_QUALIFICATION',installed=installed,skipped=skipped,requested_components=a.components,installation_permitted=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));export(out,a.records_out);return 3 if skipped else 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError,urllib.error.URLError)) else 2
        if out is not None:
            write(out/'FAILURE.json',dict(status='SKIP3' if code==3 else 'FAIL2',exit_code=code,error=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
            with (out/'WARNINGS.md').open('a',encoding='utf-8') as f:f.write('\n## '+('SKIP3: dependent analysis skipped' if code==3 else 'FAIL2: no runtime or analysis admitted')+'\n\n'+str(error)+'\n\n'+ACTION+'\n')
            if a.records_out is not None and not a.records_out.exists():export(out,a.records_out)
        print(('SKIP3' if code==3 else 'FAIL2')+': '+repr(error)+'\n'+ACTION,file=sys.stderr,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
