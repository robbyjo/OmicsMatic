# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pin an existing permitted private SNPRelate1.46 Windows runtime; no downloads, installations or analysis."""
import argparse
import hashlib
import json
import os
import platform
import subprocess
import traceback
from pathlib import Path
ACTION=('SKIP dependent original SNPRelate analyses if exact R4.6.1/SNPRelate1.46.0/gdsfmt1.48.2/RhpcBLASctl0.23-42 '
    'cannot be supplied or installation is forbidden. In an OmicsMatic source checkout, tools/literature/setup_snprelate_reference.py '
    'can download/install official Windows binaries into a NEW private workspace library with explicit --allow-download --allow-install. '
    'Then supply its COMPLETED.json to this helper. This helper never installs or changes shared libraries. '
    'Do not switch host, versions, LD estimators, pruning semantics or genotype/ID data to suppress a failure.')


def pin(path):
    digest=hashlib.sha256();count=0
    with path.open('rb') as stream:
        for raw in iter(lambda:stream.read(1048576),b''):count+=len(raw);digest.update(raw)
    return dict(bytes=count,sha256=digest.hexdigest())


def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')


def closure(roots):
    result={};total=0
    for root in roots:
        for f in sorted(root.rglob('*')):
            if f.is_symlink() or getattr(f,'is_junction',lambda:False)():raise ValueError('Linked runtime member: '+str(f))
            if f.is_file():
                assert f.resolve()==f and len(result)<20000
                result[str(f)]=pin(f);total+=result[str(f)]['bytes'];assert total<=2000000000
    return result


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();source=a.setup.resolve();out=a.out.resolve()
    assert not out.exists() and not source.is_relative_to(out)
    out.mkdir(parents=True);state=dict(status='SKIP3_SNPRELATE_EXACT_PRIVATE_RUNTIME_UNAVAILABLE',analysis_performed=False,required_action=ACTION);code=3
    try:
        assert source.is_file(),'Required exact private runtime setup is missing; skip dependent analysis and follow REQUIRED ACTION'
        assert platform.system()=='Windows' and platform.machine().lower() in ['amd64','x86_64'],'Require explicitly selected Windows x86_64 host; no automatic WSL switch'
        metadata=json.loads(source.read_bytes());assert metadata['status']=='PASS_PRIVATE_ORIGINAL_SNPRELATE146_GDSFMT_RUNTIME_SETUP_ONLY'
        rscript=Path(metadata['rscript']['path']).resolve();library=Path(metadata['private_library']).resolve()
        assert pin(rscript)=={key:metadata['rscript'][key] for key in ['bytes','sha256']}
        installed={f.relative_to(library).as_posix():pin(f) for f in library.rglob('*') if f.is_file()}
        assert installed==metadata['installed_files'],'Exact private package population differs from the original setup'
        r_home=rscript.parent.parent
        assert (r_home/'library/base').is_dir() and not out.is_relative_to(r_home) and not out.is_relative_to(library)
        roots=[r_home,library];assert not r_home.is_relative_to(library) and not library.is_relative_to(r_home)
        before=closure(roots)
        program=['.libPaths(c('+json.dumps(library.as_posix())+',file.path(R.home(),"library")),include.site=FALSE)',
            'stopifnot(getRversion()=="4.6.1",normalizePath(R.home(),winslash="/")=='+json.dumps(r_home.as_posix())+')']
        for package,version in [('gdsfmt','1.48.2'),('RhpcBLASctl','0.23-42'),('SNPRelate','1.46.0')]:
            program+=['stopifnot(requireNamespace("'+package+'",quietly=TRUE),dirname(find.package("'+package+'"))==normalizePath('+json.dumps(library.as_posix())+',winslash="/"),utils::packageDescription("'+package+'",fields="Version")=="'+version+'")']
        program+=['stopifnot(all(c("snpgdsIBDKING","snpgdsLDpruning","snpgdsGRM") %in% getNamespaceExports("SNPRelate")))',
            'cat("EXACT_PRIVATE_RUNTIME_PROFILE_ONLY_NO_ANALYSIS\\n")']
        script=out/'probe.R';script.write_text('\n'.join(program)+'\n',encoding='utf8',newline='\n')
        temp=out/'tmp';temp.mkdir()
        environment=dict(os.environ,LC_ALL='C',LANG='C',R_DEFAULT_PACKAGES='base',R_LIBS_USER=str(library),R_LIBS_SITE='',R_HOME=str(r_home),TMPDIR=str(temp))
        command=[str(rscript),'--vanilla',str(script)]
        result=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
        (out/'probe.stdout').write_bytes(result.stdout);(out/'probe.stderr').write_bytes(result.stderr)
        save(out/'probe.process.json',dict(command=command,exit_code=result.returncode));assert result.returncode==0,result.stderr[-2500:]
        assert b'EXACT_PRIVATE_RUNTIME_PROFILE_ONLY_NO_ANALYSIS' in result.stdout and closure(roots)==before
        profile=dict(schema_version=1,tool='SNPRelate',version='1.46.0',platform='windows',r_version='4.6.1',rscript=str(rscript),
            r_home=str(r_home),private_library=str(library),files=before,windows_system_DLLs_independently_pinned=False)
        save(out/'runtime-profile.json',profile)
        state.update(status='PASS_EXACT_PRIVATE_SNPRELATE146_RUNTIME_PROFILE_ONLY',profile=pin(out/'runtime-profile.json'),
            source_setup_pin=pin(source),runtime_files=len(before),runtime_bytes=sum(v['bytes'] for v in before.values()),producer=pin(Path(__file__)))
        code=0
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('cause','')[:2000],ACTION,flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact runtime/library closure checks')
    raise SystemExit(main())
