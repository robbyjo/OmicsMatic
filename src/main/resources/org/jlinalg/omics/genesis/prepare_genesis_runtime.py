# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pin an existing private GENESIS2.42 Windows runtime; no installation, downloads or data analysis."""
import argparse
import hashlib
import json
import os
import platform
import subprocess
import traceback
from pathlib import Path
ACTION=('SKIP dependent original GENESIS analysis when the exact permitted Windows R4.6.1/GENESIS2.42.0 runtime is unavailable '
    'or required installation is forbidden. Where permitted, prepare a NEW private library with tools/literature/setup_genesis_reference.py '
    'and explicit --allow-download --allow-install, then pass its COMPLETED.json to this helper. This helper installs nothing, '
    'runs no user-data analysis and changes no shared library. FAIL2 means preserve the attempt and resolve the named invalid '
    'setup/source/integrity cause before retrying NEW paths. Do not replace PC-AiR, PC-Relate or mixed models with another method, '
    'change versions or repair identities/values to suppress failure. A profile is not an admitted analysis or historical-paper reproduction.')


class Unavailable(RuntimeError):pass


def pin(path):
    digest=hashlib.sha256();size=0
    with path.open('rb') as source:
        for block in iter(lambda:source.read(1048576),b''):size+=len(block);digest.update(block)
    return dict(bytes=size,sha256=digest.hexdigest())


def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')


