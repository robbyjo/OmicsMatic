# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile six unchanged original hifiasm editions through the owned Linux Java setup primitive."""
import argparse,concurrent.futures,json,subprocess,sys,tarfile,traceback
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--allow-install',action='store_true');p.add_argument('--editions',nargs='+');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_REFERENCE_SOURCE_BUILDS',editions={},analysis_admitted=False,implementation_credit=False,full_adapter_admitted=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-additive-hifiasm-foundation-only';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};resources=ROOT/'src/main/resources/org/jlinalg/omics/hifiasm-source';profile=json.loads((resources/'profiles.json').read_bytes());assert pin(resources/'profiles.json')==built['source_pins'][(resources/'profiles.json').relative_to(ROOT).as_posix()];editions=a.editions or list(profile['editions']);assert set(editions)<=set(profile['editions']);prepared={}
        for edition in editions:
            record=profile['editions'][edition];archive=resources/record['archive']['path'];assert pin(archive)=={k:record['archive'][k] for k in ['bytes','sha256']};home=out/edition;home.mkdir();source=home/'source';source.mkdir();observed={}
            with tarfile.open(archive,'r:gz') as tar:
                for member in tar.getmembers():
                    path=PurePosixPath(member.name);assert not path.is_absolute() and '..' not in path.parts
                    if member.isdir():continue
                    assert member.isfile() and len(path.parts)>1;name=PurePosixPath(*path.parts[1:]).as_posix();assert name in record['members'] and name not in observed and '\\' not in name and ':' not in name;file=source/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(tar.extractfile(member).read());observed[name]=pin(file)
            assert observed==record['members'];request=home/'request.json';save(request,dict(schema_version=1,mode='BUILD_REFERENCE_ONLY',allow_install=a.allow_install,edition=edition,source=linux(source),out=linux(home/'receipts'),compiler_threads=2));prepared[edition]=request
        # No compiler or package manager installation is attempted. Existing
        # compiler prerequisites are used only after explicit setup permission.
        def compile(edition):
            home=out/edition;command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.HifiasmSourceBuildCli','--request',linux(prepared[edition])];print('Starting original source build',edition,flush=True)
            with (home/'java.stdout').open('xb') as first,(home/'java.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=2100)
            save(home/'java.process.json',dict(command=command,exit_code=run.returncode));receipt=json.loads((home/'receipts/COMPLETED.json').read_bytes());print(edition,receipt['status'],flush=True);return edition,dict(exit_code=run.returncode,receipt=receipt)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            for edition,r in pool.map(compile,editions):state['editions'][edition]=r
        if not a.allow_install:assert all(r['exit_code']==3 and not r['receipt']['analysis_admitted'] for r in state['editions'].values());state.update(status='passed-forbidden-hifiasm-source-installation-skips',analysis_skipped=True)
        else:
            assert all(r['exit_code']==0 and r['receipt']['status']=='PASS_ORIGINAL_HIFIASM_SOURCE_COMPILATION_ONLY' and r['receipt']['original_source_members_unchanged'] and not r['receipt']['analysis_admitted'] for r in state['editions'].values());state.update(status='passed-requested-original-hifiasm-source-builds-only')
        assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},canonical_executable_unchanged=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar'))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original compilation checks')
    raise SystemExit(main())
