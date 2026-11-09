# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install an unchanged original CellChat candidate in a new private R library."""
import argparse,json,os,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save
from setup_chipseeker_reference import inventory

BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--source-label',default='2.2.0-2025-release');p.add_argument('--allow-install',action='store_true');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    libraries=[ROOT/'build/literature-review/monocle-runtime/reference-setup-3/R-lib',*[Path(p) for p in json.loads((ROOT/'build/literature-review/monocle-runtime/reference-setup-3/STARTED.json').read_bytes())['libraries']]]
    libraries=list(dict.fromkeys(p.resolve() for p in libraries));before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    action=('Read WARNINGS.md, installer.stdout/stderr and R-RESULT.json. If installation is forbidden or an exact dependency/compiler cannot be supplied, SKIP dependent CellChat analysis. Where permitted, supply the named cause in a NEW private library and retry NEW output paths; do not alter existing libraries, patch original CellChat source or substitute editions. Package setup/constructor health alone admits no user analysis.')
    state=dict(status='SKIP3_CELLCHAT_REFERENCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,required_action=action,installation_authorized=a.allow_install)
    bindings={};original_members={};source=None
    try:
        if not a.allow_install:raise RuntimeError('Installation is not authorized; dependent CellChat analysis skipped')
        intake=BASE/'source-intake-1';receipt=json.loads((intake/'COMPLETED.json').read_bytes());entry=next(r for r in receipt['candidates'] if r['label']==a.source_label)
        source=intake/a.source_label/'source';members=intake/a.source_label/'original-members.json';assert pin(members)==entry['members'];original_members=json.loads(members.read_bytes())
        assert all(pin(source/n)==v for n,v in original_members.items())
        health_path=ROOT/'build/literature-review/monocle-runtime/compiler-health-3/HEALTH.json';health=json.loads(health_path.read_bytes());assert health['status']=='compiled-and-loaded-private-CXX20-R-extension-without-shell-warnings'
        compiler=Path(health['compiler_root']);host=Path(health['make_root']);makevars=Path(health['Makevars'])
        for file in [compiler/'bin/g++.exe',host/'make.exe',host/'bash.exe',makevars]:assert file.is_file()
        script=ROOT/'tools/literature/install_cellchat_reference.R';overrides=out/'DECLARED-OVERRIDES.json';save(overrides,{})
        for file in [Path(__file__).resolve(),script,members,health_path,makevars,compiler/'bin/g++.exe',host/'make.exe',host/'bash.exe']:
            bindings[file.relative_to(ROOT).as_posix()]=pin(file)
        for file in [Path(__file__).resolve(),script]:
            target=out/'source-snapshot'/file.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(file.read_bytes())
        # The author archive includes Linux .o/.so files. Compile only a new owned
        # copy with --preclean; never clean or modify the captured original tree.
        work=out/'work-source';shutil.copytree(source,work);assert all(pin(work/n)==v for n,v in original_members.items())
        save(out/'STARTED.json',dict(source_label=a.source_label,original_candidate=entry,source_pins=bindings,libraries=list(map(str,libraries)),compiler_health=health,original_members=original_members,source_copy_byte_identical=True,analysis_admitted=False))
        env=environment();env.update(MSYSTEM='MSYS',R_CUSTOM_TOOLS_SOFT=compiler.as_posix(),R_CUSTOM_TOOLS_PATH=(compiler/'bin').as_posix()+';'+host.as_posix(),R_MAKEVARS_USER=makevars.as_posix(),TMPDIR=str(out/'tmp'))
        (out/'tmp').mkdir();env['PATH']=str(compiler/'bin')+os.pathsep+str(host)+os.pathsep+env['PATH']
        command=[str(RSCRIPT),'--vanilla',str(script),str(out/'R-lib'),str(out),str(work),str(overrides),entry['declared_version'],*map(str,libraries)]
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:
            run=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=3600)
        save(out/'process.json',dict(command=command,exit_code=run.returncode));report=json.loads((out/'R-RESULT.json').read_bytes());state.update(report)
        if run.returncode or report['status']!='INSTALLED_ORIGINAL_CELLCHAT_REFERENCE_ONLY':raise RuntimeError(report.get('detail','Original CellChat package/DLL/constructor health did not pass'))
        unchanged={n:v for n,v in original_members.items() if n.endswith(('.R','.cpp','.h','.rda')) or n in ['DESCRIPTION','NAMESPACE']}
        assert all(pin(work/n)==v for n,v in unchanged.items()),'A package code/data input changed during compilation'
        state.update(source_label=a.source_label,original_candidate=entry,package_source_data_unchanged=True,downloads={p.name:pin(p) for p in (out/'downloads').iterdir() if p.is_file()},private_library_inventory=inventory([out/'R-lib']))
    except Exception as error:
        state.update(status='SKIP3_CELLCHAT_REFERENCE_UNAVAILABLE',detail=str(error));(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);state.update(preexisting_libraries_unchanged=before==after,preexisting_file_count=sum(map(len,before.values())),source_pins=bindings)
        if before!=after:state.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED',detail='Inspect before/after inventories')
        if source is not None and any(pin(source/n)!=v for n,v in original_members.items()):state.update(status='FAIL2_ORIGINAL_CELLCHAT_SOURCE_CHANGED',detail='Captured original source changed')
        if any(pin(ROOT/n)!=v for n,v in bindings.items()):state.update(status='FAIL2_CELLCHAT_SETUP_SOURCE_CHANGED',detail='Setup source/compiler binding changed')
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','Original package setup only; user-data analysis is not admitted.')+'\n\nRequired action: '+action+'\n',encoding='utf8')
    print(state['status'],state.get('detail',''),flush=True);return 0 if state['status']=='INSTALLED_ORIGINAL_CELLCHAT_REFERENCE_ONLY' else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/library guards')
    raise SystemExit(main())
