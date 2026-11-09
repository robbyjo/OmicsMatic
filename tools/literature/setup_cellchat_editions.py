# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install additional unchanged CellChat source editions in separate private libraries."""
import argparse,json,os,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save
from setup_chipseeker_reference import inventory

BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--source-label',choices=['1.0.0','1.1.3','2.1.2'],required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='SKIP3_ORIGINAL_CELLCHAT_EDITION_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,installation_authorized=a.allow_install)
    action='If an exact original edition/dependency/compiler is unavailable or installation is forbidden, SKIP dependent CellChat analysis. Read WARNINGS.md and installer stdout/stderr. Supply the named permitted prerequisite in NEW private paths and retry NEW output paths. Preserve all earlier attempts/libraries and original source. Do not patch upstream functions, use another CellChat edition or silently replace historical algorithms. Setup/constructor/DLL health alone admits no analysis.'
    libraries=[];before={};bindings={};members={};source=None
    try:
        if not a.allow_install:raise RuntimeError('Installation not authorized; dependent analysis skipped')
        setup=BASE/'reference-setup-1';ready=json.loads((setup/'COMPLETED.json').read_bytes());assert ready['status']=='INSTALLED_ORIGINAL_CELLCHAT_REFERENCE_ONLY'
        libraries=[BASE/'presto-reference-setup-2/R-lib',setup/'R-lib',*[Path(p) for p in json.loads((setup/'STARTED.json').read_bytes())['libraries']]]
        libraries=list(dict.fromkeys(p.resolve() for p in libraries));before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
        intake=BASE/'source-intake-1';receipt=json.loads((intake/'COMPLETED.json').read_bytes());entry=next(r for r in receipt['candidates'] if r['label']==a.source_label)
        source=intake/a.source_label/'source';memberPath=intake/a.source_label/'original-members.json';assert pin(memberPath)==entry['members'];members=json.loads(memberPath.read_bytes());assert all(pin(source/n)==v for n,v in members.items())
        healthPath=ROOT/'build/literature-review/monocle-runtime/compiler-health-3/HEALTH.json';health=json.loads(healthPath.read_bytes());assert health['status']=='compiled-and-loaded-private-CXX20-R-extension-without-shell-warnings'
        compiler=Path(health['compiler_root']);host=Path(health['make_root']);makevars=Path(health['Makevars']);script=ROOT/'tools/literature/install_cellchat_editions.R'
        overrides=out/'DECLARED-OVERRIDES.json';save(overrides,{})
        for file in [Path(__file__).resolve(),script,memberPath,healthPath,makevars,compiler/'bin/g++.exe',host/'make.exe',host/'bash.exe']:bindings[file.relative_to(ROOT).as_posix()]=pin(file)
        for file in [Path(__file__).resolve(),script]:
            target=out/'source-snapshot'/file.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(file.read_bytes())
        work=out/'work-source';shutil.copytree(source,work);assert all(pin(work/n)==v for n,v in members.items())
        save(out/'STARTED.json',dict(source_label=a.source_label,candidate=entry,libraries=list(map(str,libraries)),source_pins=bindings,original_members=members,analysis_admitted=False))
        env=environment();env.update(MSYSTEM='MSYS',R_CUSTOM_TOOLS_SOFT=compiler.as_posix(),R_CUSTOM_TOOLS_PATH=(compiler/'bin').as_posix()+';'+host.as_posix(),R_MAKEVARS_USER=makevars.as_posix(),TMPDIR=str(out/'tmp'));(out/'tmp').mkdir();env['PATH']=str(compiler/'bin')+os.pathsep+str(host)+os.pathsep+env['PATH']
        command=[str(RSCRIPT),'--vanilla',str(out/'source-snapshot/tools/literature/install_cellchat_editions.R'),str(out/'R-lib'),str(out),str(work),str(overrides),entry['declared_version'],*map(str,libraries)]
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:run=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=3600)
        save(out/'process.json',dict(command=command,exit_code=run.returncode));report=json.loads((out/'R-RESULT.json').read_bytes());state.update(report)
        assert run.returncode==0 and report['status']=='INSTALLED_ORIGINAL_CELLCHAT_EDITION_ONLY',report.get('detail','Read installer stderr')
        codeData={n:v for n,v in members.items() if n.endswith(('.R','.cpp','.h','.rda')) or n in ['DESCRIPTION','NAMESPACE']};assert all(pin(work/n)==v for n,v in codeData.items())
        state.update(source_label=a.source_label,candidate=entry,source_code_data_unchanged=True,private_library_inventory=inventory([out/'R-lib']),downloads={p.name:pin(p) for p in (out/'downloads').iterdir() if p.is_file()})
    except Exception as error:state.update(status='SKIP3_ORIGINAL_CELLCHAT_EDITION_UNAVAILABLE',detail=str(error));(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);state.update(source_pins=bindings,preexisting_libraries_unchanged=before==after,required_action=action)
        if before!=after:state.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED',detail='Inspect library inventories')
        if source is not None and any(pin(source/n)!=v for n,v in members.items()):state.update(status='FAIL2_ORIGINAL_SOURCE_CHANGED',detail='Original source changed')
        if any(pin(ROOT/n)!=v for n,v in bindings.items()):state.update(status='FAIL2_EDITION_SETUP_SOURCE_CHANGED',detail='Current setup/compiler source binding changed')
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','Original package setup only; analysis not admitted.')+'\n\nRequired action: '+action+'\n',encoding='utf8')
    print(state['status'],state.get('detail',''),flush=True);return 0 if state['status']=='INSTALLED_ORIGINAL_CELLCHAT_EDITION_ONLY' else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable edition/source/library checks')
    raise SystemExit(main())
