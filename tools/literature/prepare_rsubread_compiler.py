# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Extract official compiler files in a NEW workspace directory; never run the installer or modify shared configuration."""
import argparse,hashlib,json,os,re,subprocess,traceback,winreg,zipfile
from pathlib import Path,PureWindowsPath
from setup_deseq2_reference import ROOT,pin,save


def global_rtools_value():
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER,'Environment') as key:return winreg.QueryValueEx(key,'RTOOLS45_HOME')
    except FileNotFoundError:return None


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);prior=global_rtools_value();state=dict(status='SKIP3_RSUBREAD_COMPILER_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,installer_executed=False,whole_goal_complete=False)
    try:
        if not a.allow_install:raise PermissionError('Compiler preparation forbidden; skip dependent source compilation')
        intake=a.intake.resolve();receipt=json.loads((intake/'COMPLETED.json').read_bytes());assert receipt['status']=='passed-official-Rtools45-installer-download-only' and not receipt['executed'];installer=intake/'rtools45-6768-6492.exe';assert pin(installer)=={k:receipt[k] for k in ['bytes','sha256']};download=json.loads((intake/'innounp/DOWNLOAD.json').read_bytes());archive=intake/'innounp/innounp-2.zip';assert pin(archive)=={k:download[k] for k in ['bytes','sha256']};unpacker=intake/'innounp/innounp.exe'
        with zipfile.ZipFile(archive) as z:assert unpacker.read_bytes()==z.read('innounp.exe')
        listing=intake/'innounp-list.stdout';listed=json.loads((intake/'innounp-list.process.json').read_bytes());assert listed['exit_code']==0 and listed['command'][-1]==str(installer);rows=[row.strip() for row in listing.read_bytes().decode('utf8').splitlines() if row.strip()];names=[]
        for row in rows:
            if row.startswith('{app}\\'):
                relative=row[len('{app}\\'):];path=PureWindowsPath(relative);assert not path.is_absolute() and not path.drive and '..' not in path.parts and ':' not in relative and not relative.endswith(('\\','/'));names.append(relative)
            else:assert row in ['Inno Setup archive:           rtools45-6768-6492.exe','Inno Setup version detected:  6.5.2 (Unicode)','install_script.iss']
        assert 10000<len(names)<100000 and len({name.casefold() for name in names})==len(names);save(out/'expected-file-population.json',names);target=out/'compiler';assert not target.exists();command=[str(unpacker),'-x','-b','-n','-o','-q','-u','-c{app}','-d'+str(target),str(installer),'{app}\\*']
        with (out/'extract.stdout').open('xb') as first,(out/'extract.stderr').open('xb') as second:run=subprocess.run(command,cwd=out,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1800,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'extract.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Unpacker failed; preserve full diagnostics and skip source compilation';actual={q.relative_to(target).as_posix():pin(q) for q in target.rglob('*') if q.is_file()};expected={PureWindowsPath(name).as_posix() for name in names};assert set(actual)==expected,('Extracted compiler population differs',len(actual),len(expected));assert all(not q.is_symlink() for q in target.rglob('*'));assert sum(v['bytes'] for v in actual.values())<15000000000;toolchain=target/'x86_64-w64-mingw32.static.posix';gcc=toolchain/'bin/gcc.exe';make=target/'usr/bin/make.exe';shell=target/'usr/bin/sh.exe';assert gcc.is_file() and make.is_file() and shell.is_file();env=dict(os.environ);env['PATH']=str(toolchain/'bin')+';'+str(target/'usr/bin')+';'+env.get('PATH','');probes={}
        for name,exe in [('gcc',gcc),('make',make)]:
            run=subprocess.run([str(exe),'--version'],env=env,capture_output=True,timeout=60,creationflags=subprocess.CREATE_NO_WINDOW);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);assert run.returncode==0;probes[name]=dict(path=exe.relative_to(ROOT).as_posix(),**pin(exe))
        assert b'14.3.0' in (out/'gcc.stdout').read_bytes() and global_rtools_value()==prior;save(out/'compiler-files.json',actual);state.update(status='passed-workspace-only-official-Rtools45-files-extracted-and-probed',compiler_root=target.relative_to(ROOT).as_posix(),toolchain=toolchain.relative_to(ROOT).as_posix(),extracted_files=len(actual),extracted_bytes=sum(v['bytes'] for v in actual.values()),compiler_manifest=pin(out/'compiler-files.json'),probes=probes,source_installer=dict(path=installer.relative_to(ROOT).as_posix(),**pin(installer)),unpacker=dict(path=unpacker.relative_to(ROOT).as_posix(),**pin(unpacker)),user_rtools_environment_unchanged=True,system_libraries_modified=False,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except PermissionError as error:state['error']=str(error)
    except Exception as error:state.update(status='FAIL2_RSUBREAD_COMPILER_PREPARATION',error=repr(error));(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    state['user_rtools_environment_unchanged']=global_rtools_value()==prior;state['required_action']='Use only the exact permitted workspace compiler and explicit process-local paths for a NEW source build. If unavailable or forbidden, skip dependent source compilation; do not substitute package versions or run the installer to hide extraction failures.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Compiler preparation only\n\n'+state['status']+'\n\n'+state.get('error','No Rsubread source build or analysis has run.')+'\n\nRequired action: '+state['required_action']+'\n',encoding='utf8');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable compiler/source/path/population gates')
    raise SystemExit(main())
