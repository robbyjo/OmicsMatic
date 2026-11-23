# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build unchanged pinned EIG smartpca with privately installed GSL/OpenBLAS on Linux.

This developer reference installer grants no catalog or workflow credit. It requires
explicit --allow-install, an immutable source intake, and a new isolated output.
"""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,time

COMMIT='4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1'
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--intake',type=Path,required=True);parser.add_argument('--micromamba',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--allow-install',action='store_true')
    parser.add_argument('--reuse-dependencies',type=Path,help='Read-only dependency prefix from a retained prior attempt')
    args=parser.parse_args();out=args.out.resolve();assert not out.exists(),'Use NEW isolated output'
    out.mkdir(parents=True);installer=Path(__file__).resolve();installer_identity=pin(installer)
    shutil.copyfile(installer,out/'setup-source.py')
    state=dict(status='STARTED',analysis_executed=False,catalog_credit=0,whole_goal_complete=False,installer_source=installer_identity)
    try:
        if not args.allow_install:raise FileNotFoundError('Install not authorized; rerun with --allow-install only where permitted')
        if sys.platform!='linux':raise FileNotFoundError('This original reference build requires Linux with gcc and make; native project methods remain separate')
        for tool in ('gcc','make','ar'):
            if shutil.which(tool) is None:raise FileNotFoundError('Missing compiler prerequisite '+tool+'; install where permitted and retry NEW output')
        source=args.intake.resolve();record=json.loads((source/'SOURCE.json').read_bytes());assert record['commit']==COMMIT and record['tag']=='v7.2.1'
        original=source/'original-source'
        for row in record['members']:assert pin(original/row['path'])=={k:row[k] for k in ('bytes','sha256')},row['path']
        if not args.micromamba.is_file():raise FileNotFoundError('Supply a permitted Linux micromamba executable; dependency installation was not invoked')
        local=out/'micromamba';shutil.copyfile(args.micromamba,local);local.chmod(0o700)
        assert pin(local)==pin(args.micromamba);save(out/'MICROMAMBA.json',dict(original=str(args.micromamba),identity=pin(local)))
        prefix=(out/'dependency-prefix') if args.reuse_dependencies is None else args.reuse_dependencies.resolve()
        dependency_before=None
        if args.reuse_dependencies is not None:
            if not (prefix/'conda-meta').is_dir():raise FileNotFoundError('Missing retained dependency prefix '+str(prefix))
            installed=[json.loads(p.read_bytes()) for p in (prefix/'conda-meta').glob('*.json')]
            assert any(p['name']=='gsl' and p['version'].startswith('2.7') for p in installed)
            assert any(p['name']=='openblas' and p['version']=='0.3.30' for p in installed)
            def dependency_pins():
                return {p.relative_to(prefix).as_posix():({'symlink':os.readlink(p)} if p.is_symlink() else pin(p)) for p in sorted(prefix.rglob('*')) if p.is_symlink() or p.is_file()}
            dependency_before=dependency_pins();save(out/'REUSED-DEPENDENCIES-BEFORE.json',dependency_before)
        env=dict(os.environ,MAMBA_ROOT_PREFIX=str(out/'mamba-root'),MAMBA_NO_BANNER='1',
            CONDA_PKGS_DIRS=str(out/'package-cache'),OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',LC_ALL='C')
        def run(name,command,cwd=out,timeout=1200,admit=(0,)):
            start=time.time()
            with (out/(name+'.stdout')).open('wb') as stdout,(out/(name+'.stderr')).open('wb') as stderr:
                process=subprocess.run(list(map(str,command)),cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout)
            save(out/(name+'.json'),dict(command=list(map(str,command)),cwd=str(cwd),exit_code=process.returncode,elapsed_seconds=time.time()-start))
            if process.returncode not in admit:raise RuntimeError(name+' failed; preserve logs, fix the named dependency/build cause, and retry NEW output')
            return process
        packages=['gsl=2.7','openblas=0.3.30']
        if args.reuse_dependencies is None:
            command=[local,'create','--no-rc','--override-channels','-c','conda-forge','-p',prefix,*packages]
            run('dependency-plan',command+['--dry-run','--json'])
            run('dependency-install',command+['-y','--json'])
        run('dependency-explicit',[local,'list','--no-rc','-p',prefix,'--explicit'])
        build=out/'source-build';shutil.copytree(original,build)
        for row in record['members']:(build/row['path']).chmod(int(row['mode'],8))
        # Recursive upstream makefiles use distinct relative include directories;
        # command-line CFLAGS must supply the original absolute header root.
        cflags='-O2 -fcommon -I'+str(build/'include')+' -I'+str(prefix/'include')
        ldflags='-L'+str(prefix/'lib')+' -Wl,-rpath,'+str(prefix/'lib')
        run('compiler-version',['gcc','--version'])
        run('original-build',['make','-j2','CFLAGS='+cflags,'LDFLAGS='+ldflags,'eigensrc/smartpca'],cwd=build/'src')
        binary=build/'src/eigensrc/smartpca';assert binary.is_file()
        for row in record['members']:assert pin(build/row['path'])=={k:row[k] for k in ('bytes','sha256')},'Build changed original source: '+row['path']
        for row in record['members']:assert pin(original/row['path'])=={k:row[k] for k in ('bytes','sha256')}
        run('linked-libraries',['ldd',binary])
        run('version',[binary,'-v'])
        if dependency_before is not None:
            dependency_after=dependency_pins();save(out/'REUSED-DEPENDENCIES-AFTER.json',dependency_after)
            assert dependency_before==dependency_after,'Retained dependency prefix changed'
        state.update(status='PASS_ORIGINAL_SMARTPCA721_PRIVATE_BUILD_ONLY',source_commit=COMMIT,source_files_unchanged=len(record['members']),
            binary=str(binary),binary_identity=pin(binary),dependency_prefix=str(prefix),requested_packages=packages,
            compile_flags=dict(CFLAGS=cflags,LDFLAGS=ldflags),original_scientific_source_modified=False,existing_runtime_prefixes_modified=False,
            original_analysis_executed=False,workflow_qualified=False)
    except FileNotFoundError as error:
        state.update(status='SKIP3_SMARTPCA_REFERENCE_PREREQUISITE_UNAVAILABLE',error=str(error),required_action='Supply/install the named permitted prerequisite; otherwise skip dependent analysis. Retry NEW output.')
        (out/'WARNINGS.md').write_text('SKIP3: '+state['error']+'\n'+state['required_action']+'\n',encoding='utf-8');save(out/'COMPLETED.json',state);return 3
    except Exception as error:
        state.update(status='FAIL2_SMARTPCA_REFERENCE_SETUP',error=str(error),required_action='Preserve this attempt and all logs. Fix the named setup/build/integrity cause and retry NEW output; no analysis is admitted.')
        (out/'WARNINGS.md').write_text('FAIL2: '+state['error']+'\n'+state['required_action']+'\n',encoding='utf-8');save(out/'COMPLETED.json',state);raise
    assert pin(installer)==installer_identity,'Installer source changed during setup'
    save(out/'COMPLETED.json',state);print(json.dumps(state,indent=2));return 0
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable source-integrity assertions')
    raise SystemExit(main())
