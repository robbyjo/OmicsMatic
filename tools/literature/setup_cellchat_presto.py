# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install original Presto1.0.0 in a new private library without altering CellChat or dependencies."""
import argparse,json,os,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save
from setup_chipseeker_reference import inventory

BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    setup=BASE/'reference-setup-1';ready=json.loads((setup/'COMPLETED.json').read_bytes());assert ready['status']=='INSTALLED_ORIGINAL_CELLCHAT_REFERENCE_ONLY'
    libraries=[setup/'R-lib',*[Path(p) for p in json.loads((setup/'STARTED.json').read_bytes())['libraries']]]
    before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    action='If exact Presto1.0.0/compiler installation is unavailable or forbidden, SKIP dependent original fast-Wilcoxon analysis. Do not change do.fast automatically. Supply the named cause where permitted in NEW private paths and retry NEW outputs; preserve this attempt and all existing libraries. Package health is not user analysis.'
    state=dict(status='SKIP3_ORIGINAL_PRESTO_REFERENCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,required_action=action)
    pins={}
    try:
        assert a.allow_install,'Installation is not authorized; dependent fast-Wilcoxon analysis skipped'
        intake=BASE/'presto-source-intake-1';source_receipt=json.loads((intake/'COMPLETED.json').read_bytes());assert source_receipt['status']=='PASS_ORIGINAL_PRESTO_1_0_0_SOURCE_ONLY'
        archive=intake/'presto-original.tar.gz';desc=intake/'source/DESCRIPTION';assert pin(archive)==source_receipt['archive'] and pin(desc)==source_receipt['description']
        health_path=ROOT/'build/literature-review/monocle-runtime/compiler-health-3/HEALTH.json';health=json.loads(health_path.read_bytes());assert health['status']=='compiled-and-loaded-private-CXX20-R-extension-without-shell-warnings'
        compiler=Path(health['compiler_root']);host=Path(health['make_root']);makevars=Path(health['Makevars'])
        override=out/'DECLARED-OVERRIDES.json';save(override,dict(presto=dict(package='presto',version='1.0.0',type='source',url='https://codeload.github.com/immunogenomics/presto/tar.gz/'+source_receipt['commit'],archive=str(archive),description=str(desc))))
        script=ROOT/'tools/literature/install_cellchat_presto.R'
        for file in [Path(__file__).resolve(),script,health_path,makevars,archive,desc]:pins[file.relative_to(ROOT).as_posix()]=pin(file)
        for file in [Path(__file__).resolve(),script]:
            target=out/'source-snapshot'/file.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(file.read_bytes())
        save(out/'STARTED.json',dict(source_pins=pins,libraries=list(map(str,libraries)),source_profile=source_receipt,analysis_admitted=False))
        env=environment();env.update(MSYSTEM='MSYS',R_CUSTOM_TOOLS_SOFT=compiler.as_posix(),R_CUSTOM_TOOLS_PATH=(compiler/'bin').as_posix()+';'+host.as_posix(),R_MAKEVARS_USER=makevars.as_posix(),TMPDIR=str(out/'tmp'))
        (out/'tmp').mkdir();env['PATH']=str(compiler/'bin')+os.pathsep+str(host)+os.pathsep+env['PATH']
        command=[str(RSCRIPT),'--vanilla',str(script),str(out/'R-lib'),str(out),str(intake/'source'),str(override),*map(str,libraries)]
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:
            run=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1800)
        save(out/'process.json',dict(command=command,exit_code=run.returncode));report=json.loads((out/'R-RESULT.json').read_bytes());state.update(report)
        assert run.returncode==0 and report['status']=='INSTALLED_ORIGINAL_PRESTO_REFERENCE_ONLY',report.get('detail','Presto original namespace/native health failed')
        state.update(source_profile=source_receipt,downloads={p.name:pin(p) for p in (out/'downloads').iterdir() if p.is_file()},private_library_inventory=inventory([out/'R-lib']))
    except Exception as error:state.update(status='SKIP3_ORIGINAL_PRESTO_REFERENCE_UNAVAILABLE',detail=str(error));(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);state.update(source_pins=pins,preexisting_libraries_unchanged=before==after)
        if before!=after:state.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED',detail='Inspect before/after library inventories')
        if any(pin(ROOT/n)!=v for n,v in pins.items()):state.update(status='FAIL2_PRESTO_SETUP_SOURCE_CHANGED',detail='Setup/reference source changed')
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','Original Presto setup only.')+'\n\nRequired action: '+action+'\n',encoding='utf8')
    print(state['status'],state.get('detail',''),flush=True);return 0 if state['status']=='INSTALLED_ORIGINAL_PRESTO_REFERENCE_ONLY' else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/library checks')
    raise SystemExit(main())