def closure(roots):
    result={};size=0
    for root in roots:
        if not root.is_dir():raise Unavailable('Required exact runtime root is unavailable: '+str(root))
        for path in sorted(root.rglob('*')):
            if path.is_symlink() or getattr(path,'is_junction',lambda:False)():raise ValueError('Linked runtime member is not admitted: '+str(path))
            if path.is_file():
                assert path.resolve()==path and len(result)<40000
                result[str(path)]=pin(path);size+=result[str(path)]['bytes'];assert size<=2000000000,'Complete runtime exceeds the explicit2GB profile ceiling'
    return result


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    setup=a.setup.resolve();out=a.out.resolve()
    if out.exists() or setup.is_relative_to(out):
        print('FAIL2: preserve previous files and choose a NEW output path separate from setup metadata. Required action: '+ACTION,flush=True);return 2
    out.mkdir(parents=True);state=dict(status='SKIP3_EXACT_GENESIS_RUNTIME_UNAVAILABLE',analysis_performed=False,downloads_or_installation=False,required_action=ACTION);code=3
    roots=[];before={};protected={}
    try:
        if not setup.is_file():raise Unavailable('Exact private GENESIS setup completion is missing')
        if platform.system()!='Windows' or platform.machine().lower() not in ['amd64','x86_64']:raise Unavailable('The original runtime profile requires Windows x86_64; no automatic WSL or software substitution')
        metadata=json.loads(setup.read_bytes());assert metadata['status']=='PASS_PRIVATE_ORIGINAL_GENESIS_RUNTIME_SETUP_ONLY' and metadata['version']=='2.42.0'
        rscript=Path(metadata['rscript']['path']).resolve();library=Path(metadata['private_library']).resolve()
        if not rscript.is_file() or not library.is_dir():raise Unavailable('Required exact Rscript or private GENESIS library is unavailable')
        assert pin(rscript)=={k:metadata['rscript'][k] for k in ['bytes','sha256']},'Rscript bytes differ from the admitted setup'
        installed={path.relative_to(library).as_posix():pin(path) for path in library.rglob('*') if path.is_file()}
        missing=set(metadata['installed_files'])-set(installed)
        if missing:raise Unavailable('Required exact GENESIS package files are missing: '+', '.join(sorted(missing)[:5]))
        assert installed==metadata['installed_files'],'Complete private package population differs; no automatic repair'
        r_home=rscript.parent.parent;roots=[r_home,library]
        assert (r_home/'library/base').is_dir() and not r_home.is_relative_to(library) and not library.is_relative_to(r_home)
        assert all(not out.is_relative_to(root) and not root.is_relative_to(out) for root in roots),'Output must be separate from closed runtime roots'
        before=closure(roots);protected={str(setup):pin(setup),str(Path(__file__).resolve()):pin(Path(__file__))}
        program=['.libPaths(c('+json.dumps(library.as_posix())+',file.path(R.home(),"library")),include.site=FALSE)',
            'stopifnot(getRversion()=="4.6.1",.Machine$longdouble.digits==64L,normalizePath(R.home(),winslash="/")=='+json.dumps(r_home.as_posix())+')']
        for package,version in [('GENESIS','2.42.0'),('GWASTools','1.58.0'),('SNPRelate','1.46.0'),('gdsfmt','1.48.2'),('RhpcBLASctl','0.23-42')]:
            program+=['stopifnot(requireNamespace("'+package+'",quietly=TRUE),dirname(find.package("'+package+'"))==normalizePath('+json.dumps(library.as_posix())+',winslash="/"),utils::packageDescription("'+package+'",fields="Version")=="'+version+'")']
        program+=['stopifnot(all(c("pcair","pcrelate","pcrelateToMatrix","fitNullModel","assocTestSingle") %in% getNamespaceExports("GENESIS")))',
            'cat("EXACT_PRIVATE_GENESIS_RUNTIME_PROFILE_ONLY_NO_ANALYSIS\\n")']
        script=out/'probe.R';script.write_text('\n'.join(program)+'\n',encoding='utf8',newline='\n');temp=out/'tmp';temp.mkdir()
        environment=dict(os.environ,LC_ALL='C',LANG='C',R_DEFAULT_PACKAGES='base',R_LIBS_USER=str(library),R_LIBS_SITE='',R_HOME=str(r_home),TMPDIR=str(temp))
        command=[str(rscript),'--vanilla',str(script)]
        try:process=subprocess.Popen(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        except OSError as error:raise Unavailable('Exact Rscript could not be launched: '+repr(error)) from error
        try:stdout,stderr=process.communicate(timeout=180)
        except subprocess.TimeoutExpired:process.kill();process.communicate();raise
        (out/'probe.stdout').write_bytes(stdout);(out/'probe.stderr').write_bytes(stderr);save(out/'probe.process.json',dict(command=command,exit_code=process.returncode))
        assert process.returncode==0 and b'EXACT_PRIVATE_GENESIS_RUNTIME_PROFILE_ONLY_NO_ANALYSIS' in stdout,'Original exact-runtime introspection failed; inspect retained stderr'
        assert closure(roots)==before,'Supplied runtime changed during profiling'
        profile=dict(schema_version=1,tool='GENESIS',version='2.42.0',platform='windows',r_version='4.6.1',r_longdouble_significand_bits=64,
            rscript=str(rscript),r_home=str(r_home),private_library=str(library),files=before,windows_system_DLLs_independently_pinned=False)
        save(out/'runtime-profile.json',profile)
        state.update(status='PASS_EXACT_PRIVATE_GENESIS242_RUNTIME_PROFILE_ONLY',profile=pin(out/'runtime-profile.json'),source_setup_pin=pin(setup),runtime_files=len(before),runtime_bytes=sum(v['bytes'] for v in before.values()),producer=pin(Path(__file__)));code=0
    except Unavailable as error:state['cause']=repr(error)
    except Exception as error:
        state.update(status='FAIL2_INVALID_GENESIS_RUNTIME_PROFILE',cause=repr(error));code=2
        (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        try:
            assert all(pin(Path(n))==value for n,value in protected.items()),'Protected profiling metadata/source changed'
            if before:assert closure(roots)==before,'Supplied R distribution/private library changed'
        except Exception as error:state.update(status='FAIL2_GENESIS_RUNTIME_PRESERVATION',cause=repr(error));code=2
        (out/'WARNINGS.md').write_text('# '+state['status']+'\n\nNamed cause: '+state.get('cause','None; runtime profiling alone admits no analysis.')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('cause','')[:2500],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact complete runtime/library or protected-source checks')
    raise SystemExit(main())
