# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Restore exact original dependencies in a NEW private Linux path, then export the checked execution closure."""
import argparse,concurrent.futures,hashlib,io,json,os,platform,shutil,subprocess,sys,tarfile,urllib.request,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

ACTION='SKIP3: LOFTEE-dependent analysis is skipped until preparation succeeds. Read WARNINGS.md, COMPLETED.json and the named installer/download logs. Where permitted supply the exact locked archives, correct the named host/network/permission/storage/prefix issue, and retry a NEW short Linux x86_64 output path. Keep the analysis skipped if installation is forbidden. Never substitute versions or refresh hashes to admit changed software. This preparation runs no variant analysis and downloads no human reference/cache/GERP/ancestral data.'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');p.add_argument('--package-cache',type=Path,help='Optional read-only micromamba package cache root; missing archives are downloaded');p.add_argument('--installed-environment',type=Path,help='Optional existing exact locked environment to inspect read-only instead of installing another copy');p.add_argument('--micromamba-archive',type=Path);p.add_argument('--bioperl-archive',type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build') and not out.exists();out.mkdir(parents=True);source=Path(__file__).resolve();(out/'installer-at-attempt.py').write_bytes(source.read_bytes());lock_path=ROOT/'docs/literature/loftee-workflow-development/INSTALL-LOCK.json';profile_path=ROOT/'src/main/resources/org/jlinalg/omics/loftee-workflow/SOURCE-PROFILE.json';lock=json.loads(lock_path.read_bytes());profile=json.loads(profile_path.read_bytes());state=dict(status='SKIP3_DEPENDENCY_UNAVAILABLE',analysis_executed=False,shared_environment_modified=False,implementation_credit=False,required_action=ACTION,source_pins={f.relative_to(ROOT).as_posix():pin(f) for f in [source,lock_path,profile_path,ROOT/'tools/literature/setup_loftee_workflow_runtime.py']});save(out/'STARTED.json',dict(status='STARTING_PRIVATE_EXACT_DEPENDENCY_PREPARATION',source_pins=state['source_pins']));print(ACTION,flush=True)
    def execute(name,command,timeout=1800):
        save(out/(name+'.command.json'),dict(command=command))
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env={'HOME':str(out),'PATH':'/usr/bin:/bin','LC_ALL':'C','MAMBA_ROOT_PREFIX':str(out/'mamba-root'),'MAMBA_NO_BANNER':'1'},stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout)
        save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode);return r
    try:
        assert a.allow_install,'Installation is not authorized for this invocation: use --allow-install only where permitted'
        assert sys.platform.startswith('linux') and platform.machine() in ['x86_64','amd64'],'This exact upstream preparation requires Linux x86_64'
        assert str(out).isascii() and all(c.isalnum() or c in '/._-' for c in str(out)),'Choose a short portable ASCII output path'
        assert lock['source_profile']==pin(profile_path) and lock['package_count']==len(lock['packages'])==224
        for name,value in profile['host_files'].items():assert pin(Path(name))==value,('Captured host dependency differs',name)
        assert Path('/bin/sh').resolve()==Path('/usr/bin/dash') and Path('/lib64/ld-linux-x86-64.so.2').resolve()==Path('/usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2')
        old=profile['environment']['baseline_root'];assert len(str(out/'full-environment').encode('ascii'))<=len(old.encode('ascii')) and len(str(out/'runtime/environment').encode('ascii'))<=len(old.encode('ascii')),'Output prefix is too long for exact original binary relocation'
        assert shutil.disk_usage(out).free>=6*1024**3,'At least6 GiB free space is required for private downloads, environment and compact export'
        archives=out/'archives';archives.mkdir()
        def fetch(url,expected,target,local=None):
            assert url.startswith(('https://conda.anaconda.org/','https://codeload.github.com/'))
            if local is not None and local.is_file():
                assert not local.is_symlink() and pin(local)==expected,('Read-only supplied archive differs',str(local));shutil.copyfile(local,target);assert pin(local)==expected;receipt=dict(source='read-only-supplied-archive',original=str(local),url=url)
            else:
                with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-exact-private-dependency-setup/1.0'}),timeout=120) as response,target.open('xb') as stream:
                    count=0
                    while raw:=response.read(1048576):
                        count+=len(raw);assert count<=expected['bytes'],'Download exceeds exact locked size';stream.write(raw)
                    receipt=dict(source='download',url=url,final_url=response.url,http_status=response.status)
            assert pin(target)==expected,('Complete archive differs from locked SHA256/size',target.name);save(target.with_name(target.name+'.receipt.json'),dict(**receipt,archive=expected));return target
        mamba_row=lock['micromamba'];mamba_archive=fetch(mamba_row['url'],mamba_row['archive'],archives/'micromamba.tar.bz2',a.micromamba_archive)
        with tarfile.open(mamba_archive,mode='r:bz2') as archive:
            members=[m for m in archive.getmembers() if m.name=='bin/micromamba'];assert len(members)==1 and members[0].isfile() and members[0].size<=33554432;data=archive.extractfile(members[0]).read();assert len(data)==members[0].size;mamba=out/'micromamba';mamba.write_bytes(data);os.chmod(mamba,0o700)
        execute('micromamba-version',[str(mamba),'--version'],60);assert (out/'micromamba-version.stdout').read_text(encoding='utf8').strip()==mamba_row['version']
        def package(row):
            relative=row['url'].removeprefix('https://');local=a.package_cache/'https'/relative if a.package_cache else None;assert '/' not in row['fn'] and '\\' not in row['fn'];return fetch(row['url'],dict(bytes=row['size'],sha256=row['sha256']),archives/row['fn'],local)
        assert len({r['fn'] for r in lock['packages']})==224
        print('Preparing224 SHA256-locked original package archives in the private output',flush=True)
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:packages=list(pool.map(package,lock['packages']))
        explicit=out/'explicit-packages.txt';explicit.write_text('@EXPLICIT\n'+'\n'.join(f.as_uri()+'#'+r['md5'] for f,r in zip(packages,lock['packages']))+'\n',encoding='ascii');full=a.installed_environment.resolve() if a.installed_environment else out/'full-environment'
        if a.installed_environment:
            assert full.is_dir() and not full.is_symlink() and not full.is_relative_to(out) and not out.is_relative_to(full),'Read-only installed environment must be separate from the NEW preparation output'
            state['existing_locked_environment_used_read_only']=str(full)
        else:execute('environment-install',[str(mamba),'--no-rc','create','--yes','--offline','--prefix',str(full),'--file',str(explicit),'--json'])
        execute('installed-packages',[str(mamba),'--no-rc','list','--prefix',str(full),'--json'],120);listing=json.loads((out/'installed-packages.stdout').read_bytes());listing=listing['packages'] if isinstance(listing,dict) else listing;assert {(r['name'],r['version'],r.get('build_string',r.get('build'))) for r in listing}=={(r['name'],r['version'],r['build']) for r in lock['packages']},'Installed package set differs from exact lock'
        row=lock['bioperl'];bioperl_archive=fetch(row['url'],row['archive'],archives/'bioperl-release-1-6-924.zip',a.bioperl_archive);bioperl=out/'bioperl-source';bioperl.mkdir()
        with zipfile.ZipFile(bioperl_archive) as archive:
            assert len(archive.namelist())==len(set(archive.namelist())) and archive.testzip() is None
            for name,value in profile['bioperl']['files'].items():
                assert value['kind']=='file' and 'prefix_relocation' not in value;target=bioperl/name;assert target.resolve().is_relative_to(bioperl);raw=archive.read(row['member_prefix']+name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())=={k:value[k] for k in ['bytes','sha256']};target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);os.chmod(target,value['mode'])
        print('Exporting the exact reviewed execution closure and checking original software health',flush=True);execute('compact-export',[sys.executable,str(ROOT/'tools/literature/setup_loftee_workflow_runtime.py'),'--environment',str(full),'--bioperl',str(bioperl),'--out',str(out/'runtime'),'--allow-prepare'],1200);result=json.loads((out/'runtime/COMPLETED.json').read_bytes());assert result['status']=='PASS_EXACT_COMPACT_LOFTEE_RUNTIME_PREPARED_ONLY';assert all(pin(ROOT/n)==v for n,v in state['source_pins'].items());assert all(pin(f)==dict(bytes=r['size'],sha256=r['sha256']) for f,r in zip(packages,lock['packages']));state.update(status='PASS_EXACT_LOCKED_ENVIRONMENT_AND_COMPACT_RUNTIME_PREPARED' if a.installed_environment else 'PASS_EXACT_ORIGINAL_DEPENDENCIES_INSTALLED_AND_COMPACT_RUNTIME_PREPARED',runtime=str(out/'runtime'),environment=result['environment'],bioperl=result['bioperl'],sources=result['sources'],installed_packages=len(listing),original_archives_retained=True)
    except Exception as error:state['error']=repr(error);print('SKIP3 DEPENDENT ANALYSIS SKIPPED:',repr(error),flush=True)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Dependency preparation completed; no variant analysis was run.')+'\n\n'+ACTION+'\n',encoding='utf8');print(state['status']+'; read '+str(out/'WARNINGS.md'),flush=True);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact package/source/runtime checks')
    raise SystemExit(main())
