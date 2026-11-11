# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install exact official Monocle2 into a NEW private library; no user analysis is admitted."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tarfile
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');p.add_argument('--additional-library',type=Path,action='append',default=[]);p.add_argument('--dependency-overrides',type=Path);a=p.parse_args()
    base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    setup=base/'reference-setup-3';extra=[path.resolve(strict=True) for path in a.additional_library]
    assert all(path.is_relative_to(base) and path.is_dir() for path in extra)
    libraries=[*extra,setup/'R-lib',*map(Path,json.loads((setup/'STARTED.json').read_bytes())['libraries'])]
    before=inventory(libraries);save(out/'BASES-BEFORE.json',before)
    action='Preserve this attempt and read installer stdout/stderr and R-RESULT.json. Where permitted, provision the named dependency/compiler cause into a NEW private library and retry; otherwise skip dependent Monocle2 analysis. Do not update shared libraries or substitute Monocle3. Setup alone admits no analysis.'
    state=dict(status='SKIP3_MONOCLE2_REFERENCE_UNAVAILABLE',analysis_admitted=False,required_action=action);bindings={};process=None
    try:
        if not a.allow_install:raise RuntimeError('Installation was not authorized; dependent analysis skipped')
        intake=base/'monocle2-source-intake-1';receipt=json.loads((intake/'SOURCE.json').read_bytes());assert receipt['version']=='2.40.0'
        archive=intake/'monocle_2.40.0.tar.gz';assert pin(archive)=={k:receipt['archive'][k] for k in ['bytes','sha256']}
        stage=out/'work-source/monocle';stage.mkdir(parents=True)
        with tarfile.open(archive,'r:gz') as tar:
            for name,wanted in receipt['source_members'].items():
                assert pin(intake/'source'/name)==wanted
                member=tar.getmember('monocle/'+name);assert member.isfile() and not member.issym() and not member.islnk()
                target=(stage/name).resolve();assert target.is_relative_to(stage);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(tar.extractfile(member).read());assert pin(target)==wanted
        override_inputs=[];declared={}
        if a.dependency_overrides:
            original_overrides=a.dependency_overrides.resolve(strict=True);assert original_overrides.is_relative_to(base)
            declared=json.loads(original_overrides.read_bytes());assert isinstance(declared,dict);override_inputs.append(original_overrides)
            for name,row in declared.items():
                assert row['package']==name and row['type']=='source'
                for key in ['archive','description']:
                    path=Path(row[key]).resolve(strict=True);assert path.is_relative_to(base) and path.is_file();override_inputs.append(path)
        overrides=out/'OVERRIDES.json';save(overrides,declared)
        health=json.loads((base/'compiler-health-3/HEALTH.json').read_bytes());assert health['status']=='compiled-and-loaded-private-CXX20-R-extension-without-shell-warnings'
        compiler=Path(health['compiler_root']);host=Path(health['make_root']);script=ROOT/'tools/literature/install_monocle2_reference.R'
        for path in [Path(__file__).resolve(),script,intake/'SOURCE.json',base/'compiler-health-3/HEALTH.json',*override_inputs]:
            bindings[path.relative_to(ROOT).as_posix()]=pin(path);copy=out/'source-snapshot'/path.relative_to(ROOT);copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes(path.read_bytes())
        save(out/'STARTED.json',dict(source_pins=bindings,libraries=list(map(str,libraries)),original_source=receipt,analysis_admitted=False))
        env=dict(os.environ,LC_ALL='C',LANG='C',LANGUAGE='en',MSYSTEM='MSYS',R_CUSTOM_TOOLS_SOFT=compiler.as_posix(),R_CUSTOM_TOOLS_PATH=(compiler/'bin').as_posix()+';'+host.as_posix(),R_MAKEVARS_USER=health['Makevars'],TMPDIR=str(host.parent.parent/'tmp'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        env['PATH']=str(compiler/'bin')+os.pathsep+str(host)+os.pathsep+env['PATH']
        command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out/'R-lib'),str(out),str(stage),str(overrides),*map(str,libraries)]
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:
            process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL);save(out/'PROCESS-STARTED.json',dict(pid=process.pid,command=command))
            code=process.wait(timeout=2400)
        save(out/'process.json',dict(command=command,exit_code=code,pid=process.pid))
        if not (out/'R-RESULT.json').is_file():raise RuntimeError('R installer exited '+str(code)+' before its terminal report; inspect installer.stderr: '+(out/'installer.stderr').read_text(encoding='utf8',errors='replace')[-4000:])
        r=json.loads((out/'R-RESULT.json').read_bytes());state.update(r)
        assert code==0 and r['status']=='INSTALLED_ORIGINAL_MONOCLE2_REFERENCE_ONLY',r
    except Exception as failure:state.update(status='SKIP3_MONOCLE2_REFERENCE_UNAVAILABLE',cause=repr(failure))
    finally:
        if process is not None and process.poll() is None:
            cleanup=subprocess.run(['taskkill.exe','/PID',str(process.pid),'/T','/F'],capture_output=True,timeout=30)
            (out/'owned-cleanup.stdout').write_bytes(cleanup.stdout);(out/'owned-cleanup.stderr').write_bytes(cleanup.stderr);process.wait(timeout=30)
    after=inventory(libraries);save(out/'BASES-AFTER.json',after);assert before==after,'Existing runtime library changed'
    assert all(pin(ROOT/n)==v for n,v in bindings.items())
    state.update(preexisting_libraries_unchanged=True,analysis_admitted=False,required_action=action);save(out/'RESULT.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+str(state.get('cause',state.get('detail','')))+'\n\n'+'\n\n'.join(state.get('warnings',[]))+'\n\nREQUIRED ACTION: '+action+'\n',encoding='utf8',newline='\n')
    print(json.dumps(state),flush=True)
    return 0 if state['status']=='INSTALLED_ORIGINAL_MONOCLE2_REFERENCE_ONLY' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/library preservation checks')
    raise SystemExit(main())
