# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently bind complete retained original extractions; leave the failed preparation receipt untouched."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save,environment
from setup_rsubread_reference import inventory
from prepare_rsubread_r441 import rtools44_value


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['prepared','intake','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();prepared=a.prepared.resolve();intake=a.intake.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);prior=rtools44_value();state=dict(status='FAIL2_R441_RETAINED_EXTRACTION_BINDING',analysis_admitted=False,implementation_credit=False,installer_executed=False,prior_preparation_relabelled=False,whole_goal_complete=False)
    try:
        old=json.loads((prepared/'COMPLETED.json').read_bytes());assert old['status']=='FAIL2_RSUBREAD_R441_PREPARATION';intakeproof=json.loads((intake/'COMPLETED.json').read_bytes());assert intakeproof['status']=='passed-original-R441-and-Rtools44-download-only';roots={}
        for name,filename in [('R441','R-4.4.1-win.exe'),('Rtools44','rtools44-6459-6401.exe')]:
            home=prepared/name;assert json.loads((home/'extract.process.json').read_bytes())['exit_code']==0;archive=intake/filename;assert pin(archive)=={key:intakeproof['archives'][filename][key] for key in ['bytes','sha256']};manifest=home/'files.json';files=json.loads(manifest.read_bytes());assert inventory(home/'root')==files and set(files)==set(json.loads((home/'expected-files.json').read_bytes()));roots[name]=dict(root=(home/'root').relative_to(ROOT).as_posix(),manifest=dict(path=manifest.relative_to(ROOT).as_posix(),**pin(manifest)),file_count=len(files),archive=dict(path=archive.relative_to(ROOT).as_posix(),**pin(archive)))
        rroot=ROOT/roots['R441']['root'];tools=ROOT/roots['Rtools44']['root'];soft=tools/'x86_64-w64-mingw32.static.posix';rscript=rroot/'bin/x64/Rscript.exe';assert rscript.is_file();env=environment();env.update(R_HOME=rroot.as_posix(),R_CUSTOM_TOOLS_SOFT=soft.as_posix(),R_CUSTOM_TOOLS_PATH=str(soft/'bin')+';'+str(tools/'usr/bin'));env['PATH']=str(soft/'bin')+';'+str(tools/'usr/bin')+';'+env.get('PATH','');command=[str(rscript),'--vanilla','-e','stopifnot(getRversion()=="4.4.1");cat(R.version.string,"\\n");cat(R.home(),"\\n");cat(.Library,"\\n")'];run=subprocess.run(command,cwd=out,env=env,capture_output=True,timeout=60,creationflags=subprocess.CREATE_NO_WINDOW);(out/'R441-probe.stdout').write_bytes(run.stdout);(out/'R441-probe.stderr').write_bytes(run.stderr);save(out/'R441-probe.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0
        for name,file in [('gcc',soft/'bin/gcc.exe'),('make',tools/'usr/bin/make.exe')]:
            run=subprocess.run([str(file),'--version'],env=env,capture_output=True,timeout=60,creationflags=subprocess.CREATE_NO_WINDOW);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);assert run.returncode==0
        assert b'13.' in (out/'gcc.stdout').read_bytes() and rtools44_value()==prior;state.update(status='passed-workspace-only-original-R441-and-Rtools44-extracted',roots=roots,rscript=dict(path=rscript.relative_to(ROOT).as_posix(),**pin(rscript)),compiler_root=tools.relative_to(ROOT).as_posix(),toolchain=soft.relative_to(ROOT).as_posix(),user_rtools_environment_unchanged=True,system_libraries_modified=False,retained_failed_preparation=dict(path=(prepared/'COMPLETED.json').relative_to(ROOT).as_posix(),**pin(prepared/'COMPLETED.json')),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-file population or version checks')
    raise SystemExit(main())
