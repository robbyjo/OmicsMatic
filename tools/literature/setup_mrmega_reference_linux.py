# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile an unchanged private official MR-MEGA0.2 reference; admit no analysis."""
import argparse,hashlib,json,os,resource,signal,stat,subprocess,sys,time,traceback,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_sniffles_reference_linux import ROOT,pin,save,inventory,members

ARCHIVE_SHA256='e91eb5bdba0a398619e6cd98c351ea72d94e09d6f5a1d25bb7da8a66da938ffe'


def owned(command,home,cwd,seconds=180):
    home.mkdir();save(home/'command.json',command);child=None;started=time.monotonic();error=None
    environment=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',HOME=str(home),TMPDIR=str(home),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1')
    save(home/'environment.json',environment)
    def limits():
        for kind,value in [(resource.RLIMIT_AS,4294967296),(resource.RLIMIT_CPU,seconds),(resource.RLIMIT_FSIZE,67108864),(resource.RLIMIT_NOFILE,256),(resource.RLIMIT_CORE,0)]:resource.setrlimit(kind,(value,value))
    try:
        with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:
            child=subprocess.Popen(command,cwd=cwd,env=environment,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True,preexec_fn=limits)
            save(home/'STARTED.json',dict(owned_group=child.pid,command=command))
            while child.poll() is None:
                if time.monotonic()-started>seconds+15:raise TimeoutError('Original reference wall-time capacity exceeded')
                time.sleep(.025)
    except BaseException as failure:
        error=repr(failure);raise
    finally:
        if child is not None:
            active=lambda:[row for row in members(child.pid) if row['state']!='Z']
            if child.poll() is None or active():
                try:os.killpg(child.pid,signal.SIGTERM)
                except ProcessLookupError:pass
                deadline=time.monotonic()+3
                while active() and time.monotonic()<deadline:time.sleep(.025)
                if active():
                    try:os.killpg(child.pid,signal.SIGKILL)
                    except ProcessLookupError:pass
                child.wait(timeout=5)
            live=active();save(home/'process.json',dict(exit_code=child.returncode,error=error,elapsed_seconds=time.monotonic()-started,owned_live_after_cleanup=live))
            assert not live,'Owned reference workers remain; inspect exact process receipt before continuing'
    return child.returncode


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    state=dict(status='SKIP3_MRMEGA_ORIGINAL_REFERENCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        assert a.allow_install,'Private compilation/install permission absent; dependent original analysis skipped'
        assert sys.platform=='linux' and Path('/usr/bin/make').is_file() and Path('/usr/bin/g++').is_file(),'Permitted original Linux compiler/make runtime unavailable; skip dependent original analysis'
        intake=a.intake.resolve();archive=intake/'MR-MEGA_v0.2.zip';assert pin(archive)['sha256']==ARCHIVE_SHA256,'Official archive differs; never refresh an old scientific hash to accept another source'
        expected=json.loads((intake/'MEMBERS.json').read_bytes());assert len(expected)==100 and inventory(intake/'original')==expected
        source=out/'source';source.mkdir()
        with zipfile.ZipFile(archive) as opened:
            assert set(opened.namelist())==set(expected) and len(opened.namelist())==100
            for name,value in expected.items():
                raw=opened.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
                target=source/name;assert target.resolve().is_relative_to(source);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
        before={name:pin(source/name) for name in expected};state['status']='FAIL2_MRMEGA_PRIVATE_ORIGINAL_BUILD'
        assert owned(['/usr/bin/make'],out/'compile',source)==0,'Read complete original build stdout/stderr; no scientific source patch or analysis admitted'
        assert all(pin(source/name)==value for name,value in before.items()) and inventory(intake/'original')==expected
        executable=source/'MR-MEGA';assert executable.is_file() and owned([str(executable),'--version'],out/'version',out)==0
        version=(out/'version/stdout').read_text();assert '0.2' in version
        save(out/'ORIGINAL-SOURCE-FILES.json',before)
        state.update(status='PASS_PRIVATE_UNMODIFIED_ORIGINAL_MRMEGA_REFERENCE_BUILD_ONLY',original_version_output=version,
            original_archive=pin(archive),original_source_files=pin(out/'ORIGINAL-SOURCE-FILES.json'),
            executable=dict(path=executable.relative_to(ROOT).as_posix(),**pin(executable)),original_sources_unchanged=True,
            license_scope='Upstream archive contains mixed GPL2-or-later, MIT and BSD-style/disclaimer headers. This is a private unchanged reference build; no third-party source/binary redistribution or new permission grant is asserted.',
            source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in [Path(__file__).resolve(),ROOT/'tools/literature/setup_sniffles_reference_linux.py']})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Private original reference built; no analysis or Java implementation validated.')+'\n\nACTION: preserve sources, failed attempts and complete logs. If permitted compiler/runtime prerequisites are absent or installation is forbidden, skip dependent original analysis. Correct the named problem and retry NEW paths; never refresh known source hashes or substitute another edition.\n',encoding='utf8')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/population/owned-process checks')
    raise SystemExit(main())
