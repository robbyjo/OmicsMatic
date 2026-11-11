# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Extract original R4.4.1 and its GCC13 tool family into NEW workspace-only roots, without running installers."""
import argparse,concurrent.futures,json,os,subprocess,traceback,winreg,zipfile
from pathlib import Path,PureWindowsPath
from setup_deseq2_reference import ROOT,pin,save,environment
from setup_rsubread_reference import inventory


def rtools44_value():
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER,'Environment') as key:return winreg.QueryValueEx(key,'RTOOLS44_HOME')
    except FileNotFoundError:return None


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['intake','unpacker','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);prior=rtools44_value();state=dict(status='SKIP3_RSUBREAD_R441_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,installer_executed=False,whole_goal_complete=False)
    try:
        if not a.allow_install:raise PermissionError('Private reference preparation forbidden; skip dependent R4.4.1 analysis')
        intake=a.intake.resolve();receipt=json.loads((intake/'COMPLETED.json').read_bytes());assert receipt['status']=='passed-original-R441-and-Rtools44-download-only';unpack=a.unpacker.resolve();metadata=json.loads((unpack/'DOWNLOAD.json').read_bytes());assert pin(unpack/'innounp-2.zip')=={key:metadata[key] for key in ['bytes','sha256']};exe=unpack/'innounp.exe'
        with zipfile.ZipFile(unpack/'innounp-2.zip') as archive:assert exe.read_bytes()==archive.read('innounp.exe')
        def extract(item):
            name,filename=item;home=out/name;home.mkdir();archive=intake/filename;assert pin(archive)=={key:receipt['archives'][filename][key] for key in ['bytes','sha256']};assert not receipt['archives'][filename]['installer_executed'];command=[str(exe),'-s','-b','-n','-o','-h','-u',str(archive)];run=subprocess.run(command,cwd=home,capture_output=True,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW);(home/'list.stdout').write_bytes(run.stdout);(home/'list.stderr').write_bytes(run.stderr);save(home/'list.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0;rows=[row.strip() for row in run.stdout.decode('utf8').splitlines() if row.strip()];names=[]
            for row in rows:
                if row.startswith('{app}\\'):
                    rel=row[6:];path=PureWindowsPath(rel);assert not path.is_absolute() and not path.drive and '..' not in path.parts and ':' not in rel;names.append(path.as_posix())
                else:assert row=='install_script.iss' or row.startswith('Inno Setup archive:') or row.startswith('Inno Setup version detected:'),row
            assert 100<len(names)<100000 and len({name.casefold() for name in names})==len(names);save(home/'expected-files.json',names);target=home/'root';assert not target.exists();command=[str(exe),'-x','-b','-n','-o','-q','-u','-c{app}','-d'+str(target),str(archive),'{app}\\*']
            with (home/'extract.stdout').open('xb') as first,(home/'extract.stderr').open('xb') as second:run=subprocess.run(command,cwd=home,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1800,creationflags=subprocess.CREATE_NO_WINDOW)
            save(home/'extract.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0;actual=inventory(target);assert set(actual)==set(names),('Original extraction population differs',name,len(actual),len(names));assert all(not q.is_symlink() for q in target.rglob('*'));assert sum(value['bytes'] for value in actual.values())<15000000000;save(home/'files.json',actual);return name,dict(root=target.relative_to(ROOT).as_posix(),manifest=dict(path=(home/'files.json').relative_to(ROOT).as_posix(),**pin(home/'files.json')),file_count=len(actual),archive=dict(path=archive.relative_to(ROOT).as_posix(),**pin(archive)))
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:roots=dict(pool.map(extract,[('R441','R-4.4.1-win.exe'),('Rtools44','rtools44-6459-6401.exe')]))
        rroot=ROOT/roots['R441']['root'];tools=ROOT/roots['Rtools44']['root'];soft=tools/'x86_64-w64-mingw32.static.posix';env=environment();env.update(R_HOME=rroot.as_posix(),R_CUSTOM_TOOLS_SOFT=soft.as_posix(),R_CUSTOM_TOOLS_PATH=str(soft/'bin')+';'+str(tools/'usr/bin'));env['PATH']=str(soft/'bin')+';'+str(tools/'usr/bin')+';'+env.get('PATH','');rscript=rroot/'bin/x64/Rscript.exe';assert rscript.is_file();run=subprocess.run([str(rscript),'--vanilla','-e','stopifnot(getRversion()=="4.4.1");cat(R.version.string,"\\n");cat(R.home(),"\\n");cat(.Library,"\\n")'],cwd=out,env=env,capture_output=True,timeout=60,creationflags=subprocess.CREATE_NO_WINDOW);(out/'R441-probe.stdout').write_bytes(run.stdout);(out/'R441-probe.stderr').write_bytes(run.stderr);assert run.returncode==0,'Portable original R4.4.1 unavailable; no source compilation admitted'
        for name,file in [('gcc',soft/'bin/gcc.exe'),('make',tools/'usr/bin/make.exe')]:
            run=subprocess.run([str(file),'--version'],env=env,capture_output=True,timeout=60,creationflags=subprocess.CREATE_NO_WINDOW);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);assert run.returncode==0
        assert b'13.' in (out/'gcc.stdout').read_bytes() and rtools44_value()==prior;state.update(status='passed-workspace-only-original-R441-and-Rtools44-extracted',roots=roots,rscript=dict(path=rscript.relative_to(ROOT).as_posix(),**pin(rscript)),compiler_root=tools.relative_to(ROOT).as_posix(),toolchain=soft.relative_to(ROOT).as_posix(),user_rtools_environment_unchanged=True,system_libraries_modified=False,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except PermissionError as error:state['error']=str(error)
    except Exception as error:state.update(status='FAIL2_RSUBREAD_R441_PREPARATION',error=repr(error));(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    state['user_rtools_environment_unchanged']=rtools44_value()==prior;state['required_action']='Use these exact private roots only for a separately admitted source build and runtime. If preparation or loading fails, skip dependent R4.4.1/2.23.2 analysis and retain all diagnostics; do not substitute versions or change upstream source.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# R4.4.1 preparation only\n\n'+state['status']+'\n\n'+state.get('error','No source build or scientific analysis has run.')+'\n\nRequired action: '+state['required_action']+'\n',encoding='utf8');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original archive/population/version admission')
    raise SystemExit(main())
